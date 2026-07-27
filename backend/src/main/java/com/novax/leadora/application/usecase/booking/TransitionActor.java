package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;

/**
 * Who is driving a booking status change. Each actor owns a different slice of the
 * booking lifecycle, so the allowed transitions differ per actor rather than per
 * endpoint — see {@link BookingStatusTransitionService}.
 *
 * <p>Replaces the earlier {@code boolean isFrontOffice} flag, which could only express
 * two actors and therefore left the Reservation team with no way to confirm a booking.
 */
public enum TransitionActor {

    /** Sales owns the deal, not the room: they may withdraw a booking, never confirm one. */
    SALES,

    /** The Reservation team decides whether rooms exist — they approve or reject. */
    RESERVATION,

    /** The arrival desk moves a confirmed booking through check-in and check-out. */
    FRONT_OFFICE;

    /**
     * Maps a user's role to the lane they act in. MANAGER/ADMIN act as RESERVATION
     * because they are the escalation path when the Reservation team does not answer in
     * time; anyone else is treated as SALES (the least-privileged lane).
     */
    public static TransitionActor fromUser(UserEntity user) {
        String role = user != null && user.getRole() != null && user.getRole().getRoleName() != null
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
        return switch (role) {
            case "RESERVATION", "MANAGER", "ADMIN" -> RESERVATION;
            case "FO", "FRONT_OFFICE" -> FRONT_OFFICE;
            default -> SALES;
        };
    }
}
