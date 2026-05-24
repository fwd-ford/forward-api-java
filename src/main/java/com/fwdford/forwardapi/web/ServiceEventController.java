// POST /api/v1/service-events. Validates the request body via Bean Validation,
// delegates creation to the service and returns 201 Created with the resource
// Location header.
// POST /api/v1/service-events: valida o corpo por Bean Validation, cria via
// service e retorna 201 Created com header Location.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.service.ServiceEventService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/service-events")
public class ServiceEventController {

  private final ServiceEventService service;

  public ServiceEventController(ServiceEventService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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
