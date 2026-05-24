// Customer DTO returned by /api/v1/customers/{id}.
// DTO de cliente retornado por /api/v1/customers/{id}.
package com.fwdford.forwardapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Customer", description = "Customer profile, returned with RBAC enforcement.")
public record Customer(
    @Schema(
            description = "Customer UUID.",
            example = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10",
            format = "uuid")
        String id,
    @JsonProperty("full_name")
        @Schema(description = "Customer full name.", example = "Maria Aparecida Silva")
        String fullName,
    @Schema(
            description = "Customer email (RFC 5322).",
            example = "maria.silva@example.com",
            format = "email")
        String email,
    @Schema(description = "Phone number with country and area code.", example = "+5511987654321")
        String phone,
    @Schema(description = "City of residence.", example = "Sao Paulo") String city,
    @Schema(
            description = "Brazilian state abbreviation (UF).",
            example = "SP",
            minLength = 2,
            maxLength = 2)
        String state,
    @JsonProperty("opt_in_whatsapp")
        @Schema(
            description = "True when the customer accepted WhatsApp outreach.",
            example = "true")
        boolean optInWhatsApp,
    @JsonProperty("created_at")
        @Schema(
            description = "Timestamp the customer record was created.",
            example = "2025-09-12T18:34:00-03:00",
            format = "date-time")
        OffsetDateTime createdAt) {}
