package com.novax.leadora.application.usecase.inventory;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * What one room type looks like on one night.
 *
 * <p>Carries the three inputs alongside the answer, because "5 left" means different things to a
 * sales rep depending on whether it is 5 of a quota of 5 or 5 of a quota of 20 — the first is
 * worth asking the Reservation team to extend, the second is not.
 *
 * <p>{@code allotted} and {@code available} are {@code null} when the Reservation team has not
 * published this night. That is deliberately distinct from zero (BR-48): zero means the quota is
 * spent, null means nobody has said yet. Collapsing null to zero would make an unpublished month
 * look like a sold-out one.
 */
public record NightAvailability(

        LocalDate date,

        /** Rooms granted for this night; {@code null} when unpublished. */
        Integer allotted,

        /** Rooms consumed by bookings that still occupy a room. */
        int booked,

        /** Rooms consumed by live quotation holds. */
        int held,

        /** {@code allotted - booked - held}, floored at zero; {@code null} when unpublished. */
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
    public static NightAvailability unpublished(LocalDate date, int booked, int held) {
        return new NightAvailability(date, null, booked, held, null, false, null, false);
    }
}
