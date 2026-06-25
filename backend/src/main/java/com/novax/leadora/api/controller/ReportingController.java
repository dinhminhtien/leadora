package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.SaveReportLogRequest;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.ReportLogResponse;
import com.novax.leadora.application.usecase.reporting.GetDashboardSummaryUseCase;
import com.novax.leadora.application.usecase.reporting.SaveReportLogUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class ReportingController {

    private final SaveReportLogUseCase saveReportLogUseCase;
    private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;

    /** Dashboard KPI summary — all aggregation happens server-side */
    @GetMapping("/dashboard-summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        DashboardSummaryResponse summary = getDashboardSummaryUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(summary));
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
