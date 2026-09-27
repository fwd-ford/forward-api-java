// Access profiles. ATENDENTE and GESTOR are scoped to a single dealer; ADMIN sees
// every dealer. SERVICE is reserved for server-to-server calls (X-API-Key) and is
// never stored in app_users nor carried by a JWT.
// Perfis de acesso: ATENDENTE e GESTOR limitados a uma concessionaria; ADMIN ve tudo.
package com.fwdford.forwardapi.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Role {
  ATENDENTE(
      true,
      List.of(
          "leads:read",
          "leads:update",
          "customers:read",
          "vehicles:read",
          "scores:read",
          "service-events:read")),
  GESTOR(
      true,
      List.of(
          "leads:read",
          "leads:update",
          "customers:read",
          "vehicles:read",
          "scores:read",
          "service-events:read",
          "service-events:write")),
  ADMIN(
      false,
      List.of(
          "leads:read",
          "leads:update",
          "customers:read",
          "vehicles:read",
          "scores:read",
          "service-events:read",
          "service-events:write",
          "service-events:delete",
          "users:manage",
          "dealers:all")),
  SERVICE(
      false,
      List.of(
          "leads:read",
          "leads:update",
          "customers:read",
          "vehicles:read",
          "scores:read",
          "service-events:read",
          "service-events:write",
          "dealers:all"));

  private final boolean dealerScoped;
  private final List<String> permissions;

  Role(boolean dealerScoped, List<String> permissions) {
    this.dealerScoped = dealerScoped;
    this.permissions = permissions;
  }

  /** True when the role only sees data from its own dealer. */
  public boolean dealerScoped() {
    return dealerScoped;
  }

  /** Coarse permission names, exposed by GET /api/v1/me so clients can adapt the UI. */
  public List<String> permissions() {
    return permissions;
  }

  /** Spring Security authority, e.g. ROLE_GESTOR. */
  public String authority() {
    return "ROLE_" + name();
  }

  /** Parses a role that can be assigned to a human user (never SERVICE). */
  public static Optional<Role> parseUserRole(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    for (Role r : values()) {
      if (r != SERVICE && r.name().equals(normalized)) {
        return Optional.of(r);
      }
    }
    return Optional.empty();
  }
}
