// Customer service. Applies RBAC: end users can only read their own record;
// analyst, admin and dealer roles can read anyone.
// Service de customer: aplica RBAC (user so ve a si mesmo; analyst/admin/dealer veem qualquer).
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

  private final CustomerRepository repo;

  public CustomerService(CustomerRepository repo) {
    this.repo = repo;
  }

  public Customer get(String id, String callerSub, String callerRole) {
    if (!canRead(id, callerSub, callerRole)) {
      throw ApiException.forbidden();
    }
    return repo.findById(id).orElseThrow(() -> ApiException.notFound("customer"));
  }

  private boolean canRead(String id, String sub, String role) {
    if ("analyst".equals(role) || "admin".equals(role) || "dealer".equals(role)) {
      return true;
    }
    return "user".equals(role) && id.equals(sub);
  }
}
