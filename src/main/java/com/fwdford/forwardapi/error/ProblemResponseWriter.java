// Writes a ProblemDetail straight to the servlet response. Used by code that runs
// outside Spring MVC (security entry point, access denied handler, rate limit filter).
// Escreve ProblemDetail direto na resposta, para filtros e handlers de seguranca.
package com.fwdford.forwardapi.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemResponseWriter {

  private final ObjectMapper mapper;

  public ProblemResponseWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse resp, ProblemDetail problem) throws IOException {
    if (resp.isCommitted()) {
      return;
    }
    resp.setStatus(problem.getStatus());
    // JSON is UTF-8 by definition (RFC 8259); bytes are written through the output stream.
    resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    resp.getOutputStream().write(mapper.writeValueAsBytes(problem));
    resp.flushBuffer();
  }
}
