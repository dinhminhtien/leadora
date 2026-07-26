package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Lead counts for the summary tiles above the list (UC-8.2).
 *
 * <p><b>Why the server has to produce these.</b> The tiles used to be computed in the browser from
 * whatever page happened to be loaded, so "Qualified" meant "qualified among these ten rows". The
 * figure moved when the user turned the page or changed the sort, while the data had not changed at
 * all. Counting client-side cannot be fixed by trying harder — the client only ever holds one page,
 * and downloading every lead to count them would defeat the paging it just did.
 *
 * <p>Every field is counted over the <em>same filters</em> as the list beneath it and through the
 * same owner scope (BR-02), so the tiles and the table can never tell different stories, and a
 * sales rep's totals cover their own leads only.
 *
 * <p>Rates are returned already calculated. The client would otherwise have to re-derive them and
 * decide what to do about division by zero — a decision worth making once, here.
 */
@Getter
@Builder
public class LeadStatsResponse {

    /** Every lead matching the current filters and scope. */
    private long total;

    /** Reached {@code CONVERTED}: became a customer. */
    private long converted;

    /** Reached {@code LOST}: closed without a sale. */
    private long lost;

    /** Neither converted nor lost — still worth working on. */
    private long active;

    /** In {@code QUALIFIED}: the subset of active leads closest to a sale. */
    private long qualified;

    /**
     * {@code converted / total}, as a percentage rounded to one decimal, or {@code null} when there
     * are no leads.
     *
     * <p>Null rather than {@code 0.0} on an empty set, deliberately: "0.0%" reads as "we convert
     * nothing", which is a very different statement from "there is nothing to measure yet". The UI
     * shows a dash for null.
     *
     * <p>The denominator is <em>all</em> leads, not just the finished ones. Converted and lost
     * therefore do not add up to 100% — the remainder is work in progress, which is the honest
     * picture. Dividing by {@code converted + lost} would report 50% from a single win and a single
     * loss, and that number would look like a result rather than the coin-flip it is.
     */
    private Double convertedRate;

    /** {@code lost / total}, same convention as {@link #convertedRate}. */
    private Double lostRate;

    public static LeadStatsResponse of(long total, long converted, long lost, long qualified) {
        return LeadStatsResponse.builder()
                .total(total)
                .converted(converted)
                .lost(lost)
                .active(total - converted - lost)
                .qualified(qualified)
                .convertedRate(rate(converted, total))
                .lostRate(rate(lost, total))
                .build();
    }

    private static Double rate(long part, long total) {
        return total == 0 ? null : Math.round(part * 1000.0 / total) / 10.0;
    }
}
