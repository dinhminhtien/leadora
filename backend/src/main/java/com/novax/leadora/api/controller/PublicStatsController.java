package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.PublicStatsResponse;
import com.novax.leadora.application.usecase.reporting.GetPublicStatsUseCase;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private final GetPublicStatsUseCase getPublicStatsUseCase;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PublicStatsResponse>> getPublicStats() {
        PublicStatsResponse stats = getPublicStatsUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(stats, "Public statistics retrieved successfully"));
    }
}
