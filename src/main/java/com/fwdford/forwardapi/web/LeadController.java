// GET /api/v1/leads. Applies query-parameter validation before hitting the service.
// GET /api/v1/leads: aplica validacao dos parametros antes de chamar o service.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.service.LeadService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

  private static final List<String> STATUSES =
      List.of("new", "assigned", "contacted", "converted", "lost", "expired");

  private final LeadService service;

  public LeadController(LeadService service) {
    this.service = service;
  }

  @GetMapping
  public List<Lead> list(
      @RequestParam(name = "dealer_id", required = false) String dealerId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "limit", required = false) String limit) {

    String validDealer = "";
    if (dealerId != null && !dealerId.isEmpty()) {
      validDealer = Validations.validateUuid("dealer_id", dealerId);
    }
    String validStatus = Validations.validateEnum("status", status, STATUSES);
    int validLimit = Validations.validateLimit(limit, 50, 200);

    return service.list(new LeadFilter(validDealer, validStatus, validLimit));
  }
}
