// Principal stored in the Spring SecurityContext after a JWT (or the internal API key)
// is validated. Controllers receive it through @AuthenticationPrincipal.
// Principal autenticado colocado no SecurityContext apos validar o JWT ou a API key.
package com.fwdford.forwardapi.security;

public record AuthenticatedUser(String id, String email, String name, Role role, String dealerId) {

  /** Synthetic principal used for server-to-server calls authenticated by X-API-Key. */
  public static AuthenticatedUser service() {
    return new AuthenticatedUser(
        "service", null, "Integração interna (X-API-Key)", Role.SERVICE, null);
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  public boolean isService() {
    return role == Role.SERVICE;
  }

  /** True when this principal may read or write data owned by the given dealer. */
  public boolean canAccessDealer(String resourceDealerId) {
    if (!role.dealerScoped()) {
      return true;
    }
    return dealerId != null
        && resourceDealerId != null
        && dealerId.equalsIgnoreCase(resourceDealerId);
  }
}
