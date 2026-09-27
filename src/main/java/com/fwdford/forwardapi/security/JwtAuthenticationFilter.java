// Authentication filter mounted inside the Spring Security chain (not a servlet bean).
//   1) X-API-Key (server-to-server, e.g. n8n): compared in constant time against
//      INTERNAL_API_KEY; success grants ROLE_SERVICE.
//   2) Authorization: Bearer <jwt>: validated by JwtService; success stores an
//      AuthenticatedUser with ROLE_<ROLE> in the SecurityContext.
// Invalid credentials never short-circuit here: the failure reason is stored as a
// request attribute and the request continues unauthenticated, so public endpoints still
// work and protected ones get a precise 401 from ProblemAuthenticationEntryPoint.
// Filtro de autenticacao: API key (tempo constante) ou Bearer JWT -> SecurityContext.
package com.fwdford.forwardapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Request attribute holding the {@link AuthFailure} when credentials were rejected. */
  public static final String AUTH_FAILURE_ATTR = "forward.authFailure";

  public static final String API_KEY_HEADER = "X-API-Key";
  private static final String BEARER_PREFIX = "Bearer ";

  /** Why the presented credentials were rejected. */
  public enum AuthFailure {
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    API_KEY_INVALID
  }

  private final JwtService jwtService;
  private final byte[] internalApiKeyDigest;

  public JwtAuthenticationFilter(JwtService jwtService, String internalApiKey) {
    this.jwtService = jwtService;
    this.internalApiKeyDigest =
        internalApiKey == null || internalApiKey.isBlank() ? null : sha256(internalApiKey);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    String apiKey = req.getHeader(API_KEY_HEADER);
    String authorization = req.getHeader(HttpHeaders.AUTHORIZATION);

    if (apiKey != null) {
      if (apiKeyMatches(apiKey)) {
        authenticate(req, AuthenticatedUser.service());
      } else {
        req.setAttribute(AUTH_FAILURE_ATTR, AuthFailure.API_KEY_INVALID);
      }
    } else if (authorization != null) {
      if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
          authenticate(req, jwtService.parse(token));
        } catch (InvalidTokenException ex) {
          req.setAttribute(
              AUTH_FAILURE_ATTR,
              ex.reason() == InvalidTokenException.Reason.EXPIRED
                  ? AuthFailure.TOKEN_EXPIRED
                  : AuthFailure.TOKEN_INVALID);
        }
      } else {
        req.setAttribute(AUTH_FAILURE_ATTR, AuthFailure.TOKEN_INVALID);
      }
    }
    chain.doFilter(req, resp);
  }

  private boolean apiKeyMatches(String presented) {
    if (internalApiKeyDigest == null) {
      return false;
    }
    // Hash both sides first so the comparison is constant-time and length-independent.
    return MessageDigest.isEqual(internalApiKeyDigest, sha256(presented));
  }

  private static void authenticate(HttpServletRequest req, AuthenticatedUser user) {
    UsernamePasswordAuthenticationToken auth =
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority(user.role().authority())));
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    req.setAttribute(AUTH_FAILURE_ATTR, null);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
