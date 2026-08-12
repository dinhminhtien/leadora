package com.novax.leadora.application.usecase.inventory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What one room type looks like across a whole stay.
 *
 * <p>{@code availableForStay} is the <b>minimum</b> across the nights, not a sum or an average: a
 * guest staying three nights needs the same room on all three, so the tightest night decides. A
 * stay whose nights read 5, 0, 5 has zero available, not 3.33.
 *
 * <p>{@code limitingDates} is what turns a refusal into a sale. "Deluxe is full" ends the
 * conversation; "Deluxe is full on the 11th, but the 12th–15th are open" gives the rep something
 * to offer.
 */
public record StayAvailability(

        java.util.UUID productId,

        String roomTypeName,

        /** Minimum across the stay's nights; {@code null} when any night is unpublished. */
        Integer availableForStay,

        /** The nights that produced the minimum — where the stay is actually constrained. */
        List<LocalDate> limitingDates,

        /** Nights the hotel has closed to us. Any entry here makes the stay unsellable. */
        List<LocalDate> closedDates,

        /** Nights with no published quota. Not the same as sold out (BR-48). */
        List<LocalDate> unpublishedDates,

        /** Any published night is older than the staleness threshold (BR-50). */
        boolean stale,

        /** Oldest {@code as_of} across the stay — what the UI shows as "updated at". */
        OffsetDateTime oldestAsOf) {

    /** True when the stay can be covered without asking the Reservation team. */
    public boolean canCover(int requested) {
        return closedDates.isEmpty()
                && unpublishedDates.isEmpty()
                && !stale
                && availableForStay != null
                && availableForStay >= requested;
    }
}
