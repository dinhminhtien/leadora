package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;

import java.math.BigDecimal;

/**
 * Quotation count and total value for one status, computed by the database.
 *
 * <p>Quotations carry no assignee of their own; they are scoped through the deal they belong to,
 * so "my quotations" means the quotations of the deals assigned to the asker.
 */
public record QuotationStatusAggregate(QuotationStatus status, Long count, BigDecimal totalAmount) {

    public BigDecimal amountOrZero() {
        return totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }
}
