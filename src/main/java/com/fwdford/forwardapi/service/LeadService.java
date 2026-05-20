// Lead service. Thin pass-through to the repository for Sprint 1 read endpoints.
// Service de lead: repassa para o repositorio nos endpoints de leitura da Sprint 1.
package com.fwdford.forwardapi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.repository.LeadRepository;

@Service
public class LeadService {

  private final LeadRepository repo;

  public LeadService(LeadRepository repo) {
    this.repo = repo;
  }

  public List<Lead> list(LeadFilter filter) {
    return repo.list(filter);
  }
}
