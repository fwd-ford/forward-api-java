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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Health", description = "Liveness and readiness probes (public).")
@SecurityRequirements
public class HealthController {

  private final String version;

  public HealthController(@Value("${forward.version:dev}") String version) {
    this.version = version;
  }

  @GetMapping("/health")
  @Operation(
      operationId = "liveness",
      summary = "Liveness probe",
      description =
          "Public endpoint that confirms the process is up and returns the running version. "
              + "Does not test downstream dependencies.")
  @ApiResponses(
      @ApiResponse(
          responseCode = "200",
          description = "Service is up",
          content = @Content(schema = @Schema(implementation = HealthStatus.class))))
  public Map<String, Object> health() {
    return payload();
  }

  @GetMapping("/ready")
  @Operation(
      operationId = "readiness",
      summary = "Readiness probe",
      description =
          "Public endpoint that confirms the process is ready to serve traffic and returns the "
              + "running version. Does not test downstream dependencies.")
  @ApiResponses(
      @ApiResponse(
          responseCode = "200",
          description = "Service is ready",
          content = @Content(schema = @Schema(implementation = HealthStatus.class))))
  public Map<String, Object> ready() {
    return payload();
  }

  private Map<String, Object> payload() {
    return Map.of(
        "status", "ok", "timestamp", Instant.now(), "service", "forward-api", "version", version);
  }
}
