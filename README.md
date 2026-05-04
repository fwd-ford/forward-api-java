# forward-api (Java / Spring Boot)

HTTP and SOAP API for **ForwardService**. Java 17 + Spring Boot 3.2 port of the
Go `forward-api`, built to satisfy the SOA discipline requirement.

## Stack

- Java 17 (Eclipse Temurin)
- Spring Boot 3.2 (Web, Web Services, Security, Validation, JDBC, Actuator)
- PostgreSQL via HikariCP
- Bucket4j rate limiting
- Nimbus JOSE + JJWT for JWT validation (HS256 + JWKS)
- Logback + Logstash encoder for JSON structured logging
- Maven Wrapper (auto-downloads Maven on first run)

## Structure

```
src/main/java/com/fwdford/forwardapi/
  ForwardApiApplication.java          Spring Boot entrypoint
  config/AppProperties.java           @ConfigurationProperties bindings
  error/                              RFC 7807 problem details
  model/                              DTOs (records)
  repository/                         JDBC repositories (NamedParameterJdbcTemplate)
  security/                           JWT validators, auth filter, rate limit, Spring Security
  service/                            Business logic (RBAC)
  soap/                               Spring WS endpoint (contract-first XSD -> WSDL)
  web/                                REST controllers, CORS, security headers, request id
src/main/resources/
  application.yml                     Runtime configuration
  logback-spring.xml                  JSON logging in production, plain in dev
  xsd/vehicles.xsd                    SOAP contract
```

## Endpoints (Sprint 1)

| Method | Path                              | Auth | Description             |
|--------|-----------------------------------|------|-------------------------|
| GET    | `/health`, `/ready`               | no   | Liveness                |
| GET    | `/api/v1/me`                      | yes  | Current subject         |
| GET    | `/api/v1/customers/{id}`          | yes  | Customer by id          |
| GET    | `/api/v1/vehicles/{vin}`          | yes  | Vehicle by VIN          |
| GET    | `/api/v1/leads`                   | yes  | List leads              |
| GET    | `/api/v1/scores/{customerId}`     | yes  | Current churn score     |
| GET    | `/soap/vehicles.wsdl`             | no   | WSDL discovery          |
| POST   | `/soap/vehicles`                  | yes  | SOAP 1.1 `GetVehicle`   |

Port: `8080` (default). Override with `PORT=...`.

## Quick start

```bash
./mvnw spring-boot:run
# or
make run
```

The first `./mvnw` run downloads Apache Maven 3.9.6 automatically into
`~/.m2/wrapper`, then builds and starts the server on port 8080.

## Development

```bash
make test      # JUnit tests via mvnw
make build     # package target/forward-api.jar
make run       # live server
```
