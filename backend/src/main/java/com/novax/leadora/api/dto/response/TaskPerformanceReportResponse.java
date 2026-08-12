package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.2 — Follow-up Task Performance Report.
 *
 * <p>Three questions, kept apart because they have different answers and different time axes:
 *
 * <ul>
 *   <li><b>Raised</b> — how much follow-up work came in, counted by creation date.</li>
 *   <li><b>Resolved</b> — how much was finished and whether it was finished in time, counted by
 *       completion date.</li>
 *   <li><b>Open queue</b> — what is running late right now, measured against the clock and
 *       belonging to no period at all.</li>
 * </ul>
 *
 * <p>The report previously had one figure, {@code overdue}, that silently meant only the third.
 * A task finished three days late left the OPEN status and therefore stopped being counted, so a
 * period in which every task was late scored 0% overdue and the number improved as the backlog was
 * cleared rather than as the team became punctual.
 *
 * <p>See {@link SalesPerformanceReportResponse} for why the Jackson builder wiring is required.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = TaskPerformanceReportResponse.TaskPerformanceReportResponseBuilder.class)
public class TaskPerformanceReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    /** The business time zone day boundaries were resolved in. */
    private String timezone;

    // ── Raised in the period (created_at) ────────────────────────────────────
    private long totalTasks;
    /** Of the tasks raised in this period, how many have since been completed. */
    private long completed;
    private long open;
    private long cancelled;
    /** % completed / totalTasks — one population compared with itself. */
    private double completionRate;

    private long priorityLow;
    private long priorityMedium;
    private long priorityHigh;

    /** Follow-up work by kind: call, email, meeting, site visit. */
    private List<CountRow> activityMix;

    /** Tasks attached to no lead, customer or deal — effort that never reaches the pipeline. */
    private long orphanTasks;
    private double orphanRate;

    // ── Resolved in the period (completed_at) ────────────────────────────────

    /** Tasks finished during the period, whenever they were raised. */
    private long resolvedTotal;
    private long resolvedOnTime;
    /** Finished, but after the deadline. Invisible in the old report. */
    private long resolvedLate;
    /** Finished with no deadline recorded, so punctuality cannot be judged either way. */
    private long resolvedNoDeadline;
    /**
     * % onTime / (onTime + late) — the report's real punctuality figure.
     *
     * <p>Null, never zero, when nothing finished in the period carried a deadline. A period whose
     * completed tasks all lacked a due date is not a period of total lateness, and printing 0.0%
     * under a red meter would recreate exactly the flattering-by-accident reading this report was
     * rewritten to remove, only in the opposite direction.
     */
    private Double punctualityRate;
    /** Average hours from raising a task to finishing it. */
    private double avgCycleHours;

    /**
     * Completed tasks that carry no completion time, from before the column existed.
     *
     * <p>Reported rather than guessed. They cannot be placed on the resolved axis at all, so
     * presenting a punctuality rate without saying how much of the history it covers would overstate
     * what is known.
     */
    private long completedUndated;
    /** % of completed tasks in this period that could be judged for punctuality. */
    private double punctualityCoverage;

    // ── The open queue: tasks raised here that are still late (BR-17) ────────

    /**
     * Of the tasks raised in this period, how many are still open and already past their deadline,
     * evaluated against the clock when the report runs.
     *
     * <p>Scoped to the same cohort as {@link #totalTasks} on purpose, so {@link #overdueRate} has a
     * denominator it belongs to. A queue count over all of history divided by this period's open
     * tasks can exceed 100%, which is a rate of nothing.
     */
    private long openOverdue;
    /** % openOverdue / open — how much of what is still running is already late. */
    private double overdueRate;
    private double avgDaysOverdue;
    /** How long the overdue tasks have been overdue: 1–3 days, 4–7 days, 8+ days. */
    private List<CountRow> overdueAging;
    /**
     * Overdue tasks by priority. Three of four HIGH tasks overdue is a different situation from
     * three of forty LOW ones, and the old report presented priority and overdue as separate
     * totals, so that difference could not be seen.
     */
    private List<CountRow> overdueByPriority;

    // ── SLA (classified by the same code as UC-23.3) ─────────────────────────
    private long slaDecided;
    private long slaOnTime;
    private double slaComplianceRate;

    /** True when the figures cover only the caller's own tasks rather than the whole team. */
    private boolean ownScope;

    private List<StaffRow> staff;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TaskPerformanceReportResponseBuilder {
    }

    /** A labelled count — activity kinds, aging bands, priorities. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = CountRow.CountRowBuilder.class)
    public static class CountRow {
        private String key;
        private String label;
        private long count;

        @JsonPOJOBuilder(withPrefix = "")
        public static class CountRowBuilder {
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(builder = StaffRow.StaffRowBuilder.class)
    public static class StaffRow {
        private String name;
        private long total;
        private long completed;
        private double completionRate;

        /** Still open and past the deadline. */
        private long openOverdue;
        /** Finished late — the column the old report had no way to show. */
        private long resolvedLate;
        private long resolvedOnTime;
        /** Null when this person finished nothing datable in the period. */
        private Double punctualityRate;
        private Double avgCycleHours;

        /** True for the synthetic row holding tasks with no assignee. */
        private boolean unassigned;

        @JsonPOJOBuilder(withPrefix = "")
        public static class StaffRowBuilder {
        }
    }
}
