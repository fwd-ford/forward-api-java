// Service event (row of service_orders) returned by /api/v1/service-events.
// Evento de servico (linha de service_orders) retornado por /api/v1/service-events.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ServiceEvent", description = "Evento de serviço (revisão, recall, reparo).")
public record ServiceEvent(
    @Schema(description = "UUID do evento.", format = "uuid") String id,
    @Schema(description = "VIN do veículo.", example = "9BFZZZ5SZJB000001") String vin,
    @JsonProperty("dealer_id") @Schema(description = "UUID da concessionária.", format = "uuid")
        String dealerId,
    @JsonProperty("dealer_code")
        @Schema(description = "Código Ford da concessionária.", example = "F0001")
        String dealerCode,
    @JsonProperty("order_type")
        @Schema(
            description = "Tipo do serviço.",
            example = "scheduled_maintenance",
            allowableValues = {
              "scheduled_maintenance",
              "recall",
              "warranty_repair",
              "paid_repair",
              "inspection"
            })
        String orderType,
    @JsonProperty("service_code")
        @Schema(description = "Código numérico do tipo (1 a 5).", example = "1")
        Integer serviceCode,
    @Schema(
            description = "Situação do evento.",
            example = "scheduled",
            allowableValues = {"scheduled", "in_progress", "completed", "cancelled", "no_show"})
        String status,
    @JsonProperty("scheduled_at")
        @Schema(description = "Data e hora do serviço.", format = "date-time")
        OffsetDateTime scheduledAt,
    @JsonProperty("completed_at")
        @Schema(description = "Conclusão (quando status = completed).", format = "date-time")
        OffsetDateTime completedAt,
    @JsonProperty("mileage_km")
        @Schema(description = "Quilometragem no serviço.", example = "42500")
        Integer mileageKm,
    @JsonProperty("maintenance_number")
        @Schema(description = "Número da revisão (0 = fora do plano).", example = "3")
        Integer maintenanceNumber,
    @JsonProperty("main_source")
        @Schema(
            description = "Origem do evento.",
            example = "dealer_app",
            allowableValues = {"dealer_app", "n8n", "manual", "legacy"})
        String mainSource,
    @JsonProperty("created_at") @Schema(format = "date-time") OffsetDateTime createdAt,
    @JsonProperty("updated_at") @Schema(format = "date-time") OffsetDateTime updatedAt) {}
