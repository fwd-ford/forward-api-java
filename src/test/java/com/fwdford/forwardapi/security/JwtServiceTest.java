package com.fwdford.forwardapi.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fwdford.forwardapi.config.AppProperties;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** JWT issuing and validation rules (no Spring context). */
class JwtServiceTest {

  private static final String SECRET = "fixture-unit-test-jwt-secret-0123456789abcdef";
  private static final Instant T0 = Instant.parse("2026-09-27T12:00:00Z");
  private static final String USER_ID = "ad000000-0000-4000-8000-000000000003";
  private static final String DEALER_1 = "d0000000-0000-4000-8000-000000000001";
  private static final AuthenticatedUser ATENDENTE =
      new AuthenticatedUser(
          USER_ID, "atendente@forward.dev", "Beatriz Santos", Role.ATENDENTE, DEALER_1);

  private static AppProperties.Jwt cfg(String secret) {
    return new AppProperties.Jwt(secret, 60, "forward-api", "forward-app", 30);
  }

  private static JwtService serviceAt(Instant now) {
    return new JwtService(cfg(SECRET), false, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static SecretKey key(String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  private static String customToken(String secret, String issuer, String audience, String role) {
    var b =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .audience()
            .single(audience)
            .subject(USER_ID)
            .issuedAt(Date.from(T0))
            .expiration(Date.from(T0.plus(Duration.ofMinutes(60))))
            .claim("email", "atendente@forward.dev")
            .claim("name", "Beatriz Santos")
            .claim("dealer_id", DEALER_1);
    if (role != null) {
      b.claim("role", role);
    }
    return b.signWith(key(secret), Jwts.SIG.HS256).compact();
  }

  @Test
  void issued_token_round_trips_to_the_same_principal() {
    JwtService jwt = serviceAt(T0);
    JwtService.IssuedToken issued = jwt.issue(ATENDENTE);

    AuthenticatedUser parsed = jwt.parse(issued.token());

    assertEquals(ATENDENTE, parsed);
    assertEquals(3600, issued.expiresInSeconds());
    assertEquals(T0.plus(Duration.ofMinutes(60)), issued.expiresAt());
  }

  @Test
  void token_carries_standard_and_custom_claims() throws Exception {
    JwtService.IssuedToken issued = serviceAt(T0).issue(ATENDENTE);
    String[] parts = issued.token().split("\\.");
    JsonNode header = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[0]));
    JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));

    assertEquals("HS256", header.get("alg").asText());
    assertEquals("forward-api", claims.get("iss").asText());
    assertEquals("forward-app", claims.get("aud").asText());
    assertEquals(USER_ID, claims.get("sub").asText());
    assertEquals("atendente@forward.dev", claims.get("email").asText());
    assertEquals("Beatriz Santos", claims.get("name").asText());
    assertEquals("ATENDENTE", claims.get("role").asText());
    assertEquals(DEALER_1, claims.get("dealer_id").asText());
    assertEquals(issued.jti(), claims.get("jti").asText());
    assertEquals(3600, claims.get("exp").asLong() - claims.get("iat").asLong());
  }

  @Test
  void admin_token_has_no_dealer_claim() throws Exception {
    AuthenticatedUser admin =
        new AuthenticatedUser(
            "ad000000-0000-4000-8000-000000000001", "admin@forward.dev", "Ana", Role.ADMIN, null);
    JwtService jwt = serviceAt(T0);
    String token = jwt.issue(admin).token();
    JsonNode claims =
        new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    assertNull(claims.get("dealer_id"));
    assertEquals(admin, jwt.parse(token));
  }

  @Test
  void each_token_gets_a_unique_jti() {
    JwtService jwt = serviceAt(T0);
    assertNotEquals(jwt.issue(ATENDENTE).jti(), jwt.issue(ATENDENTE).jti());
  }

  @Test
  void expired_token_is_rejected_with_expired_reason() {
    String token = serviceAt(T0).issue(ATENDENTE).token();
    JwtService later = serviceAt(T0.plus(Duration.ofMinutes(61)));

    InvalidTokenException ex = assertThrows(InvalidTokenException.class, () -> later.parse(token));
    assertEquals(InvalidTokenException.Reason.EXPIRED, ex.reason());
  }

  @Test
  void token_within_clock_skew_is_accepted() {
    String token = serviceAt(T0).issue(ATENDENTE).token();
    JwtService slightlyLater = serviceAt(T0.plus(Duration.ofMinutes(60)).plusSeconds(20));
    assertDoesNotThrow(() -> slightlyLater.parse(token));
  }

  @Test
  void tampered_payload_is_rejected() {
    String token = serviceAt(T0).issue(ATENDENTE).token();
    String[] parts = token.split("\\.");
    String forgedClaims =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                    .replace("ATENDENTE", "ADMIN")
                    .getBytes(StandardCharsets.UTF_8));
    String forged = parts[0] + "." + forgedClaims + "." + parts[2];

    InvalidTokenException ex =
        assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(forged));
    assertEquals(InvalidTokenException.Reason.INVALID, ex.reason());
  }

  @Test
  void token_signed_with_another_secret_is_rejected() {
    String foreign =
        customToken(
            "another-secret-that-is-long-enough-0123456789",
            "forward-api",
            "forward-app",
            "ATENDENTE");
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(foreign));
  }

  @Test
  void wrong_issuer_is_rejected() {
    String token = customToken(SECRET, "https://evil.example", "forward-app", "ATENDENTE");
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(token));
  }

  @Test
  void wrong_audience_is_rejected() {
    String token = customToken(SECRET, "forward-api", "another-app", "ATENDENTE");
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(token));
  }

  @Test
  void correct_custom_token_is_accepted() {
    String token = customToken(SECRET, "forward-api", "forward-app", "GESTOR");
    AuthenticatedUser user = serviceAt(T0).parse(token);
    assertEquals(Role.GESTOR, user.role());
    assertEquals(DEALER_1, user.dealerId());
  }

  @Test
  void missing_or_unknown_role_is_rejected() {
    assertThrows(
        InvalidTokenException.class,
        () -> serviceAt(T0).parse(customToken(SECRET, "forward-api", "forward-app", null)));
    assertThrows(
        InvalidTokenException.class,
        () -> serviceAt(T0).parse(customToken(SECRET, "forward-api", "forward-app", "SERVICE")));
    assertThrows(
        InvalidTokenException.class,
        () -> serviceAt(T0).parse(customToken(SECRET, "forward-api", "forward-app", "root")));
  }

  @Test
  void unsigned_token_is_rejected() {
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
    String body =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"sub\":\"" + USER_ID + "\",\"role\":\"ADMIN\",\"iss\":\"forward-api\"}")
                    .getBytes(StandardCharsets.UTF_8));
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(header + "." + body + "."));
  }

  @Test
  void garbage_and_empty_tokens_are_rejected() {
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse("not-a-jwt"));
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(""));
    assertThrows(InvalidTokenException.class, () -> serviceAt(T0).parse(null));
  }

  @Test
  void production_without_secret_fails_fast() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new JwtService(cfg(""), true, Clock.systemUTC()));
    assertTrue(ex.getMessage().contains("JWT_SECRET"));
  }

  @Test
  void short_secret_fails_fast_in_any_environment() {
    assertThrows(
        IllegalStateException.class,
        () -> new JwtService(cfg("too-short"), false, Clock.systemUTC()));
    assertThrows(
        IllegalStateException.class,
        () -> new JwtService(cfg("too-short"), true, Clock.systemUTC()));
  }

  @Test
  void non_production_without_secret_uses_an_ephemeral_key() {
    JwtService a = new JwtService(cfg(null), false, Clock.fixed(T0, ZoneOffset.UTC));
    JwtService b = new JwtService(cfg(null), false, Clock.fixed(T0, ZoneOffset.UTC));
    String token = a.issue(ATENDENTE).token();

    assertNotNull(a.parse(token));
    assertThrows(InvalidTokenException.class, () -> b.parse(token));
  }

  @Test
  void invalid_expiration_configuration_fails_fast() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new JwtService(
                new AppProperties.Jwt(SECRET, 0, "forward-api", "forward-app", 30),
                false,
                Clock.systemUTC()));
  }
}
