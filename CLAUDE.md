# forward-api-java: Repository Instructions

## Status

Official backend of ForwardService as of 2026-04-21. Replaces a previous
Go + Fiber implementation that was archived at
[github.com/fwd-ford/forward-api](https://github.com/fwd-ford/forward-api)
(read-only, kept only for historical reference).

## Language Policy

- Code, comments, documentation: English.
- User-facing error messages (RFC 7807 `detail`): Portuguese (pt-BR) with correct diacritics.
- Comments bilingual only where clarity helps: English first, then a short PT-BR line.
- Never use em dashes or en dashes.

## Stack

- Java 17 (Eclipse Temurin)
- Spring Boot 3.5 (Web, Web Services / SOAP, Security, Validation, JDBC, Actuator)
- PostgreSQL via HikariCP; schema managed by Flyway (`db/migration` V1..V15, `db/bootstrap` idempotent demo users/data, `db/seed` demo/test only)
- Zonky embedded PostgreSQL 16 for the `demo` profile and the test suite (no Docker needed)
- Spring Data JDBC with `NamedParameterJdbcTemplate` (raw SQL, parameterized)
- Bucket4j: in-memory sliding-window rate limiting
- JJWT (HS256): the API issues and validates its own JWTs (`JwtService`)
- Logback + Logstash encoder: JSON structured logging
- Maven Wrapper (`./mvnw`): auto-downloads Maven 3.9 on first run

## Project Structure

- `src/main/java/com/fwdford/forwardapi/ForwardApiApplication.java`: Spring Boot entrypoint.
- `config/`: `@ConfigurationProperties` records bound from `application.yml`.
- `web/`: REST controllers, input validation helpers, CORS, security headers, request id filter.
- `service/`: business logic and RBAC enforcement.
- `repository/`: JDBC data access (no ORM) using `NamedParameterJdbcTemplate`.
- `security/`: `JwtService` (issue/validate), `JwtAuthenticationFilter`, `SecurityConfig` (public vs protected routes, roles), `RateLimitFilter`, RFC 7807 401/403 handlers.
- `soap/`: contract-first SOAP endpoint via Spring WS, XSD-driven WSDL.
- `error/`: `ApiException` hierarchy and `@RestControllerAdvice` mapping to RFC 7807 `ProblemDetail`.
- `src/main/resources/application.yml`: runtime config (env-overridable).
- `src/main/resources/logback-spring.xml`: JSON logs in production, plain console in dev.
- `src/main/resources/xsd/vehicles.xsd`: SOAP contract.
- `src/test/java/...`: JUnit 5 + Mockito unit tests and `it/*IT.java` HTTP integration tests (MockMvc + embedded PostgreSQL).
- `render.yaml`: Render Blueprint (Docker web service, prod profile, Supabase database).

## Mandatory Patterns

### Separation of Concerns

- **Controller**: parse request, validate input, call service, format response. No business logic.
- **Service**: business logic, orchestration, RBAC.
- **Repository**: data access only. All queries parameterized, never concatenated with user input.
- **Security filters**: auth, rate limit, CORS, headers, request id. No domain logic.

### Auth

- `POST /api/v1/auth/login` (public) checks BCrypt passwords in `app_users` and issues an HS256 JWT (`iss` forward-api, `aud` forward-app, `sub`, `role`, `dealer_id`, `exp`, `jti`). `JWT_SECRET` is mandatory in production (startup fails without it).
- `JwtAuthenticationFilter` validates signature, expiry (30 s skew), issuer and audience, then puts an `AuthenticatedUser` with `ROLE_<ROLE>` in the `SecurityContext`.
- Roles: ATENDENTE, GESTOR, ADMIN (plus SERVICE for `X-API-Key`, compared in constant time). Enforce them in `SecurityConfig` and with `@PreAuthorize` in services; non-admin data access is always scoped by the token's `dealer_id` (403 `ACCESS_OTHER_DEALER`).
- Never trust client-side role claims without server validation; Supabase RLS does not apply to the API connection, so authorization lives here.

### Error Handling

- Throw `ApiException` (use the static constructors: `badRequest`, `notFound`, `forbidden`, ...).
- `GlobalExceptionHandler` converts to RFC 7807 `ProblemDetail`.
- Never expose stack traces, internal paths, or technology details in responses.
- Log unhandled exceptions with structured fields via SLF4J.

### Security (Cybersecurity discipline)

- Rate limiting on every authenticated endpoint (Bucket4j, keyed by IP + subject).
- Input validation for UUIDs, VINs, enums, numeric limits: reject before hitting service.
- CORS allowlist comes from `forward.allowed-origins`; wildcard is never accepted.
- Security headers applied globally (HSTS, CSP, X-Frame-Options, etc).
- HTTPS/TLS 1.2+ is a deployment concern (terminated by the Render edge in production).

## Build

- `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`: run locally on `:8080` with embedded PostgreSQL + demo data (Windows: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`).
- `./mvnw test`: JUnit suite (unit + HTTP integration). `./mvnw verify -P quality` adds Checkstyle, SpotBugs and JaCoCo reports.
- Deploy: Render Blueprint (`render.yaml`); database = Supabase PostgreSQL via the Session pooler (Flyway baselines the existing schema at 13).
- `./mvnw -DskipTests package`: fat JAR at `target/forward-api.jar`.
- On Windows without bash, use `mvnw.cmd` equivalents.

## Port

`8080`: both for native dev (no container) and for the container's internal port.
