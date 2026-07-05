package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.SaveReportLogRequest;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.api.dto.response.ReportLogResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.application.usecase.reporting.GetDashboardSummaryUseCase;
import com.novax.leadora.application.usecase.reporting.GetPipelineProgressionReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetQuotationOutcomeReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetSalesPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetTaskPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.SaveReportLogUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class ReportingController {

    private final SaveReportLogUseCase saveReportLogUseCase;
    private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;
    private final GetSalesPerformanceReportUseCase getSalesPerformanceReportUseCase;
    private final GetTaskPerformanceReportUseCase getTaskPerformanceReportUseCase;
    private final GetPipelineProgressionReportUseCase getPipelineProgressionReportUseCase;
    private final GetQuotationOutcomeReportUseCase getQuotationOutcomeReportUseCase;
    private final CurrentUserProvider currentUserProvider;

    /** Dashboard KPI summary — all aggregation happens server-side */
    @GetMapping("/dashboard-summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        DashboardSummaryResponse summary = getDashboardSummaryUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /** UC-23.1 — View Sales Performance Statistics Report (Sales Manager). */
    @GetMapping("/sales-performance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<SalesPerformanceReportResponse>> getSalesPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        UserEntity actor = currentUserProvider.resolve(null);
        SalesPerformanceReportResponse report = getSalesPerformanceReportUseCase.execute(dateFrom, dateTo);
        auditView(actor, "VIEW_SALES_PERFORMANCE", dateFrom, dateTo, (int) report.getDealsTotal());
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * UC-23.2 — View Follow-up Task Performance Report. Sales Staff see their own task performance;
     * Sales Manager / Admin see team-wide performance (scoping is applied in the use case).
     */
    @GetMapping("/task-performance")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<TaskPerformanceReportResponse>> getTaskPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        UserEntity actor = currentUserProvider.resolve(null);
        TaskPerformanceReportResponse report = getTaskPerformanceReportUseCase.execute(actor, dateFrom, dateTo);
        auditView(actor, "VIEW_TASK_PERFORMANCE", dateFrom, dateTo, (int) report.getTotalTasks());
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /** UC-23.4 — View Sales Pipeline Progression Report (Sales Manager). */
    @GetMapping("/pipeline-progression")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PipelineProgressionReportResponse>> getPipelineProgression(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        UserEntity actor = currentUserProvider.resolve(null);
        PipelineProgressionReportResponse report = getPipelineProgressionReportUseCase.execute(dateFrom, dateTo);
        auditView(actor, "VIEW_PIPELINE_PROGRESSION", dateFrom, dateTo, (int) report.getTotalDeals());
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /** UC-23.5 — View Quotation Outcome Report (Sales Manager). */
    @GetMapping("/quotation-outcome")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<QuotationOutcomeReportResponse>> getQuotationOutcome(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        UserEntity actor = currentUserProvider.resolve(null);
        QuotationOutcomeReportResponse report = getQuotationOutcomeReportUseCase.execute(dateFrom, dateTo);
        auditView(actor, "VIEW_QUOTATION_OUTCOME", dateFrom, dateTo, (int) report.getTotal());
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /** POST-2 audit of a report view — best-effort: never let a logging failure break the report. */
    private void auditView(UserEntity actor, String action, LocalDate from, LocalDate to, int resultCount) {
        try {
            String role = (actor.getRole() != null) ? actor.getRole().getRoleName() : null;
            saveReportLogUseCase.logReportView(actor.getFullName(), role, action, from, to, resultCount);
        } catch (Exception ex) {
            log.warn("Report-view audit log failed for {}: {}", action, ex.getMessage());
        }
    }

    /** UC-14.2 — Save audit log when a discount report is generated */
    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<ReportLogResponse>> saveReportLog(
            @Valid @RequestBody SaveReportLogRequest request) {
        ReportLogResponse response = saveReportLogUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Report log saved successfully"));
    }
}
