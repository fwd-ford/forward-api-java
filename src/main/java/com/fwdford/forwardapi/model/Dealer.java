// Minimal dealer projection used to validate references and label responses.
// Projecao minima de concessionaria para validar referencias e rotular respostas.
package com.fwdford.forwardapi.model;

public record Dealer(String id, String code, String name, boolean active) {}
