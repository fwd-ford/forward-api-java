// Vehicle DTO returned by /api/v1/vehicles/{vin} and the SOAP GetVehicle operation.
// DTO de veiculo retornado por /api/v1/vehicles/{vin} e pela operacao SOAP GetVehicle.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Vehicle", description = "Vehicle record identified by VIN.")
public record Vehicle(
    @Schema(
            description = "17-character Vehicle Identification Number (ISO 3779).",
            example = "1HGCM82633A123456",
            pattern = "^[A-HJ-NPR-Z0-9]{17}$")
        String vin,
    @JsonProperty("customer_id")
        @Schema(
            description = "Owner's customer UUID.",
            example = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10",
            format = "uuid")
        String customerId,
    @Schema(description = "Commercial model name.", example = "Ranger") String model,
    @Schema(description = "Model year.", example = "2024", minimum = "1900") int year,
    @Schema(description = "Trim or version of the model.", example = "Limited 3.0 V6")
        String version,
    @Schema(description = "Exterior color.", example = "Azul Atlas") String color,
    @Schema(description = "True if the model is no longer in production.", example = "false")
        boolean discontinued,
    @JsonProperty("purchase_date")
        @Schema(
            description = "Date the customer purchased the vehicle.",
            example = "2023-08-14",
            format = "date")
        LocalDate purchaseDate,
    @JsonProperty("last_service_at")
        @Schema(
            description = "Timestamp of the most recent service event for this vehicle.",
            example = "2026-04-22T13:05:00-03:00",
            format = "date-time")
        OffsetDateTime lastServiceAt) {}
