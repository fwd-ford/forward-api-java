// Lead DTO returned by /api/v1/leads and /api/v1/leads/{id}. The first nine fields are
// the stable contract consumed by the mobile app; the others come from joins (customer,
// vehicle, dealer, churn score) and are omitted when null.
// DTO de lead: campos base estaveis para o app e campos extras vindos de joins.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Lead", description = "Oportunidade de retenção gerada a partir do score de churn.")
public record Lead(
    @Schema(
            description = "UUID do lead.",
            example = "a1000000-0000-4000-8000-000000000001",
            format = "uuid")
        String id,
    @JsonProperty("customer_id")
        @Schema(
            description = "UUID do cliente.",
            example = "11111111-1111-1111-1111-111111111001",
            format = "uuid")
        String customerId,
    @Schema(
            description = "VIN do veículo (17 caracteres).",
            example = "9BFZZZ5SZJB000001",
            pattern = "^[A-HJ-NPR-Z0-9]{17}$")
        String vin,
    @JsonProperty("dealer_id")
        @Schema(
            description = "UUID da concessionária responsável.",
            example = "d0000000-0000-4000-8000-000000000001",
            format = "uuid")
        String dealerId,
    @Schema(
            description = "Prioridade.",
            example = "high",
            allowableValues = {"low", "medium", "high", "critical"})
        String priority,
    @Schema(
            description = "Status no ciclo de vida.",
            example = "new",
            allowableValues = {"new", "assigned", "contacted", "converted", "lost", "expired"})
        String status,
    @Schema(description = "Motivo da geração do lead.", example = "Revisão atrasada há 18 meses.")
        String reason,
    @JsonProperty("expected_value_brl")
        @Schema(description = "Receita esperada em BRL.", example = "1200.00", minimum = "0")
        Double expectedValueBrl,
    @JsonProperty("created_at") @Schema(description = "Criação do lead.", format = "date-time")
        OffsetDateTime createdAt,
    @JsonProperty("updated_at") @Schema(description = "Última alteração.", format = "date-time")
        OffsetDateTime updatedAt,
    @JsonProperty("converted_at")
        @Schema(description = "Quando o lead virou venda (se convertido).", format = "date-time")
        OffsetDateTime convertedAt,
    @Schema(description = "Anotações do atendimento.", example = "Cliente pediu retorno amanhã.")
        String notes,
    @JsonProperty("customer_name")
        @Schema(description = "Nome do cliente.", example = "João da Silva")
        String customerName,
    @JsonProperty("vehicle_model") @Schema(description = "Modelo do veículo.", example = "Ka")
        String vehicleModel,
    @JsonProperty("vehicle_year") @Schema(description = "Ano do veículo.", example = "2018")
        Integer vehicleYear,
    @JsonProperty("dealer_name")
        @Schema(description = "Nome da concessionária.", example = "Ford Morumbi São Paulo")
        String dealerName,
    @JsonProperty("churn_probability")
        @Schema(description = "Probabilidade de churn (0 a 1).", example = "0.78")
        Double churnProbability,
    @Schema(
            description = "Segmento comportamental.",
            example = "esquecido",
            allowableValues = {"fiel", "abandono", "esquecido", "economico"})
        String segment) {}
