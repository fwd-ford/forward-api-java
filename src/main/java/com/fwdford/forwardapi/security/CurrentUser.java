// Access to the authenticated principal outside Spring MVC argument resolution
// (e.g. the SOAP endpoint, which runs in Spring WS).
// Acesso ao principal autenticado fora dos controllers REST (ex.: endpoint SOAP).
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

  private CurrentUser() {}

  public static AuthenticatedUser require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw ApiException.unauthorized();
  }
}
