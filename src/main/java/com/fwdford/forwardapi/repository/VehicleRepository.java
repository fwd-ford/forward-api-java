// Vehicle repository. Parameterized lookup by VIN.
// Repositorio de veiculos: busca parametrizada por VIN.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.Vehicle;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VehicleRepository {

  private static final String SELECT_BY_VIN =
      """
      SELECT vin, customer_id::text, current_dealer_id::text, model, year, version, color,
             discontinued, purchase_date, last_service_at
      FROM vehicles
      WHERE vin = :vin
      LIMIT 1
      """;

  // A vehicle belongs to a dealer when it is currently serviced there or when the dealer
  // owns a lead for it.
  private static final String LINKED_TO_DEALER =
      """
      SELECT EXISTS (SELECT 1 FROM vehicles v
                      WHERE v.vin = :vin AND v.current_dealer_id = :dealerId)
          OR EXISTS (SELECT 1 FROM leads l
                      WHERE l.vin = :vin AND l.dealer_id = :dealerId)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public VehicleRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Vehicle> findByVin(String vin) {
    var params = new MapSqlParameterSource("vin", vin);
    return jdbc
        .query(
            SELECT_BY_VIN,
            params,
            (rs, idx) ->
                new Vehicle(
                    rs.getString("vin"),
                    rs.getString("customer_id"),
                    rs.getString("current_dealer_id"),
                    rs.getString("model"),
                    rs.getInt("year"),
                    rs.getString("version"),
                    rs.getString("color"),
                    rs.getBoolean("discontinued"),
                    rs.getObject("purchase_date", LocalDate.class),
                    rs.getObject("last_service_at", OffsetDateTime.class)))
        .stream()
        .findFirst();
  }

  public boolean existsByVin(String vin) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM vehicles WHERE vin = :vin)",
            new MapSqlParameterSource("vin", vin),
            Boolean.class));
  }

  public boolean isLinkedToDealer(String vin, String dealerId) {
    var params =
        new MapSqlParameterSource()
            .addValue("vin", vin)
            .addValue("dealerId", UUID.fromString(dealerId));
    return Boolean.TRUE.equals(jdbc.queryForObject(LINKED_TO_DEALER, params, Boolean.class));
  }
}
