package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.api.dto.response.SentimentOverviewResponse;
import com.novax.leadora.api.dto.response.SentimentTrendResponse;
import com.novax.leadora.application.usecase.feedback.GetAspectDeepDiveFeedbackUseCase;
import com.novax.leadora.application.usecase.feedback.GetSentimentAnalyticsUseCase;
import com.novax.leadora.application.usecase.feedback.GetSentimentTrendUseCase;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/analytics/sentiment")
@RequiredArgsConstructor
public class SentimentAnalyticsController {

    private final GetSentimentAnalyticsUseCase getSentimentAnalyticsUseCase;
    private final GetSentimentTrendUseCase getSentimentTrendUseCase;
    private final GetAspectDeepDiveFeedbackUseCase getAspectDeepDiveFeedbackUseCase;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<SentimentOverviewResponse>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        SentimentOverviewResponse response = getSentimentAnalyticsUseCase.execute(startDate, endDate, headerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<SentimentTrendResponse>> getTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "week") String groupBy,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        SentimentTrendResponse response = getSentimentTrendUseCase.execute(startDate, endDate, groupBy, headerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/deep-dive")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') and @access.can('FEEDBACK_VIEW')")
    public ResponseEntity<ApiResponse<Page<FeedbackResponse>>> getDeepDive(
            @RequestParam(required = false) String aspect,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        Page<FeedbackResponse> response = getAspectDeepDiveFeedbackUseCase.execute(
                aspect, sentiment, startDate, endDate, page, size, headerUserId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
