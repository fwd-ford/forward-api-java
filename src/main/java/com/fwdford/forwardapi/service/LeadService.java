// Lead service. Dealer scoping: ATENDENTE and GESTOR always see only their own dealer's
// leads (asking for another dealer_id is 403 ACCESS_OTHER_DEALER); ADMIN and SERVICE see
// every dealer and may filter by dealer_id.
// Service de leads com escopo por concessionaria.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.repository.LeadRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LeadService {

  private final LeadRepository repo;

  public LeadService(LeadRepository repo) {
    this.repo = repo;
  }

  public List<Lead> list(LeadFilter filter, AuthenticatedUser user) {
    LeadFilter effective = filter;
    if (user.role().dealerScoped()) {
      if (filter.dealerId() != null
          && !filter.dealerId().isEmpty()
          && !filter.dealerId().equalsIgnoreCase(user.dealerId())) {
        throw ApiException.forbiddenOtherDealer();
      }
      effective = new LeadFilter(user.dealerId(), filter.status(), filter.limit());
    }
    return repo.list(effective);
  }
}
