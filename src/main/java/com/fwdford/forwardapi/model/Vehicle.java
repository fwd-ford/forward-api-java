// Vehicle DTO returned by /api/v1/vehicles/{vin} and the SOAP GetVehicle operation.
// DTO de veiculo retornado por /api/v1/vehicles/{vin} e pela operacao SOAP GetVehicle.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Vehicle(
    String vin,
    @JsonProperty("customer_id") String customerId,
    String model,
    int year,
    String version,
    String color,
    boolean discontinued,
    @JsonProperty("purchase_date") LocalDate purchaseDate,
    @JsonProperty("last_service_at") OffsetDateTime lastServiceAt) {}
