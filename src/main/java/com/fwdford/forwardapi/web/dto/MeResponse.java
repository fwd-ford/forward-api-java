// Response of GET /api/v1/me: who is calling and what the profile may do.
// Resposta de GET /api/v1/me: quem esta chamando e o que o perfil pode fazer.
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Me", description = "Usuário autenticado e suas permissões.")
public record MeResponse(
    @Schema(
            description = "UUID do usuário ('service' para chamadas com X-API-Key).",
            example = "ad000000-0000-4000-8000-000000000003")
        String id,
    @Schema(description = "Nome completo.", example = "Beatriz Santos") String name,
    @Schema(description = "E-mail.", example = "atendente@forward.dev", nullable = true)
        String email,
    @Schema(
            description = "Perfil de acesso.",
            example = "ATENDENTE",
            allowableValues = {"ATENDENTE", "GESTOR", "ADMIN", "SERVICE"})
        String role,
    @JsonProperty("dealer_id")
        @Schema(
            description = "Concessionária do usuário (null para ADMIN e SERVICE).",
            example = "d0000000-0000-4000-8000-000000000001",
            nullable = true)
        String dealerId,
    @JsonProperty("dealer_name")
        @Schema(
            description = "Nome da concessionária.",
            example = "Ford Morumbi São Paulo",
            nullable = true)
        String dealerName,
    @ArraySchema(
            arraySchema = @Schema(description = "Permissões derivadas do perfil."),
            schema = @Schema(example = "leads:read"))
        List<String> permissions) {}
