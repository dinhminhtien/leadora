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
 *
 * <p>{@code CANCELLED} is Sales withdrawing the question before Reservation answered
 * (UC-26.4). Distinct from {@code SUPERSEDED}, which the system sets when the question
 * itself changed, and from {@code REJECTED}, which is an answer. Like the other two
 * terminal states it is never deleted — the row stays for the audit trail — but it is
 * not an answer, so it must not speak for the quotation.
 */
public enum RoomRequestStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    SUPERSEDED,
    CANCELLED;

    /**
     * States that no longer represent a live question or a usable answer, and so are
     * skipped when resolving the request that currently speaks for a quotation.
     *
     * <p>Excluding {@code CANCELLED} here is what makes re-submission (UC-26.5) work:
     * after a cancellation the next-newest confirmed or rejected answer — or nothing at
     * all — becomes current again, rather than the withdrawn request masking it.
     */
    public static java.util.List<RoomRequestStatus> notSpeakingForQuotation() {
        return java.util.List.of(SUPERSEDED, CANCELLED);
    }
}
