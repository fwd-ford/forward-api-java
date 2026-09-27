// 401 handler for protected endpoints reached without valid credentials. Chooses the
// problem code from the failure recorded by JwtAuthenticationFilter and adds the
// RFC 6750 WWW-Authenticate header.
// Responde 401 em problem+json com codigo especifico (ausente, expirado, invalido).
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.error.ProblemResponseWriter;
import com.fwdford.forwardapi.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemResponseWriter writer;

  public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest req, HttpServletResponse resp, AuthenticationException authException)
      throws IOException {
    Object failure = req.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTR);
    String code;
    String detail;
    String challenge;
    if (failure == JwtAuthenticationFilter.AuthFailure.TOKEN_EXPIRED) {
      code = "AUTH_TOKEN_EXPIRED";
      detail = "Token expirado. Faça login novamente.";
      challenge = "Bearer realm=\"forward-api\", error=\"invalid_token\"";
    } else if (failure == JwtAuthenticationFilter.AuthFailure.TOKEN_INVALID) {
      code = "AUTH_TOKEN_INVALID";
      detail = "Token inválido. Envie um JWT emitido por POST /api/v1/auth/login.";
      challenge = "Bearer realm=\"forward-api\", error=\"invalid_token\"";
    } else if (failure == JwtAuthenticationFilter.AuthFailure.API_KEY_INVALID) {
      code = "AUTH_API_KEY_INVALID";
      detail = "Chave de API inválida.";
      challenge = "Bearer realm=\"forward-api\"";
    } else {
      code = "AUTH_REQUIRED";
      detail = "Autenticação necessária. Envie o header Authorization: Bearer <token>.";
      challenge = "Bearer realm=\"forward-api\"";
    }
    resp.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
    writer.write(resp, Problems.build(HttpStatus.UNAUTHORIZED, code, detail, req));
  }
}
