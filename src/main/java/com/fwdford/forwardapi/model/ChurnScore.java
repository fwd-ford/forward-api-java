// Churn score DTO returned by /api/v1/scores/{customerId}.
// DTO de score de churn retornado por /api/v1/scores/{customerId}.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "ChurnScore",
    description = "Latest churn probability and behavioral segment computed for a customer.")
public record ChurnScore(
    @Schema(
            description = "Score UUID.",
            example = "5e1d2f3a-4b6c-4d7e-8f9a-0b1c2d3e4f50",
            format = "uuid")
        String id,
    @JsonProperty("customer_id")
        @Schema(
            description = "Customer UUID this score belongs to.",
            example = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10",
            format = "uuid")
        String customerId,
    @Schema(
            description = "VIN of the vehicle considered when computing the score (nullable).",
            example = "1HGCM82633A123456",
            pattern = "^[A-HJ-NPR-Z0-9]{17}$",
            nullable = true)
        String vin,
    @JsonProperty("model_version")
        @Schema(
            description = "Identifier of the ML model that produced this score.",
            example = "v1.3.0")
        String modelVersion,
    @Schema(
            description = "Behavioral segment inferred by the model.",
            example = "abandono",
            allowableValues = {"fiel", "abandono", "esquecido", "economico"})
        String segment,
    @JsonProperty("churn_probability")
        @Schema(
            description = "Predicted probability of churn in the next 90 days, in [0, 1].",
            example = "0.82",
            minimum = "0",
            maximum = "1")
        double churnProbability,
    @Schema(
            description = "Confidence of the prediction, in [0, 1] (nullable).",
            example = "0.91",
            minimum = "0",
            maximum = "1",
            nullable = true)
        Double confidence,
    @JsonProperty("computed_at")
        @Schema(
            description = "Timestamp the score was computed.",
            example = "2026-05-23T22:10:00-03:00",
            format = "date-time")
        OffsetDateTime computedAt) {}
