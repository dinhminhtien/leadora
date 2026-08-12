package com.novax.leadora.application.usecase.inventory;

/**
 * How confidently the CRM can say a room can be sold.
 *
 * <p>Three values rather than a boolean, because a two-way answer forces every "we don't know"
 * into either a false yes or a false no — and both are damaging. Saying yes when quota has run
 * out oversells. Saying no when the Reservation team simply has not published next month's quota
 * yet would stop Sales quoting anything beyond the published horizon, which would make the
 * feature unusable within a week of go-live.
 */
public enum RoomAvailabilityVerdict {

    /** Enough quota for what is being asked, and the figures are recent. Proceed silently. */
    OK,

    /**
     * Might be sellable; the CRM cannot tell. Quota is short, unpublished for these nights, or
     * too old to trust.
     *
     * <p>Quoting is allowed — a quotation is an offer, not a reservation, and the hotel may well
     * still have rooms outside our quota. Turning the offer into a booking is not, until the
     * Reservation team has confirmed against the real PMS.
     */
    NEEDS_CONFIRMATION,

    /**
     * Definitely not sellable: the room type does not exist, is inactive, or the hotel has closed
     * the date to us. No amount of confirmation changes it, so there is nothing to ask about.
     */
    BLOCKED;

    /** Worst of two verdicts — a stay is only as sellable as its weakest night. */
    public RoomAvailabilityVerdict andThen(RoomAvailabilityVerdict other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
