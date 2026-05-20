// Lead DTO returned by /api/v1/leads.
// DTO de lead retornado por /api/v1/leads.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Lead(
    String id,
    @JsonProperty("customer_id") String customerId,
    String vin,
    @JsonProperty("dealer_id") String dealerId,
    String priority,
    String status,
    String reason,
    @JsonProperty("expected_value_brl") Double expectedValueBrl,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("converted_at") OffsetDateTime convertedAt) {}
