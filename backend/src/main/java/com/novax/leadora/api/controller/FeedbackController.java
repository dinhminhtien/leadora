package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.SubmitFeedbackRequest;
import com.novax.leadora.api.dto.request.UpdateReviewStatusRequest;
import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.api.dto.response.FeedbackTokenValidationResponse;
import com.novax.leadora.api.dto.response.SubmitFeedbackResponse;
import com.novax.leadora.application.usecase.feedback.*;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final ValidateFeedbackTokenUseCase validateFeedbackTokenUseCase;
    private final SubmitFeedbackUseCase submitFeedbackUseCase;
    private final GetFeedbackListUseCase getFeedbackListUseCase;
    private final GetFeedbackDetailUseCase getFeedbackDetailUseCase;
    private final UpdateFeedbackReviewStatusUseCase updateFeedbackReviewStatusUseCase;

    // --- Public Guest Endpoints ---

    @GetMapping("/public/{token}/validate")
    public ResponseEntity<ApiResponse<FeedbackTokenValidationResponse>> validateToken(@PathVariable String token) {
        FeedbackTokenValidationResponse response = validateFeedbackTokenUseCase.execute(token);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/public/{token}")
    public ResponseEntity<ApiResponse<SubmitFeedbackResponse>> submitFeedback(
            @PathVariable String token,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        SubmitFeedbackResponse response = submitFeedbackUseCase.execute(token, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // --- Management/Internal Dashboard Endpoints ---

    @GetMapping
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<FeedbackResponse>>> getFeedbacks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) Short rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        Page<FeedbackResponse> response = getFeedbackListUseCase.execute(
                search, reviewStatus, rating, page, size, headerUserId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getFeedbackDetail(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        FeedbackResponse response = getFeedbackDetailUseCase.execute(id, headerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/review-status")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateReviewStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        updateFeedbackReviewStatusUseCase.execute(id, request, headerUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Feedback review status updated successfully"));
    }
}
