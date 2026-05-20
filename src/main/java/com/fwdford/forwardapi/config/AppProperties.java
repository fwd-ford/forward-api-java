// Strongly typed configuration bound from application.yml / environment variables.
// Configuracao tipada vinda do application.yml ou variaveis de ambiente.
package com.fwdford.forwardapi.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forward")
public record AppProperties(
    String env,
    List<String> allowedOrigins,
    RateLimit rateLimit,
    Jwt jwt,
    String internalApiKey,
    long maxBodyBytes) {
  public boolean isProduction() {
    return env != null && env.equalsIgnoreCase("production");
  }

  public record RateLimit(int max, Duration window) {}

  public record Jwt(String secret, String jwksUrl, String issuer) {}
}
