// Churn score service. Scores are internal analytics: every staff profile may read them,
// but ATENDENTE and GESTOR only for customers of their own dealer (same rule as
// CustomerService). Missing customer -> 404 CUSTOMER_NOT_FOUND; customer without a
// current score -> 404 SCORE_NOT_FOUND.
// Service de score de churn com escopo por concessionaria.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.repository.ScoreRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class ScoreService {

  private final ScoreRepository repo;
  private final CustomerService customers;

  public ScoreService(ScoreRepository repo, CustomerService customers) {
    this.repo = repo;
    this.customers = customers;
  }

  public ChurnScore getCurrent(String customerId, AuthenticatedUser user) {
    customers.get(customerId, user);
    return repo.findCurrentByCustomer(customerId)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "SCORE_NOT_FOUND", "Nenhum score de churn calculado para este cliente."));
  }
}
