package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;

/** One row of "how many tasks are in each status", computed by the database. */
public record TaskStatusCount(TaskStatus status, Long count) {
}
