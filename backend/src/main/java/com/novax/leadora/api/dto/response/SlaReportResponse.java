package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.3 — SLA Compliance Report.
 *
 * <p>The central definition here: <b>a deadline that was missed stays missed.</b> Classifying by
 * current status put a breach that someone later resolved into the RESOLVED bucket and out of the
 * breach count, so the breach rate fell as the team cleaned up its backlog — a compliance metric
 * that rewards you for erasing the evidence. Every record is now judged on
 * {@code resolvedAt vs deadlineAt}, and {@link #breachedCount} covers late resolutions as well as
 * deadlines still running out.
 *
 * <p>See {@link SalesPerformanceReportResponse} for why the Jackson builder wiring is required.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = SlaReportResponse.SlaReportResponseBuilder.class)
public class SlaReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    // ── Aggregate summary ────────────────────────────────────────────────────────
    private int totalTracked;

    /** Every record that finished — on time, late, or with no usable timestamp. */
    private int resolvedCount;
    /** Finished within the deadline. */
    private int resolvedOnTimeCount;
    /** Finished, but after the deadline had already passed — still a breach. */
    private int resolvedLateCount;
    /**
     * Marked resolved but carrying no resolution timestamp, so the deadline outcome is unknowable.
     *
     * <p>Excluded from {@link #complianceRatePct} rather than assumed on time. Counting them as met
     * is the same flattery this report exists to remove — it credits compliance to records holding
     * no evidence of it. A non-zero value here is a data-quality signal worth chasing.
     */
    private int undeterminedCount;

    /** Deadline missed: late resolutions plus records still unresolved past their deadline. */
    private int breachedCount;
    /** Unresolved, past the deadline, still running. */
    private int openBreachedCount;

    /** Still running, past the warning threshold but inside the deadline. */
    private int warningCount;
    /** Still running and comfortably inside the deadline. */
    private int withinSlaCount;

    /** Still running, whatever the warning state — these have no outcome yet. */
    private int inFlightCount;

    /** breachedCount / totalTracked × 100 */
    private double breachRatePct;

    /**
     * resolvedOnTime / (resolvedOnTime + breached) × 100 — measured only over records whose
     * deadline outcome is knowable.
     *
     * <p>Two groups are held out of the denominator, both for the same reason: no evidence either
     * way. In-flight records have deadlines that have not arrived, and {@link #undeterminedCount}
     * records have no resolution timestamp. Folding either into the numerator moves the headline
     * with the size of the gap rather than with performance.
     */
    private double complianceRatePct;

    /** resolvedCount / (resolvedCount + openBreached) × 100 — how much of the load got closed out. */
    private double resolutionRatePct;

    /** Average hours from startedAt → resolvedAt for resolved records only. */
    private double avgProcessingHours;

    // ── Breakdown per activity type ───────────────────────────────────────────────
    private List<ActivityBreakdown> byActivityType;

    @JsonPOJOBuilder(withPrefix = "")
    public static class SlaReportResponseBuilder {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = ActivityBreakdown.ActivityBreakdownBuilder.class)
    public static class ActivityBreakdown {
        private String activityType;
        private String activityLabel;
        private int total;
        private int resolved;
        private int resolvedOnTime;
        private int breached;
        private int warning;
        private int withinSla;
        private double breachRatePct;
        private double complianceRatePct;
        private double avgProcessingHours;

        @JsonPOJOBuilder(withPrefix = "")
        public static class ActivityBreakdownBuilder {
        }
    }
}
