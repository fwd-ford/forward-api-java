// Attaches a correlation ID to every request. Accepts an incoming X-Request-Id header
// (validated to avoid log injection) or generates a fresh UUID. The id is echoed in the
// response header, pushed into the SLF4J MDC and embedded in every problem response.
// First filter of the chain: RequestId -> SecurityHeaders -> RateLimit -> Spring Security.
// Anexa um ID de correlacao a cada request; primeiro filtro da cadeia.
package com.fwdford.forwardapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    String rid = req.getHeader(HEADER);
    if (rid == null || !SAFE_ID.matcher(rid).matches()) {
      rid = UUID.randomUUID().toString();
    }
    req.setAttribute(WebAttrs.REQUEST_ID, rid);
    resp.setHeader(HEADER, rid);
    MDC.put("request_id", rid);
    try {
      chain.doFilter(req, resp);
    } finally {
      MDC.remove("request_id");
    }
  }
}
