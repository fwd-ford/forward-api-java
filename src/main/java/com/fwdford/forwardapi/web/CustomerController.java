// GET /api/v1/customers/{id}. Validates UUID and delegates RBAC to the service.
// GET /api/v1/customers/{id}: valida UUID e delega RBAC ao service.
package com.fwdford.forwardapi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.service.CustomerService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable String id, HttpServletRequest req) {
        String validId = Validations.validateUuid("id", id);
        AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
        String sub  = p != null ? p.sub()  : null;
        String role = p != null ? p.role() : null;
        return service.get(validId, sub, role);
    }
}
