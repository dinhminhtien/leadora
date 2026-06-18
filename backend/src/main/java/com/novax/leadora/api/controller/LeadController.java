package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.lead.ConvertLeadUseCase;
import com.novax.leadora.application.usecase.lead.CreateLeadUseCase;
import com.novax.leadora.application.usecase.lead.GetLeadDetailUseCase;
import com.novax.leadora.application.usecase.lead.GetLeadListUseCase;
import com.novax.leadora.application.usecase.lead.UpdateLeadUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final CreateLeadUseCase createLeadUseCase;
    private final GetLeadListUseCase getLeadListUseCase;
    private final GetLeadDetailUseCase getLeadDetailUseCase;
    private final UpdateLeadUseCase updateLeadUseCase;
    private final ConvertLeadUseCase convertLeadUseCase;

    /** UC-8.1 — Create Lead */
    @PostMapping
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(@Valid @RequestBody CreateLeadRequest request) {
        LeadResponse lead = createLeadUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(lead, "Lead created successfully"));
    }

    /** UC-8.2 — View Lead List */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<LeadResponse>>> getLeads(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean isCorporate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<LeadResponse> leads = getLeadListUseCase.execute(search, status, source, isCorporate, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(leads));
    }

    /** UC-8.3 — View Lead Detail */
    @GetMapping("/{leadId}")
    public ResponseEntity<ApiResponse<LeadResponse>> getLeadDetail(@PathVariable UUID leadId) {
        LeadResponse lead = getLeadDetailUseCase.execute(leadId);
        return ResponseEntity.ok(ApiResponse.success(lead));
    }

    /** UC-8.4 — Update Lead */
    @PutMapping("/{leadId}")
    public ResponseEntity<ApiResponse<LeadResponse>> updateLead(
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadRequest request
    ) {
        LeadResponse lead = updateLeadUseCase.execute(leadId, request);
        return ResponseEntity.ok(ApiResponse.success(lead, "Lead updated successfully"));
    }

    /** UC-8.5 — Convert Lead to Customer */
    @PostMapping("/{leadId}/convert")
    public ResponseEntity<ApiResponse<ConvertLeadResponse>> convertLead(
            @PathVariable UUID leadId,
            @Valid @RequestBody ConvertLeadRequest request
    ) {
        ConvertLeadResponse response = convertLeadUseCase.execute(leadId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Lead converted to customer successfully"));
    }
}
