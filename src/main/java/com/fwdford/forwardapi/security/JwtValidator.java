// Common contract for JWT validation. Implementations: HS256 shared secret or JWKS asymmetric.
// Contrato comum de validacao JWT: implementacoes em HS256 e JWKS.
package com.fwdford.forwardapi.security;

import java.util.Map;

public interface JwtValidator {
  Map<String, Object> validate(String rawToken) throws Exception;
}
