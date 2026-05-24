// Request body for POST /api/v1/service-events. Bean Validation rejects malformed payloads.
// Corpo da requisicao para POST /api/v1/service-events: Bean Validation rejeita payloads malformados.
package com.fwdford.forwardapi.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

public record CreateServiceEventRequest(
    @NotBlank(message = "vin obrigatorio") String vin,
    @NotBlank(message = "dealerCode obrigatorio") String dealerCode,
    @NotNull(message = "serviceCode obrigatorio")
        @Min(value = 1, message = "serviceCode deve ser >= 1")
        @Max(value = 5, message = "serviceCode deve ser <= 5")
        Integer serviceCode,
    @NotNull(message = "maintenanceNumber obrigatorio")
        @Min(value = 0, message = "maintenanceNumber deve ser >= 0")
        Integer maintenanceNumber,
    @Min(value = 0, message = "km deve ser >= 0") Integer km,
    @NotNull(message = "serviceDate obrigatorio") OffsetDateTime serviceDate,
    @NotBlank(message = "mainSource obrigatorio")
        @Pattern(
            regexp = "^(dealer_app|n8n|manual|legacy)$",
            message = "mainSource invalido")
        String mainSource) {}
