package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;

import java.math.BigDecimal;

/** Booking count and total value for one status, computed by the database. */
public record BookingStatusAggregate(BookingStatus status, Long count, BigDecimal totalAmount) {

    public BigDecimal amountOrZero() {
        return totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }
}
