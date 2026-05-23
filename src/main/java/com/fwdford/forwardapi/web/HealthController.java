// Liveness and readiness endpoints. Public: do not touch dependencies.
// Endpoints de saude: publicos e nao testam dependencias.
package com.fwdford.forwardapi.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Liveness and readiness probes (public).")
@SecurityRequirements
public class HealthController {

  private final String version;

  public HealthController(@Value("${forward.version:dev}") String version) {
    this.version = version;
  }

  @GetMapping({"/health", "/ready"})
  @Operation(
      summary = "Liveness and readiness probe",
      description =
          "Public endpoint that confirms the process is up and returns the running version. "
              + "Does not test downstream dependencies. Mapped to both /health and /ready.")
  @ApiResponses(
      @ApiResponse(
          responseCode = "200",
          description = "Service is up",
          content =
              @Content(
                  schema =
                      @Schema(
                          type = "object",
                          example =
                              "{\"status\":\"ok\",\"timestamp\":\"2026-05-23T00:00:00Z\",\"service\":\"forward-api\",\"version\":\"0.1.0\"}"))))
  public Map<String, Object> health() {
    return Map.of(
        "status", "ok", "timestamp", Instant.now(), "service", "forward-api", "version", version);
  }
}
