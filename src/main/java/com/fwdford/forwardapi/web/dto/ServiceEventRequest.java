// Body of POST /api/v1/service-events and PUT /api/v1/service-events/{id} (full
// representation). Fields are snake_case; the legacy camelCase names from Sprint 2
// (dealerCode, serviceCode, ...) are still accepted as aliases.
// Corpo do POST e do PUT de eventos de servico (snake_case, com aliases camelCase).
package com.fwdford.forwardapi.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Schema(name = "ServiceEventRequest", description = "Dados de um evento de serviço.")
public record ServiceEventRequest(
    @Schema(
            description = "VIN de 17 caracteres (ISO 3779).",
            example = "9BFZZZ5SZJB000011",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "vin é obrigatório")
        String vin,
    @JsonProperty("dealer_code")
        @JsonAlias("dealerCode")
        @Schema(
            description = "Código da concessionária.",
            example = "F0001",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "dealer_code é obrigatório")
        @Size(max = 20, message = "dealer_code deve ter no máximo 20 caracteres")
        String dealerCode,
    @JsonProperty("service_code")
        @JsonAlias("serviceCode")
        @Schema(
            description =
                "Tipo: 1 revisão programada, 2 recall, 3 reparo em garantia, 4 reparo pago,"
                    + " 5 inspeção.",
            example = "1",
            minimum = "1",
            maximum = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "service_code é obrigatório")
        @Min(value = 1, message = "service_code deve estar entre 1 e 5")
        @Max(value = 5, message = "service_code deve estar entre 1 e 5")
        Integer serviceCode,
    @JsonProperty("maintenance_number")
        @JsonAlias("maintenanceNumber")
        @Schema(
            description = "Número da revisão (0 = fora do plano).",
            example = "3",
            minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "maintenance_number é obrigatório")
        @Min(value = 0, message = "maintenance_number deve ser >= 0")
        Integer maintenanceNumber,
    @Schema(description = "Quilometragem no serviço.", example = "42500", nullable = true)
        @Min(value = 0, message = "km deve ser >= 0")
        Integer km,
    @JsonProperty("service_date")
        @JsonAlias("serviceDate")
        @Schema(
            description = "Data e hora do serviço (ISO 8601 com offset).",
            example = "2026-10-20T09:00:00-03:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "service_date é obrigatório")
        OffsetDateTime serviceDate,
    @JsonProperty("main_source")
        @JsonAlias("mainSource")
        @Schema(
            description = "Origem do evento.",
            example = "dealer_app",
            allowableValues = {"dealer_app", "n8n", "manual", "legacy"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "main_source é obrigatório")
        @Pattern(
            regexp = "^(dealer_app|n8n|manual|legacy)$",
            message = "main_source deve ser dealer_app, n8n, manual ou legacy")
        String mainSource,
    @Schema(
            description = "Situação (padrão scheduled).",
            example = "scheduled",
            allowableValues = {"scheduled", "in_progress", "completed", "cancelled", "no_show"},
            nullable = true)
        @Pattern(
            regexp = "^(scheduled|in_progress|completed|cancelled|no_show)$",
            message = "status deve ser scheduled, in_progress, completed, cancelled ou no_show")
        String status) {}
