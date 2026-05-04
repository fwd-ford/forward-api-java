// Query parameters accepted by the leads listing endpoint.
// Parametros aceitos na listagem de leads.
package com.fwdford.forwardapi.model;

public record LeadFilter(String dealerId, String status, int limit) {}
