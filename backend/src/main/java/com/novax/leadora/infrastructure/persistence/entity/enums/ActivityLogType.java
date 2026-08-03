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

    // Security / Identity events
    USER_LOGGED_IN,
    USER_LOGGED_OUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    USER_ACCOUNT_CREATED,
    USER_ACCOUNT_UPDATED,

    // Security / Access control events
    LOGIN_FAILED,
    ACCESS_DENIED_EVENT,
    INVALID_TOKEN_ACCESS,
    FEEDBACK_LINK_EXPIRED,

    // Handover (UC-20.x Sales/Reservation, UC-22.3 Front Office). The module previously wrote only
    // to the log file, so BR-37's "old value / new value / actor / target" was not queryable and
    // the POST-2 audit requirement of UC-22.3 was unmet.
    HANDOVER_SUBMITTED,
    HANDOVER_READINESS_UPDATED
}
