package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum HandoverStatus {
    DRAFT,
    SUBMITTED,
    ACKNOWLEDGED,
    READY,

    /**
     * The arrival is over, however it ended — the guest checked out, cancelled, or never came.
     *
     * <p>The lifecycle previously had no end: nothing ever marked a handover finished, so every
     * record stayed nominally open forever and "how many arrivals are still outstanding" had no
     * answer. Hiding closed bookings from the Front Office list treats the symptom; this records
     * the fact.
     *
     * <p>Set by {@code CloseFinishedHandoversUseCase}, keyed off the booking reaching a state
     * outside {@link BookingStatus#LIVE_FOR_ARRIVAL} — deliberately not on CHECKED_IN, which is the
     * guest arriving at the desk and still very much the front desk's business.
     */
    CLOSED
}
