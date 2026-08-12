package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import java.util.UUID;

/**
 * Triggered when Reservation staff approves a quotation in RESERVATION_PENDING.
 * The booking is created and the quotation status transitions to BOOKING_REQUEST.
 */
public record ReservationApprovedEvent(QuotationEntity quotation, UUID bookingId) {
}
