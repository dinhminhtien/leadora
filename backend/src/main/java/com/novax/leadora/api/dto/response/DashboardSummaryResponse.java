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
    private double activeLeadsGrowthPct; // WoW growth

    // ── Deal KPIs ─────────────────────────────────────────────────────────────
    private long activeDealsCount;
    private BigDecimal activeDealsValue;
    private BigDecimal weightedPipelineValue;
    private BigDecimal totalDealsValue;

    // ── Task KPIs ─────────────────────────────────────────────────────────────
    private long pendingTasksCount;
    private long overdueTasksCount;

    // ── SLA & Performance KPIs ────────────────────────────────────────────────
    private double slaComplianceRatePct;
    private double avgResponseHours;
    private BigDecimal avgDealSize;
    private double avgDealSizeGrowthPct; // MoM growth
    private double winRatePct;
    private String winRateBenchmarkLabel;

    // ── Sales Funnel ──────────────────────────────────────────────────────────
    private List<StageSummary> funnelStages;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private List<LeaderboardEntry> leaderboard;

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
