// Strongly typed configuration bound from application.yml / environment variables.
// Configuracao tipada vinda do application.yml ou variaveis de ambiente.
package com.fwdford.forwardapi.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "forward")
public record AppProperties(
    @DefaultValue("development") String env,
    @DefaultValue("dev") String version,
    List<String> allowedOrigins,
    @DefaultValue RateLimit rateLimit,
    @DefaultValue Jwt jwt,
    String internalApiKey,
    @DefaultValue("1048576") long maxBodyBytes) {

  public boolean isProduction() {
    return env != null && env.equalsIgnoreCase("production");
  }

  /** Bucket4j limits: global per client IP, plus a stricter bucket for the login endpoint. */
  public record RateLimit(
      @DefaultValue("60") int max,
      @DefaultValue("1m") Duration window,
      @DefaultValue("5") int loginMax,
      @DefaultValue("1m") Duration loginWindow) {}

  /** Self-issued HS256 JWT settings. */
  public record Jwt(
      String secret,
      @DefaultValue("60") int expirationMinutes,
      @DefaultValue("forward-api") String issuer,
      @DefaultValue("forward-app") String audience,
      @DefaultValue("30") long clockSkewSeconds) {}
}
