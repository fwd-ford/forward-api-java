// Customer service. Sprint 1 relaxes RBAC: any authenticated caller can read
// any customer record. The mobile app needs broad read access for the Lead
// Detail flow (customer name, phone for tel: action). Sprint 2 will tighten
// to dealer-scoped access via the leads table.
// Service de customer: no Sprint 1 qualquer caller autenticado le qualquer
// customer (necessario pro app mobile na tela Lead Detail). Sprint 2
// restringe por dealer via tabela de leads.
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
    if (callerSub == null || callerSub.isBlank()) {
      throw ApiException.forbidden();
    }
    return repo.findById(id).orElseThrow(() -> ApiException.notFound("customer"));
  }
}
