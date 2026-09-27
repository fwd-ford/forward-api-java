# Arquitetura do forward-api-java

Documento de arquitetura da API do **ForwardService** (Challenge Ford x FIAP 2026), entregue na
Sprint 3 da disciplina de Arquitetura Orientada a Serviços e Web Services.

Contexto da Sprint 3: o projeto Supabase (banco gerenciado e autenticação) foi excluído. A API
passou a ser **autocontida**: emite e valida os próprios JWTs, guarda os usuários na tabela
`app_users` e, no perfil `demo`, sobe um PostgreSQL 16 embarcado (sem Docker) com as migrations e
um seed de demonstração.

Os diagramas abaixo foram escritos em Mermaid (fontes em [`docs/diagrams/`](diagrams/)) e
renderizados em PNG pela API do kroki.io.

## 1. Componentes da solução

![Componentes da solução](img/01-componentes.png)

| Componente | Responsabilidade | Como fala com a API |
|---|---|---|
| **forward-mobile** (React Native + Expo) | App do atendente e do gestor: fila de leads, detalhe do cliente, registro de contato. | HTTPS + JSON, `Authorization: Bearer <JWT>` obtido em `POST /api/v1/auth/login`. |
| **forward-web** (SvelteKit) | Painel do gestor e visão Ford regional/nacional. | HTTPS + JSON com JWT. |
| **Sistemas legados** | Consulta de veículo por VIN em contrato SOAP. | SOAP 1.1 (`/soap/vehicles`, WSDL público) com JWT. |
| **n8n** | Automação: alertas de leads críticos, WhatsApp, rotinas agendadas. | HTTPS + JSON com `X-API-Key` (perfil técnico `SERVICE`). |
| **forward-ml** (Python + XGBoost) | Calcula o score de churn e gera leads em lote. | Grava `churn_scores` e `leads` no PostgreSQL. |
| **forward-api-java** (este repositório) | Única porta de entrada dos dados: autenticação, autorização por perfil e por concessionária, regras de negócio (máquina de estados de leads, eventos de serviço), auditoria, erros padronizados. | Servidor. |
| **PostgreSQL 16** | Fonte da verdade. Esquema versionado por Flyway (V1 a V15). | JDBC (HikariCP). |
| **Fly.io** | Hospedagem (região `gru`), TLS na borda, health check em `/health`. | Proxy reverso com `Fly-Client-IP` e `X-Forwarded-*`. |

## 2. Camadas internas da API

![Camadas da API](img/02-camadas.png)

| Pacote | Responsabilidade | Não faz |
|---|---|---|
| `web` | Controllers REST: parse e validação de entrada (UUID, VIN, enums, paginação, Bean Validation), DTOs em snake_case, status HTTP, `Location`, `X-Total-Count`. Filtros `RequestIdFilter`, `SecurityHeadersFilter`, CORS. | Regra de negócio, SQL. |
| `security` | `JwtService` (emissão e validação), `JwtAuthenticationFilter`, `SecurityConfig`, `RateLimitFilter`, `Role`, `AuthenticatedUser`, handlers de 401/403. | Consultas de domínio. |
| `service` | Regras de negócio e autorização fina: `@PreAuthorize` por perfil, escopo por concessionária (403 `ACCESS_OTHER_DEALER`), máquina de estados do lead, validação de referências (422), duplicidade (409), auditoria. | HTTP, SQL. |
| `repository` | Acesso a dados com `NamedParameterJdbcTemplate`. Todo valor é parâmetro nomeado; filtros dinâmicos usam fragmentos fixos. | Regras de acesso. |
| `soap` | Endpoint Spring WS `GetVehicle` (contrato `xsd/vehicles.xsd`), reutiliza `VehicleService`. | Duplicar regra do REST. |
| `error` | `GlobalExceptionHandler` (estende `ResponseEntityExceptionHandler`), `Problems`, `ProblemErrorController`: todo erro sai em RFC 7807. | Expor stack trace, SQL ou nomes de classe. |
| `config` | `AppProperties` tipado, OpenAPI, `EmbeddedPostgresConfig` (perfis `demo` e `test`), `Clock`. | |

## 3. Fluxo de autenticação e autorização

![Fluxo de autenticação](img/03-fluxo-autenticacao.png)

### JWT emitido pela API

| Item | Valor |
|---|---|
| Algoritmo | HS256 (HMAC-SHA256). Tokens com outro `alg` ou sem assinatura são recusados. |
| Segredo | `JWT_SECRET`, mínimo 32 bytes. Com `ENV=production` a aplicação não sobe sem ele. Fora de produção, sem segredo, gera uma chave aleatória por boot e registra um WARN. |
| Claims | `iss=forward-api`, `aud=forward-app`, `sub` (UUID do usuário), `email`, `name`, `role` (ATENDENTE, GESTOR ou ADMIN), `dealer_id` (só perfis com concessionária), `iat`, `nbf`, `exp`, `jti` aleatório. |
| Expiração | `JWT_EXPIRATION_MINUTES` (padrão 60). Tolerância de relógio de 30 s. |
| Validação | Assinatura, algoritmo, `exp`/`nbf`, `iss`, `aud`, presença e formato de `sub`, `jti`, `email`, `name`, `role`, `dealer_id` obrigatório para ATENDENTE/GESTOR. |
| Uso das claims | `role` vira a authority `ROLE_<ROLE>` (regras de URL e `@PreAuthorize`); `dealer_id` define o escopo de dados; `sub` identifica o autor no `audit_log`. |

### Login

- `POST /api/v1/auth/login` é público e limitado a 5 tentativas por minuto por IP (429 com `Retry-After`).
- Senhas só existem como hash BCrypt (custo 10); um `CHECK` no banco impede gravar texto puro.
- E-mail inexistente e senha errada devolvem exatamente o mesmo 401 `AUTH_INVALID_CREDENTIALS`;
  para o tempo de resposta também não revelar contas, um hash BCrypt fictício é verificado quando
  o e-mail não existe. `AUTH_USER_DISABLED` só aparece depois que a senha confere.
- Toda tentativa (sucesso e falha) é gravada no `audit_log` com IP, user agent e request id.

### Chamadas servidor-a-servidor

O header `X-API-Key` (valor em `INTERNAL_API_KEY`) autentica integrações como o n8n com o perfil
`SERVICE`. A comparação é feita sobre o SHA-256 dos dois lados com `MessageDigest.isEqual`, em
tempo constante e sem vazar o tamanho da chave.

## 4. Cadeia de filtros

![Cadeia de filtros](img/04-cadeia-filtros.png)

1. **RequestIdFilter**: aceita um `X-Request-Id` seguro (ou gera um UUID), devolve no header e
   no corpo de erro (`request_id`) e coloca no MDC dos logs.
2. **SecurityHeadersFilter**: CSP, HSTS, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`,
   `Permissions-Policy`. Roda antes da autenticação, então 401/403/429 também saem protegidos.
3. **RateLimitFilter**: Bucket4j por IP do cliente (60 req/min global; login 5/min). Fica antes
   do Spring Security para que rajadas de requisições sem token ou com token inválido também
   sejam limitadas.
4. **Spring Security**: `CorsFilter` (allowlist), `JwtAuthenticationFilter`,
   `ExceptionTranslationFilter` (401/403 em problem+json) e `AuthorizationFilter`.

A ordem é garantida por `@Order(HIGHEST_PRECEDENCE + 10/20/30)` nos filtros servlet, todos
anteriores ao `springSecurityFilterChain` (ordem -100).

## 5. Matriz de permissões

Autorização em duas camadas (defesa em profundidade): regras de URL no `SecurityConfig`, para que
um perfil sem permissão receba 403 antes de qualquer validação de corpo, e `@PreAuthorize` nos
métodos de serviço. O escopo por concessionária é verificado no serviço, com o `dealer_id` do token.

| Recurso | ATENDENTE | GESTOR | ADMIN | SERVICE (X-API-Key) |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | público | público | público | público |
| `GET /api/v1/me` | sim | sim | sim | sim |
| `GET /api/v1/leads`, `GET /api/v1/leads/{id}` | própria concessionária | própria concessionária | todas (filtro `dealer_id`) | todas |
| `PATCH /api/v1/leads/{id}` | própria concessionária | própria concessionária | todas | todas |
| `GET /api/v1/customers/{id}` e `/{id}/score` | própria concessionária | própria concessionária | todas | todas |
| `GET /api/v1/scores/{customerId}` (deprecado) | própria concessionária | própria concessionária | todas | todas |
| `GET /api/v1/vehicles/{vin}` e SOAP `GetVehicle` | própria concessionária | própria concessionária | todas | todas |
| `GET /api/v1/service-events`, `/{id}` | própria concessionária | própria concessionária | todas | todas |
| `POST /api/v1/service-events`, `PUT /{id}` | 403 | própria concessionária | todas | todas |
| `DELETE /api/v1/service-events/{id}` | 403 | 403 | sim | 403 |
| `/api/v1/users` (GET, POST, PATCH, DELETE) | 403 | 403 | sim | 403 |

"Própria concessionária" significa: recurso de outra concessionária responde **403
`ACCESS_OTHER_DEALER`**; recurso inexistente responde **404**. Um cliente ou veículo pertence à
concessionária quando ela atende o veículo (`vehicles.current_dealer_id`) ou possui um lead dele.

## 6. Dados e perfis de execução

| Perfil | Banco | Migrations | Seed | Uso |
|---|---|---|---|---|
| (padrão) | PostgreSQL de `DATABASE_URL` | V1 a V15 | não | produção com banco externo |
| `demo` | PostgreSQL 16 embarcado (Zonky), efêmero | V1 a V15 | sim | demonstração local e Fly.io |
| `seed` | PostgreSQL de `DATABASE_URL` (banco vazio) | V1 a V15 | sim | ambiente local com PostgreSQL |
| `test` | PostgreSQL 16 embarcado | V1 a V15 | sim | testes automatizados |

- V1 a V13 são cópias literais das migrations do `forward-infra` (dealers, customers, vehicles,
  service_orders, churn_scores, leads, communications, lead_outcomes, audit_log, RLS, triggers,
  campos de evento de serviço, LGPD).
- V14 cria `app_users` (e-mail único sem diferenciar maiúsculas, perfil com `CHECK`, hash BCrypt
  obrigatório, concessionária obrigatória para ATENDENTE/GESTOR).
- V15 adiciona `leads.notes` e a chave natural de `service_orders` (evita eventos duplicados).
- O seed (`db/seed/R__seed_demo_data.sql`, idempotente) cria 10 concessionárias, 12 clientes com
  veículos e scores, 9 eventos de serviço, 18 leads em 4 concessionárias com todos os status e
  prioridades, e 6 usuários.

## 7. Decisões de projeto

| Decisão | Motivo |
|---|---|
| JWT próprio HS256 em vez de IdP externo | O Supabase Auth deixou de existir; um único serviço emite e valida, então uma chave simétrica é suficiente e simples de operar (um segredo no Fly). |
| API stateless, CSRF desabilitado | Não há sessão nem cookie; cada requisição carrega o header `Authorization`, que um site de terceiros não consegue forjar. |
| PostgreSQL embarcado no perfil demo | Demonstração e testes sem Docker e sem banco hospedado, com o mesmo PostgreSQL 16 e as mesmas migrations de produção. |
| Rate limit antes da autenticação | Protege também contra rajadas de requisições sem token ou com token inválido (antes elas passavam livres). |
| 422 para referência inexistente no corpo | O recurso da URL existe e o JSON é válido; o problema é semântico (VIN ou concessionária inexistente). 404 fica reservado para a URL. |
| 409 para conflito de estado | Transição de lead inválida, e-mail duplicado, evento duplicado, autoexclusão do admin. |
| Lista de leads como array JSON + `X-Total-Count` | Mantém compatibilidade com o app mobile e ainda expõe o total para paginação. |
| Auditoria na mesma transação | A alteração do lead e o registro no `audit_log` são confirmados (ou desfeitos) juntos. |
| Erros RFC 7807 com `code` estável | O cliente decide pelo `code` (ex.: `LEAD_INVALID_TRANSITION`) e exibe o `detail` em pt-BR. |
