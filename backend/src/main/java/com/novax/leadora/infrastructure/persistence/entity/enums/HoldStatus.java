package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Lifecycle of a soft hold placed on room allotment while a quotation is being worked.
 *
 * <p>A hold is what stops two sales reps quoting the last room to different customers. It is not
 * a reservation: the hotel knows nothing about it, and it expires on its own if the quotation
 * goes nowhere.
 *
 * <p>The states exist to keep one invariant true (BR-47): <b>a sale is deducted from availability
 * exactly once</b>. Only {@link #ACTIVE} deducts. When a quotation becomes a booking the hold
 * moves to {@link #CONVERTED} in the same transaction that creates the booking, because from
 * that moment the booking itself is what consumes the quota — if both counted, the last room
 * would appear sold twice.
 *
 * <p>Terminal rows are never deleted, matching {@code RoomRequestStatus}: "why did this room
 * disappear for an afternoon" is a question the audit trail has to be able to answer.
 */
public enum HoldStatus {

    /** Holding rooms out of availability. The only state that counts against quota. */
    ACTIVE,

    /** Became a booking; the booking now consumes the quota instead. */
    CONVERTED,

    /** Given back deliberately — the quotation was closed, rejected, or revised away. */
    RELEASED,

    /** Given back by the expiry sweep because the quotation was never converted in time. */
    EXPIRED
}
