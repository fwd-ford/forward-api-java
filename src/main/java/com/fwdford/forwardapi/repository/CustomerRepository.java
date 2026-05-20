// Customer repository. Parameterized queries only; never concatenates user input.
// Repositorio de customers: queries parametrizadas, sem concatenacao de input.
package com.fwdford.forwardapi.repository;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fwdford.forwardapi.model.Customer;

@Repository
public class CustomerRepository {

  private static final String SELECT_BY_ID =
      """
      SELECT id::text, full_name, email, phone, city, state, opt_in_whatsapp, created_at
      FROM customers
      WHERE id = :id
      LIMIT 1
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public CustomerRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Customer> findById(String id) {
    var params = new MapSqlParameterSource("id", java.util.UUID.fromString(id));
    return jdbc
        .query(
            SELECT_BY_ID,
            params,
            (rs, idx) ->
                new Customer(
                    rs.getString("id"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getBoolean("opt_in_whatsapp"),
                    rs.getObject("created_at", java.time.OffsetDateTime.class)))
        .stream()
        .findFirst();
  }
}
