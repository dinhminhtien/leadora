package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.request.UpdateDealStatusRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.api.dto.response.PipelineDealCardResponse;
import com.novax.leadora.api.dto.response.DealWorkflowSummaryResponse;
import com.novax.leadora.application.usecase.deal.CreateDealUseCase;
import com.novax.leadora.application.usecase.deal.GetDealDetailUseCase;
import com.novax.leadora.application.usecase.deal.GetDealListUseCase;
import com.novax.leadora.application.usecase.deal.GetPipelineDealsUseCase;
import com.novax.leadora.application.usecase.deal.GetQuotableDealsUseCase;
import com.novax.leadora.application.usecase.deal.GetDealWorkflowSummaryUseCase;
import com.novax.leadora.application.usecase.deal.UpdateDealUseCase;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('DEAL_VIEW')")
public class DealController {

    private final GetDealListUseCase getDealListUseCase;
    private final GetDealDetailUseCase getDealDetailUseCase;
    private final CreateDealUseCase createDealUseCase;
    private final UpdateDealUseCase updateDealUseCase;
    private final GetPipelineDealsUseCase getPipelineDealsUseCase;
    private final GetQuotableDealsUseCase getQuotableDealsUseCase;
    private final GetDealWorkflowSummaryUseCase getDealWorkflowSummaryUseCase;
    private final DealWorkflowSyncService dealWorkflowSyncService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DealResponse>>> getAllDeals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId) {
        List<DealResponse> deals = getDealListUseCase.execute(search, ownerId);
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    /**
     * Deals a new quotation can be raised against (UC-14.1), paged and searchable.
     *
     * <p>Eligibility is one condition — the deal is still active ({@code DealStatus.OPEN});
     * WON and LOST are closed and cannot be quoted. Applied server-side alongside the usual
     * owner scoping, so the picker can search without downloading the whole list. Gated on
     * DEAL_VIEW by the class-level rule: choosing a deal is reading deals.
     */
    @GetMapping("/quotable")
    public ResponseEntity<ApiResponse<Page<DealResponse>>> getQuotableDeals(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DealResponse> deals = getQuotableDealsUseCase.execute(search, page, size);
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    /** Feeds the Sales Pipeline board, which the sidebar gates on PIPELINE_VIEW (UC-11.1). */
    @GetMapping("/pipeline")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('PIPELINE_VIEW')")
    public ResponseEntity<ApiResponse<List<PipelineDealCardResponse>>> getPipelineDeals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId) {
        List<PipelineDealCardResponse> deals = getPipelineDealsUseCase.execute(search, ownerId);
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    @GetMapping("/{id}/workflow")
    public ResponseEntity<ApiResponse<DealWorkflowSummaryResponse>> getDealWorkflowSummary(@PathVariable UUID id) {
        DealWorkflowSummaryResponse summary = getDealWorkflowSummaryUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponse>> getDealById(@PathVariable UUID id) {
        DealResponse deal = getDealDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(deal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('DEAL_WRITE')")
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(@Valid @RequestBody DealRequest request) {
        DealResponse created = createDealUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('DEAL_WRITE')")
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
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('DEAL_WRITE')")
    public ResponseEntity<ApiResponse<DealResponse>> updateDealStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDealStatusRequest request) {
        DealResponse updated = updateDealUseCase.updateDealStatus(id, request.getStatus(), request.getNotes());
        return ResponseEntity.ok(ApiResponse.success(updated, "Deal status updated successfully"));
    }
    @PostMapping("/{id}/sync-pipeline")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('DEAL_WRITE')")
    public ResponseEntity<ApiResponse<DealResponse>> syncPipeline(@PathVariable UUID id) {
        dealWorkflowSyncService.syncPipelineStage(id);
        DealResponse updated = getDealDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(updated, "Deal pipeline stage synchronized successfully"));
    }
}

