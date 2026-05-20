// Vehicle repository. Parameterized lookup by VIN.
// Repositorio de veiculos: busca parametrizada por VIN.
package com.fwdford.forwardapi.repository;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fwdford.forwardapi.model.Vehicle;

@Repository
public class VehicleRepository {

  private static final String SELECT_BY_VIN =
      """
      SELECT vin, customer_id::text, model, year, version, color,
             discontinued, purchase_date, last_service_at
      FROM vehicles
      WHERE vin = :vin
      LIMIT 1
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
                    rs.getString("model"),
                    rs.getInt("year"),
                    rs.getString("version"),
                    rs.getString("color"),
                    rs.getBoolean("discontinued"),
                    rs.getObject("purchase_date", java.time.LocalDate.class),
                    rs.getObject("last_service_at", java.time.OffsetDateTime.class)))
        .stream()
        .findFirst();
  }
}
