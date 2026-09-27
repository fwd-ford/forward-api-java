// User administration (ADMIN only): GET list, GET /{id}, POST (201 + Location),
// PATCH /{id}, DELETE /{id} (204). ADMIN is enforced twice: URL rule in SecurityConfig
// (so other profiles get 403 before any validation) and @PreAuthorize in UserService.
// Administracao de usuarios (somente ADMIN) com CRUD completo.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.Role;
import com.fwdford.forwardapi.service.UserService;
import com.fwdford.forwardapi.web.dto.CreateUserRequest;
import com.fwdford.forwardapi.web.dto.UpdateUserRequest;
import com.fwdford.forwardapi.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "Administração de usuários (somente ADMIN).")
public class UserController {

  private static final List<String> ROLES = List.of("ATENDENTE", "GESTOR", "ADMIN");

  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping
  @Operation(
      operationId = "listUsers",
      summary = "Listar usuários",
      description = "Somente ADMIN. Total no header X-Total-Count.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de usuários",
        headers =
            @Header(
                name = "X-Total-Count",
                description = "Total de usuários que atendem aos filtros",
                schema = @Schema(type = "integer")),
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<List<UserResponse>> list(
      @Parameter(
              description = "Filtra por perfil.",
              schema = @Schema(allowableValues = {"ATENDENTE", "GESTOR", "ADMIN"}))
          @RequestParam(name = "role", required = false)
          String role,
      @Parameter(description = "Filtra por ativo/inativo.")
          @RequestParam(name = "active", required = false)
          Boolean active,
      @Parameter(description = "Filtra por concessionária.", schema = @Schema(format = "uuid"))
          @RequestParam(name = "dealer_id", required = false)
          String dealerId,
      @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "200"))
          @RequestParam(name = "limit", required = false)
          Integer limit,
      @Parameter(schema = @Schema(type = "integer", minimum = "0"))
          @RequestParam(name = "offset", required = false)
          Integer offset) {
    String validRole = Validations.validateEnum("role", role, ROLES);
    UUID dealer =
        dealerId == null || dealerId.isEmpty()
            ? null
            : UUID.fromString(Validations.validateUuid("dealer_id", dealerId));
    PageResult<UserResponse> page =
        users.list(
            validRole.isEmpty() ? null : Role.valueOf(validRole),
            active,
            dealer,
            Validations.validateLimit(limit, 50, 200),
            Validations.validateOffset(offset));
    return ResponseEntity.ok()
        .header(Pagination.TOTAL_COUNT, String.valueOf(page.total()))
        .body(page.items());
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getUser", summary = "Usuário por id", description = "Somente ADMIN.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Usuário encontrado",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public UserResponse get(@Parameter(description = "UUID do usuário.") @PathVariable String id) {
    return users.get(UUID.fromString(Validations.validateUuid("id", id)));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "createUser",
      summary = "Criar usuário",
      description =
          "Somente ADMIN. E-mail duplicado: 409 USER_EMAIL_TAKEN. Senha fraca: 400."
              + " Concessionária inexistente: 422. ATENDENTE e GESTOR exigem dealer_id.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Usuário criado",
        headers = @Header(name = "Location", description = "URL do novo recurso"),
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType"),
    @ApiResponse(responseCode = "422", ref = "UnprocessableEntity"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<UserResponse> create(
      @Valid @RequestBody CreateUserRequest req,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
    UserResponse created = users.create(req, actor);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "updateUser",
      summary = "Atualizar usuário",
      description =
          "Somente ADMIN. Campos opcionais: name, role, active, dealer_id, password. O ADMIN não"
              + " pode alterar o próprio perfil nem desativar a própria conta (409).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Usuário atualizado",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "422", ref = "UnprocessableEntity"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public UserResponse update(
      @Parameter(description = "UUID do usuário.") @PathVariable String id,
      @Valid @RequestBody UpdateUserRequest req,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
    return users.update(UUID.fromString(Validations.validateUuid("id", id)), req, actor);
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteUser",
      summary = "Excluir usuário",
      description = "Somente ADMIN. Responde 204. Excluir a própria conta: 409 USER_SELF_DELETE.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Usuário excluído"),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "UUID do usuário.") @PathVariable String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
    users.delete(UUID.fromString(Validations.validateUuid("id", id)), actor);
    return ResponseEntity.noContent().build();
  }
}
