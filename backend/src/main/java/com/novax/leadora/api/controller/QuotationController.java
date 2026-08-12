package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CloseQuotationRequest;
import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.api.dto.request.CreateQuotationRequest;
import com.novax.leadora.api.dto.request.ProcessApprovalRequest;
import com.novax.leadora.api.dto.request.ReviseQuotationRequest;
import com.novax.leadora.api.dto.request.SendQuotationRequest;
import com.novax.leadora.api.dto.request.SubmitQuotationRequest;
import com.novax.leadora.api.dto.request.TrackCustomerResponseRequest;
import com.novax.leadora.api.dto.request.ReservationRejectRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.CreateQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.GetPendingApprovalsUseCase;
import com.novax.leadora.application.usecase.quotation.GetQuotationByIdUseCase;
import com.novax.leadora.application.usecase.quotation.GetQuotationListUseCase;
import com.novax.leadora.application.usecase.quotation.ProcessQuotationApprovalUseCase;
import com.novax.leadora.application.usecase.quotation.ReviseQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.SendQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.SubmitQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.CloseQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.ConvertToBookingUseCase;
import com.novax.leadora.application.usecase.quotation.ExpireOverdueQuotationsUseCase;
import com.novax.leadora.application.usecase.quotation.TrackCustomerResponseUseCase;
import com.novax.leadora.application.usecase.quotation.ApproveReservationUseCase;
import com.novax.leadora.application.usecase.quotation.RejectReservationUseCase;
import com.novax.leadora.application.usecase.quotation.ResendQuotationEmailUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequiredArgsConstructor
public class QuotationController {

    private final CreateQuotationUseCase createQuotationUseCase;
    private final GetQuotationListUseCase getQuotationListUseCase;
    private final GetQuotationByIdUseCase getQuotationByIdUseCase;
    private final GetPendingApprovalsUseCase getPendingApprovalsUseCase;
    private final ProcessQuotationApprovalUseCase processApprovalUseCase;
    private final SubmitQuotationUseCase submitQuotationUseCase;
    private final SendQuotationUseCase sendQuotationUseCase;
    private final ReviseQuotationUseCase reviseQuotationUseCase;
    private final TrackCustomerResponseUseCase trackCustomerResponseUseCase;
    private final ConvertToBookingUseCase convertToBookingUseCase;
    private final CloseQuotationUseCase closeQuotationUseCase;
    private final ExpireOverdueQuotationsUseCase expireOverdueUseCase;
    private final ApproveReservationUseCase approveReservationUseCase;
    private final RejectReservationUseCase rejectReservationUseCase;
    private final ResendQuotationEmailUseCase resendQuotationEmailUseCase;

    /** UC-14.1 — Create Room Quotation */
    @PostMapping("/api/v1/quotations")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> createQuotation(
            @Valid @RequestBody CreateQuotationRequest request) {
        QuotationResponse response = createQuotationUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Quotation created successfully"));
    }

    /** Get all quotations with server-side pagination, filters, and sorting */
    @GetMapping("/api/v1/quotations")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN','RESERVATION') and @access.can('QUOTATION_VIEW')")
    public ResponseEntity<ApiResponse<Page<QuotationResponse>>> getQuotations(
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) List<QuotationStatus> statuses,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "validUntil") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<QuotationResponse> quotations = getQuotationListUseCase.execute(status, statuses, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(quotations));
    }

    /** UC-14.5 — Get quotation by ID (for pre-populating revision form) */
    @GetMapping("/api/v1/quotations/{id}")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN','RESERVATION') and @access.can('QUOTATION_VIEW')")
    public ResponseEntity<ApiResponse<QuotationResponse>> getQuotationById(@PathVariable UUID id) {
        QuotationResponse response = getQuotationByIdUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** UC-14.3 — Get quotations pending manager approval. Manager only. */
    @GetMapping("/api/v1/quotations/pending-approvals")
    @PreAuthorize("hasRole('MANAGER') and @access.can('QUOTATION_APPROVE')")
    public ResponseEntity<ApiResponse<List<QuotationResponse>>> getPendingApprovals() {
        List<QuotationResponse> pending = getPendingApprovalsUseCase.execute();
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    /** UC-14.1 — Submit a DRAFT quotation: discount ≤10% → APPROVED, >10% → PENDING_APPROVAL */
    @PostMapping("/api/v1/quotations/{id}/submit")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> submitQuotation(
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitQuotationRequest request) {
        SubmitQuotationRequest req = request != null ? request : new SubmitQuotationRequest();
        QuotationResponse response = submitQuotationUseCase.execute(id, req);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation submitted successfully"));
    }

    /** UC-14.3 — Process approval decision (approve / reject / request changes). Manager only. */
    @PostMapping("/api/v1/quotations/{id}/process-approval")
    @PreAuthorize("hasRole('MANAGER') and @access.can('QUOTATION_APPROVE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> processApproval(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessApprovalRequest request) {
        QuotationResponse response = processApprovalUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation processed successfully"));
    }

    /** UC-14.5 — Create a new version of an existing quotation */
    @PostMapping("/api/v1/quotations/{id}/revise")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> reviseQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody ReviseQuotationRequest request) {
        QuotationResponse response = reviseQuotationUseCase.execute(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Quotation revised successfully"));
    }

    /** UC-14.4 — Send approved quotation to customer */
    @PostMapping("/api/v1/quotations/{id}/send")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> sendQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody SendQuotationRequest request) {
        QuotationResponse response = sendQuotationUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation sent successfully"));
    }

    /** Resend quotation email to customer (if previous email hasn't been opened) */
    @PostMapping("/api/v1/quotations/{id}/resend")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> resendQuotation(
            @PathVariable UUID id) {
        QuotationResponse response = resendQuotationEmailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation email resent successfully"));
    }

    /** UC-14.7 — Convert manually accepted quotation to confirmed booking (Sales Flow) */
    @PostMapping("/api/v1/quotations/{id}/convert")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<BookingResponse>> convertToBooking(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertToBookingRequest request) {
        BookingResponse response = convertToBookingUseCase.execute(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking created successfully"));
    }

    /** UC-14.8 — Manually close a quotation */
    @PostMapping("/api/v1/quotations/{id}/close")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> closeQuotation(
            @PathVariable UUID id,
            @Valid @RequestBody CloseQuotationRequest request) {
        QuotationResponse response = closeQuotationUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quotation closed successfully"));
    }

    /** UC-14.8 — Batch expire all overdue quotations (validUntil < today) */
    @PostMapping("/api/v1/quotations/expire-overdue")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<Object>> expireOverdue(
            @RequestBody(required = false) ExpireOverdueRequest request) {
        ExpireOverdueRequest req = request != null ? request : new ExpireOverdueRequest();
        Object result = expireOverdueUseCase.execute(req);
        return ResponseEntity.ok(ApiResponse.success(result, "Overdue quotations expired successfully"));
    }

    /** UC-14.6 — Track customer response (Accept / Reject / Interested / Need Revision) */
    @PostMapping("/api/v1/quotations/{id}/track-response")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> trackCustomerResponse(
            @PathVariable UUID id,
            @Valid @RequestBody TrackCustomerResponseRequest request) {
        QuotationResponse response = trackCustomerResponseUseCase.execute(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Customer response recorded successfully"));
    }

    /**
     * BR-15 — Approve Reservation Request.
     * Invoked by Reservation staff to confirm availability and create the booking.
     */
    @PostMapping("/api/v1/quotations/{id}/reservation-approve")
    @PreAuthorize("hasAnyRole('RESERVATION', 'MANAGER', 'ADMIN') and @access.can('RESERVATION_WRITE')")
    public ResponseEntity<ApiResponse<BookingResponse>> approveReservation(
            @PathVariable UUID id) {
        BookingResponse response = approveReservationUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation request approved and booking created successfully."));
    }

    /**
     * BR-15 — Reject Reservation Request.
     * Invoked by Reservation staff to reject a portal-accepted quotation due to unavailability or other reasons.
     */
    @PostMapping("/api/v1/quotations/{id}/reservation-reject")
    @PreAuthorize("hasAnyRole('RESERVATION', 'MANAGER', 'ADMIN') and @access.can('RESERVATION_WRITE')")
    public ResponseEntity<ApiResponse<QuotationResponse>> rejectReservation(
            @PathVariable UUID id,
            @Valid @RequestBody ReservationRejectRequest requestBody) {
        QuotationResponse response = QuotationResponse.from(
                rejectReservationUseCase.execute(id, requestBody.getReason(), requestBody.getNote())
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation request rejected successfully."));
    }
}