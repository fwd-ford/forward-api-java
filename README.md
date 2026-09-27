# forward-api-java

![org](https://img.shields.io/badge/org-fwd--ford-blue?style=flat-square)
![stack](https://img.shields.io/badge/stack-Java_17_·_Spring_Boot_3.5-333?style=flat-square)
![api](https://img.shields.io/badge/API-REST_nível_2_·_SOAP-6f42c1?style=flat-square)
![auth](https://img.shields.io/badge/auth-JWT_HS256_·_RBAC-green?style=flat-square)

API REST e SOAP do **ForwardService**, plataforma de retenção de clientes da rede Ford
(Challenge Ford x FIAP 2026). Este repositório é a entrega da Sprint 3 da disciplina de
**Arquitetura Orientada a Serviços e Web Services**.

- **Autenticação própria**: a API emite e valida os próprios JWTs (o Supabase Auth não é mais
  usado) e guarda os usuários em `app_users` com senha BCrypt.
- **Produção**: web service Docker no **Render** (`https://forward-api-java.onrender.com`) com o
  banco **PostgreSQL do Supabase** (sa-east-1). No perfil `demo` a API roda com um
  **PostgreSQL 16 embarcado**, sem Docker e sem nenhum serviço externo.
- **Segurança**: Spring Security stateless, JWT HS256 com `iss`, `aud`, `exp` e `jti`, perfis
  ATENDENTE, GESTOR e ADMIN, escopo de dados por concessionária, rate limit, auditoria,
  proteção contra XXE e log injection.
- **REST nível 2**: recursos, verbos HTTP corretos (GET, POST, PUT, PATCH, DELETE), status
  coerentes (200, 201 + `Location`, 204, 400, 401, 403, 404, 405, 409, 415, 422, 429).
- **Erros padronizados** em RFC 7807 (`application/problem+json`) com mensagens em pt-BR.
- **220 testes automatizados** (121 unitários e 99 de integração contra PostgreSQL real
  embarcado), cobertura de linhas em torno de 92% (JaCoCo).

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
14. [Deploy no Render (Blueprint)](#deploy-no-render-blueprint)
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
| Linguagem e framework | Java 17, Spring Boot 3.5.16 com Tomcat 10.1.60 (Web, Security, Validation, JDBC, Web Services, Actuator) |
| Banco | PostgreSQL (Supabase em produção); Flyway V1 a V16 + bootstrap; PostgreSQL 16 embarcado (Zonky) nos perfis `demo` e `test` |
| Acesso a dados | `NamedParameterJdbcTemplate` com SQL parametrizado (sem ORM) |
| Autenticação | JWT HS256 emitido pela própria API (JJWT 0.12), senhas BCrypt |
| Autorização | Spring Security (regras de URL + `@PreAuthorize`) e escopo por concessionária |
| Proteções | Bucket4j (rate limit por IP), CORS com allowlist, headers de segurança, auditoria em `audit_log`, XML sem DOCTYPE, logs sanitizados |
| Documentação | springdoc-openapi (Swagger UI), `openapi.yaml`, coleção Postman |
| Qualidade | JUnit 5, MockMvc, JaCoCo, Spotless, Checkstyle, SpotBugs + FindSecBugs, Trivy, gitleaks, CodeQL, Semgrep |
| Hospedagem | Render (Docker, plano free) via Blueprint [`render.yaml`](render.yaml) |
| Logs | Logback (JSON com Logstash encoder em produção/demo) com `request_id` em todas as linhas |

## Como executar

Pré-requisito: **Java 17** (Temurin recomendado). O Maven Wrapper baixa o Maven na primeira
execução. Não é preciso Docker nem banco instalado para o perfil demo.

### 1. Perfil demo (sem Docker, sem banco externo)

Sobe um PostgreSQL 16 embarcado, aplica as migrations, o bootstrap e o seed de demonstração e
inicia a API em `http://localhost:8080`.

```bash
# Linux, macOS ou Git Bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Windows PowerShell ou cmd
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"

# Ou com o JAR
./mvnw -DskipTests package
java -jar target/forward-api.jar --spring.profiles.active=demo
```

- Os dados são efêmeros: cada inicialização recria o banco.
- Sem `JWT_SECRET` a API gera uma chave aleatória a cada boot (aviso WARN no log); tokens antigos
  deixam de valer após reiniciar. Para tokens estáveis: `JWT_SECRET=$(openssl rand -base64 48)`.
- O perfil demo registra logs em JSON; para logs legíveis use `LOG_FORMAT=CONSOLE` (ou `make demo`).
- Teste rápido: `scripts/smoke-demo.sh` executa o fluxo completo (login, leads, PATCH, 401, 403,
  409, 422, SOAP) e imprime a transcrição (exemplo em
  [`docs/evidencias/smoke-demo.txt`](docs/evidencias/smoke-demo.txt)).

### 2. Com um PostgreSQL existente (perfil prod)

O perfil `prod` é o mesmo usado no Render: aplica as migrations com baseline na versão 13 (o
esquema 001 a 013 do `forward-infra` já existe) e depois o bootstrap idempotente de dados.
Funciona tanto no PostgreSQL do `docker compose` do `forward-infra` (porta **55432**) quanto em
um banco vazio.

```bash
cd ../forward-infra/docker && docker compose up -d postgres && cd ../../forward-api-java

DATABASE_URL='jdbc:postgresql://localhost:55432/forward?sslmode=disable' \
DATABASE_USER=forward DATABASE_PASSWORD=forward_dev \
JWT_SECRET="$(openssl rand -base64 48)" \
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

O perfil `prod` assume `ENV=production` (logs JSON e `JWT_SECRET` obrigatório). Sem perfil, a API
aplica somente as migrations (sem baseline e sem dados).

## Variáveis de ambiente

Modelo completo em [`.env.example`](.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `8080` | Porta HTTP (o Render injeta a sua). |
| `SPRING_PROFILES_ACTIVE` | (vazio) | `prod` (banco externo, baseline 13 + bootstrap), `demo` (banco embarcado + dados) ou vazio. |
| `ENV` | `development` (`production` no perfil prod) | `production` ativa logs JSON e torna `JWT_SECRET` obrigatório. |
| `JWT_SECRET` | (vazio) | Chave HS256, mínimo 32 bytes. No Render é gerada automaticamente. |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token (1 a 1440). |
| `JWT_USER_STATE_CACHE_TTL` | `30s` | Cache do estado do usuário usado na revogação de tokens (`0s` desliga). |
| `ADMIN_BOOTSTRAP_PASSWORD` | (vazio; `Forward@2026` no perfil demo) | Cria `admin@forward.dev` (perfis prod/demo) somente quando definida. Nunca é registrada em log. |
| `DEMO_USERS_PASSWORD` | `Forward@2026` | Senha dos usuários de demonstração GESTOR/ATENDENTE criados pelo bootstrap; vazio = não cria. Alterar depois não muda usuários existentes. |
| `DATABASE_URL` | `jdbc:postgresql://localhost:55432/forward?sslmode=disable` | JDBC do PostgreSQL (ignorado no perfil demo). |
| `DATABASE_USER`, `DATABASE_PASSWORD` | `forward`, `forward_dev` | Credenciais do banco. |
| `DATABASE_POOL_SIZE` | `10` (`5` no perfil prod) | Conexões do HikariCP. |
| `INTERNAL_API_KEY` | (vazio) | Chave do header `X-API-Key` para integrações (perfil SERVICE). Vazio desativa. |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8081` | Allowlist de CORS (curingas são ignorados). |
| `RATE_LIMIT_MAX`, `RATE_LIMIT_WINDOW` | `60`, `1m` | Limite global por IP. |
| `LOGIN_RATE_LIMIT_MAX`, `LOGIN_RATE_LIMIT_WINDOW` | `5`, `1m` | Limite de tentativas de login por IP. |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` atrás de proxy (Render): IP do cliente e `https` vindos de `X-Forwarded-*` de proxies internos. |
| `LOG_LEVEL`, `LOG_FORMAT` | `INFO`, automático | `LOG_FORMAT=JSON` ou `CONSOLE`. |

## Usuários de demonstração

Senhas gravadas somente como hash BCrypt, calculado no banco com pgcrypto (nenhum hash é
versionado). No perfil `demo` e nos testes, **todos usam `Forward@2026`**.

| E-mail | Perfil | Concessionária | Onde existe e senha |
|---|---|---|---|
| `admin@forward.dev` | ADMIN | todas | demo e testes (`Forward@2026`); em produção **só se `ADMIN_BOOTSTRAP_PASSWORD` estiver definida**, com essa senha |
| `gestor@forward.dev` | GESTOR | F0001 Ford Morumbi São Paulo | produção (`DEMO_USERS_PASSWORD`, padrão `Forward@2026`), demo e testes |
| `atendente@forward.dev` | ATENDENTE | F0001 Ford Morumbi São Paulo | produção (`DEMO_USERS_PASSWORD`), demo e testes |
| `atendente2@forward.dev` | ATENDENTE | F0002 Ford Barra Rio | produção (`DEMO_USERS_PASSWORD`), demo e testes |
| `gestor2@forward.dev` | GESTOR | F0002 Ford Barra Rio | demo e testes |
| `inativo@forward.dev` | ATENDENTE (desativado) | F0001 | demo e testes (login: 401 `AUTH_USER_DISABLED`) |

O bootstrap ([`db/bootstrap`](src/main/resources/db/bootstrap/R__bootstrap_demo_data.sql)) também
garante 10 concessionárias, 16 clientes com veículos e scores de churn, 9 eventos de serviço e
22 leads (10 na F0001 e 7 na F0002) com todos os status e prioridades. Ele referencia as
concessionárias pelo código e usa `ON CONFLICT DO NOTHING`, então nunca altera dados existentes
(nem senhas de usuários que já existem).

**Risco aceito no ambiente de demonstração:** os usuários GESTOR/ATENDENTE de produção usam por
padrão a senha publicada `Forward@2026`, porque os professores e o botão "Usar usuário de teste" do
app dependem dela. São perfis de baixo privilégio, restritos à própria concessionária, sobre dados
sintéticos. Para fechar o risco, defina outra `DEMO_USERS_PASSWORD` antes do primeiro deploy (ou
vazio para não criar esses usuários). O ADMIN nunca é criado com senha publicada em produção: sem
`ADMIN_BOOTSTRAP_PASSWORD` ele não existe, e um `admin@forward.dev` antigo que ainda tenha a senha
publicada é desativado (ou recebe a senha configurada) na próxima inicialização.

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
BASE=http://localhost:8080   # ou https://forward-api-java.onrender.com
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"gestor@forward.dev","password":"Forward@2026"}' | jq -r .access_token)

curl -i $BASE/api/v1/me -H "Authorization: Bearer $TOKEN"
curl -i "$BASE/api/v1/leads?status=new&limit=10" -H "Authorization: Bearer $TOKEN"
curl -i -X PATCH $BASE/api/v1/leads/a1000000-0000-4000-8000-000000000001 \
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
  `dealer_id` (ATENDENTE e GESTOR), `token_version`, `iat`, `nbf`, `exp` (60 min) e `jti` único.
- Validados: assinatura, algoritmo, expiração (30 s de tolerância), emissor, audiência e claims
  obrigatórias. Token ausente: 401 `AUTH_REQUIRED`; expirado: 401 `AUTH_TOKEN_EXPIRED`; inválido:
  401 `AUTH_TOKEN_INVALID`, sempre com o header `WWW-Authenticate: Bearer`.
- E-mail inexistente e senha errada recebem a mesma resposta 401 `AUTH_INVALID_CREDENTIALS`.
- Login limitado a 5 tentativas por minuto por IP (429 `RATE_LIMITED` com `Retry-After`).

### Revogação

O JWT não fica valendo "às cegas" até expirar. A cada requisição, depois de validar a
assinatura, a API compara o token com o estado atual do usuário em `app_users` (existe, está
ativo, mesmo `role`, mesmo `dealer_id` e mesmo `token_version`). Qualquer diferença responde
**401 `AUTH_TOKEN_REVOKED`** ("Sessão revogada..."), e o app deve pedir um novo login.

- O `token_version` do usuário é incrementado pelo ADMIN em qualquer `PATCH /api/v1/users/{id}`
  que envie `role`, `active`, `dealer_id` ou `password` (mesmo com o valor atual: `PATCH
  {"active": true}` força novo login). Excluir o usuário também revoga os tokens. Alterar só o
  nome não revoga.
- O estado do usuário fica em um cache em memória de 30 s (`JWT_USER_STATE_CACHE_TTL`; `0`
  desliga). As alterações feitas pelo ADMIN invalidam o cache na hora, então a revogação é
  imediata nesta instância; em várias instâncias, no máximo um TTL.
- Chamadas com `X-API-Key` (perfil SERVICE) não dependem de `app_users`.

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
A autorização é sempre da API: no Supabase, a API conecta como dono das tabelas, então as
políticas RLS do banco não se aplicam a ela (e `app_users` tem RLS sem políticas e sem grants para
`anon`/`authenticated`, ou seja, a Data API do Supabase não a expõe).

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
| `AUTH_TOKEN_REVOKED` | 401 | usuário desativado, excluído, com perfil, concessionária ou senha alterados depois da emissão do token |
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
./mvnw test                                      # 220 testes (unitários + integração)
./mvnw verify                                    # + JaCoCo e relatório HTML do Surefire
./mvnw spotless:check && ./mvnw verify -P quality  # mesmas verificações do CI
```

- **Unitários (121)**: `JwtServiceTest` (emissão, expiração, tolerância, adulteração, `iss`/`aud`,
  `alg=none`, `token_version`, segredo obrigatório em produção), `JwtAuthenticationFilterTest`
  (revogação por usuário excluído, inativo, perfil, concessionária e versão), `UserStateCacheTest`
  (TTL do cache), `LogSanitizerTest`, `SecureXmlTest` (DOCTYPE/XXE recusados), regras de serviço
  com Mockito e validações.
- **Integração (99)**: `@SpringBootTest` + MockMvc contra PostgreSQL 16 embarcado com migrations,
  bootstrap e seed. `AuthIT`, `SecurityIT` (401/403 por perfil e por concessionária, CORS,
  headers), `TokenRevocationIT` (token antigo recusado logo após desativar, rebaixar, trocar de
  concessionária, redefinir senha ou excluir o usuário), `LeadIT`, `ServiceEventIT`, `UserIT`,
  `ErrorHandlingIT`, `HttpServerIT` (Tomcat real:
  SOAP, WSDL, XXE, Swagger) e `ProdMigrationIT` (reproduz o banco do Supabase: esquema do
  forward-infra sem histórico do Flyway + seed antigo, e valida baseline 13, V14+, bootstrap
  idempotente, dados antigos intactos, ADMIN criado só com `ADMIN_BOOTSTRAP_PASSWORD` e remediação
  de um ADMIN antigo com a senha publicada). Testes que alteram dados fazem rollback ao final.
- Relatórios: `target/site/jacoco/index.html` (cobertura) e `target/reports/surefire.html`.
- Evidências desta entrega em [`docs/evidencias/`](docs/evidencias/): resumo da execução
  (`testes-2026-09-27.txt`), cobertura por pacote (`jacoco-resumo.md`), relatório HTML do Surefire
  e a transcrição do smoke test do JAR (`smoke-demo.txt`).

## SOAP

Contrato em [`src/main/resources/xsd/vehicles.xsd`](src/main/resources/xsd/vehicles.xsd); WSDL
público em `/soap/vehicles.wsdl`. A operação exige o mesmo JWT do REST, respeita o escopo por
concessionária e recusa mensagens com DOCTYPE (proteção contra XXE).

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
| Swagger UI (público) | `/swagger-ui.html` (botão **Authorize** com o token) |
| OpenAPI JSON / YAML | `/v3/api-docs` / `/v3/api-docs.yaml` |
| Snapshot versionado | [`openapi.yaml`](openapi.yaml) |
| Coleção Postman | [`docs/ForwardService.postman_collection.json`](docs/ForwardService.postman_collection.json) |

A especificação traz a descrição em pt-BR com o fluxo de autenticação e os usuários de demo, os
servidores `https://forward-api-java.onrender.com` e `http://localhost:8080`, o esquema
`bearerAuth` aplicado a todas as operações exceto as públicas e as respostas de erro reais de
cada operação. Para regenerar o snapshot com a API rodando:

```bash
curl -s http://localhost:8080/v3/api-docs.yaml -o openapi.yaml
```

Na coleção Postman, rode primeiro a pasta **01 Auth**: os logins gravam `token` (GESTOR),
`adminToken` e `atendenteToken` nas variáveis da coleção. Para usar a produção, troque a variável
`baseUrl` para `https://forward-api-java.onrender.com`.

## Deploy no Render (Blueprint)

A infraestrutura está descrita em [`render.yaml`](render.yaml): um web service Docker no plano
free, nome `forward-api-java` (URL `https://forward-api-java.onrender.com`), health check em
`/health`, deploy automático a cada commit na `main`, perfil `prod`, `JWT_SECRET` e
`INTERNAL_API_KEY` gerados pelo próprio Render e JVM ajustada para 512 MB.

1. Acesse **render.com** e clique em **Sign in with GitHub** (conta com acesso à organização
   `fwd-ford`).
2. No painel, **New** > **Blueprint**.
3. Selecione o repositório **fwd-ford/forward-api-java** (branch `main`); o Render lê o
   `render.yaml` e mostra o serviço `forward-api-java`.
4. Preencha as variáveis marcadas como `sync: false`:
   - `DATABASE_URL`: `jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require`
   - `DATABASE_USER`: `postgres.ysewoopjgdpvnkfhffgy`
   - `DATABASE_PASSWORD`: a senha do banco do projeto Supabase
   - `ADMIN_BOOTSTRAP_PASSWORD` (opcional): senha forte para criar `admin@forward.dev`. Deixe em
     branco para não criar nenhum ADMIN. Guarde-a num cofre de senhas; ela não aparece em logs.
   - `DEMO_USERS_PASSWORD` não faz parte do Blueprint: sem ela, os usuários de teste
     GESTOR/ATENDENTE usam `Forward@2026`. Para trocar a senha publicada, adicione a variável em
     **Environment** antes do primeiro deploy.
5. Clique em **Apply**. O primeiro build leva alguns minutos; acompanhe em **Logs**. Na primeira
   inicialização o Flyway cria o baseline na versão 13, aplica V14 a V16 e o bootstrap de dados.
6. Teste: `curl https://forward-api-java.onrender.com/health` e depois o login com
   `gestor@forward.dev` / `Forward@2026`.

Onde copiar a conexão do Supabase: **Supabase > Project Settings > Database > Connection string >
Session pooler** (ou botão **Connect** no topo do projeto). O painel mostra uma URI
`postgresql://postgres.ysewoopjgdpvnkfhffgy:[YOUR-PASSWORD]@aws-1-sa-east-1.pooler.supabase.com:5432/postgres`;
para o JDBC, prefixe `jdbc:`, tire usuário e senha da URL (eles vão em `DATABASE_USER` e
`DATABASE_PASSWORD`) e acrescente `?sslmode=require`. Confira o host exibido no painel do projeto: este projeto está no pooler `aws-1-sa-east-1` (conferido em 27/09/2026; o `aws-0` responde "tenant not found").

Notas importantes:

- Use o **Session pooler (porta 5432, IPv4)**. O host direto `db.<ref>.supabase.co` é só IPv6 e o
  Render não tem saída IPv6. **Não use o Transaction pooler (6543)**: ele quebra prepared
  statements e o lock do Flyway (se for inevitável, acrescente `prepareThreshold=0` à URL).
- A autorização é feita pela API. Ela conecta como dono das tabelas, por isso o RLS do Supabase
  não se aplica a ela; a tabela `app_users` tem RLS habilitado sem políticas e sem grants para
  `anon`/`authenticated`, então não fica exposta na Data API do Supabase.
- Plano free: o serviço hiberna após 15 minutos sem tráfego; a primeira requisição depois disso
  leva cerca de 1 minuto (cold start). Abra `/health` antes de uma demonstração.
- O `JWT_SECRET` gerado pelo Render é estável entre deploys; tokens continuam válidos após
  reinícios até expirarem (60 min).

## Solução de problemas

| Sintoma | Causa e solução |
|---|---|
| Porta 8080 ocupada | Use `PORT=8081` (ou `--server.port=8081`). |
| PostgreSQL embarcado não inicia no Windows | O PostgreSQL recusa rodar como Administrador. Execute o terminal como usuário comum. |
| Processos `postgres.exe` sobrando | Ao matar a JVM à força o banco embarcado não é encerrado. Finalize os processos cujo caminho contém `embedded-pg` (Gerenciador de Tarefas ou `taskkill`). |
| 401 `AUTH_TOKEN_INVALID` depois de reiniciar o demo | Sem `JWT_SECRET` a chave muda a cada boot. Faça login de novo ou defina `JWT_SECRET`. |
| 429 no login | Limite de tentativas por minuto por IP; aguarde o tempo do header `Retry-After`. |
| API não sobe no Render: `JWT_SECRET is required` | O perfil prod exige a variável; confira se o Blueprint gerou `JWT_SECRET`. |
| `Connection refused`/timeout para o Supabase | Use o Session pooler (IPv4, 5432) e `sslmode=require`; o host direto é IPv6. |
| `prepared statement "S_1" already exists` | URL do Transaction pooler (6543); troque pelo Session pooler (5432). |
| Primeira resposta lenta no Render | Cold start do plano free (cerca de 1 min após 15 min parado). |
| Logs em JSON no demo | Comportamento esperado; use `LOG_FORMAT=CONSOLE` para logs legíveis. |
| Primeira execução local lenta | Download do Maven, das dependências e extração dos binários do PostgreSQL (uma vez). |
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
  util/          LogSanitizer (log injection), SecureXml (XXE)
  web/           Controllers REST, DTOs (web/dto), filtros de request id e headers, CORS
src/main/resources/
  db/migration/  Flyway V1..V16 (V1..V13 = forward-infra, V14 app_users, V15 notas/chave natural,
                 V16 token_version)
  db/bootstrap/  R__bootstrap_demo_data.sql (perfis prod, demo e test; idempotente)
  db/seed/       R__seed_demo_data.sql (usuários extras, somente demo e test)
  application*.yml, logback-spring.xml, xsd/vehicles.xsd
src/test/java/.../it/   Testes de integração (MockMvc, Tomcat real, migração de produção)
docs/                   ARQUITETURA.md, diagramas (Mermaid + PNG), Postman, evidências
render.yaml             Blueprint do Render
scripts/smoke-demo.sh   Smoke test da API em execução
```

CI (GitHub Actions, workflows reutilizáveis de `fwd-ford/.github`): Spotless, Checkstyle,
SpotBugs + FindSecBugs, testes, Trivy (filesystem) e gitleaks; pipeline DevSecOps (CodeQL,
Semgrep, SBOM, imagem Docker) em modo relatório.
