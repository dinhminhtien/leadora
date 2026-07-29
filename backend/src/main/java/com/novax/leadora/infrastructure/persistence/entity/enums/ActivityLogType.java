package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum ActivityLogType {
    LEAD_CREATED,
    LEAD_STATUS_UPDATED,
    LEAD_CONVERTED,
    DEAL_CREATED,
    DEAL_STAGE_UPDATED,
    DEAL_AUTO_WON,
    QUOTATION_CREATED,
    QUOTATION_SUBMITTED,
    QUOTATION_APPROVED,
    QUOTATION_REJECTED,
    BOOKING_CREATED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    TASK_CREATED,
    TASK_COMPLETED,
    LEAD_UPDATED,
    DEAL_UPDATED,
    QUOTATION_UPDATED,
    BOOKING_UPDATED,

    // Handover (UC-20.x Sales/Reservation, UC-22.3 Front Office). The module previously wrote only
    // to the log file, so BR-37's "old value / new value / actor / target" was not queryable and
    // the POST-2 audit requirement of UC-22.3 was unmet.
    HANDOVER_SUBMITTED,
    HANDOVER_READINESS_UPDATED
}
