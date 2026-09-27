# forward-api-java

![org](https://img.shields.io/badge/org-fwd--ford-blue?style=flat-square)
![stack](https://img.shields.io/badge/stack-Java_17_·_Spring_Boot_3.5-333?style=flat-square)
![api](https://img.shields.io/badge/API-REST_nível_2_·_SOAP-6f42c1?style=flat-square)
![auth](https://img.shields.io/badge/auth-JWT_HS256_·_RBAC-green?style=flat-square)

API REST e SOAP do **ForwardService**, plataforma de retenção de clientes da rede Ford
(Challenge Ford x FIAP 2026). Este repositório é a entrega da Sprint 3 da disciplina de
**Arquitetura Orientada a Serviços e Web Services**.

- **Autocontida**: o projeto Supabase (banco e autenticação) foi excluído. A API emite e valida os
  próprios JWTs e, no perfil `demo`, roda com um **PostgreSQL 16 embarcado**, sem Docker e sem
  nenhum serviço externo.
- **Segurança**: Spring Security stateless, JWT HS256 com `iss`, `aud`, `exp` e `jti`, perfis
  ATENDENTE, GESTOR e ADMIN, escopo de dados por concessionária, rate limit, auditoria.
- **REST nível 2**: recursos, verbos HTTP corretos (GET, POST, PUT, PATCH, DELETE), status
  coerentes (200, 201 + `Location`, 204, 400, 401, 403, 404, 405, 409, 415, 422, 429).
- **Erros padronizados** em RFC 7807 (`application/problem+json`) com mensagens em pt-BR.
- **171 testes automatizados** (84 unitários e 87 de integração HTTP contra PostgreSQL real
  embarcado), cobertura de linhas em torno de 90% (JaCoCo).

## Sumário

1. [Arquitetura](#arquitetura)
2. [Stack](#stack)
3. [Como executar](#como-executar)
4. [Variáveis de ambiente](#variáveis-de-ambiente)
5. [Usuários de demonstração](#usuários-de-demonstração)
6. [Autenticação passo a passo](#autenticação-passo-a-passo)
7. [Perfis e permissões](#perfis-e-permissões)
8. [Endpoints](#endpoints)
9. [REST nível 2](#rest-nível-2)
10. [Formato de erro](#formato-de-erro)
11. [Testes](#testes)
12. [SOAP](#soap)
13. [OpenAPI, Swagger e Postman](#openapi-swagger-e-postman)
14. [Deploy no Fly.io](#deploy-no-flyio)
15. [Solução de problemas](#solução-de-problemas)
16. [Estrutura do projeto](#estrutura-do-projeto)

## Arquitetura

![Componentes da solução](docs/img/01-componentes.png)

A API é a única porta de entrada dos dados para o app mobile, o painel web, integrações (n8n) e
sistemas SOAP. Detalhes, fluxo de autenticação, cadeia de filtros e decisões de projeto em
[`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

![Fluxo de autenticação](docs/img/03-fluxo-autenticacao.png)

## Stack

| Tema | Tecnologia |
|---|---|
| Linguagem e framework | Java 17, Spring Boot 3.5.14 (Web, Security, Validation, JDBC, Web Services, Actuator) |
| Banco | PostgreSQL 16; Flyway (V1 a V15); PostgreSQL embarcado Zonky nos perfis `demo` e `test` |
| Acesso a dados | `NamedParameterJdbcTemplate` com SQL parametrizado (sem ORM) |
| Autenticação | JWT HS256 emitido pela própria API (JJWT 0.12), senhas BCrypt |
| Autorização | Spring Security (regras de URL + `@PreAuthorize`) e escopo por concessionária |
| Proteções | Bucket4j (rate limit por IP), CORS com allowlist, headers de segurança, auditoria em `audit_log` |
| Documentação | springdoc-openapi (Swagger UI), `openapi.yaml`, coleção Postman |
| Qualidade | JUnit 5, MockMvc, JaCoCo, Spotless, Checkstyle, SpotBugs + FindSecBugs, Trivy, gitleaks |
| Logs | Logback (JSON com Logstash encoder em produção/demo) com `request_id` em todas as linhas |

## Como executar

Pré-requisito: **Java 17** (Temurin recomendado). O Maven Wrapper baixa o Maven na primeira
execução. Não é preciso Docker nem banco instalado.

### 1. Perfil demo (sem Docker, sem banco externo)

Sobe um PostgreSQL 16 embarcado, aplica as migrations e o seed de demonstração e inicia a API em
`http://localhost:8080`.

```bash
# Linux, macOS ou Git Bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Windows PowerShell ou cmd
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"

# Ou com o JAR
./mvnw -DskipTests package
java -jar target/forward-api.jar --spring.profiles.active=demo
```

- Os dados são efêmeros: cada inicialização recria o banco a partir do seed.
- Sem `JWT_SECRET` a API gera uma chave aleatória a cada boot (aviso WARN no log); tokens antigos
  deixam de valer após reiniciar. Para tokens estáveis: `JWT_SECRET=$(openssl rand -base64 48)`.
- O perfil demo registra logs em JSON; para logs legíveis use `LOG_FORMAT=CONSOLE` (ou `make demo`).
- Teste rápido: `scripts/smoke-demo.sh` executa o fluxo completo (login, leads, PATCH, 401, 403,
  409, 422, SOAP) e imprime a transcrição (exemplo em
  [`docs/evidencias/smoke-demo.txt`](docs/evidencias/smoke-demo.txt)).

### 2. Com PostgreSQL (docker compose do forward-infra, porta 55432)

O compose do `forward-infra` publica o PostgreSQL na porta **55432** do host. A API usa um banco
dedicado (`forward_api`), com o esquema gerenciado pelo Flyway:

```bash
cd ../forward-infra/docker && docker compose up -d postgres
docker exec forward-postgres createdb -U forward forward_api
cd ../../forward-api-java

# Esquema + seed de demonstração (perfil seed). Sem o perfil, só as migrations.
DATABASE_URL='jdbc:postgresql://localhost:55432/forward_api?sslmode=disable' \
DATABASE_USER=forward DATABASE_PASSWORD=forward_dev \
SPRING_PROFILES_ACTIVE=seed ./mvnw spring-boot:run
```

Esses valores de `DATABASE_*` já são o padrão do `application.yml`; o perfil padrão (sem `seed`)
aplica somente as migrations, que é o comportamento de produção.

## Variáveis de ambiente

Modelo completo em [`.env.example`](.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `8080` | Porta HTTP. |
| `SPRING_PROFILES_ACTIVE` | (vazio) | `demo` (banco embarcado + seed), `seed` (banco externo + seed) ou vazio. |
| `ENV` | `development` | `production` ativa logs JSON e torna `JWT_SECRET` obrigatório (a API não sobe sem ele). |
| `JWT_SECRET` | (vazio) | Chave HS256, mínimo 32 bytes. Vazio fora de produção: chave aleatória por boot. |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token (1 a 1440). |
| `DATABASE_URL` | `jdbc:postgresql://localhost:55432/forward_api?sslmode=disable` | JDBC do PostgreSQL (ignorado no perfil demo). |
| `DATABASE_USER`, `DATABASE_PASSWORD` | `forward`, `forward_dev` | Credenciais do banco. |
| `INTERNAL_API_KEY` | (vazio) | Chave do header `X-API-Key` para integrações (perfil SERVICE). Vazio desativa. |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8081` | Allowlist de CORS (curingas são ignorados). |
| `RATE_LIMIT_MAX`, `RATE_LIMIT_WINDOW` | `60`, `1m` | Limite global por IP. |
| `LOGIN_RATE_LIMIT_MAX`, `LOGIN_RATE_LIMIT_WINDOW` | `5`, `1m` | Limite de tentativas de login por IP. |
| `TRUSTED_CLIENT_IP_HEADER` | (vazio) | Header do proxy com o IP real (Fly.io: `Fly-Client-IP`). |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` atrás de proxy confiável (mantém `https` no `Location`). |
| `LOG_LEVEL`, `LOG_FORMAT` | `INFO`, automático | `LOG_FORMAT=JSON` ou `CONSOLE`. |

## Usuários de demonstração

Criados pelo seed (perfis `demo`, `seed` e `test`). **Senha de todos: `Forward@2026`** (gravada
somente como hash BCrypt).

| E-mail | Perfil | Concessionária | Observação |
|---|---|---|---|
| `admin@forward.dev` | ADMIN | todas | administra usuários, exclui eventos |
| `gestor@forward.dev` | GESTOR | F0001 Ford Morumbi São Paulo | cria e altera eventos de serviço |
| `atendente@forward.dev` | ATENDENTE | F0001 Ford Morumbi São Paulo | 8 leads |
| `atendente2@forward.dev` | ATENDENTE | F0002 Ford Barra Rio | 5 leads |
| `gestor2@forward.dev` | GESTOR | F0002 Ford Barra Rio | |
| `inativo@forward.dev` | ATENDENTE | F0001 | desativado: login responde 401 `AUTH_USER_DISABLED` |

O seed também cria 10 concessionárias, 12 clientes com veículos e scores de churn, 9 eventos de
serviço e 18 leads em 4 concessionárias, cobrindo todos os status e prioridades.

## Autenticação passo a passo

1. Login (público) devolve o JWT:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gestor@forward.dev","password":"Forward@2026"}'
```

```json
{
  "access_token": "<JWT HS256 emitido pela API>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "expires_at": "2026-09-27T07:30:35Z",
  "user": {
    "id": "ad000000-0000-4000-8000-000000000002",
    "name": "Gustavo Mendes",
    "email": "gestor@forward.dev",
    "role": "GESTOR",
    "dealer_id": "d0000000-0000-4000-8000-000000000001",
    "dealer_name": "Ford Morumbi São Paulo"
  }
}
```

2. Use o token nas demais chamadas:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gestor@forward.dev","password":"Forward@2026"}' | jq -r .access_token)

curl -i http://localhost:8080/api/v1/me -H "Authorization: Bearer $TOKEN"
curl -i "http://localhost:8080/api/v1/leads?status=new&limit=10" -H "Authorization: Bearer $TOKEN"
curl -i -X PATCH http://localhost:8080/api/v1/leads/a1000000-0000-4000-8000-000000000001 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"contacted","notes":"Cliente pediu retorno amanhã."}'
```

No PowerShell:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType 'application/json' -Body '{"email":"gestor@forward.dev","password":"Forward@2026"}'
$headers = @{ Authorization = "Bearer $($login.access_token)" }
Invoke-RestMethod -Uri http://localhost:8080/api/v1/leads -Headers $headers
```

Regras do token:

- HS256, `iss=forward-api`, `aud=forward-app`, `sub` = id do usuário, `email`, `name`, `role`,
  `dealer_id` (ATENDENTE e GESTOR), `iat`, `nbf`, `exp` (60 min) e `jti` único.
- Validados: assinatura, algoritmo, expiração (30 s de tolerância), emissor, audiência e claims
  obrigatórias. Token ausente: 401 `AUTH_REQUIRED`; expirado: 401 `AUTH_TOKEN_EXPIRED`; inválido:
  401 `AUTH_TOKEN_INVALID`, sempre com o header `WWW-Authenticate: Bearer`.
- E-mail inexistente e senha errada recebem a mesma resposta 401 `AUTH_INVALID_CREDENTIALS`.
- Login limitado a 5 tentativas por minuto por IP (429 `RATE_LIMITED` com `Retry-After`).

## Perfis e permissões

| Recurso | ATENDENTE | GESTOR | ADMIN | SERVICE (X-API-Key) |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | público | público | público | público |
| `GET /api/v1/me` | sim | sim | sim | sim |
| Leads: listar, detalhar, `PATCH` | própria concessionária | própria concessionária | todas | todas |
| Clientes, score, veículos, SOAP `GetVehicle` | própria concessionária | própria concessionária | todas | todas |
| Eventos de serviço: listar e detalhar | própria concessionária | própria concessionária | todas | todas |
| Eventos de serviço: `POST` e `PUT` | 403 | própria concessionária | todas | todas |
| Eventos de serviço: `DELETE` | 403 | 403 | sim | 403 |
| Usuários (`/api/v1/users`) | 403 | 403 | sim | 403 |

Recurso de outra concessionária: **403 `ACCESS_OTHER_DEALER`**; perfil sem permissão: **403
`ACCESS_DENIED`**. As regras de perfil existem no `SecurityConfig` (URL) e nos serviços
(`@PreAuthorize`); o escopo por concessionária é aplicado nos serviços com o `dealer_id` do token.

## Endpoints

Todos os endpoints protegidos também podem responder 401 e 429.

| Método | Caminho | Perfis | Sucesso | Erros |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | público | 200 | 400, 401, 415, 429 |
| GET | `/api/v1/me` | todos | 200 | 401 |
| GET | `/api/v1/leads` | todos (escopo) | 200 + `X-Total-Count` | 400, 403 |
| GET | `/api/v1/leads/{id}` | todos (escopo) | 200 | 400, 403, 404 |
| PATCH | `/api/v1/leads/{id}` | todos (escopo) | 200 | 400, 403, 404, 409, 415 |
| GET | `/api/v1/customers/{id}` | todos (escopo) | 200 | 400, 403, 404 |
| GET | `/api/v1/customers/{id}/score` | todos (escopo) | 200 | 400, 403, 404 |
| GET | `/api/v1/scores/{customerId}` (deprecado) | todos (escopo) | 200 + `Deprecation` | 400, 403, 404 |
| GET | `/api/v1/vehicles/{vin}` | todos (escopo) | 200 | 400, 403, 404 |
| GET | `/api/v1/service-events` | todos (escopo) | 200 + `X-Total-Count` | 400 |
| GET | `/api/v1/service-events/{id}` | todos (escopo) | 200 | 400, 403, 404 |
| POST | `/api/v1/service-events` | GESTOR, ADMIN, SERVICE | 201 + `Location` | 400, 403, 409, 415, 422 |
| PUT | `/api/v1/service-events/{id}` | GESTOR, ADMIN, SERVICE | 200 | 400, 403, 404, 409, 415, 422 |
| DELETE | `/api/v1/service-events/{id}` | ADMIN | 204 | 403, 404 |
| GET | `/api/v1/users` | ADMIN | 200 + `X-Total-Count` | 400, 403 |
| GET | `/api/v1/users/{id}` | ADMIN | 200 | 403, 404 |
| POST | `/api/v1/users` | ADMIN | 201 + `Location` | 400, 403, 409, 415, 422 |
| PATCH | `/api/v1/users/{id}` | ADMIN | 200 | 400, 403, 404, 409, 422 |
| DELETE | `/api/v1/users/{id}` | ADMIN | 204 | 403, 404, 409 |
| GET | `/health`, `/ready`, `/actuator/health` | público | 200 | |
| GET | `/v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html` | público | 200 | |
| GET | `/soap/vehicles.wsdl` | público | 200 | |
| POST | `/soap/vehicles` (`GetVehicle`) | todos (escopo) | 200 | 401, SOAP Fault |

Filtros de listagem: leads aceitam `status`, `priority`, `dealer_id` (ADMIN), `limit` (1 a 200,
padrão 50) e `offset`; eventos aceitam `vin`, `limit` e `offset`; usuários aceitam `role`,
`active`, `dealer_id`, `limit` e `offset`.

Máquina de estados do lead (`PATCH /api/v1/leads/{id}` com `{"status": ..., "notes": ...}`):

```
new       -> assigned | contacted | lost
assigned  -> contacted | lost
contacted -> converted | lost
converted, lost, expired: finais (409 LEAD_INVALID_TRANSITION)
```

Repetir o status atual é idempotente (200). Status desconhecido responde 400. Toda alteração é
gravada no `audit_log` na mesma transação.

## REST nível 2

- **Recursos** identificados por URI (`/leads/{id}`, `/customers/{id}/score`,
  `/service-events/{id}`, `/users/{id}`), representações JSON em snake_case.
- **Verbos com semântica HTTP**: GET seguro, PUT substitui a representação, PATCH altera
  parcialmente (aceita `application/json` e `application/merge-patch+json`), DELETE remove.
- **Status coerentes**: 201 com `Location` apontando para um GET que existe; 204 sem corpo no
  DELETE; 404 para URI inexistente; 422 quando o corpo referencia algo que não existe (VIN,
  concessionária); 409 para conflito de estado ou duplicidade; 405 e 415 preservados pelo
  handler de erros (antes viravam 500).
- **Coleções** como array JSON (compatível com o app) e total no header `X-Total-Count`.
- **Evolução compatível**: o antigo `/api/v1/scores/{id}` continua respondendo com os headers
  `Deprecation: true` e `Link: <...>; rel="successor-version"`; o corpo de eventos de serviço
  aceita os nomes camelCase da Sprint 2 como aliases.

## Formato de erro

Todo erro é `application/problem+json` (RFC 7807) com os mesmos campos:

```json
{
  "type": "urn:forward:problem:lead-invalid-transition",
  "title": "Conflito",
  "status": 409,
  "detail": "Transição de status inválida: converted -> lost (o lead já está em um status final).",
  "instance": "/api/v1/leads/a1000000-0000-4000-8000-000000000005",
  "code": "LEAD_INVALID_TRANSITION",
  "timestamp": "2026-09-27T06:30:37.211Z",
  "request_id": "0f6d2c1e-6a3b-4b0e-9d7c-2a1f5e8b9c10"
}
```

Erros de validação trazem também `errors: [{"field": "status", "message": "..."}]`. O
`request_id` é o mesmo do header `X-Request-Id` (enviado pelo cliente ou gerado).

| Código | Status | Quando |
|---|---|---|
| `VALIDATION_FAILED`, `INVALID_PARAMETER`, `MALFORMED_JSON`, `INVALID_FIELD_VALUE`, `TYPE_MISMATCH`, `EMPTY_PATCH`, `USER_DEALER_REQUIRED` | 400 | entrada inválida |
| `AUTH_REQUIRED`, `AUTH_TOKEN_INVALID`, `AUTH_TOKEN_EXPIRED`, `AUTH_API_KEY_INVALID` | 401 | token ou chave ausente/inválido |
| `AUTH_INVALID_CREDENTIALS`, `AUTH_USER_DISABLED` | 401 | login |
| `ACCESS_DENIED`, `ACCESS_OTHER_DEALER` | 403 | perfil sem permissão, outra concessionária |
| `LEAD_NOT_FOUND`, `CUSTOMER_NOT_FOUND`, `VEHICLE_NOT_FOUND`, `SCORE_NOT_FOUND`, `SERVICE_EVENT_NOT_FOUND`, `USER_NOT_FOUND`, `NOT_FOUND` | 404 | recurso ou rota inexistente |
| `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | 405 / 415 | verbo ou Content-Type não suportado |
| `LEAD_INVALID_TRANSITION`, `SERVICE_EVENT_DUPLICATE`, `USER_EMAIL_TAKEN`, `USER_SELF_DELETE`, `USER_SELF_MODIFICATION`, `CONFLICT` | 409 | conflito de estado |
| `REFERENCED_VEHICLE_NOT_FOUND`, `REFERENCED_DEALER_NOT_FOUND` | 422 | referência inexistente no corpo |
| `RATE_LIMITED` | 429 | limite excedido (`Retry-After`) |
| `INTERNAL_ERROR` | 500 | erro inesperado (sem detalhes internos) |

## Testes

```bash
./mvnw test                                      # 171 testes (unitários + integração HTTP)
./mvnw verify                                    # + JaCoCo e relatório HTML do Surefire
./mvnw spotless:check && ./mvnw verify -P quality  # mesmas verificações do CI
```

- **Unitários (84)**: `JwtServiceTest` (emissão, expiração, tolerância, adulteração, `iss`/`aud`,
  `alg=none`, segredo obrigatório em produção), regras de serviço com Mockito, validações.
- **Integração (87)**: `@SpringBootTest` + MockMvc contra PostgreSQL 16 embarcado com as
  migrations e o seed. `AuthIT`, `SecurityIT` (401/403 por perfil e por concessionária, CORS,
  headers), `LeadIT`, `ServiceEventIT`, `UserIT`, `ErrorHandlingIT` e `HttpServerIT` (Tomcat real:
  SOAP, WSDL, Swagger). Testes que alteram dados fazem rollback ao final.
- Relatórios: `target/site/jacoco/index.html` (cobertura) e `target/reports/surefire.html`.
- Evidências desta entrega em [`docs/evidencias/`](docs/evidencias/): resumo da execução
  (`testes-2026-09-27.txt`), cobertura por pacote (`jacoco-resumo.md`), relatório HTML do Surefire
  e a transcrição do smoke test do JAR (`smoke-demo.txt`).

## SOAP

Contrato em [`src/main/resources/xsd/vehicles.xsd`](src/main/resources/xsd/vehicles.xsd); WSDL
público em `http://localhost:8080/soap/vehicles.wsdl`. A operação exige o mesmo JWT do REST e
respeita o escopo por concessionária.

```bash
curl -s -X POST http://localhost:8080/soap/vehicles \
  -H 'Content-Type: text/xml' -H "Authorization: Bearer $TOKEN" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:v="urn:forwardservice:vehicles"><soapenv:Body>
        <v:GetVehicleRequest><v:VIN>9BFZZZ5SZJB000001</v:VIN></v:GetVehicleRequest>
      </soapenv:Body></soapenv:Envelope>'
```

Resposta: `GetVehicleResponse` com `VIN`, `Model`, `Year` e `Discontinued`. VIN inválido,
inexistente ou de outra concessionária gera um SOAP Fault `Client` com a mensagem em pt-BR.

## OpenAPI, Swagger e Postman

| Recurso | Caminho |
|---|---|
| Swagger UI (público) | `http://localhost:8080/swagger-ui.html` (botão **Authorize** com o token) |
| OpenAPI JSON / YAML | `/v3/api-docs` / `/v3/api-docs.yaml` |
| Snapshot versionado | [`openapi.yaml`](openapi.yaml) |
| Coleção Postman | [`docs/ForwardService.postman_collection.json`](docs/ForwardService.postman_collection.json) |

A especificação traz a descrição em pt-BR com o fluxo de autenticação e os usuários de demo, os
servidores `http://localhost:8080` e `https://forward-api-java.fly.dev`, o esquema `bearerAuth`
aplicado a todas as operações exceto as públicas e as respostas de erro reais de cada operação.
Para regenerar o snapshot com a API rodando:

```bash
curl -s http://localhost:8080/v3/api-docs.yaml -o openapi.yaml
```

Na coleção Postman, rode primeiro a pasta **01 Auth**: os logins gravam `token` (GESTOR),
`adminToken` e `atendenteToken` nas variáveis da coleção.

## Deploy no Fly.io

App `forward-api-java`, região `gru`, configuração em [`fly.toml`](fly.toml). Como o Supabase foi
excluído, o `fly.toml` usa `SPRING_PROFILES_ACTIVE=demo`: a máquina sobe o PostgreSQL embarcado
com o seed (dados efêmeros, recriados a cada boot). A imagem usa Ubuntu Jammy (glibc, exigida
pelos binários do PostgreSQL), usuário não root e JVM dimensionada para 512 MB.

```bash
fly auth login
# Obrigatório: com ENV=production a API não sobe sem JWT_SECRET
fly secrets set JWT_SECRET="$(openssl rand -base64 48)" --app forward-api-java
# Opcional: chave para integrações (n8n)
fly secrets set INTERNAL_API_KEY="$(openssl rand -hex 32)" --app forward-api-java

fly deploy --remote-only --app forward-api-java
fly logs --app forward-api-java
curl https://forward-api-java.fly.dev/health
```

Para usar um PostgreSQL gerenciado, remova `SPRING_PROFILES_ACTIVE` do `fly.toml` e defina
`DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` com `fly secrets set`; o Flyway cria o
esquema na primeira inicialização.

## Solução de problemas

| Sintoma | Causa e solução |
|---|---|
| Porta 8080 ocupada | Use `PORT=8081` (ou `--server.port=8081`). |
| PostgreSQL embarcado não inicia no Windows | O PostgreSQL recusa rodar como Administrador. Execute o terminal como usuário comum. |
| Processos `postgres.exe` sobrando | Ao matar a JVM à força o banco embarcado não é encerrado. Finalize os processos cujo caminho contém `embedded-pg` (Gerenciador de Tarefas ou `taskkill`). |
| 401 `AUTH_TOKEN_INVALID` depois de reiniciar o demo | Sem `JWT_SECRET` a chave muda a cada boot. Faça login de novo ou defina `JWT_SECRET`. |
| 429 no login | Limite de 5 tentativas por minuto por IP; aguarde o tempo do header `Retry-After`. |
| Logs em JSON no demo | Comportamento esperado; use `LOG_FORMAT=CONSOLE` para logs legíveis. |
| Primeira execução lenta | Download do Maven, das dependências e extração dos binários do PostgreSQL (uma vez). |
| Docker em Mac com Apple Silicon | Construa com `docker build --platform linux/amd64 .` (o JAR gerado no container inclui os binários Linux x86_64). |
| `spotless:check` falha no Windows | Rode `./mvnw spotless:apply` antes do commit (fins de linha LF). |

## Estrutura do projeto

```text
src/main/java/com/fwdford/forwardapi/
  config/        AppProperties, OpenAPI, EmbeddedPostgresConfig (demo/test), Clock
  error/         ApiException, Problems (RFC 7807), GlobalExceptionHandler, /error
  model/         Records de domínio (Lead, LeadStatus, ServiceEvent, AppUser, ...)
  repository/    SQL parametrizado com NamedParameterJdbcTemplate
  security/      JwtService, JwtAuthenticationFilter, SecurityConfig, RateLimitFilter, Role
  service/       Regras de negócio, @PreAuthorize, escopo por concessionária, auditoria
  soap/          Endpoint Spring WS GetVehicle
  web/           Controllers REST, DTOs (web/dto), filtros de request id e headers, CORS
src/main/resources/
  db/migration/  Flyway V1..V15 (V1..V13 = forward-infra, V14 app_users, V15 notas/chave natural)
  db/seed/       R__seed_demo_data.sql (somente perfis demo, seed e test)
  application*.yml, logback-spring.xml, xsd/vehicles.xsd
src/test/java/.../it/   Testes de integração HTTP (MockMvc e Tomcat real)
docs/                   ARQUITETURA.md, diagramas (Mermaid + PNG), Postman, evidências
scripts/smoke-demo.sh   Smoke test da API em execução
```

CI (GitHub Actions, workflows reutilizáveis de `fwd-ford/.github`): Spotless, Checkstyle,
SpotBugs + FindSecBugs, testes, Trivy (filesystem) e gitleaks; pipeline DevSecOps
(CodeQL, Semgrep, SBOM, imagem) em modo relatório.
