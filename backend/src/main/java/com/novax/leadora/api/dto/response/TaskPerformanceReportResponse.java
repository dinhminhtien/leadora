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
 * UC-23.2 — Follow-up Task Performance Report. Aggregated server-side over tasks for an optional
 * date range, with priority distribution and a per-staff breakdown.
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

    private long totalTasks;
    private long completed;
    private long open;
    private long cancelled;
    private long overdue;            // not finished and past end_at (BR-17, derived)
    private double completionRate;   // %
    private double overdueRate;      // %

    private long priorityLow;
    private long priorityMedium;
    private long priorityHigh;

    /** True when the figures cover only the caller's own tasks rather than the whole team. */
    private boolean ownScope;

    private List<StaffRow> staff;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TaskPerformanceReportResponseBuilder {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonDeserialize(builder = StaffRow.StaffRowBuilder.class)
    public static class StaffRow {
        private String name;
        private long total;
        private long completed;
        private long overdue;
        private double completionRate;  // %
        /** True for the synthetic row holding tasks with no assignee. */
        private boolean unassigned;

        @JsonPOJOBuilder(withPrefix = "")
        public static class StaffRowBuilder {
        }
    }
}
