// Hardened default security headers on every response.
// Mitigates XSS, clickjacking, and MIME sniffing.
// Cabecalhos de seguranca em toda resposta: mitiga XSS, clickjacking, MIME sniffing.
package com.fwdford.forwardapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    resp.setHeader("X-Content-Type-Options", "nosniff");
    resp.setHeader("X-Frame-Options", "DENY");
    resp.setHeader("Referrer-Policy", "no-referrer");
    resp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    resp.setHeader("Content-Security-Policy", cspFor(req.getRequestURI()));
    resp.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    chain.doFilter(req, resp);
  }

  // Swagger UI ships its own JS/CSS bundle and fetches /v3/api-docs over XHR.
  // The default lockdown CSP blocks all of that, so we relax it for those paths only.
  // Swagger UI carrega bundle proprio e faz XHR; relaxamos a CSP so para esses paths.
  private static String cspFor(String path) {
    if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
      return "default-src 'self'; "
          + "script-src 'self' 'unsafe-inline'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data:; "
          + "font-src 'self'; "
          + "connect-src 'self'; "
          + "frame-ancestors 'none'";
    }
    return "default-src 'none'; frame-ancestors 'none'";
  }
}
