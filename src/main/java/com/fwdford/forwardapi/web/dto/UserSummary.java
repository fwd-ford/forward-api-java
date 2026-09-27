// Public view of the logged-in user embedded in the login response.
// Visao publica do usuario autenticado, embutida na resposta do login.
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fwdford.forwardapi.model.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserSummary", description = "Dados básicos do usuário autenticado.")
public record UserSummary(
    @Schema(description = "UUID do usuário.", example = "ad000000-0000-4000-8000-000000000002")
        String id,
    @Schema(description = "Nome completo.", example = "Gustavo Mendes") String name,
    @Schema(description = "E-mail.", example = "gestor@forward.dev") String email,
    @Schema(
            description = "Perfil de acesso.",
            example = "GESTOR",
            allowableValues = {"ATENDENTE", "GESTOR", "ADMIN"})
        String role,
    @JsonProperty("dealer_id")
        @Schema(
            description = "Concessionária do usuário (null para ADMIN).",
            example = "d0000000-0000-4000-8000-000000000001",
            nullable = true)
        String dealerId,
    @JsonProperty("dealer_name")
        @Schema(
            description = "Nome da concessionária (null para ADMIN).",
            example = "Ford Morumbi São Paulo",
            nullable = true)
        String dealerName) {

  public static UserSummary from(AppUser u) {
    return new UserSummary(
        u.id(), u.fullName(), u.email(), u.role().name(), u.dealerId(), u.dealerName());
  }
}
