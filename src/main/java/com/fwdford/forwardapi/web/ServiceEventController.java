// POST /api/v1/service-events. Validates the request body via Bean Validation,
// delegates creation to the service and returns 201 Created with the resource
// Location header.
// POST /api/v1/service-events: valida o corpo por Bean Validation, cria via
// service e retorna 201 Created com header Location.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.service.ServiceEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/api/v1/service-events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
    name = "Service Events",
    description = "Service event registration (scheduled vehicle maintenance).")
public class ServiceEventController {

  private final ServiceEventService service;

  public ServiceEventController(ServiceEventService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "createServiceEvent",
      summary = "Register a service event",
      description =
          "Records a scheduled maintenance event for a vehicle. The VIN must already exist in the "
              + "system; otherwise 404 is returned. The dealerCode and serviceCode pair identifies "
              + "the workshop and the kind of service performed. Returns 201 with the created "
              + "resource and a Location header pointing at /api/v1/service-events/{id}.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Service event created",
        content = @Content(schema = @Schema(implementation = ServiceEvent.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed on the request body",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden by RBAC",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "VIN not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ServiceEvent> create(@Valid @RequestBody CreateServiceEventRequest req) {
    ServiceEvent created = service.create(req);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }
}
