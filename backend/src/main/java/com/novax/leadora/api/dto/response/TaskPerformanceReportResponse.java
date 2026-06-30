package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.2 — Follow-up Task Performance Report. Aggregated server-side over tasks for an optional
 * date range, with priority distribution and a per-staff breakdown.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskPerformanceReportResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    private long totalTasks;
    private long completed;
    private long open;
    private long cancelled;
    private long overdue;            // OPEN and past end_at
    private double completionRate;   // %
    private double overdueRate;      // %

    private long priorityLow;
    private long priorityMedium;
    private long priorityHigh;

    private List<StaffRow> staff;

    @Getter
    @Builder
    public static class StaffRow {
        private String name;
        private long total;
        private long completed;
        private long overdue;
        private double completionRate;  // %
    }
}
