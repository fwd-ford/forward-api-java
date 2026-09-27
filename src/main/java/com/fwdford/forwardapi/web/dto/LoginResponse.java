// Response of POST /api/v1/auth/login (OAuth2-style token response + user).
// Resposta do login: token no estilo OAuth2 e dados do usuario.
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "LoginResponse", description = "Token de acesso emitido pela API.")
public record LoginResponse(
    @JsonProperty("access_token")
        @Schema(description = "JWT HS256 a ser enviado em Authorization: Bearer <token>.")
        String accessToken,
    @JsonProperty("token_type") @Schema(description = "Sempre Bearer.", example = "Bearer")
        String tokenType,
    @JsonProperty("expires_in")
        @Schema(description = "Validade do token em segundos.", example = "3600")
        long expiresIn,
    @JsonProperty("expires_at")
        @Schema(description = "Instante de expiração (ISO 8601, UTC).", format = "date-time")
        Instant expiresAt,
    @Schema(description = "Usuário autenticado.") UserSummary user) {

  // Tokens must never end up in logs.
  @Override
  public String toString() {
    return "LoginResponse[user=" + user + ", expiresIn=" + expiresIn + "]";
  }
}
