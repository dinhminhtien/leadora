package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.api.dto.response.SentimentOverviewResponse;
import com.novax.leadora.api.dto.response.SentimentTrendResponse;
import com.novax.leadora.api.dto.response.StaffSentimentPerformanceResponse;
import com.novax.leadora.application.usecase.feedback.GetAspectDeepDiveFeedbackUseCase;
import com.novax.leadora.application.usecase.feedback.GetSentimentAnalyticsUseCase;
import com.novax.leadora.application.usecase.feedback.GetSentimentTrendUseCase;
import com.novax.leadora.application.usecase.feedback.GetStaffSentimentPerformanceUseCase;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/sentiment")
@RequiredArgsConstructor
public class SentimentAnalyticsController {

    private final GetSentimentAnalyticsUseCase getSentimentAnalyticsUseCase;
    private final GetSentimentTrendUseCase getSentimentTrendUseCase;
    private final GetAspectDeepDiveFeedbackUseCase getAspectDeepDiveFeedbackUseCase;
    private final GetStaffSentimentPerformanceUseCase getStaffSentimentPerformanceUseCase;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('MANAGER') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<SentimentOverviewResponse>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        SentimentOverviewResponse response = getSentimentAnalyticsUseCase.execute(toUtc(startDate), toUtc(endDate), headerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasRole('MANAGER') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<SentimentTrendResponse>> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "week") String groupBy,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        SentimentTrendResponse response = getSentimentTrendUseCase.execute(toUtc(startDate), toUtc(endDate), groupBy, headerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/deep-dive")
    @PreAuthorize("hasRole('MANAGER') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<Page<FeedbackResponse>>> getDeepDive(
            @RequestParam(required = false) String aspect,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String salesStaffName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        Page<FeedbackResponse> response = getAspectDeepDiveFeedbackUseCase.execute(
                aspect, sentiment, salesStaffName, toUtc(startDate), toUtc(endDate), page, size, headerUserId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/staff-performance")
    @PreAuthorize("hasRole('MANAGER') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<List<StaffSentimentPerformanceResponse>>> getStaffPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate
    ) {
        List<StaffSentimentPerformanceResponse> response = getStaffSentimentPerformanceUseCase.execute(toUtc(startDate), toUtc(endDate));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private OffsetDateTime toUtc(OffsetDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.withOffsetSameInstant(java.time.ZoneOffset.UTC);
    }
}

