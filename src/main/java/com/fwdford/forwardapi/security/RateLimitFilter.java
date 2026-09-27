// Rate limiter keyed by client IP. Runs BEFORE the Spring Security chain so floods of
// unauthenticated or invalid-token requests are throttled too. Two Bucket4j buckets:
//   - global: RATE_LIMIT_MAX requests per RATE_LIMIT_WINDOW, applied to every request;
//   - login:  LOGIN_RATE_LIMIT_MAX attempts per LOGIN_RATE_LIMIT_WINDOW on
//             POST /api/v1/auth/login (brute-force protection), on top of the global one.
// The login endpoint is recognized by a Spring Security request matcher (normalized path
// and method), never by comparing raw request strings. The client IP is the servlet
// container's remote address (behind a proxy, set from X-Forwarded-For only for trusted
// proxies by server.forward-headers-strategy=native).
// In-memory buckets: for multi-instance deployments migrate to a shared store (Redis).
// Rate limit por IP antes da autenticacao, com balde mais restrito para o login.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.error.ProblemResponseWriter;
import com.fwdford.forwardapi.error.Problems;
import com.fwdford.forwardapi.util.LogSanitizer;
import com.fwdford.forwardapi.web.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

  public static final String LOGIN_PATH = "/api/v1/auth/login";

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  /** Upper bound on tracked clients; the maps are reset when exceeded (memory guard). */
  private static final int MAX_TRACKED_CLIENTS = 50_000;

  private static final RequestMatcher LOGIN_ENDPOINT =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_PATH);

  private final AppProperties.RateLimit limits;
  private final ClientIpResolver ipResolver;
  private final ProblemResponseWriter writer;
  private final Map<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

  public RateLimitFilter(
      AppProperties props, ClientIpResolver ipResolver, ProblemResponseWriter writer) {
    this.limits = props.rateLimit();
    this.ipResolver = ipResolver;
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    String ip = ipResolver.resolve(req);
    boolean allowed = tryConsume(globalBuckets, ip, limits.max(), limits.window(), resp);
    if (allowed && LOGIN_ENDPOINT.matches(req)) {
      allowed = tryConsume(loginBuckets, ip, limits.loginMax(), limits.loginWindow(), resp);
      if (!allowed) {
        log.warn("login_rate_limited ip={}", LogSanitizer.sanitize(ip));
      }
    }
    if (!allowed) {
      ApiException ex = ApiException.tooManyRequests();
      writer.write(resp, Problems.build(ex.status(), ex.code(), ex.title(), ex.detail(), req));
      return;
    }
    chain.doFilter(req, resp);
  }

  private boolean tryConsume(
      Map<String, Bucket> buckets,
      String key,
      int capacity,
      Duration window,
      HttpServletResponse resp) {
    if (buckets.size() > MAX_TRACKED_CLIENTS) {
      buckets.clear();
    }
    Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, window));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    resp.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
    resp.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    if (!probe.isConsumed()) {
      long retryAfter =
          Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
      resp.setHeader("Retry-After", String.valueOf(retryAfter));
    }
    return probe.isConsumed();
  }

  private static Bucket newBucket(int capacity, Duration window) {
    Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build();
    return Bucket.builder().addLimit(limit).build();
  }
}
