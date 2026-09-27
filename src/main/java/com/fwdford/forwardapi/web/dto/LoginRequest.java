// Body of POST /api/v1/auth/login.
// Corpo do POST /api/v1/auth/login.
package com.fwdford.forwardapi.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credenciais do usuário.")
public record LoginRequest(
    @Schema(
            description = "E-mail do usuário (sem diferenciar maiúsculas).",
            example = "gestor@forward.dev",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "e-mail é obrigatório")
        @Email(message = "e-mail inválido")
        @Size(max = 254, message = "e-mail deve ter no máximo 254 caracteres")
        String email,
    @Schema(
            description = "Senha do usuário.",
            example = "Forward@2026",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "senha é obrigatória")
        @Size(max = 72, message = "senha deve ter no máximo 72 caracteres")
        String password) {

  // Never print the password (records include every component in toString by default).
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}
