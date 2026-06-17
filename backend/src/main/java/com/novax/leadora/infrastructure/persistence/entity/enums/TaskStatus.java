package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_CUSTOMER,
    COMPLETED,
    CANCELLED,
    // OVERDUE is calculated and not persisted, but included for completeness
    OVERDUE
}
