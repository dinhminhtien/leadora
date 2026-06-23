package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Task Status Enum - Represents the business state of a task.
 * OVERDUE is NOT persisted; it is calculated dynamically based on:
 * - task.status == OPEN
 * - task.endTime < now()
 */
public enum TaskStatus {
    OPEN,
    COMPLETED,
    CANCELLED
}
