// Response schema for /api/v1/me. Documentation-only DTO; the controller
// still returns Map<String, Object> for runtime simplicity.
// Schema de resposta de /api/v1/me; usado so para o OpenAPI, o controller
// continua retornando Map em runtime.
package com.fwdford.forwardapi.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AuthMe",
    description = "Subject and role of the authenticated caller (JWT or X-API-Key).")
public record AuthMe(
    @Schema(
            description =
                "Subject identifier (Supabase user UUID for JWT, service id for API key).",
            example = "f7b3c1d2-4a55-4e8c-9b6f-1234567890ab")
        String sub,
    @Schema(
            description = "Role granted to the caller.",
            example = "admin",
            allowableValues = {"end_user", "dealer", "analyst", "admin", "service"})
        String role) {}
