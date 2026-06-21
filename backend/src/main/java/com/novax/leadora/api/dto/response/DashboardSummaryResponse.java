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

    // ── Deal KPIs ─────────────────────────────────────────────────────────────
    private long activeDealsCount;
    private BigDecimal activeDealsValue;
    private BigDecimal weightedPipelineValue;
    private BigDecimal totalDealsValue;

    // ── Task KPIs ─────────────────────────────────────────────────────────────
    private long pendingTasksCount;
    private long overdueTasksCount;

    // ── Sales Funnel ──────────────────────────────────────────────────────────
    private List<StageSummary> funnelStages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StageSummary {
        private String stage;
        private long count;
        private BigDecimal value;
    }
}
