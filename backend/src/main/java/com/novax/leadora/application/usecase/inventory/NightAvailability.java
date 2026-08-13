package com.novax.leadora.application.usecase.inventory;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * What one room type looks like on one night, as last synchronized from the Reservation side.
 *
 * <p>Carries the inputs alongside the answer, because "5 left" means different things to a sales
 * rep depending on whether it is 5 of a quota of 5 or 5 of a quota of 20 — the first is worth
 * asking the Reservation team to extend, the second is not.
 *
 * <p>{@code allotted} and {@code available} are {@code null} when the Reservation team has not
 * published this night. That is deliberately distinct from zero (BR-48): zero means the quota is
 * spent, null means nobody has said yet. Collapsing null to zero would make an unpublished month
 * look like a sold-out one.
 *
 * <p>There is no "held" term. Leadora used to subtract soft holds it placed on the quota itself,
 * which is exactly the "hold" Report 1 FE-19 and LI-02 put on the Reservation side of the
 * boundary. What remains is reference information: the quota the Reservation team published, less
 * what this CRM has already committed against it.
 */
public record NightAvailability(

        LocalDate date,

        /** Rooms granted for this night; {@code null} when unpublished. */
        Integer allotted,

        /** Rooms consumed by bookings that still occupy a room. */
        int booked,

        /** {@code allotted - booked}, floored at zero; {@code null} when unpublished. */
        Integer available,

        /** The hotel has closed this date to us — not the same as running out of quota. */
        boolean closed,

        /** When the Reservation team last vouched for the quota; {@code null} when unpublished. */
        OffsetDateTime asOf,

        /** {@code asOf} is older than the configured staleness threshold (BR-50). */
        boolean stale) {

    public boolean published() {
        return allotted != null;
    }

    /** An unpublished night: no quota on record, so no answer — not an answer of zero. */
    public static NightAvailability unpublished(LocalDate date, int booked) {
        return new NightAvailability(date, null, booked, null, false, null, false);
    }
}
