package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CloseQuotationRequest;
import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import com.novax.leadora.api.dto.request.ProcessApprovalRequest;
import com.novax.leadora.api.dto.request.ReviseQuotationRequest;
import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.request.TrackCustomerResponseRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.CreateQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.GetPendingApprovalsUseCase;
import com.novax.leadora.application.usecase.quotation.GetQuotationByIdUseCase;
import com.novax.leadora.application.usecase.quotation.GetQuotationListUseCase;
import com.novax.leadora.application.usecase.quotation.ProcessQuotationApprovalUseCase;
import com.novax.leadora.application.usecase.quotation.ReviseQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.SendQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.CloseQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.ConvertToBookingUseCase;
import com.novax.leadora.application.usecase.quotation.ExpireOverdueQuotationsUseCase;
import com.novax.leadora.application.usecase.quotation.TrackCustomerResponseUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final CreateQuotationUseCase createQuotationUseCase;
    private final GetQuotationListUseCase getQuotationListUseCase;
    private final GetQuotationByIdUseCase getQuotationByIdUseCase;
    private final GetPendingApprovalsUseCase getPendingApprovalsUseCase;
    private final ProcessQuotationApprovalUseCase processApprovalUseCase;
    private final SendQuotationUseCase sendQuotationUseCase;
    private final ReviseQuotationUseCase reviseQuotationUseCase;
    private final TrackCustomerResponseUseCase trackCustomerResponseUseCase;
    private final ConvertToBookingUseCase convertToBookingUseCase;
    private final CloseQuotationUseCase closeQuotationUseCase;
    private final ExpireOverdueQuotationsUseCase expireOverdueUseCase;

    /** UC-14.1 — Create Room Quotation */
    @PostMapping
    public ResponseEntity<ApiResponse<QuotationResponse>> createQuotation(
            @Valid @RequestBody CreateQuotationRequest request) {
        QuotationResponse response = createQuotationUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Quotation created successfully"));
    }

    /** Get all quotations */
    @GetMapping
    public ResponseEntity<ApiResponse<List<QuotationResponse>>> getQuotations() {
        List<QuotationResponse> quotations = getQuotationListUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(quotations));
    }

    /** UC-14.5 — Get quotation by ID (for pre-populating revision form) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuotationResponse>> getQuotationById(@PathVariable UUID id) {
        QuotationResponse response = getQuotationByIdUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** UC-14.3 — Get quotations pending manager approval */
    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<List<QuotationResponse>>> getPendingApprovals() {
        List<QuotationResponse> pending = getPendingApprovalsUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    /** UC-14.3 — Process approval decision (approve / reject / request changes) */
    @PostMapping("/{id}/process-approval")
    public ResponseEntity<ApiResponse<QuotationResponse>> processApproval(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessApprovalRequest request) {
        QuotationResponse response = processApprovalUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation processed successfully"));
    }

    /** UC-14.5 — Create a new version of an existing quotation */
    @PostMapping("/{id}/revise")
    public ResponseEntity<ApiResponse<QuotationResponse>> reviseQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody ReviseQuotationRequest request) {
        QuotationResponse response = reviseQuotationUseCase.execute(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Quotation revised successfully"));
    }

    /** UC-14.4 — Send approved quotation to customer */
    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<QuotationResponse>> sendQuotation(
            @PathVariable UUID id,
            @RequestBody SendQuotationRequest request) {
        QuotationResponse response = sendQuotationUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation sent successfully"));
    }

    /** UC-14.7 — Convert accepted quotation to confirmed booking */
    @PostMapping("/{id}/convert")
    public ResponseEntity<ApiResponse<BookingResponse>> convertToBooking(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertToBookingRequest request) {
        BookingResponse response = convertToBookingUseCase.execute(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking created successfully"));
    }

    /** UC-14.8 — Manually close a quotation */
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<QuotationResponse>> closeQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody CloseQuotationRequest request) {
        QuotationResponse response = closeQuotationUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation closed successfully"));
    }

    /** UC-14.8 — Batch expire all overdue quotations (validUntil < today) */
    @PostMapping("/expire-overdue")
    public ResponseEntity<ApiResponse<Object>> expireOverdue(
            @RequestBody(required = false) ExpireOverdueRequest request) {
        ExpireOverdueRequest req = request != null ? request : new ExpireOverdueRequest();
        Object result = expireOverdueUseCase.execute(req);
        return ResponseEntity.ok(ApiResponse.success(result, "Overdue quotations expired successfully"));
    }

    /** UC-14.6 — Track customer response (Accept / Reject / Interested / Need Revision) */
    @PostMapping("/{id}/track-response")
    public ResponseEntity<ApiResponse<QuotationResponse>> trackCustomerResponse(
            @PathVariable UUID id,
            @Valid @RequestBody TrackCustomerResponseRequest request) {
        QuotationResponse response = trackCustomerResponseUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Customer response recorded successfully"));
    }
}