// Authenticated principal exposed to controllers. Carries sub and role only.
// Principal autenticado disponivel nos controllers: apenas sub e role.
package com.fwdford.forwardapi.web;

public record AuthPrincipal(String sub, String role) {}
