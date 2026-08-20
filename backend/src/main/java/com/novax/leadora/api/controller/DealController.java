package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.api.dto.request.UpdateDealStatusRequest;
import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.api.dto.response.DealStatsResponse;
import com.novax.leadora.api.dto.response.PipelineDealCardResponse;
import com.novax.leadora.api.dto.response.DealWorkflowSummaryResponse;
import com.novax.leadora.application.usecase.deal.CreateDealUseCase;
import com.novax.leadora.application.usecase.deal.DealMapper;
import com.novax.leadora.application.usecase.deal.GetDealDetailUseCase;
import com.novax.leadora.application.usecase.deal.GetDealListUseCase;
import com.novax.leadora.application.usecase.deal.GetDealStatsUseCase;
import com.novax.leadora.application.usecase.deal.GetPipelineDealsUseCase;
import com.novax.leadora.application.usecase.deal.GetQuotableDealsUseCase;
import com.novax.leadora.application.usecase.deal.GetDealWorkflowSummaryUseCase;
import com.novax.leadora.application.usecase.deal.UpdateDealUseCase;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
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
    private final GetDealStatsUseCase getDealStatsUseCase;
    private final GetDealDetailUseCase getDealDetailUseCase;
    private final CreateDealUseCase createDealUseCase;
    private final UpdateDealUseCase updateDealUseCase;
    private final GetPipelineDealsUseCase getPipelineDealsUseCase;
    private final GetQuotableDealsUseCase getQuotableDealsUseCase;
    private final GetDealWorkflowSummaryUseCase getDealWorkflowSummaryUseCase;
    private final DealWorkflowSyncService dealWorkflowSyncService;
    private final DealMapper dealMapper;

    /**
     * The Deals list, paged and searchable — mirrors {@code LeadController#getLeads}.
     *
     * <p>{@code stage}/{@code status} are the wire-format strings the frontend already uses
     * elsewhere ({@code DealMapper#mapStageToEnum}/{@code mapStatusToEnum}), e.g. {@code
     * "Inquiry"}/{@code "Confirmed"} and {@code "active"}/{@code "won"}/{@code "lost"} — not the
     * raw enum names — so the Deals screen's existing filter dropdowns can be wired through
     * unchanged.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DealResponse>>> getAllDeals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DealResponse> deals = getDealListUseCase.execute(
                search, ownerId, parseStage(stage), parseStatus(status), page, size);
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    /**
     * Counts and totals for the tiles above the list (UC-12.x), over the same filters and owner
     * scope as {@link #getAllDeals}. Separate from the list because the list is paged and these
     * are not — the client cannot total what it never received.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DealStatsResponse>> getDealStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String stage) {
        DealStatsResponse stats = getDealStatsUseCase.execute(search, ownerId, parseStage(stage));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Every deal matching the current filters, unpaged — feeds the "Export" button, which has
     * always exported the whole filtered set rather than one page. Kept as its own endpoint
     * (rather than raising {@code size} on {@link #getAllDeals}) so nothing can accidentally ask
     * the paged endpoint to return the whole table.
     */
    @GetMapping("/export")
    public ResponseEntity<ApiResponse<List<DealResponse>>> exportDeals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status) {
        List<DealResponse> deals =
                getDealListUseCase.executeAll(search, ownerId, parseStage(stage), parseStatus(status));
        return ResponseEntity.ok(ApiResponse.success(deals));
    }

    private DealPipelineStage parseStage(String stage) {
        return (stage == null || stage.isBlank() || "all".equalsIgnoreCase(stage))
                ? null
                : dealMapper.mapStageToEnum(stage);
    }

    private DealStatus parseStatus(String status) {
        return (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
                ? null
                : dealMapper.mapStatusToEnum(status);
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

