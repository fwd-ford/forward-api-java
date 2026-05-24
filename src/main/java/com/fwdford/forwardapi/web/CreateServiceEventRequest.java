// Request body for POST /api/v1/service-events. Bean Validation rejects malformed payloads.
// Corpo da requisicao para POST /api/v1/service-events: Bean Validation rejeita payloads
// malformados.
package com.fwdford.forwardapi.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

@Schema(
    name = "CreateServiceEventRequest",
    description = "Payload to register a new service event for a vehicle.")
public record CreateServiceEventRequest(
    @Schema(
            description = "17-character Vehicle Identification Number (ISO 3779).",
            example = "1HGCM82633A123456",
            pattern = "^[A-HJ-NPR-Z0-9]{17}$",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "vin obrigatorio")
        String vin,
    @Schema(
            description = "Dealer code (workshop identifier).",
            example = "BR-SP-0042",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "dealerCode obrigatorio")
        String dealerCode,
    @Schema(
            description = "Service type code, ranging from 1 (oil change) to 5 (full overhaul).",
            example = "2",
            minimum = "1",
            maximum = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "serviceCode obrigatorio")
        @Min(value = 1, message = "serviceCode deve ser >= 1")
        @Max(value = 5, message = "serviceCode deve ser <= 5")
        Integer serviceCode,
    @Schema(
            description = "Scheduled maintenance number (0 for unscheduled, 1+ for revisions).",
            example = "3",
            minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "maintenanceNumber obrigatorio")
        @Min(value = 0, message = "maintenanceNumber deve ser >= 0")
        Integer maintenanceNumber,
    @Schema(
            description = "Vehicle mileage at service time, in kilometers.",
            example = "42500",
            minimum = "0",
            nullable = true)
        @Min(value = 0, message = "km deve ser >= 0")
        Integer km,
    @Schema(
            description = "Service date and time (ISO 8601 with offset).",
            example = "2026-05-24T14:30:00-03:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "serviceDate obrigatorio")
        OffsetDateTime serviceDate,
    @Schema(
            description = "Origin of the event payload.",
            example = "dealer_app",
            allowableValues = {"dealer_app", "n8n", "manual", "legacy"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "mainSource obrigatorio")
        @Pattern(regexp = "^(dealer_app|n8n|manual|legacy)$", message = "mainSource invalido")
        String mainSource) {}
