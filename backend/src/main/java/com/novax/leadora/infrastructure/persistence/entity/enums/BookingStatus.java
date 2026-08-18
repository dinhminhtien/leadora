package com.novax.leadora.infrastructure.persistence.entity.enums;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW,
    REJECTED;

    /**
     * The states in which a guest arrival is still worth preparing for, and therefore in which an
     * operational handover may still be read on the arrival desk and written to (UC-22.1 step 3,
     * BR-44).
     *
     * <p>Lives on the enum because three layers need the same answer — the JPA specification that
     * filters the list, the Front Office readiness update, and the Sales-side handover update. It
     * had drifted into three separate private copies, which is how the Sales copy ended up missing
     * {@code NO_SHOW} and {@code PENDING}.
     *
     * <p>Deliberately a whitelist: a status added to this enum later is excluded until somebody
     * decides it belongs, rather than silently appearing on the front desk.
     */
    public static final Set<BookingStatus> LIVE_FOR_ARRIVAL =
            EnumSet.of(CONFIRMED, CHECKED_IN);

    /** @see #LIVE_FOR_ARRIVAL */
    public boolean isLiveForArrival() {
        return LIVE_FOR_ARRIVAL.contains(this);
    }

    /**
     * The states in which a booking still occupies a room, and so must be subtracted from room
     * allotment (BR-47).
     *
     * <p>{@code PENDING} counts. Reservation has not confirmed it yet, but Sales has promised the
     * room to a customer, and quietly reselling it is worse than briefly understating what is
     * left. The cost is that a booking left pending indefinitely sits on quota — which is a
     * reason to chase stale bookings, not a reason to stop counting them.
     *
     * <p>{@code CHECKED_OUT} and {@code NO_SHOW} are excluded because the nights they covered are
     * in the past: nothing is freed by releasing them, since those nights can no longer be sold.
     *
     * <p>Lives here for the same reason as {@link #LIVE_FOR_ARRIVAL} — the availability grid, the
     * quotation gate and the hold logic all need the same answer, and three private copies is how
     * they drift apart.
     */
    public static final Set<BookingStatus> CONSUMING_INVENTORY =
            EnumSet.of(PENDING, CONFIRMED, CHECKED_IN);

    /** @see #CONSUMING_INVENTORY */
    public boolean isConsumingInventory() {
        return CONSUMING_INVENTORY.contains(this);
    }
}
