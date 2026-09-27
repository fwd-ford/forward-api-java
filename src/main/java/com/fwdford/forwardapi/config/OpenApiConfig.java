// OpenAPI metadata exposed at /v3/api-docs (Swagger UI at /swagger-ui.html): info in
// pt-BR with the authentication flow and demo users, servers, the bearerAuth scheme
// (applied globally; public operations opt out with @SecurityRequirements) and reusable
// RFC 7807 error responses referenced by the controllers.
// Metadados OpenAPI: descricao em pt-BR, servidores, esquema bearer e respostas de erro.
package com.fwdford.forwardapi.config;

import com.fwdford.forwardapi.error.ApiProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String PROBLEM_JSON = "application/problem+json";

  private static final String DESCRIPTION =
      """
      API REST (nível 2 de maturidade de Richardson) e SOAP do **ForwardService**, a plataforma \
      de retenção de clientes da rede Ford (Challenge Ford x FIAP 2026).

      ## Autenticação (JWT)
      1. `POST /api/v1/auth/login` com `{"email": "...", "password": "..."}` (endpoint público).
      2. Copie o `access_token` da resposta (JWT HS256, validade de 60 minutos).
      3. Envie `Authorization: Bearer <access_token>` em todas as outras chamadas \
      (no Swagger UI, botão **Authorize**).

      Claims do token: `iss=forward-api`, `aud=forward-app`, `sub` (id do usuário), `email`, \
      `name`, `role`, `dealer_id`, `token_version`, `iat`, `exp`, `jti`. A cada requisição a API \
      confere o token com o estado atual do usuário: se ele foi desativado, excluído, mudou de \
      perfil ou de concessionária (ou teve a senha redefinida), o token antigo recebe \
      `401 AUTH_TOKEN_REVOKED` antes mesmo de expirar. Chamadas servidor-a-servidor (n8n) podem \
      usar o header `X-API-Key`.

      ## Perfis
      | Perfil | Escopo |
      |---|---|
      | ATENDENTE | Leads, clientes e veículos da própria concessionária; atualiza leads. |
      | GESTOR | Tudo do ATENDENTE + cria e altera eventos de serviço da concessionária. |
      | ADMIN | Todas as concessionárias, exclui eventos de serviço e administra usuários. |

      ## Usuários de demonstração (perfis `prod` e `demo`)
      Senha de todos: `Forward@2026`

      | E-mail | Perfil | Concessionária |
      |---|---|---|
      | admin@forward.dev | ADMIN | todas |
      | gestor@forward.dev | GESTOR | F0001 Ford Morumbi São Paulo |
      | atendente@forward.dev | ATENDENTE | F0001 Ford Morumbi São Paulo |
      | atendente2@forward.dev | ATENDENTE | F0002 Ford Barra Rio |

      ## Erros
      Todo erro segue a RFC 7807 (`application/problem+json`) com os campos `type`, `title`, \
      `status`, `detail`, `instance`, `code`, `timestamp` e `request_id` (mensagens em pt-BR). \
      Listas retornam o total no header `X-Total-Count`. Limite de requisições por IP com \
      resposta `429` e header `Retry-After`.
      """;

  @Bean
  public OpenAPI forwardApiOpenApi(@Value("${forward.version:3.0.0}") String version) {
    Info info =
        new Info()
            .title("ForwardService API")
            .version("3.0.0")
            .description(DESCRIPTION)
            .contact(
                new Contact()
                    .name("Forward Team (FIAP)")
                    .url("https://github.com/fwd-ford/forward-api-java"));

    SecurityScheme bearer =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT emitido por POST /api/v1/auth/login.");

    SecurityScheme apiKey =
        new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key")
            .description("Chave interna para integrações servidor-a-servidor (n8n, cron).");

    Components components =
        new Components()
            .addSecuritySchemes("bearerAuth", bearer)
            .addSecuritySchemes("apiKey", apiKey);
    ModelConverters.getInstance().read(ApiProblem.class).forEach(components::addSchemas);
    ModelConverters.getInstance()
        .read(com.fwdford.forwardapi.error.GlobalExceptionHandler.FieldProblem.class)
        .forEach(components::addSchemas);
    problemResponses().forEach(components::addResponses);

    OpenAPI api =
        new OpenAPI()
            .info(info)
            .externalDocs(
                new ExternalDocumentation()
                    .description("README, arquitetura e matriz de permissões")
                    .url("https://github.com/fwd-ford/forward-api-java#readme"))
            .servers(
                List.of(
                    new Server()
                        .url("https://forward-api-java.onrender.com")
                        .description("Produção (Render + Supabase)"),
                    new Server().url("http://localhost:8080").description("Local (perfil demo)")))
            .components(components)
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    api.addExtension("x-build-version", version);
    return api;
  }

  private static Map<String, ApiResponse> problemResponses() {
    Map<String, ApiResponse> r = new LinkedHashMap<>();
    r.put(
        "BadRequest",
        problem(
            "Requisição inválida (validação, JSON malformado, parâmetro inválido)",
            400,
            "VALIDATION_FAILED",
            "Um ou mais campos são inválidos."));
    r.put(
        "InvalidCredentials",
        problem(
            "Credenciais inválidas ou usuário desativado",
            401,
            "AUTH_INVALID_CREDENTIALS",
            "E-mail ou senha inválidos."));
    r.put(
        "Unauthorized",
        problem(
            "Token ausente, inválido, expirado ou revogado (AUTH_REQUIRED, AUTH_TOKEN_INVALID,"
                + " AUTH_TOKEN_EXPIRED, AUTH_TOKEN_REVOKED)",
            401,
            "AUTH_REQUIRED",
            "Autenticação necessária. Envie o header Authorization: Bearer <token>."));
    r.put(
        "Forbidden",
        problem(
            "Perfil sem permissão (ACCESS_DENIED) ou recurso de outra concessionária"
                + " (ACCESS_OTHER_DEALER)",
            403,
            "ACCESS_OTHER_DEALER",
            "Este recurso pertence a outra concessionária."));
    r.put(
        "NotFound",
        problem("Recurso não encontrado", 404, "LEAD_NOT_FOUND", "Lead não encontrado."));
    r.put(
        "Conflict",
        problem(
            "Conflito com o estado atual (transição inválida, duplicidade)",
            409,
            "LEAD_INVALID_TRANSITION",
            "Transição de status inválida: converted -> lost."));
    r.put(
        "UnprocessableEntity",
        problem(
            "Referência inexistente no corpo (veículo ou concessionária)",
            422,
            "REFERENCED_VEHICLE_NOT_FOUND",
            "O veículo informado (vin) não existe."));
    r.put(
        "UnsupportedMediaType",
        problem(
            "Content-Type não suportado",
            415,
            "UNSUPPORTED_MEDIA_TYPE",
            "Content-Type não suportado. Envie o corpo como application/json."));
    r.put(
        "TooManyRequests",
        problem(
            "Limite de requisições excedido (header Retry-After)",
            429,
            "RATE_LIMITED",
            "Limite de requisições excedido. Tente novamente em instantes."));
    return r;
  }

  private static ApiResponse problem(String description, int status, String code, String detail) {
    Map<String, Object> example = new LinkedHashMap<>();
    example.put("type", "urn:forward:problem:" + code.toLowerCase().replace('_', '-'));
    example.put(
        "title",
        com.fwdford.forwardapi.error.Problems.defaultTitle(
            org.springframework.http.HttpStatusCode.valueOf(status)));
    example.put("status", status);
    example.put("detail", detail);
    example.put("instance", "/api/v1/...");
    example.put("code", code);
    example.put("timestamp", "2026-09-27T12:00:00Z");
    example.put("request_id", "5b0c7a4e-8f1d-4c1e-9a55-2f4d7e9b1c33");
    MediaType media =
        new MediaType()
            .schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))
            .example(example);
    return new ApiResponse()
        .description(description)
        .content(new Content().addMediaType(PROBLEM_JSON, media));
  }
}
