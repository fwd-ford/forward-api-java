// Body of POST /api/v1/users (ADMIN only).
// Corpo do POST /api/v1/users (somente ADMIN).
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateUserRequest", description = "Dados para criar um usuário.")
public record CreateUserRequest(
    @Schema(example = "marina.lopes@forward.dev", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "e-mail é obrigatório")
        @Email(message = "e-mail inválido")
        @Size(max = 254, message = "e-mail deve ter no máximo 254 caracteres")
        String email,
    @Schema(example = "Marina Lopes", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "nome é obrigatório")
        @Size(max = 120, message = "nome deve ter no máximo 120 caracteres")
        String name,
    @Schema(
            description = PasswordPolicy.DESCRIPTION,
            example = "Troca@2026",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "senha é obrigatória")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,
    @Schema(
            allowableValues = {"ATENDENTE", "GESTOR", "ADMIN"},
            example = "ATENDENTE",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "perfil é obrigatório")
        @Pattern(
            regexp = "^(ATENDENTE|GESTOR|ADMIN)$",
            message = "perfil deve ser ATENDENTE, GESTOR ou ADMIN")
        String role,
    @JsonProperty("dealer_id")
        @Schema(
            description = "Obrigatório para ATENDENTE e GESTOR; ignorado para ADMIN.",
            example = "d0000000-0000-4000-8000-000000000001",
            format = "uuid",
            nullable = true)
        @Pattern(regexp = PasswordPolicy.UUID_REGEX, message = "dealer_id deve ser um UUID válido")
        String dealerId) {

  // Never print the password.
  @Override
  public String toString() {
    return "CreateUserRequest[email=" + email + ", role=" + role + ", dealerId=" + dealerId + "]";
  }
}
