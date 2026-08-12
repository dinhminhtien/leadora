package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;

/**
 * Triggered when a customer completes OTP verification.
 * Quotation status transitions to RESERVATION_PENDING.
 */
public record QuotationAcceptedByCustomerEvent(QuotationEntity quotation) {
}
