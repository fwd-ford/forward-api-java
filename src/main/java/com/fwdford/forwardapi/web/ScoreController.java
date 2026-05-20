// GET /api/v1/scores/{customerId}. RBAC is applied inside the service layer.
// GET /api/v1/scores/{customerId}: RBAC aplicado no service.
package com.fwdford.forwardapi.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.service.ScoreService;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

  private final ScoreService service;

  public ScoreController(ScoreService service) {
    this.service = service;
  }

  @GetMapping("/{customerId}")
  public ChurnScore get(@PathVariable String customerId, HttpServletRequest req) {
    String validId = Validations.validateUuid("customerId", customerId);
    AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
    String role = p != null ? p.role() : null;
    return service.getCurrent(validId, role);
  }
}
