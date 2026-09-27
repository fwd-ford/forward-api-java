// Body of PATCH /api/v1/leads/{id}. Partial update: send status, notes or both.
// An empty string in notes clears them. The target status must be a valid transition
// from the current one (see LeadStatus), otherwise 409 LEAD_INVALID_TRANSITION.
// Corpo do PATCH de lead: atualizacao parcial de status e/ou notas.
package com.fwdford.forwardapi.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
    name = "LeadPatchRequest",
    description = "Atualização parcial de um lead (ao menos um campo).")
public record LeadPatchRequest(
    @Schema(
            description =
                "Novo status. Transições: new -> assigned|contacted|lost; assigned ->"
                    + " contacted|lost; contacted -> converted|lost. converted, lost e expired"
                    + " são finais.",
            example = "contacted",
            allowableValues = {"new", "assigned", "contacted", "converted", "lost", "expired"},
            nullable = true)
        @Pattern(
            regexp = "^(new|assigned|contacted|converted|lost|expired)$",
            message = "status inválido: use new, assigned, contacted, converted, lost ou expired")
        String status,
    @Schema(
            description = "Anotações do atendimento (máx. 2000 caracteres; \"\" limpa).",
            example = "Cliente atendeu e pediu retorno amanhã às 10h.",
            nullable = true)
        @Size(max = 2000, message = "notes deve ter no máximo 2000 caracteres")
        String notes) {}
