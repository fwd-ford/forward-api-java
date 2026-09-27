// Lead service: listing, detail and partial update with the status state machine.
// Dealer scoping: ATENDENTE and GESTOR only see and change leads of their own dealer
// (another dealer's lead -> 403 ACCESS_OTHER_DEALER); ADMIN and SERVICE see every dealer
// and may filter by dealer_id. Every PATCH is written to audit_log in the same transaction.
// Service de leads: listagem, detalhe e PATCH com maquina de estados e auditoria.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.model.LeadStatus;
import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.repository.LeadRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.web.dto.LeadPatchRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

  private static final Logger log = LoggerFactory.getLogger(LeadService.class);

  private final LeadRepository repo;
  private final AuditService audit;

  public LeadService(LeadRepository repo, AuditService audit) {
    this.repo = repo;
    this.audit = audit;
  }

  public PageResult<Lead> list(LeadFilter filter, AuthenticatedUser user) {
    LeadFilter effective = filter;
    if (user.role().dealerScoped()) {
      if (filter.dealerId() != null
          && !filter.dealerId().isEmpty()
          && !filter.dealerId().equalsIgnoreCase(user.dealerId())) {
        throw ApiException.forbiddenOtherDealer();
      }
      effective = filter.withDealer(user.dealerId());
    }
    return new PageResult<>(repo.list(effective), repo.count(effective));
  }

  public Lead get(UUID id, AuthenticatedUser user) {
    Lead lead = load(id);
    requireDealer(lead, user);
    return lead;
  }

  @PreAuthorize("hasAnyRole('ATENDENTE', 'GESTOR', 'ADMIN', 'SERVICE')")
  @Transactional
  public Lead patch(UUID id, LeadPatchRequest req, AuthenticatedUser user) {
    if (req.status() == null && req.notes() == null) {
      throw ApiException.badRequest(
          "EMPTY_PATCH", "Informe ao menos um campo para atualizar: status ou notes.");
    }
    Lead current = load(id);
    requireDealer(current, user);

    LeadStatus from =
        LeadStatus.fromValue(current.status())
            .orElseThrow(() -> new IllegalStateException("unknown lead status in database"));
    String newStatus = null;
    if (req.status() != null) {
      LeadStatus to =
          LeadStatus.fromValue(req.status())
              .orElseThrow(() -> ApiException.badRequest("VALIDATION_FAILED", "status inválido."));
      if (to != from) {
        if (!from.canTransitionTo(to)) {
          throw ApiException.conflict(
              "LEAD_INVALID_TRANSITION",
              "Transição de status inválida: "
                  + from.value()
                  + " -> "
                  + to.value()
                  + (from.isTerminal() ? " (o lead já está em um status final)." : "."));
        }
        newStatus = to.value();
      }
    }
    boolean notesSet = req.notes() != null;
    String notes = notesSet && !req.notes().isBlank() ? req.notes().trim() : null;

    repo.update(id, newStatus, notesSet, notes);

    Map<String, Object> changes = new LinkedHashMap<>();
    if (newStatus != null) {
      changes.put("status_from", from.value());
      changes.put("status_to", newStatus);
    }
    if (notesSet) {
      changes.put("notes_changed", true);
    }
    audit.record(user, "lead.updated", "lead", id.toString(), changes);
    log.info(
        "lead_updated id={} status_from={} status_to={} actor={}",
        id,
        from.value(),
        newStatus,
        user.id());
    return load(id);
  }

  private Lead load(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> ApiException.notFound("LEAD_NOT_FOUND", "Lead não encontrado."));
  }

  private static void requireDealer(Lead lead, AuthenticatedUser user) {
    if (!user.canAccessDealer(lead.dealerId())) {
      throw ApiException.forbiddenOtherDealer();
    }
  }
}
