// POST /api/v1/auth/login (public). Validates the body and delegates to AuthService.
// POST /api/v1/auth/login (publico): valida o corpo e delega ao AuthService.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.service.AuthService;
import com.fwdford.forwardapi.web.dto.LoginRequest;
import com.fwdford.forwardapi.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Autenticação: login com e-mail e senha, emissão de JWT.")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirements
  @Operation(
      operationId = "login",
      summary = "Login (emite JWT)",
      description =
          "Endpoint público. Valida e-mail e senha (BCrypt) e devolve um JWT HS256 com"
              + " validade de 60 minutos (JWT_EXPIRATION_MINUTES). Use o token no header"
              + " `Authorization: Bearer <access_token>`. E-mail inexistente e senha errada"
              + " devolvem a mesma resposta 401 AUTH_INVALID_CREDENTIALS. Limite: 5 tentativas"
              + " por minuto por IP (429).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Login realizado",
        content = @Content(schema = @Schema(implementation = LoginResponse.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "InvalidCredentials"),
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public LoginResponse login(@Valid @RequestBody LoginRequest req) {
    return auth.login(req.email(), req.password());
  }
}
