package com.novax.leadora.application.usecase.chat.dto;

import java.time.OffsetDateTime;

/**
 * One SLA tracking record, flattened for the chat snapshot.
 *
 * <p>{@code sla_tracking} references its subject polymorphically ({@code entity_type} +
 * {@code entity_id}) and carries no assignee of its own, so the owner has to be resolved by
 * joining whichever parent table the row points at. That resolution happens once, in SQL, in
 * {@code ChatAggregateRepository} — doing it per row in Java (as the SLA breach scheduler does)
 * would be one query per record.
 *
 * @param assigneeName resolved owner, or null when the parent is unassigned or no longer exists
 */
public record SlaRow(String activityType, String entityType, String status,
                     OffsetDateTime deadlineAt, String assigneeName) {
}
