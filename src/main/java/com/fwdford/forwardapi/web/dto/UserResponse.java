// User representation returned by the /api/v1/users admin endpoints.
// Representacao de usuario nos endpoints administrativos /api/v1/users.
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fwdford.forwardapi.model.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "User", description = "Usuário da aplicação (nunca inclui o hash da senha).")
public record UserResponse(
    @Schema(description = "UUID do usuário.", example = "ad000000-0000-4000-8000-000000000003")
        String id,
    @Schema(description = "Nome completo.", example = "Beatriz Santos") String name,
    @Schema(description = "E-mail.", example = "atendente@forward.dev") String email,
    @Schema(
            description = "Perfil de acesso.",
            example = "ATENDENTE",
            allowableValues = {"ATENDENTE", "GESTOR", "ADMIN"})
        String role,
    @JsonProperty("dealer_id")
        @Schema(description = "Concessionária (null para ADMIN).", nullable = true)
        String dealerId,
    @JsonProperty("dealer_name") @Schema(description = "Nome da concessionária.", nullable = true)
        String dealerName,
    @Schema(description = "Usuário ativo (pode fazer login).", example = "true") boolean active,
    @JsonProperty("last_login_at")
        @Schema(description = "Último login bem-sucedido.", nullable = true, format = "date-time")
        OffsetDateTime lastLoginAt,
    @JsonProperty("created_at") @Schema(format = "date-time") OffsetDateTime createdAt,
    @JsonProperty("updated_at") @Schema(format = "date-time") OffsetDateTime updatedAt) {

  public static UserResponse from(AppUser u) {
    return new UserResponse(
        u.id(),
        u.fullName(),
        u.email(),
        u.role().name(),
        u.dealerId(),
        u.dealerName(),
        u.active(),
        u.lastLoginAt(),
        u.createdAt(),
        u.updatedAt());
  }
}
