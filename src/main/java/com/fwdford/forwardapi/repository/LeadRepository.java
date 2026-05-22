// Lead repository. Dynamic filters built from whitelisted columns only; all values
// are bound via named parameters, never concatenated.
// Repositorio de leads: filtros dinamicos so com colunas permitidas, valores parametrizados.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeadRepository {

  private static final String BASE_SELECT =
      """
      SELECT id::text, customer_id::text, vin, dealer_id::text, priority::text, status::text,
             reason, expected_value_brl, created_at, converted_at
      FROM leads
      WHERE 1=1
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public LeadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Lead> list(LeadFilter filter) {
    StringBuilder sql = new StringBuilder(BASE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource();

    if (filter.dealerId() != null && !filter.dealerId().isEmpty()) {
      sql.append(" AND dealer_id = :dealerId");
      params.addValue("dealerId", java.util.UUID.fromString(filter.dealerId()));
    }
    if (filter.status() != null && !filter.status().isEmpty()) {
      sql.append(" AND status = :status");
      params.addValue("status", filter.status());
    }
    sql.append(" ORDER BY created_at DESC LIMIT :lim");
    params.addValue("lim", filter.limit());

    return jdbc.query(
        sql.toString(),
        params,
        (rs, idx) ->
            new Lead(
                rs.getString("id"),
                rs.getString("customer_id"),
                rs.getString("vin"),
                rs.getString("dealer_id"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getObject("expected_value_brl") instanceof java.math.BigDecimal bd
                    ? bd.doubleValue()
                    : null,
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("converted_at", java.time.OffsetDateTime.class)));
  }
}
