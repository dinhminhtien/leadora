package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.request.UpdateDealStatusRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.application.usecase.deal.CreateDealUseCase;
import com.novax.leadora.application.usecase.deal.GetDealDetailUseCase;
import com.novax.leadora.application.usecase.deal.GetDealListUseCase;
import com.novax.leadora.application.usecase.deal.UpdateDealUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class DealController {

    private final GetDealListUseCase getDealListUseCase;
    private final GetDealDetailUseCase getDealDetailUseCase;
    private final CreateDealUseCase createDealUseCase;
    private final UpdateDealUseCase updateDealUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DealResponse>>> getAllDeals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId) {
        List<DealResponse> deals = getDealListUseCase.execute(search, ownerId);
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponse>> getDealById(@PathVariable UUID id) {
        DealResponse deal = getDealDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(deal));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(@Valid @RequestBody DealRequest request) {
        DealResponse created = createDealUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponse>> updateDeal(
            @PathVariable UUID id,
            @Valid @RequestBody DealRequest request) {
        DealResponse updated = updateDealUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * Close an open deal as won or lost. Unlike PUT, this skips the stage-transition
     * validation, so marking a deal lost does not require an estimated close date.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DealResponse>> updateDealStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDealStatusRequest request) {
        DealResponse updated = updateDealUseCase.updateDealStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(updated, "Deal status updated successfully"));
    }
}

