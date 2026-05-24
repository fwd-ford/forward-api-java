// Service event resource returned by POST /api/v1/service-events.
// Recurso de evento de servico retornado por POST /api/v1/service-events.
package com.fwdford.forwardapi.model;

import java.time.OffsetDateTime;

public record ServiceEvent(
    String id,
    String vin,
    String dealerId,
    String orderType,
    String status,
    OffsetDateTime scheduledAt,
    Integer mileageKm,
    Integer maintenanceNumber,
    String mainSource) {}
