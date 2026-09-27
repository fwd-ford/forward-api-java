// Lead repository. Dynamic filters are built from a fixed set of SQL fragments; every
// value is bound via named parameters, never concatenated. Reads join customer, vehicle,
// dealer and the lead's churn score so the app gets everything in one call.
// Repositorio de leads: filtros com fragmentos fixos e valores parametrizados.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeadRepository {

  private static final String SELECT =
      """
      SELECT l.id::text AS id, l.customer_id::text AS customer_id, l.vin,
             l.dealer_id::text AS dealer_id, l.priority::text AS priority,
             l.status::text AS status, l.reason, l.expected_value_brl, l.created_at,
             l.updated_at, l.converted_at, l.notes,
             c.full_name AS customer_name, v.model AS vehicle_model, v.year AS vehicle_year,
             d.name AS dealer_name, sc.churn_probability, sc.segment
      FROM leads l
      JOIN customers c ON c.id = l.customer_id
      LEFT JOIN vehicles v ON v.vin = l.vin
      LEFT JOIN dealers d ON d.id = l.dealer_id
      LEFT JOIN LATERAL (
          SELECT s.churn_probability, s.segment
          FROM churn_scores s
          WHERE (l.score_id IS NOT NULL AND s.id = l.score_id)
             OR (l.score_id IS NULL AND s.customer_id = l.customer_id AND s.is_current)
          ORDER BY s.computed_at DESC
          LIMIT 1
      ) sc ON TRUE
      """;

  private static final String UPDATE =
      """
      UPDATE leads
         SET status = COALESCE(CAST(:status AS lead_status), status),
             notes = CASE WHEN :notesSet THEN CAST(:notes AS text) ELSE notes END,
             assigned_at = CASE
                 WHEN CAST(:status AS text) = 'assigned' AND assigned_at IS NULL THEN NOW()
                 ELSE assigned_at END,
             converted_at = CASE
                 WHEN CAST(:status AS text) = 'converted' THEN NOW()
                 ELSE converted_at END
       WHERE id = :id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public LeadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Lead> list(LeadFilter filter) {
    StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
    MapSqlParameterSource params = new MapSqlParameterSource();
    appendFilters(filter, sql, params);
    sql.append(" ORDER BY l.created_at DESC, l.id LIMIT :lim OFFSET :off");
    params.addValue("lim", filter.limit()).addValue("off", filter.offset());
    return jdbc.query(sql.toString(), params, LeadRepository::map);
  }

  public long count(LeadFilter filter) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM leads l WHERE 1=1");
    MapSqlParameterSource params = new MapSqlParameterSource();
    appendFilters(filter, sql, params);
    Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
    return total == null ? 0 : total;
  }

  public Optional<Lead> findById(UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE l.id = :id", new MapSqlParameterSource("id", id), LeadRepository::map)
        .stream()
        .findFirst();
  }

  /**
   * Applies a partial update. A null status keeps the current one; notes are only written when
   * {@code notesSet} is true (null clears them).
   */
  public int update(UUID id, String status, boolean notesSet, String notes) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("status", status, Types.VARCHAR)
            .addValue("notesSet", notesSet)
            .addValue("notes", notes, Types.VARCHAR);
    return jdbc.update(UPDATE, params);
  }

  private static void appendFilters(
      LeadFilter filter, StringBuilder sql, MapSqlParameterSource params) {
    if (filter.dealerId() != null && !filter.dealerId().isEmpty()) {
      sql.append(" AND l.dealer_id = :dealerId");
      params.addValue("dealerId", UUID.fromString(filter.dealerId()));
    }
    if (filter.status() != null && !filter.status().isEmpty()) {
      sql.append(" AND l.status = CAST(:status AS lead_status)");
      params.addValue("status", filter.status());
    }
    if (filter.priority() != null && !filter.priority().isEmpty()) {
      sql.append(" AND l.priority = CAST(:priority AS lead_priority)");
      params.addValue("priority", filter.priority());
    }
  }

  private static Lead map(ResultSet rs, int rowNum) throws SQLException {
    return new Lead(
        rs.getString("id"),
        rs.getString("customer_id"),
        rs.getString("vin"),
        rs.getString("dealer_id"),
        rs.getString("priority"),
        rs.getString("status"),
        rs.getString("reason"),
        toDouble(rs.getObject("expected_value_brl")),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("converted_at", OffsetDateTime.class),
        rs.getString("notes"),
        rs.getString("customer_name"),
        rs.getString("vehicle_model"),
        rs.getObject("vehicle_year") == null ? null : rs.getInt("vehicle_year"),
        rs.getString("dealer_name"),
        toDouble(rs.getObject("churn_probability")),
        rs.getString("segment"));
  }

  private static Double toDouble(Object value) {
    return value instanceof BigDecimal bd ? bd.doubleValue() : null;
  }
}
