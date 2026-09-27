// User management. GET /api/v1/me is open to every authenticated profile; the
// /api/v1/users CRUD is ADMIN only (@PreAuthorize). Business rules:
//   - e-mail unique (case-insensitive) -> 409 USER_EMAIL_TAKEN
//   - ATENDENTE/GESTOR require an existing dealer; ADMIN never has one
//   - an admin cannot delete, deactivate or change the role of their own account (409)
// Every write is recorded in audit_log.
// Gestao de usuarios: /me para todos; CRUD de /api/v1/users somente ADMIN.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.AppUser;
import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.repository.DealerRepository;
import com.fwdford.forwardapi.repository.UserRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.Role;
import com.fwdford.forwardapi.security.UserStateCache;
import com.fwdford.forwardapi.web.dto.CreateUserRequest;
import com.fwdford.forwardapi.web.dto.MeResponse;
import com.fwdford.forwardapi.web.dto.UpdateUserRequest;
import com.fwdford.forwardapi.web.dto.UserResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final DealerRepository dealers;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final UserStateCache userStates;

  public UserService(
      UserRepository users,
      DealerRepository dealers,
      PasswordEncoder encoder,
      AuditService audit,
      UserStateCache userStates) {
    this.users = users;
    this.dealers = dealers;
    this.encoder = encoder;
    this.audit = audit;
    this.userStates = userStates;
  }

  /** Current principal with fresh data from the database. */
  public MeResponse me(AuthenticatedUser principal) {
    if (principal.isService()) {
      return new MeResponse(
          principal.id(),
          principal.name(),
          null,
          Role.SERVICE.name(),
          null,
          null,
          Role.SERVICE.permissions());
    }
    AppUser user =
        users
            .findById(UUID.fromString(principal.id()))
            .orElseThrow(
                () ->
                    new ApiException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "AUTH_TOKEN_INVALID",
                        "O usuário do token não existe mais. Faça login novamente."));
    if (!user.active()) {
      throw ApiException.userDisabled();
    }
    return new MeResponse(
        user.id(),
        user.fullName(),
        user.email(),
        user.role().name(),
        user.dealerId(),
        user.dealerName(),
        user.role().permissions());
  }

  @PreAuthorize("hasRole('ADMIN')")
  public PageResult<UserResponse> list(
      Role role, Boolean active, UUID dealerId, int limit, int offset) {
    var items =
        users.list(role, active, dealerId, limit, offset).stream().map(UserResponse::from).toList();
    return new PageResult<>(items, users.count(role, active, dealerId));
  }

  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse get(UUID id) {
    return UserResponse.from(load(id));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public UserResponse create(CreateUserRequest req, AuthenticatedUser actor) {
    String email = req.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw ApiException.conflict("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
    }
    Role role = Role.parseUserRole(req.role()).orElseThrow(() -> invalidRole());
    UUID dealerId =
        resolveDealer(role, req.dealerId() == null ? null : UUID.fromString(req.dealerId()));

    UUID id =
        users.insert(
            email, encoder.encode(req.password()), req.name().trim(), role, dealerId, true);
    audit.record(
        actor,
        "user.created",
        "app_user",
        id.toString(),
        details("email", email, "role", role.name(), "dealer_id", dealerId));
    return UserResponse.from(load(id));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public UserResponse update(UUID id, UpdateUserRequest req, AuthenticatedUser actor) {
    if (req.isEmpty()) {
      throw ApiException.badRequest(
          "EMPTY_PATCH", "Informe ao menos um campo: name, role, active, dealer_id ou password.");
    }
    AppUser current = load(id);
    boolean self = current.id().equalsIgnoreCase(actor.id());

    Role role =
        req.role() == null
            ? current.role()
            : Role.parseUserRole(req.role()).orElseThrow(() -> invalidRole());
    boolean active = req.active() == null ? current.active() : req.active();
    if (self && (role != current.role() || !active)) {
      throw ApiException.conflict(
          "USER_SELF_MODIFICATION",
          "Você não pode alterar o próprio perfil nem desativar a própria conta.");
    }
    UUID requestedDealer =
        req.dealerId() != null
            ? UUID.fromString(req.dealerId())
            : current.dealerId() == null ? null : UUID.fromString(current.dealerId());
    UUID dealerId = resolveDealer(role, requestedDealer);
    String name = req.name() == null ? current.fullName() : req.name().trim();
    if (name.isEmpty()) {
      throw ApiException.badRequest("VALIDATION_FAILED", "nome não pode ser vazio.");
    }
    String newHash = req.password() == null ? null : encoder.encode(req.password());

    // Any change to role, active, dealer or password revokes the tokens issued before, even
    // when the new value equals the old one (so PATCH {"active": true} forces a new login).
    boolean revokeTokens =
        req.role() != null || req.active() != null || req.dealerId() != null || newHash != null;
    users.update(id, name, role, dealerId, active, newHash, revokeTokens);
    userStates.invalidateAfterCommit(id.toString());
    Map<String, Object> changes = new LinkedHashMap<>();
    if (req.name() != null) {
      changes.put("name", name);
    }
    if (role != current.role()) {
      changes.put("role", Map.of("from", current.role().name(), "to", role.name()));
    }
    if (active != current.active()) {
      changes.put("active", Map.of("from", current.active(), "to", active));
    }
    if (!Objects.equals(current.dealerId(), dealerId == null ? null : dealerId.toString())) {
      changes.put("dealer_id", dealerId == null ? "null" : dealerId.toString());
    }
    if (newHash != null) {
      changes.put("password_reset", true);
    }
    if (revokeTokens) {
      changes.put("tokens_revoked", true);
    }
    audit.record(actor, "user.updated", "app_user", id.toString(), changes);
    return UserResponse.from(load(id));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public void delete(UUID id, AuthenticatedUser actor) {
    if (id.toString().equalsIgnoreCase(actor.id())) {
      throw ApiException.conflict("USER_SELF_DELETE", "Você não pode excluir a própria conta.");
    }
    AppUser current = load(id);
    users.deleteById(id);
    userStates.invalidateAfterCommit(id.toString());
    audit.record(
        actor,
        "user.deleted",
        "app_user",
        id.toString(),
        details("email", current.email(), "role", current.role().name(), "dealer_id", null));
  }

  private AppUser load(UUID id) {
    return users
        .findById(id)
        .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
  }

  /** ADMIN never has a dealer; ATENDENTE/GESTOR need an existing one. */
  private UUID resolveDealer(Role role, UUID requested) {
    if (!role.dealerScoped()) {
      return null;
    }
    if (requested == null) {
      throw ApiException.badRequest(
          "USER_DEALER_REQUIRED", "dealer_id é obrigatório para os perfis ATENDENTE e GESTOR.");
    }
    if (dealers.findById(requested).isEmpty()) {
      throw ApiException.unprocessable(
          "REFERENCED_DEALER_NOT_FOUND", "A concessionária informada (dealer_id) não existe.");
    }
    return requested;
  }

  private static ApiException invalidRole() {
    return ApiException.badRequest(
        "VALIDATION_FAILED", "perfil deve ser ATENDENTE, GESTOR ou ADMIN.");
  }

  private static Map<String, Object> details(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      Object value = kv[i + 1];
      m.put(String.valueOf(kv[i]), value == null ? null : String.valueOf(value));
    }
    return m;
  }
}
