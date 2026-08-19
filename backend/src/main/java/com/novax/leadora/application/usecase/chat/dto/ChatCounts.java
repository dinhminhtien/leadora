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
 * <p>The derived figures are kept out of {@code byArea} on purpose. Each is a second count over
 * rows already counted under their own status, so leaving them in the map would inflate every
 * {@link #total} that includes them — an area would report more records than it holds.
 *
 * @param byArea            status buckets per area
 * @param overdueTasks      tasks past their deadline and not closed — derived, never stored (BR-17)
 * @param lowRatedFeedback  submitted feedback the customer scored 2 or less — derived likewise
 */
public record ChatCounts(Map<CrmArea, List<StatusBucket>> byArea, long overdueTasks,
                         long lowRatedFeedback) {

    public List<StatusBucket> of(CrmArea area) {
        return byArea.getOrDefault(area, List.of());
    }

    /** Total records in an area, across every status. */
    public long total(CrmArea area) {
        return of(area).stream().mapToLong(b -> b.count()).sum();
    }

    /** Records in one status; pass {@code SomeStatus.name()} so the enum stays the source. */
    public long count(CrmArea area, String status) {
        return of(area).stream()
                .filter(b -> b.status().equals(status))
                .mapToLong(b -> b.count()).sum();
    }

    /** Total value of the records in one status, zero when the area carries no amounts. */
    public BigDecimal amount(CrmArea area, String status) {
        return of(area).stream()
                .filter(b -> b.status().equals(status))
                .map(b -> b.amountOrZero())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }
}
