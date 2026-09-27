// Authentication filter mounted inside the Spring Security chain (not a servlet bean).
//
// Credentials are resolved in one place (resolveCredentials) and only a cryptographically
// verified, still current result ever reaches the SecurityContext:
//   1) X-API-Key (server-to-server, e.g. n8n): SHA-256 digests compared in constant time
//      against INTERNAL_API_KEY; success yields the SERVICE principal (ROLE_SERVICE).
//   2) Authorization: Bearer <jwt>: signature, algorithm, exp, iss, aud and claims checked
//      by JwtService, then the claims are compared with the user's current state in
//      app_users (exists, active, role, dealer_id, token_version; UserStateCache, short TTL).
//      A token of a deleted, deactivated, demoted or moved user is rejected as revoked even
//      before it expires. Success yields the user's principal (ROLE_<ROLE>).
// Which header is present only selects the verifier; it never grants access by itself.
// Invalid credentials never short-circuit here: the failure reason is stored as a request
// attribute and the request continues unauthenticated, so public endpoints still work and
// protected ones get a precise 401 from ProblemAuthenticationEntryPoint.
// Filtro de autenticacao: verifica a credencial (API key ou JWT), confere o estado atual do
// usuario (revogacao) e so entao preenche o SecurityContext.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.util.LogSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final String BEARER_PREFIX = "bearer ";
  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  /** Why the presented credentials were rejected. */
  public enum AuthFailure {
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    TOKEN_REVOKED,
    API_KEY_INVALID
  }

  /** Outcome of credential verification: a verified principal, a failure, or nothing sent. */
  record Resolution(AuthenticatedUser principal, AuthFailure failure) {
    static final Resolution ANONYMOUS = new Resolution(null, null);

    static Resolution verified(AuthenticatedUser principal) {
      return new Resolution(principal, null);
    }

    static Resolution rejected(AuthFailure failure) {
      return new Resolution(null, failure);
    }
  }

  private final JwtService jwtService;
  private final byte[] internalApiKeyDigest;
  private final UserStateCache userStates;

  public JwtAuthenticationFilter(
      JwtService jwtService, String internalApiKey, UserStateCache userStates) {
    this.jwtService = jwtService;
    this.internalApiKeyDigest =
        internalApiKey == null || internalApiKey.isBlank() ? null : sha256(internalApiKey);
    this.userStates = userStates;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    Resolution resolution =
        resolveCredentials(req.getHeader(API_KEY_HEADER), req.getHeader(HttpHeaders.AUTHORIZATION));
    req.setAttribute(AUTH_FAILURE_ATTR, resolution.failure());
    storePrincipal(resolution.principal());
    chain.doFilter(req, resp);
  }

  /** Verifies whichever credential was sent. Package-private for unit tests. */
  Resolution resolveCredentials(String apiKey, String authorization) {
    if (apiKey != null) {
      return apiKeyMatches(apiKey)
          ? Resolution.verified(AuthenticatedUser.service())
          : Resolution.rejected(AuthFailure.API_KEY_INVALID);
    }
    if (authorization == null) {
      return Resolution.ANONYMOUS;
    }
    String token = bearerToken(authorization);
    if (token == null) {
      return Resolution.rejected(AuthFailure.TOKEN_INVALID);
    }
    AuthenticatedUser claims;
    try {
      claims = jwtService.parse(token);
    } catch (InvalidTokenException ex) {
      return Resolution.rejected(
          ex.reason() == InvalidTokenException.Reason.EXPIRED
              ? AuthFailure.TOKEN_EXPIRED
              : AuthFailure.TOKEN_INVALID);
    }
    String revocation = revocationReason(claims, userStates.get(claims.id()));
    if (revocation != null) {
      log.info(
          "token_revoked user_id={} reason={}", LogSanitizer.sanitize(claims.id()), revocation);
      return Resolution.rejected(AuthFailure.TOKEN_REVOKED);
    }
    return Resolution.verified(claims);
  }

  /** Why a signature-valid token no longer matches the user, or null when it still does. */
  static String revocationReason(AuthenticatedUser claims, Optional<UserSecurityState> current) {
    if (current.isEmpty()) {
      return "user_not_found";
    }
    UserSecurityState state = current.get();
    if (!state.active()) {
      return "user_disabled";
    }
    if (state.role() != claims.role()) {
      return "role_changed";
    }
    if (!sameDealer(state.dealerId(), claims.dealerId())) {
      return "dealer_changed";
    }
    if (state.tokenVersion() != claims.tokenVersion()) {
      return "token_version_changed";
    }
    return null;
  }

  private static boolean sameDealer(String a, String b) {
    return a == null ? b == null : a.equalsIgnoreCase(b);
  }

  /** Token part of an "Authorization: Bearer <token>" header, or null for other schemes. */
  static String bearerToken(String authorization) {
    if (!authorization.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = authorization.substring(BEARER_PREFIX.length()).trim();
    return token.isEmpty() ? null : token;
  }

  private boolean apiKeyMatches(String presented) {
    // Hash both sides first so the comparison is constant-time and length-independent.
    return internalApiKeyDigest != null
        && MessageDigest.isEqual(internalApiKeyDigest, sha256(presented));
  }

  /** Stores a verified principal in a fresh SecurityContext (null means anonymous). */
  private static void storePrincipal(AuthenticatedUser principal) {
    if (principal == null) {
      return;
    }
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority())));
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(token);
    SecurityContextHolder.setContext(ctx);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
