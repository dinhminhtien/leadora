package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum EntityType {
    LEAD,
    DEAL,
    TASK,
    QUOTATION,
    BOOKING,
    PAYMENT,
    USER,
    /** Operational / arrival handover (UC-20.x, UC-22.x). */
    HANDOVER,
    FEEDBACK
}
