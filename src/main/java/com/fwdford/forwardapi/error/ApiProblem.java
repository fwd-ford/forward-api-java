// Documentation-only schema of the RFC 7807 body produced by Problems/GlobalExceptionHandler.
// Runtime responses are Spring ProblemDetail instances with these same properties.
// Schema (so para documentacao) do corpo RFC 7807 devolvido em todos os erros.
package com.fwdford.forwardapi.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    name = "ApiProblem",
    description =
        "Erro no formato RFC 7807 (application/problem+json). Todo erro da API segue este"
            + " formato; 'code' é estável e pode ser usado pelo cliente.")
public record ApiProblem(
    @Schema(
            description = "URI que identifica o tipo do problema.",
            example = "urn:forward:problem:access-other-dealer")
        String type,
    @Schema(description = "Resumo do problema (pt-BR).", example = "Acesso negado") String title,
    @Schema(description = "Status HTTP.", example = "403") int status,
    @Schema(
            description = "Explicação para o usuário (pt-BR).",
            example = "Este recurso pertence a outra concessionária.")
        String detail,
    @Schema(
            description = "Caminho da requisição.",
            example = "/api/v1/leads/a1000000-0000-4000-8000-000000000009")
        String instance,
    @Schema(description = "Código estável do erro.", example = "ACCESS_OTHER_DEALER") String code,
    @Schema(description = "Instante do erro (UTC).", example = "2026-09-27T12:00:00Z")
        String timestamp,
    @JsonProperty("request_id")
        @Schema(
            description = "Id de correlação (header X-Request-Id).",
            example = "5b0c7a4e-8f1d-4c1e-9a55-2f4d7e9b1c33")
        String requestId,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description = "Erros por campo (somente em VALIDATION_FAILED).",
                    nullable = true))
        List<GlobalExceptionHandler.FieldProblem> errors) {}
