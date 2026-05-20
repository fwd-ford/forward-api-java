// Sliding-window rate limiter keyed by client IP plus authenticated subject.
// Uses Bucket4j in-memory buckets; for multi-instance deployments, migrate to Redis.
// Rate limit por IP + subject; em multi-instancia, migrar para Redis.
package com.fwdford.forwardapi.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.web.AuthPrincipal;
import com.fwdford.forwardapi.web.WebAttrs;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

@Component
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {

  private final AppProperties props;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();

  public RateLimitFilter(AppProperties props) {
    this.props = props;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {

    String key = clientKey(req);
    Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
    if (!bucket.tryConsume(1)) {
      Duration window = props.rateLimit().window();
      resp.setStatus(429);
      resp.setHeader("Retry-After", String.valueOf(window.toSeconds()));
      resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      var body =
          Map.of(
              "type", "about:blank",
              "title", "Muitas requisicoes",
              "status", 429,
              "detail", "Tente novamente em instantes.",
              "code", "rate_limited");
      resp.getWriter().write(mapper.writeValueAsString(body));
      return;
    }
    chain.doFilter(req, resp);
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(props.rateLimit().max())
            .refillGreedy(props.rateLimit().max(), props.rateLimit().window())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private static String clientKey(HttpServletRequest req) {
    String ip = req.getRemoteAddr();
    Object p = req.getAttribute(WebAttrs.PRINCIPAL);
    if (p instanceof AuthPrincipal ap && ap.sub() != null && !ap.sub().isEmpty()) {
      return ip + "|" + ap.sub();
    }
    return ip;
  }
}
