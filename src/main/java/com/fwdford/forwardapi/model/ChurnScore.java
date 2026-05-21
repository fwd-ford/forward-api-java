// Churn score DTO returned by /api/v1/scores/{customerId}.
// DTO de score de churn retornado por /api/v1/scores/{customerId}.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChurnScore(
    String id,
    @JsonProperty("customer_id") String customerId,
    String vin,
    @JsonProperty("model_version") String modelVersion,
    String segment,
    @JsonProperty("churn_probability") double churnProbability,
    Double confidence,
    @JsonProperty("computed_at") OffsetDateTime computedAt) {}
