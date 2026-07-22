package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;

import java.math.BigDecimal;

/**
 * Deal count and total expected revenue for one status, computed by the database.
 *
 * <p>{@code totalRevenue} is null when every deal in the bucket has a null revenue — SUM over an
 * all-null column returns null rather than zero, so callers must treat null as zero.
 */
public record DealStatusAggregate(DealStatus status, Long count, BigDecimal totalRevenue) {

    public BigDecimal revenueOrZero() {
        return totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }
}
