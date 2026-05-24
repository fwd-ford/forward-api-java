// Authentication filter. Accepts either a Bearer JWT or an X-API-Key header
// (for server-to-server calls). Exposes an AuthPrincipal on the request.
// Filtro de autenticacao: aceita Bearer JWT ou X-API-Key (server-to-server).
package com.fwdford.forwardapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.web.AuthPrincipal;
import com.fwdford.forwardapi.web.WebAttrs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {

  private final JwtValidator validator;
  private final AppProperties props;
  private final ObjectMapper mapper = new ObjectMapper();

  public AuthFilter(Optional<JwtValidator> validator, AppProperties props) {
    this.validator = validator.orElse(null);
    this.props = props;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    // CORS preflight is unauthenticated by design; tokens never travel on OPTIONS.
    // Without this bypass the browser receives 401 before CORS headers are written.
    // Preflight CORS nao carrega token por design; sem isso, browser toma 401.
    if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
      return true;
    }
    String path = req.getRequestURI();
    if (path.equals("/health") || path.equals("/ready")) {
      return true;
    }
    if (path.startsWith("/actuator/")) {
      return true;
    }
    // WSDL discovery is public; only the SOAP POST itself requires auth.
    // A descoberta do WSDL eh publica; somente o POST SOAP exige auth.
    if (path.equals("/soap/vehicles.wsdl")) {
      return true;
    }
    // OpenAPI/Swagger UI is public so the contract can be browsed without auth.
    // OpenAPI/Swagger UI sao publicos para permitir leitura do contrato sem auth.
    if (path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.equals("/swagger-ui.html")) {
      return true;
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {

    // When no validator is configured we let requests through (dev mode).
    // Quando nao ha validador configurado, passa direto (modo dev).
    if (validator == null && (props.internalApiKey() == null || props.internalApiKey().isEmpty())) {
      chain.doFilter(req, resp);
      return;
    }

    String apiKey = req.getHeader("X-API-Key");
    if (props.internalApiKey() != null
        && !props.internalApiKey().isEmpty()
        && props.internalApiKey().equals(apiKey)) {
      req.setAttribute(WebAttrs.PRINCIPAL, new AuthPrincipal("internal", "admin"));
      chain.doFilter(req, resp);
      return;
    }

    String authz = req.getHeader("Authorization");
    if (authz == null || !authz.startsWith("Bearer ")) {
      writeUnauthorized(resp);
      return;
    }
    String raw = authz.substring("Bearer ".length()).trim();

    Map<String, Object> claims;
    try {
      if (validator == null) {
        writeUnauthorized(resp);
        return;
      }
      claims = validator.validate(raw);
    } catch (Exception ex) {
      writeUnauthorized(resp);
      return;
    }

    String sub = asString(claims.get("sub"));
    String role = asString(claims.get("role"));
    if (claims.get("app_metadata") instanceof Map<?, ?> amd) {
      Object r = amd.get("role");
      if (r instanceof String s && !s.isEmpty()) {
        role = s;
      }
    }
    req.setAttribute(WebAttrs.PRINCIPAL, new AuthPrincipal(sub, role));
    chain.doFilter(req, resp);
  }

  private static String asString(Object v) {
    return v instanceof String s ? s : null;
  }

  private void writeUnauthorized(HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var body =
        Map.of(
            "type", "about:blank",
            "title", "Nao autenticado",
            "status", 401,
            "detail", "Token ausente ou invalido.",
            "code", "unauthorized");
    resp.getWriter().write(mapper.writeValueAsString(body));
  }
}
