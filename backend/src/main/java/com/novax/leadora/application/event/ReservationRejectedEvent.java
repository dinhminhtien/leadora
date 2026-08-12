package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReservationRejectReason;

/**
 * Triggered when Reservation staff rejects a quotation in RESERVATION_PENDING.
 * The quotation status transitions to RESERVATION_REJECTED.
 */
public record ReservationRejectedEvent(QuotationEntity quotation, ReservationRejectReason reason, String note) {
}
