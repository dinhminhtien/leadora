package com.novax.leadora.infrastructure.persistence.entity.enums;

public enum QuotationStatus {
    DRAFT,
    PENDING_APPROVAL,
    SENT,
    APPROVED,
    REJECTED,
    EXPIRED,
    CLOSED,
    CONVERTED,
    PENDING_REVISION,
    ACCEPTED,
    INTERESTED,
    SUPERSEDED,
    PENDING_CUSTOMER_RESPONSE,
    ACCEPTED_BY_CUSTOMER,
    BOOKING_REQUEST,
    /**
     * UC-14.x: Customer completed OTP verification. Quotation is awaiting
     * Reservation staff availability check before a Booking is created.
     * Set by CustomerAcceptQuotationUseCase (called from ConfirmQuotationOtpUseCase).
     */
    RESERVATION_PENDING,
    /**
     * UC-14.x: Reservation staff determined no rooms are available for the
     * requested dates/type. Quotation is returned to Sales for follow-up
     * (revise with alternative dates/rooms, or close the deal).
     */
    RESERVATION_REJECTED
}

