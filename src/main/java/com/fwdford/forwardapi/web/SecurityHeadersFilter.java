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
    resp.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    resp.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    chain.doFilter(req, resp);
  }
}
