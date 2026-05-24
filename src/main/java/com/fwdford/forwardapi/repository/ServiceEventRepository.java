// Service event repository. Inserts into service_orders and resolves dealer codes
// to UUIDs. All values are bound via named parameters; the order_type enum is
// cast in SQL.
// Repositorio de eventos de servico: insere em service_orders e resolve dealer
// code para UUID. Valores parametrizados; enum order_type convertido no SQL.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.ServiceEvent;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceEventRepository {

  private static final String SELECT_DEALER_ID_BY_CODE =
      "SELECT id FROM dealers WHERE code = :code AND active = TRUE LIMIT 1";

  private static final String INSERT_SQL =
      """
      INSERT INTO service_orders
        (vin, dealer_id, order_type, scheduled_at, mileage_km, maintenance_number, main_source)
      VALUES
        (:vin, :dealerId, CAST(:orderType AS service_order_type), :scheduledAt, :mileageKm,
         :maintenanceNumber, :mainSource)
      RETURNING id::text AS id, vin, dealer_id::text AS dealer_id, order_type::text AS order_type,
                status::text AS status, scheduled_at, mileage_km, maintenance_number, main_source
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ServiceEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UUID> findDealerIdByCode(String code) {
    MapSqlParameterSource params = new MapSqlParameterSource("code", code);
    return jdbc
        .query(SELECT_DEALER_ID_BY_CODE, params, (rs, idx) -> rs.getObject("id", UUID.class))
        .stream()
        .findFirst();
  }

  public ServiceEvent insert(
      String vin,
      UUID dealerId,
      String orderType,
      OffsetDateTime scheduledAt,
      Integer mileageKm,
      int maintenanceNumber,
      String mainSource) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("vin", vin)
            .addValue("dealerId", dealerId)
            .addValue("orderType", orderType)
            .addValue("scheduledAt", scheduledAt)
            .addValue("mileageKm", mileageKm)
            .addValue("maintenanceNumber", maintenanceNumber)
            .addValue("mainSource", mainSource);

    return jdbc.queryForObject(
        INSERT_SQL,
        params,
        (rs, idx) ->
            new ServiceEvent(
                rs.getString("id"),
                rs.getString("vin"),
                rs.getString("dealer_id"),
                rs.getString("order_type"),
                rs.getString("status"),
                rs.getObject("scheduled_at", OffsetDateTime.class),
                rs.getObject("mileage_km") == null ? null : rs.getInt("mileage_km"),
                rs.getInt("maintenance_number"),
                rs.getString("main_source")));
  }
}
