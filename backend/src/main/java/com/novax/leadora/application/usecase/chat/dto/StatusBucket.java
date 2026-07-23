package com.novax.leadora.application.usecase.chat.dto;

import java.math.BigDecimal;

/**
 * One status of one CRM area: how many records, and their total value where money applies.
 *
 * <p>The status is a plain string rather than an enum because these rows come from a single query
 * spanning seven tables, each with its own status type. Callers compare against {@code Xxx.name()}
 * so the enum still supplies the constant.
 *
 * @param amount null for areas that carry no monetary value (leads, tasks, customers)
 */
public record StatusBucket(String status, long count, BigDecimal amount) {

    public BigDecimal amountOrZero() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
