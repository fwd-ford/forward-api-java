// Customer DTO returned by /api/v1/customers/{id}.
// DTO de cliente retornado por /api/v1/customers/{id}.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Customer(
    String id,
    @JsonProperty("full_name") String fullName,
    String email,
    String phone,
    String city,
    String state,
    @JsonProperty("opt_in_whatsapp") boolean optInWhatsApp,
    @JsonProperty("created_at") OffsetDateTime createdAt) {}
