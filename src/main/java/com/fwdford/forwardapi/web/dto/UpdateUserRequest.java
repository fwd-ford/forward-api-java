// Body of PATCH /api/v1/users/{id} (ADMIN only). Every field is optional but at least one
// must be present. role=ADMIN clears the dealer (admins see every dealer).
// Corpo do PATCH /api/v1/users/{id}: campos opcionais, ao menos um obrigatorio.
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateUserRequest", description = "Campos alteráveis de um usuário.")
public record UpdateUserRequest(
    @Schema(example = "Beatriz Santos Lima", nullable = true)
        @Size(min = 1, max = 120, message = "nome deve ter entre 1 e 120 caracteres")
        String name,
    @Schema(
            allowableValues = {"ATENDENTE", "GESTOR", "ADMIN"},
            nullable = true)
        @Pattern(
            regexp = "^(ATENDENTE|GESTOR|ADMIN)$",
            message = "perfil deve ser ATENDENTE, GESTOR ou ADMIN")
        String role,
    @Schema(description = "false bloqueia o login do usuário.", nullable = true) Boolean active,
    @JsonProperty("dealer_id")
        @Schema(format = "uuid", nullable = true)
        @Pattern(regexp = PasswordPolicy.UUID_REGEX, message = "dealer_id deve ser um UUID válido")
        String dealerId,
    @Schema(
            description = "Nova senha (redefinição pelo ADMIN). " + PasswordPolicy.DESCRIPTION,
            format = "password",
            nullable = true)
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password) {

  public boolean isEmpty() {
    return name == null && role == null && active == null && dealerId == null && password == null;
  }

  // Never print the password.
  @Override
  public String toString() {
    return "UpdateUserRequest[name=" + name + ", role=" + role + ", active=" + active + "]";
  }
}
