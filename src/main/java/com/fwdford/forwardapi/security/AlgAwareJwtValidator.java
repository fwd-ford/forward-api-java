// Picks HS256 or JWKS asymmetric validation per token by reading the JWT
// header's `alg` claim. Lets the backend accept Supabase projects on either
// signing scheme without an env switch.
// Roteia HS256/JWKS pelo header `alg` do token; aceita ambos esquemas Supabase.
package com.fwdford.forwardapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class AlgAwareJwtValidator implements JwtValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JwtValidator hs256;
  private final JwtValidator jwks;

  public AlgAwareJwtValidator(JwtValidator hs256, JwtValidator jwks) {
    this.hs256 = hs256;
    this.jwks = jwks;
  }

  @Override
  public Map<String, Object> validate(String rawToken) throws Exception {
    String alg = headerAlg(rawToken);
    if ("HS256".equals(alg) || "HS384".equals(alg) || "HS512".equals(alg)) {
      if (hs256 == null) {
        throw new IllegalStateException("token is HS* but SUPABASE_JWT_SECRET is not configured");
      }
      return hs256.validate(rawToken);
    }
    if (jwks == null) {
      throw new IllegalStateException(
          "token is asymmetric but SUPABASE_JWKS_URL is not configured");
    }
    return jwks.validate(rawToken);
  }

  private static String headerAlg(String rawToken) throws Exception {
    int dot = rawToken.indexOf('.');
    if (dot <= 0) throw new IllegalArgumentException("malformed jwt: missing header");
    byte[] headerBytes = Base64.getUrlDecoder().decode(rawToken.substring(0, dot));
    Map<?, ?> header = MAPPER.readValue(new String(headerBytes, StandardCharsets.UTF_8), Map.class);
    Object alg = header.get("alg");
    return alg instanceof String s ? s : null;
  }
}
