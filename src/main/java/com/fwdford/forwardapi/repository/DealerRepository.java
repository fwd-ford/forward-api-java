// Dealer lookups. Parameterized queries only.
// Consultas de concessionarias; somente queries parametrizadas.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.Dealer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DealerRepository {

  private static final String SELECT = "SELECT id::text AS id, code, name, active FROM dealers ";

  private static final RowMapper<Dealer> MAPPER =
      (rs, i) ->
          new Dealer(
              rs.getString("id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getBoolean("active"));

  private final NamedParameterJdbcTemplate jdbc;

  public DealerRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Dealer> findById(UUID id) {
    return jdbc
        .query(SELECT + "WHERE id = :id", new MapSqlParameterSource("id", id), MAPPER)
        .stream()
        .findFirst();
  }

  public Optional<Dealer> findActiveByCode(String code) {
    return jdbc
        .query(
            SELECT + "WHERE code = :code AND active = TRUE",
            new MapSqlParameterSource("code", code),
            MAPPER)
        .stream()
        .findFirst();
  }
}
