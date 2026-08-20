package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Deal counts and totals for the summary tiles above the Deals list.
 *
 * <p>These used to be computed in the browser from whichever page of deals happened to be loaded,
 * so "Pipeline Value" meant "pipeline value of the ten rows on this page" and moved when the user
 * turned the page while the underlying data had not changed. Counted here over the whole filtered
 * set instead, the same way {@code LeadStatsResponse} fixed the equivalent problem for leads.
 *
 * <p>Computed over the <em>same filters and owner scope</em> as {@code GetDealListUseCase}, so the
 * tiles and the table beneath them can never disagree.
 */
@Getter
@Builder
public class DealStatsResponse {

    /** Deals still open (status OPEN), matching the current filters. */
    private long activeCount;

    /** Sum of {@code expectedRevenue} over those same open deals. */
    private BigDecimal activeValue;

    /** Sum of {@code expectedRevenue} over deals WON, matching the current filters. */
    private BigDecimal wonValue;

    /**
     * {@code won / (won + lost)} as a percentage rounded to one decimal, or {@code null} when
     * nothing has closed yet. Null rather than {@code 0.0} — no closed deals is a different
     * statement from "we win nothing" and should not read as one.
     */
    private Double winRate;

    public static DealStatsResponse of(long activeCount, BigDecimal activeValue,
                                       long wonCount, BigDecimal wonValue, long lostCount) {
        long totalClosed = wonCount + lostCount;
        Double winRate = totalClosed == 0
                ? null
                : Math.round(wonCount * 1000.0 / totalClosed) / 10.0;

        return DealStatsResponse.builder()
                .activeCount(activeCount)
                .activeValue(activeValue != null ? activeValue : BigDecimal.ZERO)
                .wonValue(wonValue != null ? wonValue : BigDecimal.ZERO)
                .winRate(winRate)
                .build();
    }
}
