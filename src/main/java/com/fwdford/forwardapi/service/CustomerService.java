// Customer service. Dealer-scoped: ATENDENTE and GESTOR only read customers linked to
// their dealer (vehicle serviced there or lead owned by it); ADMIN and SERVICE read all.
// Unknown id -> 404 CUSTOMER_NOT_FOUND; other dealer's customer -> 403 ACCESS_OTHER_DEALER.
// Service de clientes com escopo por concessionaria.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.repository.CustomerRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

  private final CustomerRepository repo;

  public CustomerService(CustomerRepository repo) {
    this.repo = repo;
  }

  public Customer get(String id, AuthenticatedUser user) {
    Customer customer =
        repo.findById(id)
            .orElseThrow(
                () -> ApiException.notFound("CUSTOMER_NOT_FOUND", "Cliente não encontrado."));
    requireAccess(id, user);
    return customer;
  }

  /** Throws 403 ACCESS_OTHER_DEALER when a dealer-scoped user asks for another dealer's data. */
  void requireAccess(String customerId, AuthenticatedUser user) {
    if (user.role().dealerScoped()
        && (user.dealerId() == null || !repo.isLinkedToDealer(customerId, user.dealerId()))) {
      throw ApiException.forbiddenOtherDealer();
    }
  }
}
