// JWKS-based JWT validator. Downloads and caches public keys from a JWKS URL,
// verifies any of ES256/RS256 tokens Supabase issues with asymmetric signing.
// Validador JWT via JWKS: baixa e cacheia chaves publicas, verifica ES256/RS256.
package com.fwdford.forwardapi.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class JwksJwtValidator implements JwtValidator {

  private final ConfigurableJWTProcessor<SecurityContext> processor;

  public JwksJwtValidator(String jwksUrl) throws Exception {
    DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
    var keySource = JWKSourceBuilder.<SecurityContext>create(new URL(jwksUrl)).build();
    p.setJWSKeySelector(
        new JWSVerificationKeySelector<>(
            java.util.Set.of(JWSAlgorithm.RS256, JWSAlgorithm.ES256), keySource));
    this.processor = p;
  }

  @Override
  public Map<String, Object> validate(String rawToken) throws Exception {
    var claims = processor.process(rawToken, null);
    return new HashMap<>(claims.getClaims());
  }
}
