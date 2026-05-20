// HS256 JWT validator using a shared secret. Mirrors the Go implementation.
// Validador JWT HS256 com segredo compartilhado. Espelha a implementacao Go.
package com.fwdford.forwardapi.security;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Jwts;

public class Hs256JwtValidator implements JwtValidator {

  private final SecretKey key;

  public Hs256JwtValidator(String secret) {
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Override
  public Map<String, Object> validate(String rawToken) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(rawToken).getPayload();
  }
}
