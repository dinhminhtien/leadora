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

    /**
     * Records whose deadline question is settled: on time, late, or overdue and still running. The
     * shared denominator of {@link #breachRatePct} and {@link #complianceRatePct}.
     */
    private int decidedCount;

    /**
     * breached / decided × 100 — the exact complement of {@link #complianceRatePct}.
     *
     * <p>This used to divide by {@link #totalTracked}, which put the two headline rates on different
     * denominators: on live data they read 29.4% and 67.4% for the same period, summing to 96.8%
     * with no explanation of the missing 3.2. The compliance rate had already been moved onto the
     * decided population for the reason that applies here too — counting records whose deadline has
     * not arrived dilutes the rate with the size of the open queue instead of measuring performance.
     *
     * <p>Null when {@link #decidedCount} is zero. A period whose every record is still inside its
     * deadline has no breach rate, and publishing {@code 0.0} there is indistinguishable from a
     * flawless month — the same unknown-as-zero flattery {@link #undeterminedCount} exists to stop.
     * It would also break the complement above, since the compliance rate would read 0.0 too.
     */
    private Double breachRatePct;

    /**
     * resolvedOnTime / (resolvedOnTime + breached) × 100 — measured only over records whose
     * deadline outcome is knowable. Null when none is.
     *
     * <p>Two groups are held out of the denominator, both for the same reason: no evidence either
     * way. In-flight records have deadlines that have not arrived, and {@link #undeterminedCount}
     * records have no resolution timestamp. Folding either into the numerator moves the headline
     * with the size of the gap rather than with performance.
     */
    private Double complianceRatePct;

    /**
     * resolvedCount / (resolvedCount + openBreached) × 100 — how much of the load got closed out.
     * Null when nothing has either closed or run past its deadline.
     */
    private Double resolutionRatePct;

    // Processing time is two measurements, not one.
    //
    // A record that finished has a known duration. A record still running has only a lower bound.
    // Averaging the finished ones alone is statistically clean but hides the queue: on live data
    // Booking Confirmation resolved in 24.4 hours on average while fifteen of its records had been
    // open for 455 hours — the report called it the fastest process in the system.

    /**
     * Average hours from startedAt → resolvedAt, over resolved records that carry both timestamps.
     * Null when none does — unknown, not instant.
     */
    private Double avgProcessingHours;

    /** How many records {@link #avgProcessingHours} averages over. */
    private int processingSamples;

    /** Average hours the still-running records have been open. Null when none are. */
    private Double avgOpenAgeHours;

    /** How many still-running records {@link #avgOpenAgeHours} covers. */
    private int openAgeSamples;

    /**
     * What this period could not establish, in the words a reader needs to discount a figure by —
     * same contract as {@link QuotationOutcomeReportResponse#getDataGaps()}.
     */
    private List<String> dataGaps;

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

        /**
         * Every record of this activity type in the period.
         *
         * <p>The columns below partition it exactly:
         * {@code resolvedOnTime + resolvedLate + undetermined + openBreached + warning + withinSla}.
         * They used to stop at a lumped {@code breached}, so a reader could not tell a late
         * resolution from a deadline still running out, and the rows could not be added back up to
         * the headline to check them.
         */
        private int total;

        private int resolved;
        private int resolvedOnTime;
        /** Finished after the deadline — part of {@link #breached}. */
        private int resolvedLate;
        /** Unresolved and past the deadline — the other part of {@link #breached}. */
        private int openBreached;
        /** Finished with no resolution timestamp; held out of both rates. */
        private int undetermined;
        /** resolvedLate + openBreached. */
        private int breached;
        private int warning;
        private int withinSla;
        /** The denominator of both rates below: resolvedOnTime + breached. */
        private int decided;
        /** Null when this activity type has nothing settled — unknown, not a clean sheet. */
        private Double breachRatePct;
        private Double complianceRatePct;
        /** Null when nothing of this type has been resolved with usable timestamps. */
        private Double avgProcessingHours;
        private int processingSamples;
        /** Null when nothing of this type is still running. */
        private Double avgOpenAgeHours;
        private int openAgeSamples;

        @JsonPOJOBuilder(withPrefix = "")
        public static class ActivityBreakdownBuilder {
        }
    }
}
