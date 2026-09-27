// Service event repository (table service_orders). All values are bound via named
// parameters; enum columns are cast in SQL.
// Repositorio de eventos de servico (tabela service_orders), SQL parametrizado.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.ServiceEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceEventRepository {

  /** service_code (API) to order_type (database enum). */
  public static final Map<Integer, String> SERVICE_CODE_TO_ORDER_TYPE =
      Map.of(
          1, "scheduled_maintenance",
          2, "recall",
          3, "warranty_repair",
          4, "paid_repair",
          5, "inspection");

  private static final String SELECT =
      """
      SELECT so.id::text AS id, so.vin, so.dealer_id::text AS dealer_id, d.code AS dealer_code,
             so.order_type::text AS order_type, so.status::text AS status, so.scheduled_at,
             so.completed_at, so.mileage_km, so.maintenance_number, so.main_source,
             so.created_at, so.updated_at
      FROM service_orders so
      JOIN dealers d ON d.id = so.dealer_id
      """;

  private static final String FILTER =
      """
      WHERE (CAST(:vin AS text) IS NULL OR so.vin = CAST(:vin AS text))
        AND (CAST(:dealerId AS uuid) IS NULL OR so.dealer_id = CAST(:dealerId AS uuid))
      """;

  private static final String INSERT =
      """
      INSERT INTO service_orders
        (vin, dealer_id, order_type, status, scheduled_at, completed_at, mileage_km,
         maintenance_number, main_source)
      VALUES
        (:vin, :dealerId, CAST(:orderType AS service_order_type),
         CAST(:status AS service_order_status), :scheduledAt,
         CASE WHEN CAST(:status AS text) = 'completed' THEN NOW() END,
         :mileageKm, :maintenanceNumber, :mainSource)
      RETURNING id::text AS id
      """;

  private static final String UPDATE =
      """
      UPDATE service_orders
         SET vin = :vin,
             dealer_id = :dealerId,
             order_type = CAST(:orderType AS service_order_type),
             status = CAST(:status AS service_order_status),
             scheduled_at = :scheduledAt,
             completed_at = CASE WHEN CAST(:status AS text) = 'completed'
                                 THEN COALESCE(completed_at, NOW()) END,
             mileage_km = :mileageKm,
             maintenance_number = :maintenanceNumber,
             main_source = :mainSource
       WHERE id = :id
      """;

  private static final String DUPLICATE =
      """
      SELECT EXISTS (
        SELECT 1 FROM service_orders
         WHERE vin = :vin AND dealer_id = :dealerId
           AND order_type = CAST(:orderType AS service_order_type)
           AND scheduled_at = :scheduledAt
           AND (CAST(:excludeId AS uuid) IS NULL OR id <> CAST(:excludeId AS uuid)))
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ServiceEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ServiceEvent> list(String vin, UUID dealerId, int limit, int offset) {
    MapSqlParameterSource params = filterParams(vin, dealerId);
    params.addValue("lim", limit).addValue("off", offset);
    return jdbc.query(
        SELECT
            + FILTER
            + " ORDER BY so.scheduled_at DESC NULLS LAST, so.created_at DESC, so.id"
            + " LIMIT :lim OFFSET :off",
        params,
        ServiceEventRepository::map);
  }

  public long count(String vin, UUID dealerId) {
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_orders so " + FILTER,
            filterParams(vin, dealerId),
            Long.class);
    return total == null ? 0 : total;
  }

  public Optional<ServiceEvent> findById(UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE so.id = :id",
            new MapSqlParameterSource("id", id),
            ServiceEventRepository::map)
        .stream()
        .findFirst();
  }

  public boolean existsDuplicate(
      String vin, UUID dealerId, String orderType, OffsetDateTime scheduledAt, UUID excludeId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("vin", vin)
            .addValue("dealerId", dealerId)
            .addValue("orderType", orderType)
            .addValue("scheduledAt", scheduledAt)
            .addValue("excludeId", excludeId, Types.OTHER);
    return Boolean.TRUE.equals(jdbc.queryForObject(DUPLICATE, params, Boolean.class));
  }

  public UUID insert(
      String vin,
      UUID dealerId,
      String orderType,
      String status,
      OffsetDateTime scheduledAt,
      Integer mileageKm,
      int maintenanceNumber,
      String mainSource) {
    MapSqlParameterSource params =
        writeParams(
            vin,
            dealerId,
            orderType,
            status,
            scheduledAt,
            mileageKm,
            maintenanceNumber,
            mainSource);
    String id = jdbc.queryForObject(INSERT, params, String.class);
    if (id == null) {
      throw new IllegalStateException("INSERT service_orders returned no id");
    }
    return UUID.fromString(id);
  }

  public int update(
      UUID id,
      String vin,
      UUID dealerId,
      String orderType,
      String status,
      OffsetDateTime scheduledAt,
      Integer mileageKm,
      int maintenanceNumber,
      String mainSource) {
    MapSqlParameterSource params =
        writeParams(
            vin,
            dealerId,
            orderType,
            status,
            scheduledAt,
            mileageKm,
            maintenanceNumber,
            mainSource);
    params.addValue("id", id);
    return jdbc.update(UPDATE, params);
  }

  public int deleteById(UUID id) {
    return jdbc.update(
        "DELETE FROM service_orders WHERE id = :id", new MapSqlParameterSource("id", id));
  }

  private static MapSqlParameterSource writeParams(
      String vin,
      UUID dealerId,
      String orderType,
      String status,
      OffsetDateTime scheduledAt,
      Integer mileageKm,
      int maintenanceNumber,
      String mainSource) {
    return new MapSqlParameterSource()
        .addValue("vin", vin)
        .addValue("dealerId", dealerId)
        .addValue("orderType", orderType)
        .addValue("status", status)
        .addValue("scheduledAt", scheduledAt)
        .addValue("mileageKm", mileageKm, Types.INTEGER)
        .addValue("maintenanceNumber", maintenanceNumber)
        .addValue("mainSource", mainSource);
  }

  private static MapSqlParameterSource filterParams(String vin, UUID dealerId) {
    return new MapSqlParameterSource()
        .addValue("vin", vin, Types.VARCHAR)
        .addValue("dealerId", dealerId, Types.OTHER);
  }

  private static ServiceEvent map(ResultSet rs, int rowNum) throws SQLException {
    String orderType = rs.getString("order_type");
    return new ServiceEvent(
        rs.getString("id"),
        rs.getString("vin"),
        rs.getString("dealer_id"),
        rs.getString("dealer_code"),
        orderType,
        serviceCodeOf(orderType),
        rs.getString("status"),
        rs.getObject("scheduled_at", OffsetDateTime.class),
        rs.getObject("completed_at", OffsetDateTime.class),
        rs.getObject("mileage_km") == null ? null : rs.getInt("mileage_km"),
        rs.getInt("maintenance_number"),
        rs.getString("main_source"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private static Integer serviceCodeOf(String orderType) {
    return SERVICE_CODE_TO_ORDER_TYPE.entrySet().stream()
        .filter(e -> e.getValue().equals(orderType))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
