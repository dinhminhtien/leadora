package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * Payment count and total amount for one status, computed by the database.
 *
 * <p>Payments hang off a booking, so they are scoped through that booking's assignee.
 */
public record PaymentStatusAggregate(PaymentStatus status, Long count, BigDecimal totalAmount) {

    public BigDecimal amountOrZero() {
        return totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }
}
