package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * UC-14.x / BR-15 — Reasons why Reservation staff might reject a quotation
 * that is in {@code RESERVATION_PENDING} status.
 */
public enum ReservationRejectReason {
    /** No rooms of the requested type are available for the specified dates. */
    NO_ROOM_AVAILABLE,

    /** The requested room is under maintenance/renovation. */
    ROOM_UNDER_MAINTENANCE,

    /** The hotel is overbooked for these dates. */
    OVERBOOKED,

    /** The quotation has invalid pricing, dates, or other configuration errors. */
    INVALID_QUOTATION,

    /** The customer contacted the hotel and requested to cancel or is no longer interested. */
    CUSTOMER_REQUEST_NO_LONGER_AVAILABLE,

    /** The requested room configuration/combination is invalid. */
    INVALID_ROOM_CONFIGURATION,

    /** Any other reason not covered above. */
    OTHER
}
