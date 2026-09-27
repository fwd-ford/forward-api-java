// Escapes the password placeholders used by db/bootstrap and db/seed before Flyway substitutes
// them into '...' SQL string literals: single quotes are doubled, so a password containing a
// quote can neither break the migration nor inject SQL. Values come from
// spring.flyway.placeholders (ADMIN_BOOTSTRAP_PASSWORD, DEMO_USERS_PASSWORD) and are never
// logged (org.flywaydb is pinned to INFO in application.yml).
// Escapa aspas nas senhas usadas como placeholders do Flyway (bootstrap e seed).
package com.fwdford.forwardapi.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FlywayPlaceholderConfig {

  /** Placeholders substituted inside '...' SQL literals. */
  static final List<String> SQL_LITERAL_PLACEHOLDERS =
      List.of("admin_bootstrap_password", "demo_users_password");

  @Bean
  public FlywayConfigurationCustomizer sqlLiteralPlaceholders() {
    return configuration -> {
      Map<String, String> placeholders = new HashMap<>(configuration.getPlaceholders());
      for (String key : SQL_LITERAL_PLACEHOLDERS) {
        placeholders.computeIfPresent(key, (k, value) -> sqlLiteral(value));
      }
      configuration.placeholders(placeholders);
    };
  }

  /** Content of a standard SQL string literal ('...') for the given value. */
  public static String sqlLiteral(String value) {
    return value == null ? "" : value.replace("'", "''");
  }
}
