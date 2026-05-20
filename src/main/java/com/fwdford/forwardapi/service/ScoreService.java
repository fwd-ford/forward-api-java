// Score service. Scores are never exposed to end users; only analyst, admin, dealer.
// Service de score: nao exposto ao usuario final; apenas analyst/admin/dealer.
package com.fwdford.forwardapi.service;

import org.springframework.stereotype.Service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.repository.ScoreRepository;

@Service
public class ScoreService {

  private final ScoreRepository repo;

  public ScoreService(ScoreRepository repo) {
    this.repo = repo;
  }

  public ChurnScore getCurrent(String customerId, String callerRole) {
    if (!"analyst".equals(callerRole)
        && !"admin".equals(callerRole)
        && !"dealer".equals(callerRole)) {
      throw ApiException.forbidden();
    }
    return repo.findCurrentByCustomer(customerId).orElseThrow(() -> ApiException.notFound("score"));
  }
}
