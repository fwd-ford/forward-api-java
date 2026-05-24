// OpenAPI metadata: title, version, contact and servers exposed at /v3/api-docs.
// Metadados OpenAPI: titulo, versao, contato e servers expostos em /v3/api-docs.
package com.fwdford.forwardapi.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI forwardApiOpenApi() {
    Server dev = new Server().url("http://localhost:18080").description("Local Docker");
    Server devNative = new Server().url("http://localhost:8080").description("Local native (mvnw)");
    Server prod = new Server().url("https://forward-api.fwd-ford.com").description("Production");

    Info info =
        new Info()
            .title("Forward API")
            .description(
                "REST and SOAP backend for ForwardService, the Ford x FIAP 2026 challenge"
                    + " platform. Exposes customer, vehicle, lead, churn score and service event"
                    + " resources for the dealer network.\n\n"
                    + "**Authentication**: every endpoint except `/health` and `/ready` requires"
                    + " a bearer JWT (Supabase) **or** an `X-API-Key` header for"
                    + " server-to-server callers (n8n, cron jobs).\n\n"
                    + "**Rate limiting**: requests are limited per IP and subject via Bucket4j."
                    + " When the bucket is empty the API returns `429 Too Many Requests`.\n\n"
                    + "**Errors**: every error response follows RFC 7807 `application/problem+json`"
                    + " using Spring's `ProblemDetail` model. User-facing `detail` messages are"
                    + " in Portuguese (pt-BR).")
            .version("0.1.0")
            .contact(
                new Contact()
                    .name("Forward Team")
                    .email("dev@fwd-ford.com")
                    .url("https://github.com/fwd-ford/forward-api-java"))
            .license(
                new License()
                    .name("Proprietary")
                    .url("https://github.com/fwd-ford/forward-api-java/blob/main/LICENSE"));

    SecurityScheme bearer =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description(
                "Supabase JWT issued to authenticated users. Validated against the configured"
                    + " HS256 secret or the project JWKS (asymmetric).");

    SecurityScheme apiKey =
        new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key")
            .description(
                "Internal API key for server-to-server calls (n8n, cron). Set via the"
                    + " `INTERNAL_API_KEY` env var.");

    ExternalDocumentation externalDocs =
        new ExternalDocumentation()
            .description("Repository, runbook and architecture docs")
            .url("https://github.com/fwd-ford/forward-api-java#readme");

    return new OpenAPI()
        .info(info)
        .externalDocs(externalDocs)
        .servers(List.of(dev, devNative, prod))
        .components(
            new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth", bearer)
                .addSecuritySchemes("apiKey", apiKey))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .addSecurityItem(new SecurityRequirement().addList("apiKey"));
  }
}
