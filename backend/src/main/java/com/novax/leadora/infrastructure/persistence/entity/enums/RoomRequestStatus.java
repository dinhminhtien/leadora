package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Lifecycle of a room-availability request raised by Sales to the Reservation team.
 *
 * <p>This CRM does not own room inventory — the Reservation team checks the hotel's
 * real PMS outside this system. A request therefore records a question and the
 * answer a human gave, never a number the CRM computed itself.
 *
 * <p>{@code SUPERSEDED} is set when the quotation's room type, dates or quantity
 * change after the answer was given: the old answer no longer applies, so a fresh
 * request must be raised (mirrors the quotation's own revision lineage).
 */
public enum RoomRequestStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    SUPERSEDED
}
