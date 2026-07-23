package com.novax.leadora.application.usecase.chat.dto;

import com.novax.leadora.application.usecase.chat.intent.CrmArea;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Every CRM area's status breakdown for one scope, as gathered by a single query.
 *
 * <p>Areas with no matching records are simply absent from the map; the accessors return zero for
 * them, so callers never have to distinguish "no rows" from "not asked for".
 *
 * @param byArea       status buckets per area
 * @param overdueTasks tasks past their deadline and not closed — derived, never stored (BR-17)
 */
public record ChatCounts(Map<CrmArea, List<StatusBucket>> byArea, long overdueTasks) {

    public List<StatusBucket> of(CrmArea area) {
        return byArea.getOrDefault(area, List.of());
    }

    /** Total records in an area, across every status. */
    public long total(CrmArea area) {
        return of(area).stream().mapToLong(StatusBucket::count).sum();
    }

    /** Records in one status; pass {@code SomeStatus.name()} so the enum stays the source. */
    public long count(CrmArea area, String status) {
        return of(area).stream()
                .filter(b -> b.status().equals(status))
                .mapToLong(StatusBucket::count).sum();
    }

    /** Total value of the records in one status, zero when the area carries no amounts. */
    public BigDecimal amount(CrmArea area, String status) {
        return of(area).stream()
                .filter(b -> b.status().equals(status))
                .map(StatusBucket::amountOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
