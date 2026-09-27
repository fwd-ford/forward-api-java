package com.fwdford.forwardapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.security.JwtAuthenticationFilter.AuthFailure;
import com.fwdford.forwardapi.security.JwtAuthenticationFilter.Resolution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Credential resolution of the authentication filter (no Spring context). */
class JwtAuthenticationFilterTest {

  private static final String SECRET = "fixture-unit-test-jwt-secret-0123456789abcdef";
  private static final String API_KEY = "fixture-internal-api-key";
  private static final AppProperties.Jwt CFG =
      new AppProperties.Jwt(SECRET, 60, "forward-api", "forward-app", 30);
  private static final AuthenticatedUser GESTOR =
      new AuthenticatedUser(
          "ad000000-0000-4000-8000-000000000002",
          "gestor@forward.dev",
          "Gustavo Mendes",
          Role.GESTOR,
          "d0000000-0000-4000-8000-000000000001");

  private final JwtService jwt = new JwtService(CFG, false, Clock.systemUTC());
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, API_KEY);

  @Test
  void no_credentials_is_anonymous_without_failure() {
    Resolution r = filter.resolveCredentials(null, null);
    assertNull(r.principal());
    assertNull(r.failure());
  }

  @Test
  void valid_bearer_token_yields_the_principal() {
    String token = jwt.issue(GESTOR).token();
    assertEquals(GESTOR, filter.resolveCredentials(null, "Bearer " + token).principal());
  }

  @Test
  void bearer_scheme_is_case_insensitive() {
    String token = jwt.issue(GESTOR).token();
    assertEquals(GESTOR, filter.resolveCredentials(null, "bearer " + token).principal());
  }

  @Test
  void other_schemes_and_empty_tokens_are_invalid() {
    assertEquals(
        AuthFailure.TOKEN_INVALID,
        filter.resolveCredentials(null, "Basic YWRtaW46YWRtaW4=").failure());
    assertEquals(AuthFailure.TOKEN_INVALID, filter.resolveCredentials(null, "Bearer   ").failure());
    assertEquals(
        AuthFailure.TOKEN_INVALID, filter.resolveCredentials(null, "Bearer x.y.z").failure());
  }

  @Test
  void expired_token_reports_expired() {
    JwtService past =
        new JwtService(
            CFG, false, Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));
    String expired = past.issue(GESTOR).token();
    Resolution r = filter.resolveCredentials(null, "Bearer " + expired);
    assertNull(r.principal());
    assertEquals(AuthFailure.TOKEN_EXPIRED, r.failure());
  }

  @Test
  void matching_api_key_yields_the_service_principal() {
    Resolution r = filter.resolveCredentials(API_KEY, null);
    assertEquals(Role.SERVICE, r.principal().role());
  }

  @Test
  void wrong_api_key_is_rejected_even_with_a_valid_token() {
    String token = jwt.issue(GESTOR).token();
    Resolution r = filter.resolveCredentials("wrong-key", "Bearer " + token);
    assertNull(r.principal());
    assertEquals(AuthFailure.API_KEY_INVALID, r.failure());
  }

  @Test
  void api_key_is_rejected_when_not_configured() {
    JwtAuthenticationFilter noKey = new JwtAuthenticationFilter(jwt, "");
    assertEquals(AuthFailure.API_KEY_INVALID, noKey.resolveCredentials("", null).failure());
    assertEquals(AuthFailure.API_KEY_INVALID, noKey.resolveCredentials(API_KEY, null).failure());
  }

  @Test
  void bearer_token_extraction() {
    assertEquals("abc", JwtAuthenticationFilter.bearerToken("Bearer abc"));
    assertEquals("abc", JwtAuthenticationFilter.bearerToken("BEARER   abc  "));
    assertNull(JwtAuthenticationFilter.bearerToken("Token abc"));
    assertNull(JwtAuthenticationFilter.bearerToken("Bearer"));
  }
}
