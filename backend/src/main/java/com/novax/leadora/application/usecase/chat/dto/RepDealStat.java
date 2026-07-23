package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;

import java.math.BigDecimal;

/**
 * Deal totals for one sales rep in one status — the raw rows behind the team-wide per-rep table.
 *
 * <p>Grouping by (rep, status) keeps the query free of CASE expressions, whose result type HQL
 * infers inconsistently. The result set is tiny (reps x statuses), so the pivot into one row per
 * rep is done in Java.
 */
public record RepDealStat(String repName, DealStatus status, Long count, BigDecimal totalRevenue) {

    public BigDecimal revenueOrZero() {
        return totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }
}
