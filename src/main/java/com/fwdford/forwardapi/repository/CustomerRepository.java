// Customer repository. Parameterized queries only; never concatenates user input.
// Repositorio de customers: queries parametrizadas, sem concatenacao de input.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.Customer;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

  private static final String SELECT_BY_ID =
      """
      SELECT id::text, full_name, email, phone, city, state, opt_in_whatsapp, created_at
      FROM customers
      WHERE id = :id
      LIMIT 1
      """;

  // A customer belongs to a dealer when one of their vehicles is serviced there or when
  // the dealer owns a lead for them.
  private static final String LINKED_TO_DEALER =
      """
      SELECT EXISTS (SELECT 1 FROM vehicles v
                      WHERE v.customer_id = :id AND v.current_dealer_id = :dealerId)
          OR EXISTS (SELECT 1 FROM leads l
                      WHERE l.customer_id = :id AND l.dealer_id = :dealerId)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public CustomerRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Customer> findById(String id) {
    var params = new MapSqlParameterSource("id", UUID.fromString(id));
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
                    rs.getObject("created_at", OffsetDateTime.class)))
        .stream()
        .findFirst();
  }

  public boolean isLinkedToDealer(String customerId, String dealerId) {
    var params =
        new MapSqlParameterSource()
            .addValue("id", UUID.fromString(customerId))
            .addValue("dealerId", UUID.fromString(dealerId));
    return Boolean.TRUE.equals(jdbc.queryForObject(LINKED_TO_DEALER, params, Boolean.class));
  }
}
