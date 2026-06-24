package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.DealRequest;
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

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    private final GetDealListUseCase getDealListUseCase;
    private final GetDealDetailUseCase getDealDetailUseCase;
    private final CreateDealUseCase createDealUseCase;
    private final UpdateDealUseCase updateDealUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DealResponse>>> getAllDeals() {
        List<DealResponse> deals = getDealListUseCase.execute();
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
}

