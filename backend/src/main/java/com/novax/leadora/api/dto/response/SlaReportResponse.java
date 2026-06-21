package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SlaReportResponse {

    private String fromDate;
    private String toDate;

    // ── Aggregate summary ────────────────────────────────────────────────────────
    private int totalTracked;
    private int resolvedCount;
    private int breachedCount;
    private int warningCount;
    private int withinSlaCount;

    /** breachedCount / totalTracked × 100 */
    private double breachRatePct;

    /** (withinSlaCount + resolvedCount) / totalTracked × 100 */
    private double complianceRatePct;

    /** resolvedCount / (resolvedCount + breachedCount) × 100 */
    private double resolutionRatePct;

    /** Average hours from startedAt → updatedAt for RESOLVED records only */
    private double avgProcessingHours;

    // ── Breakdown per activity type ───────────────────────────────────────────────
    private List<ActivityBreakdown> byActivityType;

    @Data
    @Builder
    public static class ActivityBreakdown {
        private String activityType;
        private String activityLabel;
        private int total;
        private int resolved;
        private int breached;
        private int warning;
        private int withinSla;
        private double breachRatePct;
        private double avgProcessingHours;
    }
}
