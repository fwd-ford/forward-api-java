// GET /api/v1/me: authenticated caller, fresh from the database, with its permissions.
// GET /api/v1/me: usuario autenticado (dados atualizados do banco) e permissoes.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.UserService;
import com.fwdford.forwardapi.web.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Me", description = "Usuário autenticado.")
public class MeController {

  private final UserService users;

  public MeController(UserService users) {
    this.users = users;
  }

  @GetMapping("/me")
  @Operation(
      operationId = "me",
      summary = "Usuário autenticado",
      description =
          "Retorna o usuário dono do token (todos os perfis), com a concessionária e a lista de"
              + " permissões derivadas do perfil. Útil para o app montar a interface.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Usuário autenticado",
        content = @Content(schema = @Schema(implementation = MeResponse.class))),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return users.me(user);
  }
}
