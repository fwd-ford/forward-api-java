// Picks the best JWT validator for the current configuration.
// Priority: JWKS URL > HS256 secret > none (development-only bypass).
// Escolhe o validador JWT; JWKS > HS256 > bypass (dev).
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtValidatorFactory {

  private static final Logger log = LoggerFactory.getLogger(JwtValidatorFactory.class);

  @Bean
  public JwtValidator jwtValidator(AppProperties props) throws Exception {
    var jwt = props.jwt();
    boolean hasJwks = jwt != null && jwt.jwksUrl() != null && !jwt.jwksUrl().isBlank();
    boolean hasSecret = jwt != null && jwt.secret() != null && !jwt.secret().isBlank();

    JwtValidator jwks = hasJwks ? new JwksJwtValidator(jwt.jwksUrl()) : null;
    JwtValidator hs256 = hasSecret ? new Hs256JwtValidator(jwt.secret()) : null;

    // Both configured: route per token header alg. Supabase projects emit HS256
    // by default and RS256/ES256 only after migrating to asymmetric keys, so
    // picking by alg keeps both flavors working from the same deploy.
    // Ambos: roteia pelo header alg do token (HS256 default Supabase; RS256 apos migrar).
    if (hasJwks && hasSecret) {
      log.info("jwt validator: ALG-AWARE (HS256 + JWKS asymmetric) url={}", jwt.jwksUrl());
      return new AlgAwareJwtValidator(hs256, jwks);
    }
    if (hasJwks) {
      log.info("jwt validator: JWKS (asymmetric) url={}", jwt.jwksUrl());
      return jwks;
    }
    if (hasSecret) {
      log.info("jwt validator: HS256 (shared secret)");
      return hs256;
    }
    log.warn(
        "jwt validator: DISABLED (no SUPABASE_JWT_SECRET or SUPABASE_JWKS_URL). Endpoints are"
            + " open.");
    return null;
  }
}
