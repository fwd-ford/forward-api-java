// Query parameters accepted by the leads listing endpoint (already validated).
// Empty/null values mean "no filter".
// Parametros aceitos na listagem de leads (ja validados); vazio = sem filtro.
package com.fwdford.forwardapi.model;

public record LeadFilter(String dealerId, String status, String priority, int limit, int offset) {

  public LeadFilter withDealer(String newDealerId) {
    return new LeadFilter(newDealerId, status, priority, limit, offset);
  }
}
