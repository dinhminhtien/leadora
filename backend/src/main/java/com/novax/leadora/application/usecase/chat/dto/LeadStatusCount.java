package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;

/**
 * One row of "how many leads are in each status", computed by the database.
 *
 * <p>Part of the chat assistant's read-only snapshot: the counts the assistant quotes are produced
 * by {@code GROUP BY} rather than by loading every lead into the JVM and counting there.
 */
public record LeadStatusCount(LeadStatus status, Long count) {
}
