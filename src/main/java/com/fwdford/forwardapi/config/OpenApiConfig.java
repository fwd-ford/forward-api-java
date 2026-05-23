// OpenAPI metadata: title, version, contact and servers exposed at /v3/api-docs.
// Metadados OpenAPI: titulo, versao, contato e servers expostos em /v3/api-docs.
package com.fwdford.forwardapi.config;

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
                "HTTP and SOAP API for ForwardService — Ford x FIAP 2026 Challenge. "
                    + "Provides customer, vehicle, lead and churn score data for the dealer network.")
            .version("0.1.0")
            .contact(
                new Contact()
                    .name("Forward Team")
                    .email("dev@fwd-ford.com")
                    .url("https://github.com/fwd-ford"))
            .license(new License().name("Proprietary"));

    SecurityScheme bearer =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Supabase JWT issued to authenticated users.");

    SecurityScheme apiKey =
        new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key")
            .description("Internal API key for server-to-server calls (N8N, cron).");

    return new OpenAPI()
        .info(info)
        .servers(List.of(dev, devNative, prod))
        .components(
            new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth", bearer)
                .addSecuritySchemes("apiKey", apiKey))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .addSecurityItem(new SecurityRequirement().addList("apiKey"));
  }
}
