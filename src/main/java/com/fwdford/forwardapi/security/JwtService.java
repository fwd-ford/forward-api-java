// Issues and validates the API's own JWTs (HS256). Replaces the Supabase validators:
// the API is now its own identity provider.
//   iss = forward-api, aud = forward-app, sub = user id, plus email, name, role,
//   dealer_id (dealer-scoped roles only), token_version, iat, nbf, exp and a random jti.
// token_version is compared with app_users.token_version on every request (see
// JwtAuthenticationFilter), which is how tokens are revoked before they expire.
// Validation checks signature, algorithm, exp/nbf (30 s clock skew), iss, aud and the
// presence/format of every claim the authorization layer relies on.
// Emite e valida os JWTs da propria API (HS256): assinatura, exp, iss, aud e claims.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class JwtService {

  public static final String CLAIM_EMAIL = "email";
  public static final String CLAIM_NAME = "name";
  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_DEALER_ID = "dealer_id";
  public static final String CLAIM_TOKEN_VERSION = "token_version";
  public static final String ALGORITHM = "HS256";

  /** HS256 needs a key of at least 256 bits (RFC 7518, section 3.2). */
  static final int MIN_SECRET_BYTES = 32;

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern UUID_RE =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final SecretKey key;
  private final String issuer;
  private final String audience;
  private final Duration ttl;
  private final Clock clock;
  private final JwtParser parser;

  @Autowired
  public JwtService(AppProperties props, Clock clock) {
    this(props.jwt(), props.isProduction(), clock);
  }

  public JwtService(AppProperties.Jwt cfg, boolean production, Clock clock) {
    if (cfg.expirationMinutes() < 1 || cfg.expirationMinutes() > 24 * 60) {
      throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be between 1 and 1440.");
    }
    this.key = resolveKey(cfg.secret(), production);
    this.issuer = cfg.issuer();
    this.audience = cfg.audience();
    this.ttl = Duration.ofMinutes(cfg.expirationMinutes());
    this.clock = clock;
    this.parser =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .requireAudience(audience)
            .clockSkewSeconds(cfg.clockSkewSeconds())
            .clock(() -> Date.from(clock.instant()))
            .build();
  }

  /**
   * Resolves the HMAC key. Production (ENV=production) fails fast when the secret is missing; other
   * environments get an ephemeral random key and a WARN. A configured secret shorter than 32 bytes
   * is always rejected.
   */
  static SecretKey resolveKey(String secret, boolean production) {
    if (secret == null || secret.isBlank()) {
      if (production) {
        throw new IllegalStateException(
            "JWT_SECRET is required when ENV=production (at least 32 bytes / 256 bits).");
      }
      byte[] random = new byte[MIN_SECRET_BYTES];
      RANDOM.nextBytes(random);
      log.warn(
          "jwt_secret_missing: JWT_SECRET is not set, generated an ephemeral random signing key."
              + " Tokens become invalid after a restart. Set JWT_SECRET for stable tokens.");
      return new SecretKeySpec(random, HMAC_SHA256);
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must have at least 32 bytes (256 bits); got " + bytes.length + ".");
    }
    return new SecretKeySpec(bytes, HMAC_SHA256);
  }

  /** Signs a new access token for the given user. */
  public IssuedToken issue(AuthenticatedUser user) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(ttl);
    String jti = UUID.randomUUID().toString();
    JwtBuilder builder =
        Jwts.builder()
            .header()
            .type("JWT")
            .and()
            .id(jti)
            .issuer(issuer)
            .audience()
            .single(audience)
            .subject(user.id())
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(CLAIM_EMAIL, user.email())
            .claim(CLAIM_NAME, user.name())
            .claim(CLAIM_ROLE, user.role().name())
            .claim(CLAIM_TOKEN_VERSION, user.tokenVersion());
    if (user.dealerId() != null) {
      builder.claim(CLAIM_DEALER_ID, user.dealerId());
    }
    String token = builder.signWith(key, Jwts.SIG.HS256).compact();
    return new IssuedToken(token, jti, expiresAt, ttl.toSeconds());
  }

  /**
   * Validates a compact JWS and maps its claims to the authenticated principal.
   *
   * @throws InvalidTokenException with reason EXPIRED or INVALID
   */
  public AuthenticatedUser parse(String token) {
    if (token == null || token.isBlank()) {
      throw InvalidTokenException.invalid("empty token");
    }
    Jws<Claims> jws;
    try {
      jws = parser.parseSignedClaims(token);
    } catch (ExpiredJwtException ex) {
      throw InvalidTokenException.expired();
    } catch (JwtException | IllegalArgumentException ex) {
      throw InvalidTokenException.invalid(ex.getClass().getSimpleName());
    }
    if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
      throw InvalidTokenException.invalid("unexpected alg");
    }
    try {
      return toPrincipal(jws.getPayload());
    } catch (JwtException ex) {
      throw InvalidTokenException.invalid("claim type");
    }
  }

  private static AuthenticatedUser toPrincipal(Claims c) {
    String sub = c.getSubject();
    if (sub == null || !UUID_RE.matcher(sub).matches()) {
      throw InvalidTokenException.invalid("sub");
    }
    if (c.getId() == null || c.getId().isBlank()) {
      throw InvalidTokenException.invalid("jti");
    }
    if (c.getIssuedAt() == null || c.getExpiration() == null) {
      throw InvalidTokenException.invalid("iat/exp");
    }
    String email = c.get(CLAIM_EMAIL, String.class);
    String name = c.get(CLAIM_NAME, String.class);
    if (email == null || email.isBlank() || name == null || name.isBlank()) {
      throw InvalidTokenException.invalid("email/name");
    }
    Role role =
        Role.parseUserRole(c.get(CLAIM_ROLE, String.class))
            .orElseThrow(() -> InvalidTokenException.invalid("role"));
    String dealerId = c.get(CLAIM_DEALER_ID, String.class);
    if (dealerId != null && !UUID_RE.matcher(dealerId).matches()) {
      throw InvalidTokenException.invalid("dealer_id");
    }
    if (role.dealerScoped() && dealerId == null) {
      throw InvalidTokenException.invalid("dealer_id required");
    }
    Object version = c.get(CLAIM_TOKEN_VERSION);
    if (!(version instanceof Integer || version instanceof Long)
        || ((Number) version).longValue() < 0) {
      throw InvalidTokenException.invalid("token_version");
    }
    return new AuthenticatedUser(sub, email, name, role, dealerId, ((Number) version).longValue());
  }

  public long ttlSeconds() {
    return ttl.toSeconds();
  }

  /** Signed token plus the metadata returned by POST /api/v1/auth/login. */
  public record IssuedToken(String token, String jti, Instant expiresAt, long expiresInSeconds) {}
}
