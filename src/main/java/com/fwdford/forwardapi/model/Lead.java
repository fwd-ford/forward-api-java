// Lead DTO returned by /api/v1/leads.
// DTO de lead retornado por /api/v1/leads.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Lead", description = "Sales lead generated for the dealer network.")
public record Lead(
    @Schema(
            description = "Lead UUID.",
            example = "9a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
            format = "uuid")
        String id,
    @JsonProperty("customer_id")
        @Schema(
            description = "Customer UUID this lead refers to.",
            example = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10",
            format = "uuid")
        String customerId,
    @Schema(
            description = "17-character VIN of the vehicle this lead refers to.",
            example = "1HGCM82633A123456",
            pattern = "^[A-HJ-NPR-Z0-9]{17}$")
        String vin,
    @JsonProperty("dealer_id")
        @Schema(
            description = "Dealer UUID assigned to follow up.",
            example = "11111111-1111-1111-1111-111111111111",
            format = "uuid")
        String dealerId,
    @Schema(
            description = "Priority level inferred from churn score and segment.",
            example = "high",
            allowableValues = {"low", "medium", "high", "critical"})
        String priority,
    @Schema(
            description = "Lifecycle status of the lead.",
            example = "new",
            allowableValues = {"new", "assigned", "contacted", "converted", "lost", "expired"})
        String status,
    @Schema(
            description = "Why the lead was generated (free text or canonical code).",
            example = "expected_churn>0.8")
        String reason,
    @JsonProperty("expected_value_brl")
        @Schema(
            description = "Expected revenue in BRL if this lead converts.",
            example = "18500.00",
            minimum = "0")
        Double expectedValueBrl,
    @JsonProperty("created_at")
        @Schema(
            description = "Timestamp the lead was created.",
            example = "2026-05-20T09:15:00-03:00",
            format = "date-time")
        OffsetDateTime createdAt,
    @JsonProperty("converted_at")
        @Schema(
            description = "Timestamp the lead became a sale (null when not converted yet).",
            example = "2026-05-22T17:42:00-03:00",
            format = "date-time",
            nullable = true)
        OffsetDateTime convertedAt) {}
