// 403 handler used by Spring Security when an authenticated principal lacks the role
// required by a URL rule. Method-level denials (@PreAuthorize) are rendered with the
// same body by GlobalExceptionHandler.
// Responde 403 em problem+json quando o perfil nao tem permissao.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.error.ProblemResponseWriter;
import com.fwdford.forwardapi.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemResponseWriter writer;

  public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest req, HttpServletResponse resp, AccessDeniedException accessDenied)
      throws IOException {
    ApiException ex = ApiException.forbidden();
    writer.write(resp, Problems.build(ex.status(), ex.code(), ex.title(), ex.detail(), req));
  }
}
