// Response schema for /health and /ready. Used only for OpenAPI documentation —
// the controllers still return Map<String, Object> for runtime simplicity.
// Schema de resposta dos endpoints /health e /ready, usado so para o OpenAPI;
// os controllers continuam retornando Map em runtime.
package com.fwdford.forwardapi.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(
    name = "HealthStatus",
    description = "Health probe payload. Indicates the process is up and reports its version.")
public record HealthStatus(
    @Schema(description = "Always \"ok\" when the service is responsive.", example = "ok")
        String status,
    @Schema(description = "Server timestamp in ISO 8601.", example = "2026-05-24T19:00:00Z")
        OffsetDateTime timestamp,
    @Schema(description = "Service identifier.", example = "forward-api") String service,
    @Schema(description = "Running version (Maven artifact version).", example = "0.1.0")
        String version) {}
