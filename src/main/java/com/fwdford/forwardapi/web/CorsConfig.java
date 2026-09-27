// CORS configuration with explicit origin allowlist. Wildcard entries are dropped.
// Configuracao CORS com allowlist explicita; nunca aceita curinga.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  // Exposed as CorsConfigurationSource (not CorsFilter) so Spring Security
  // can mount its own CORS filter ahead of the auth chain. A plain CorsFilter
  // bean ran AFTER our AuthFilter, which let 401 responses ship without
  // Access-Control-Allow-Origin and broke every browser request.
  // Expoe Source pro Spring Security mover o CORS antes da auth.
  @Bean
  @Primary
  public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
    List<String> origins =
        props.allowedOrigins() == null
            ? List.of()
            : props.allowedOrigins().stream()
                .map(String::trim)
                .filter(o -> !o.isEmpty() && !o.contains("*"))
                .toList();
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(origins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "X-Request-Id", "X-API-Key"));
    // Headers the browser may read from responses (pagination, tracing, rate limit).
    cfg.setExposedHeaders(
        List.of(
            "Location",
            "X-Total-Count",
            "X-Request-Id",
            "Retry-After",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "Deprecation",
            "Link"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
