package com.novax.leadora.api.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    // ── Lead KPIs ─────────────────────────────────────────────────────────────
    private long activeLeadsCount;
    private long totalLeadsCount;
    /**
     * Week-on-week change, or {@code null} when last week held no leads.
     *
     * <p>Nullable on purpose, and the same reasoning applies to every rate below. A growth
     * percentage measured against an empty base is not zero and it is not 12.5 - it does not
     * exist, and the tile has to say so with a dash. These fields used to carry invented
     * constants for exactly the case where there was nothing to measure, so a clean demo
     * account displayed a full scorecard of numbers that were never computed from anything.
     */
    private Double activeLeadsGrowthPct;

    // ── Deal KPIs ─────────────────────────────────────────────────────────────
    private long activeDealsCount;
    private BigDecimal activeDealsValue;
    private BigDecimal weightedPipelineValue;
    private BigDecimal totalDealsValue;

    // ── Task KPIs ─────────────────────────────────────────────────────────────
    private long pendingTasksCount;
    private long overdueTasksCount;

    // ── SLA & Performance KPIs ────────────────────────────────────────────────
    /** Share of SLA records met, or {@code null} when none is being tracked in scope. */
    private Double slaComplianceRatePct;
    /** Mean hours to resolve, or {@code null} when nothing has been resolved yet. */
    private Double avgResponseHours;
    private BigDecimal avgDealSize;
    /**
     * Month-on-month change in average deal size, or {@code null} when the previous month held
     * no deals to compare against. Previously the literal {@code 8.0}, returned unconditionally -
     * it was never computed, so it read as a trend while being a constant.
     */
    private Double avgDealSizeGrowthPct;
    /** Won / closed, or {@code null} when no deal has closed yet in scope. */
    private Double winRatePct;
    /**
     * A word for the win rate above, or {@code null} when there is no win rate.
     *
     * <p>Describes this company's own number against fixed thresholds. It used to read
     * "Top 10%", which claims a rank against an industry benchmark the system does not hold
     * and cannot compute.
     */
    private String winRateBenchmarkLabel;

    // ── Sales Funnel ──────────────────────────────────────────────────────────
    private List<StageSummary> funnelStages;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private List<LeaderboardEntry> leaderboard;

    // ── Monthly Forecasts ─────────────────────────────────────────────────────
    private List<MonthlyForecast> monthlyForecasts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyForecast {
        private String month;
        private BigDecimal value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StageSummary {
        private String stage;
        private long count;
        private BigDecimal value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaderboardEntry {
        private String name;
        private long actionCount;
    }
}
