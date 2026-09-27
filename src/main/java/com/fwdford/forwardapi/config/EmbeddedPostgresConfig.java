// Embedded PostgreSQL 16 for the "demo" and "test" profiles. Starts a real Postgres
// process from native binaries bundled in the JAR (Zonky), so the API runs with zero
// external dependencies: no Docker, no hosted database. Flyway then applies the
// migrations (and the demo seed) on top of this instance. Data is ephemeral.
// Postgres embarcado para os perfis demo e test: sobe um Postgres real a partir de
// binarios empacotados no JAR, sem Docker. Os dados sao efemeros.
package com.fwdford.forwardapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"demo", "test"})
public class EmbeddedPostgresConfig {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);
  private static final String SUPERUSER = "postgres";
  private static final String DATABASE = "postgres";

  @Bean(destroyMethod = "close")
  public EmbeddedPostgres embeddedPostgres(
      @Value("${forward.embedded-db.port:0}") int port,
      @Value("${forward.embedded-db.startup-timeout:60s}") Duration startupTimeout)
      throws IOException {
    EmbeddedPostgres.Builder builder =
        EmbeddedPostgres.builder()
            // Small footprint: the demo runs next to the JVM on a 512 MB machine.
            .setServerConfig("shared_buffers", "32MB")
            .setServerConfig("max_connections", "40")
            .setServerConfig("work_mem", "4MB")
            .setServerConfig("maintenance_work_mem", "32MB")
            .setServerConfig("timezone", "UTC")
            // Ephemeral data: durability knobs off for faster boot and tests.
            .setServerConfig("fsync", "off")
            .setServerConfig("synchronous_commit", "off")
            .setServerConfig("full_page_writes", "off")
            .setLocaleConfig("locale", "C")
            .setPGStartupWait(startupTimeout);
    if (port > 0) {
      builder.setPort(port);
    }
    EmbeddedPostgres pg = builder.start();
    log.info("embedded_postgres_started port={} version=16", pg.getPort());
    return pg;
  }

  @Bean
  public DataSource dataSource(
      EmbeddedPostgres pg, @Value("${forward.embedded-db.pool-size:10}") int poolSize) {
    HikariConfig cfg = new HikariConfig();
    cfg.setPoolName("forward-embedded-pg");
    cfg.setJdbcUrl(pg.getJdbcUrl(SUPERUSER, DATABASE));
    cfg.setUsername(SUPERUSER);
    cfg.setMaximumPoolSize(poolSize);
    cfg.setMinimumIdle(1);
    cfg.setConnectionTestQuery("SELECT 1");
    return new HikariDataSource(cfg);
  }
}
