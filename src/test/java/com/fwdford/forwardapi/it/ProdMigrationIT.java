package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fwdford.forwardapi.config.FlywayPlaceholderConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateOutput;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Production start-up against a database like the restored Supabase project: schema created outside
 * Flyway from forward-infra 001-013 plus the original forward-infra seed. Flyway is configured
 * exactly as application-prod.yml (baseline 13, db/migration + db/bootstrap).
 */
class ProdMigrationIT {

  private static final String DEMO_PASSWORD = "Forward@2026";
  // Contains a quote on purpose: placeholder values are SQL-escaped (FlywayPlaceholderConfig).
  private static final String ADMIN_PASSWORD = "Adm1n'Boot#2026";
  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
  private static EmbeddedPostgres pg;

  @BeforeAll
  static void start() throws IOException {
    pg =
        EmbeddedPostgres.builder()
            .setLocaleConfig("locale", "C")
            .setServerConfig("fsync", "off")
            .setServerConfig("max_connections", "20")
            .start();
    JdbcTemplate admin = new JdbcTemplate(pg.getPostgresDatabase());
    admin.execute("CREATE DATABASE supabase_like");
    admin.execute("CREATE DATABASE empty_db");
    admin.execute("CREATE DATABASE legacy_admin");
  }

  @AfterAll
  static void stop() throws IOException {
    pg.close();
  }

  private static final String ADMIN_EMAIL = "admin@forward.dev";

  /** Flyway as in application-prod.yml; an empty admin value means the env var is not set. */
  private static Flyway prodFlyway(DataSource ds, String adminPassword) {
    return Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration", "classpath:db/bootstrap")
        .baselineOnMigrate(true)
        .baselineVersion("13")
        .placeholders(
            Map.of(
                "admin_bootstrap_password",
                FlywayPlaceholderConfig.sqlLiteral(adminPassword),
                "demo_users_password",
                FlywayPlaceholderConfig.sqlLiteral(DEMO_PASSWORD)))
        .load();
  }

  @Test
  @DisplayName(
      "perfil prod no schema do forward-infra: baseline 13, V14+, bootstrap e dados antigos"
          + " intactos")
  void upgrades_existing_forward_infra_schema() throws Exception {
    DataSource ds = pg.getDatabase("postgres", "supabase_like");
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    // Like Supabase: forward-infra 001-011, the original seed, then 012-013, no Flyway history.
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .target("11")
        .load()
        .migrate();
    runScript(ds, "forward-infra/seed.sql");
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .target("13")
        .load()
        .migrate();
    jdbc.execute("DROP TABLE flyway_schema_history");
    assertThat(count(ds, "SELECT COUNT(*) FROM leads")).isEqualTo(3);

    MigrateResult result = prodFlyway(ds, "").migrate();

    assertThat(result.success).isTrue();
    List<String> applied = new ArrayList<>();
    for (MigrateOutput m : result.migrations) {
      applied.add(m.version.isEmpty() ? "R:" + m.description : "V" + m.version);
    }
    assertThat(applied).containsExactly("V14", "V15", "V16", "R:bootstrap demo data");
    assertThat(
            count(
                ds,
                "SELECT COUNT(*) FROM flyway_schema_history"
                    + " WHERE type = 'BASELINE' AND version = '13'"))
        .isEqualTo(1);

    // Original rows untouched (the forward-infra seed wrote names without accents).
    assertThat(
            string(
                ds,
                "SELECT full_name FROM customers"
                    + " WHERE id = '11111111-1111-1111-1111-111111111001'"))
        .isEqualTo("Joao da Silva");
    assertThat(count(ds, "SELECT COUNT(*) FROM dealers")).isEqualTo(10);
    assertThat(count(ds, "SELECT COUNT(*) FROM customers")).isEqualTo(16);

    // Without ADMIN_BOOTSTRAP_PASSWORD: no ADMIN at all, only the three demo users (bcrypt).
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users")).isEqualTo(3);
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users WHERE role = 'ADMIN'")).isZero();
    assertPasswords(ds, "role <> 'ADMIN'", DEMO_PASSWORD);
    assertThat(
            string(
                ds,
                "SELECT d.code FROM app_users u JOIN dealers d ON d.id = u.dealer_id"
                    + " WHERE u.email = 'atendente2@forward.dev'"))
        .isEqualTo("F0002");

    // Enough leads for the mobile app in the two demo dealers (bootstrap + original seed).
    assertThat(
            count(
                ds,
                "SELECT COUNT(*) FROM leads l JOIN dealers d ON d.id = l.dealer_id"
                    + " WHERE d.code IN ('F0001', 'F0002')"))
        .isGreaterThanOrEqualTo(15);
    assertThat(count(ds, "SELECT COUNT(*) FROM leads WHERE score_id IS NULL")).isZero();
    assertThat(
            count(
                ds,
                "SELECT COUNT(*) FROM (SELECT customer_id FROM churn_scores WHERE is_current"
                    + " GROUP BY customer_id HAVING COUNT(*) > 1) duplicated"))
        .isZero();

    // V16: token_version defaults to 0 for the bootstrap users.
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users WHERE token_version <> 0")).isZero();

    // V15 on existing data and V14 hardening for the Supabase Data API.
    assertThat(
            count(
                ds,
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname ="
                    + " 'uq_service_orders_natural_key'"))
        .isEqualTo(1);
    assertThat(
            count(
                ds, "SELECT COUNT(*) FROM pg_class WHERE relname = 'app_users' AND relrowsecurity"))
        .isEqualTo(1);

    // Idempotent: a second start migrates nothing and re-running the bootstrap changes nothing.
    long leads = count(ds, "SELECT COUNT(*) FROM leads");
    assertThat(prodFlyway(ds, "").migrate().migrationsExecuted).isZero();
    // Re-run with another demo password: existing passwords are never overwritten.
    runBootstrap(ds, "", "Outra#Senha2026");
    assertThat(count(ds, "SELECT COUNT(*) FROM leads")).isEqualTo(leads);
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users")).isEqualTo(3);
    assertThat(count(ds, "SELECT COUNT(*) FROM customers")).isEqualTo(16);
    assertPasswords(ds, "role <> 'ADMIN'", DEMO_PASSWORD);
  }

  @Test
  @DisplayName("perfil prod com ADMIN_BOOTSTRAP_PASSWORD em banco vazio: ADMIN criado e faz login")
  void creates_admin_only_when_the_bootstrap_password_is_set() {
    DataSource ds = pg.getDatabase("postgres", "empty_db");

    MigrateResult result = prodFlyway(ds, ADMIN_PASSWORD).migrate();

    assertThat(result.success).isTrue();
    assertThat(result.migrationsExecuted).isEqualTo(17);
    assertThat(count(ds, "SELECT COUNT(*) FROM leads")).isEqualTo(TestData.LEADS_TOTAL);
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users")).isEqualTo(4);
    assertThat(string(ds, "SELECT id::text FROM dealers WHERE code = 'F0001'"))
        .isEqualTo(TestData.DEALER_1);
    assertPasswords(ds, "role <> 'ADMIN'", DEMO_PASSWORD);
    // The login check of AuthService: active user whose BCrypt hash matches the password.
    assertThat(
            count(
                ds,
                "SELECT COUNT(*) FROM app_users WHERE email = 'admin@forward.dev'"
                    + " AND role = 'ADMIN' AND active AND dealer_id IS NULL"))
        .isEqualTo(1);
    assertPasswords(ds, "role = 'ADMIN'", ADMIN_PASSWORD);
    assertThat(passwordMatches(ds, ADMIN_EMAIL, DEMO_PASSWORD)).isFalse();
  }

  @Test
  @DisplayName(
      "ADMIN legado com a senha publicada: desativado sem variável, trocado com a variável e"
          + " nunca sobrescrito depois")
  void remediates_a_legacy_admin_with_the_published_password() throws Exception {
    DataSource ds = pg.getDatabase("postgres", "legacy_admin");
    // State left by the previous bootstrap: admin@forward.dev with the published password.
    prodFlyway(ds, DEMO_PASSWORD).migrate();
    assertThat(passwordMatches(ds, ADMIN_EMAIL, DEMO_PASSWORD)).isTrue();

    runBootstrap(ds, "", DEMO_PASSWORD);
    assertThat(string(ds, "SELECT active::text FROM app_users WHERE email = 'admin@forward.dev'"))
        .isEqualTo("false");
    assertThat(count(ds, "SELECT token_version FROM app_users WHERE email = 'admin@forward.dev'"))
        .isEqualTo(1);

    runBootstrap(ds, ADMIN_PASSWORD, DEMO_PASSWORD);
    assertThat(string(ds, "SELECT active::text FROM app_users WHERE email = 'admin@forward.dev'"))
        .isEqualTo("true");
    assertThat(passwordMatches(ds, ADMIN_EMAIL, ADMIN_PASSWORD)).isTrue();
    assertThat(passwordMatches(ds, ADMIN_EMAIL, DEMO_PASSWORD)).isFalse();

    // A password that is no longer the published default is never overwritten again.
    runBootstrap(ds, "Outra#Senha2026", DEMO_PASSWORD);
    assertThat(passwordMatches(ds, ADMIN_EMAIL, ADMIN_PASSWORD)).isTrue();
    assertThat(count(ds, "SELECT token_version FROM app_users WHERE email = 'admin@forward.dev'"))
        .isEqualTo(2);
    assertThat(count(ds, "SELECT COUNT(*) FROM app_users")).isEqualTo(4);
  }

  private static void assertPasswords(DataSource ds, String where, String password) {
    List<String> hashes =
        new JdbcTemplate(ds)
            .queryForList("SELECT password_hash FROM app_users WHERE " + where, String.class);
    assertThat(hashes).isNotEmpty();
    for (String hash : hashes) {
      assertThat(hash).startsWith("$2a$10$").doesNotContain(password);
      assertThat(ENCODER.matches(password, hash)).isTrue();
    }
  }

  private static boolean passwordMatches(DataSource ds, String email, String password) {
    String hash =
        new JdbcTemplate(ds)
            .queryForObject(
                "SELECT password_hash FROM app_users WHERE email = ?", String.class, email);
    return ENCODER.matches(password, hash);
  }

  /** Runs a classpath SQL script statement by statement (like psql would). */
  private static void runScript(DataSource ds, String path) throws SQLException {
    try (Connection c = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          c, new EncodedResource(new ClassPathResource(path), StandardCharsets.UTF_8));
    }
  }

  /** Re-runs the bootstrap script with the given placeholder values, as Flyway would. */
  private static void runBootstrap(DataSource ds, String adminPassword, String demoPassword)
      throws Exception {
    String sql =
        new ClassPathResource("db/bootstrap/R__bootstrap_demo_data.sql")
            .getContentAsString(StandardCharsets.UTF_8)
            .replace(
                "${admin_bootstrap_password}", FlywayPlaceholderConfig.sqlLiteral(adminPassword))
            .replace("${demo_users_password}", FlywayPlaceholderConfig.sqlLiteral(demoPassword));
    try (Connection c = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          c,
          new EncodedResource(
              new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
  }

  private static long count(DataSource ds, String sql) {
    List<Long> rows = new JdbcTemplate(ds).queryForList(sql, Long.class);
    return rows.isEmpty() ? 0 : rows.get(0);
  }

  private static String string(DataSource ds, String sql) {
    List<String> rows = new JdbcTemplate(ds).queryForList(sql, String.class);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
