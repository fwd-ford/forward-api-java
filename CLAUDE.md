# forward-api-java — Repository Instructions

## Status

Official backend of ForwardService as of 2026-04-21. Replaces a previous
Go + Fiber implementation that was archived at
[github.com/fwd-ford/forward-api](https://github.com/fwd-ford/forward-api)
(read-only, kept only for historical reference).

## Language Policy

- Code, comments, documentation: English.
- User-facing error messages (RFC 7807 `detail`): Portuguese (pt-BR) with correct diacritics.
- Comments bilingual only where clarity helps — English first, then a short PT-BR line.
- Never use em dashes or en dashes.

## Stack

- Java 17 (Eclipse Temurin)
- Spring Boot 3.2 (Web, Web Services / SOAP, Security, Validation, JDBC, Actuator)
- PostgreSQL via HikariCP
- Spring Data JDBC with `NamedParameterJdbcTemplate` (raw SQL, parameterized)
- Bucket4j — in-memory sliding-window rate limiting
- Nimbus JOSE (JWKS) + JJWT (HS256) — JWT validation
- Logback + Logstash encoder — JSON structured logging
- Maven Wrapper (`./mvnw`) — auto-downloads Maven 3.9 on first run

## Project Structure

- `src/main/java/com/fwdford/forwardapi/ForwardApiApplication.java` — Spring Boot entrypoint.
- `config/` — `@ConfigurationProperties` records bound from `application.yml`.
- `web/` — REST controllers, input validation helpers, CORS, security headers, request id filter.
- `service/` — business logic and RBAC enforcement.
- `repository/` — JDBC data access (no ORM) using `NamedParameterJdbcTemplate`.
- `security/` — JWT validators (HS256, JWKS), auth filter, rate limit filter, Spring Security config.
- `soap/` — contract-first SOAP endpoint via Spring WS, XSD-driven WSDL.
- `error/` — `ApiException` hierarchy and `@RestControllerAdvice` mapping to RFC 7807 `ProblemDetail`.
- `src/main/resources/application.yml` — runtime config (env-overridable).
- `src/main/resources/logback-spring.xml` — JSON logs in production, plain console in dev.
- `src/main/resources/xsd/vehicles.xsd` — SOAP contract.
- `src/test/java/...` — JUnit 5 + Mockito unit tests.

## Mandatory Patterns

### Separation of Concerns

- **Controller**: parse request, validate input, call service, format response. No business logic.
- **Service**: business logic, orchestration, RBAC.
- **Repository**: data access only. All queries parameterized, never concatenated with user input.
- **Security filters**: auth, rate limit, CORS, headers, request id. No domain logic.

### Auth

- Validate Supabase JWT in `AuthFilter`. Support HS256 (shared secret) and JWKS (asymmetric).
- Accept `X-API-Key` for server-to-server calls (N8N, cron) in lieu of JWT.
- Extract `sub` and `role` into `AuthPrincipal` stored as a request attribute.
- Never trust client-side role claims without server validation.

### Error Handling

- Throw `ApiException` (use the static constructors: `badRequest`, `notFound`, `forbidden`, ...).
- `GlobalExceptionHandler` converts to RFC 7807 `ProblemDetail`.
- Never expose stack traces, internal paths, or technology details in responses.
- Log unhandled exceptions with structured fields via SLF4J.

### Security (Cybersecurity discipline)

- Rate limiting on every authenticated endpoint (Bucket4j, keyed by IP + subject).
- Input validation for UUIDs, VINs, enums, numeric limits — reject before hitting service.
- CORS allowlist comes from `forward.allowed-origins`; wildcard is never accepted.
- Security headers applied globally (HSTS, CSP, X-Frame-Options, etc).
- HTTPS/TLS 1.2+ is a deployment concern (handled by Fly.io edge proxy in production; `fly.toml` enforces `force_https = true`).

## Build

- `./mvnw spring-boot:run` — run locally on `:8080`.
- `./mvnw test` — JUnit suite.
- `./mvnw -DskipTests package` — fat JAR at `target/forward-api.jar`.
- On Windows without bash, use `mvnw.cmd` equivalents.

## Port

`8080` — both for native dev (no container) and for the container's internal port.
