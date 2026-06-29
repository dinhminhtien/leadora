package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.SaveReportLogRequest;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.ReportLogResponse;
import com.novax.leadora.api.dto.response.SalesPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.application.usecase.reporting.GetDashboardSummaryUseCase;
import com.novax.leadora.application.usecase.reporting.GetSalesPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.GetTaskPerformanceReportUseCase;
import com.novax.leadora.application.usecase.reporting.SaveReportLogUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class ReportingController {

    private final SaveReportLogUseCase saveReportLogUseCase;
    private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;
    private final GetSalesPerformanceReportUseCase getSalesPerformanceReportUseCase;
    private final GetTaskPerformanceReportUseCase getTaskPerformanceReportUseCase;

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
        return ResponseEntity.ok(ApiResponse.success(
                getSalesPerformanceReportUseCase.execute(dateFrom, dateTo)));
    }

    /** UC-23.2 — View Follow-up Task Performance Report (Sales Manager). */
    @GetMapping("/task-performance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<TaskPerformanceReportResponse>> getTaskPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                getTaskPerformanceReportUseCase.execute(dateFrom, dateTo)));
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
