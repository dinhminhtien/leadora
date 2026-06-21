package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.application.usecase.ManageDealUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    private final ManageDealUseCase manageDealUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DealResponse>>> getAllDeals() {
        List<DealResponse> deals = manageDealUseCase.getAllDeals();
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponse>> getDealById(@PathVariable UUID id) {
        DealResponse deal = manageDealUseCase.getDealById(id);
        return ResponseEntity.ok(ApiResponse.success(deal));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(@Valid @RequestBody DealRequest request) {
        DealResponse created = manageDealUseCase.createDeal(request);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponse>> updateDeal(
            @PathVariable UUID id,
            @Valid @RequestBody DealRequest request) {
        DealResponse updated = manageDealUseCase.updateDeal(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
