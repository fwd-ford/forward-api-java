# Arquitetura do forward-api-java

Documento de arquitetura da API do **ForwardService** (Challenge Ford x FIAP 2026), entregue na
Sprint 3 da disciplina de Arquitetura Orientada a Serviços e Web Services.

Contexto da Sprint 3: a autenticação deixou de depender do Supabase Auth. A API **emite e valida
os próprios JWTs** e guarda os usuários na tabela `app_users`. Em produção ela roda como web
service Docker no **Render** e usa o **PostgreSQL do Supabase** (projeto restaurado, região
sa-east-1), cujo esquema foi criado pelas migrations 001 a 013 do `forward-infra`. Para
demonstrações locais e para os testes, o perfil `demo`/`test` sobe um PostgreSQL 16 embarcado
(sem Docker) com as mesmas migrations.

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
| **Render** | Hospedagem do container (plano free), TLS na borda, health check em `/health`, deploy automático pela `main` (Blueprint `render.yaml`). | Proxy reverso com `X-Forwarded-For`/`X-Forwarded-Proto`. |
| **Supabase PostgreSQL** | Fonte da verdade em produção (sa-east-1). Esquema do `forward-infra` (001 a 013) + Flyway V14 a V16. | JDBC com TLS pelo Session pooler (IPv4, porta 5432). |
| **PostgreSQL 16 embarcado** | Banco efêmero dos perfis `demo` e `test`. | JDBC local. |

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
| `util` | `LogSanitizer` (remove quebras de linha e caracteres de controle antes de logar) e `SecureXml` (fábrica DOM sem DOCTYPE nem entidades externas). | |
| `config` | `AppProperties` tipado, OpenAPI, `EmbeddedPostgresConfig` (perfis `demo` e `test`), `Clock`. | |

## 3. Fluxo de autenticação e autorização

![Fluxo de autenticação](img/03-fluxo-autenticacao.png)

### JWT emitido pela API

| Item | Valor |
|---|---|
| Algoritmo | HS256 (HMAC-SHA256). Tokens com outro `alg` ou sem assinatura são recusados. |
| Segredo | `JWT_SECRET`, mínimo 32 bytes. Com `ENV=production` (padrão do perfil `prod`) a aplicação não sobe sem ele; no Render ele é gerado pelo Blueprint. Fora de produção, sem segredo, a API gera uma chave aleatória por boot e registra um WARN. |
| Claims | `iss=forward-api`, `aud=forward-app`, `sub` (UUID do usuário), `email`, `name`, `role` (ATENDENTE, GESTOR ou ADMIN), `dealer_id` (só perfis com concessionária), `token_version`, `iat`, `nbf`, `exp`, `jti` aleatório. |
| Expiração | `JWT_EXPIRATION_MINUTES` (padrão 60). Tolerância de relógio de 30 s. |
| Validação | Assinatura, algoritmo, `exp`/`nbf`, `iss`, `aud`, presença e formato de `sub`, `jti`, `email`, `name`, `role`, `token_version`, `dealer_id` obrigatório para ATENDENTE/GESTOR. Depois, comparação com o estado atual do usuário (revogação, abaixo). |
| Uso das claims | `role` vira a authority `ROLE_<ROLE>` (regras de URL e `@PreAuthorize`); `dealer_id` define o escopo de dados; `sub` identifica o autor no `audit_log`. |

### Login

- `POST /api/v1/auth/login` é público e limitado a 5 tentativas por minuto por IP (429 com
  `Retry-After`).
- Senhas só existem como hash BCrypt; um `CHECK` no banco impede gravar texto puro. Os usuários de
  demonstração são criados com `crypt()`/`gen_salt('bf')` do pgcrypto no próprio banco, então
  nenhum hash fica versionado.
- E-mail inexistente e senha errada devolvem exatamente o mesmo 401 `AUTH_INVALID_CREDENTIALS`;
  para o tempo de resposta também não revelar contas, um hash BCrypt fictício é verificado quando
  o e-mail não existe. `AUTH_USER_DISABLED` só aparece depois que a senha confere.
- Toda tentativa (sucesso e falha) é gravada no `audit_log` com IP, user agent e request id.

### Filtro de autenticação

O `JwtAuthenticationFilter` separa duas etapas: `resolveCredentials` verifica a credencial
enviada (digest SHA-256 da `X-API-Key` comparado em tempo constante com `INTERNAL_API_KEY`, ou
JWT validado pelo `JwtService` e conferido com o estado atual do usuário) e devolve um principal
verificado ou o motivo da falha; só um principal verificado é gravado no `SecurityContext`.
Qual header chegou apenas escolhe o verificador, nunca concede acesso por si só. Rotas públicas
são decididas pelos request matchers do Spring Security, não por comparação manual de strings.

### Revogação de tokens

Depois de validar o JWT, o filtro compara as claims com o estado atual do usuário em `app_users`
(existe, `active`, `role`, `dealer_id` e `token_version`), lido pelo `UserStateCache` (cache em
memória de 30 s, configurável em `JWT_USER_STATE_CACHE_TTL`). Se qualquer item diverge, a
requisição segue sem autenticação e a rota protegida responde **401 `AUTH_TOKEN_REVOKED`**. O
ADMIN incrementa o `token_version` (migration V16) em todo `PATCH` que envia `role`, `active`,
`dealer_id` ou `password`, e o `DELETE` remove o usuário; nos dois casos o cache é invalidado
imediatamente (e de novo após o commit). Assim, um usuário desativado, excluído, rebaixado ou
movido de concessionária perde o acesso na próxima requisição, e não só quando o token expira.
O caminho `X-API-Key` (SERVICE) não consulta `app_users`.

## 4. Cadeia de filtros

![Cadeia de filtros](img/04-cadeia-filtros.png)

1. **RequestIdFilter**: aceita um `X-Request-Id` seguro (ou gera um UUID), devolve no header e
   no corpo de erro (`request_id`) e coloca no MDC dos logs.
2. **SecurityHeadersFilter**: CSP, HSTS, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`,
   `Permissions-Policy`. Roda antes da autenticação, então 401/403/429 também saem protegidos.
3. **RateLimitFilter**: Bucket4j por IP do cliente (global e, para o login, 5 por minuto). Fica
   antes do Spring Security para que rajadas sem token ou com token inválido também sejam
   limitadas. O endpoint de login é reconhecido por um `PathPatternRequestMatcher`. Atrás do
   proxy do Render, o IP vem do `RemoteIpValve` do Tomcat (`FORWARD_HEADERS_STRATEGY=native`),
   que só confia em `X-Forwarded-For` enviado por proxies internos.
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

A autorização é sempre da API. No Supabase ela conecta como dono das tabelas, então as políticas
RLS do banco (feitas para o Supabase Auth) não se aplicam a ela. A tabela nova `app_users` tem RLS
habilitado sem políticas e os grants de `anon`/`authenticated` revogados, para que a Data API do
Supabase nunca exponha os hashes.

## 6. Dados e perfis de execução

| Perfil | Banco | Migrations | Dados | Uso |
|---|---|---|---|---|
| `prod` | PostgreSQL de `DATABASE_URL` (Supabase) | baseline 13, depois V14 a V16 (banco vazio: V1 a V16) | bootstrap | Render |
| `demo` | PostgreSQL 16 embarcado (Zonky), efêmero | V1 a V16 | bootstrap + seed | demonstração local |
| `test` | PostgreSQL 16 embarcado | V1 a V16 | bootstrap + seed | testes automatizados |
| (padrão) | PostgreSQL de `DATABASE_URL` | V1 a V16 | nenhum | banco genérico |

- V1 a V13 são cópias literais das migrations do `forward-infra` (dealers, customers, vehicles,
  service_orders, churn_scores, leads, communications, lead_outcomes, audit_log, RLS, triggers,
  campos de evento de serviço, LGPD). No Supabase elas já foram aplicadas fora do Flyway, por isso
  o perfil `prod` usa `baseline-on-migrate` com `baseline-version: 13`.
- V14 cria `app_users` (e-mail único sem diferenciar maiúsculas, perfil com `CHECK`, hash BCrypt
  obrigatório, concessionária obrigatória para ATENDENTE/GESTOR, RLS sem políticas).
- V15 adiciona `leads.notes` (`ADD COLUMN IF NOT EXISTS`) e a chave natural de `service_orders`,
  criada somente se não houver duplicatas no banco existente.
- V16 adiciona `app_users.token_version` (padrão 0), usado na revogação de tokens.
- `db/bootstrap/R__bootstrap_demo_data.sql` (perfis `prod`, `demo` e `test`) é idempotente e
  seguro sobre o seed antigo do `forward-infra`: referencia concessionárias pelo código, usa
  `ON CONFLICT DO NOTHING` e só cria score de churn para clientes sem score atual. Garante 10
  concessionárias, 16 clientes com veículos e scores, 9 eventos de serviço, 22 leads (10 na F0001 e
  7 na F0002) e os usuários de demonstração: GESTOR/ATENDENTE com `DEMO_USERS_PASSWORD` (padrão
  `Forward@2026`, risco aceito: baixo privilégio e dados sintéticos) e o ADMIN somente quando
  `ADMIN_BOOTSTRAP_PASSWORD` está definida (no perfil `prod` não há ADMIN com senha publicada; um
  ADMIN antigo que ainda tenha a senha publicada é desativado ou recebe a senha configurada). As
  senhas chegam ao SQL como placeholders do Flyway, com aspas escapadas, e não são registradas em
  log.
- `db/seed/R__seed_demo_data.sql` (somente `demo` e `test`) acrescenta `gestor2@forward.dev` e o
  usuário desativado `inativo@forward.dev`.
- O cenário do Supabase é reproduzido no teste `ProdMigrationIT`: esquema criado sem histórico do
  Flyway + seed antigo, depois a inicialização de produção (baseline, V14 a V16, bootstrap) e uma
  segunda execução para provar a idempotência.

## 7. Decisões de projeto

| Decisão | Motivo |
|---|---|
| JWT próprio HS256 em vez de IdP externo | Um único serviço emite e valida, então uma chave simétrica é suficiente e simples de operar (um segredo gerado pelo Render). |
| API stateless, CSRF desabilitado | Não há sessão nem cookie; cada requisição carrega o header `Authorization`, que um site de terceiros não consegue forjar. |
| Render + Supabase em produção | Hospedagem Docker gratuita com deploy pela `main` e o banco já existente do projeto (restaurado). Conexão pelo Session pooler porque o Render não tem IPv6 e o Transaction pooler quebra prepared statements. |
| Baseline 13 no Flyway | O esquema do Supabase veio do `forward-infra` sem histórico do Flyway; o baseline evita reaplicar 001 a 013 e V14+ são escritas para rodar sobre esse esquema. |
| PostgreSQL embarcado nos perfis demo e test | Demonstração e testes sem Docker e sem banco hospedado, com PostgreSQL 16 e as mesmas migrations. |
| Rate limit antes da autenticação | Protege também contra rajadas de requisições sem token ou com token inválido. |
| 422 para referência inexistente no corpo | O recurso da URL existe e o JSON é válido; o problema é semântico (VIN ou concessionária inexistente). 404 fica reservado para a URL. |
| 409 para conflito de estado | Transição de lead inválida, e-mail duplicado, evento duplicado, autoexclusão do admin. |
| Lista de leads como array JSON + `X-Total-Count` | Mantém compatibilidade com o app mobile e ainda expõe o total para paginação. |
| Auditoria na mesma transação | A alteração do lead e o registro no `audit_log` são confirmados (ou desfeitos) juntos. |
| Erros RFC 7807 com `code` estável | O cliente decide pelo `code` (ex.: `LEAD_INVALID_TRANSITION`) e exibe o `detail` em pt-BR. |
| Hashes de senha calculados no banco | O repositório não contém hashes (scanners de segredo) e cada ambiente gera o seu sal. |
| Revogação por `token_version` + cache curto | Mantém a API stateless (sem lista de tokens) e ainda derruba na hora o acesso de quem foi desativado, excluído, rebaixado ou mudou de concessionária; o cache de 30 s evita uma consulta ao banco por requisição. |
