package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.GeneratePaymentRequest;
import com.novax.leadora.api.dto.request.UpdatePaymentStatusRequest;
import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.application.usecase.payment.*;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Module 21 — Deposit & Payment Management.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'ADMIN', 'FRONT_OFFICE')")
public class PaymentController {

    private final GeneratePaymentRequestUseCase generatePaymentRequestUseCase;
    private final GetPaymentListUseCase getPaymentListUseCase;
    private final GetPaymentDetailUseCase getPaymentDetailUseCase;
    private final UpdatePaymentStatusUseCase updatePaymentStatusUseCase;
    private final CancelPaymentRequestUseCase cancelPaymentRequestUseCase;
    private final CurrentUserProvider currentUserProvider;

    /** UC-21.1 — Generate Payment Request. */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> generate(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody GeneratePaymentRequest request
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        PaymentResponse response = generatePaymentRequestUseCase.execute(request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment request generated successfully"));
    }

    /** UC-21.2 — View Payment List. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        Page<PaymentResponse> response = getPaymentListUseCase.execute(search, status, paymentType, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** UC-21.3 — View Payment Detail. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getDetail(@PathVariable UUID id) {
        PaymentResponse details = getPaymentDetailUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    /** UC-21.4 — Update Payment Status. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PaymentResponse>> updateStatus(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        PaymentResponse response = updatePaymentStatusUseCase.execute(id, request, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment status updated successfully"));
    }

    /** UC-21.5 — Cancel Payment Request. */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES', 'RESERVATION', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id
    ) {
        UserEntity actor = currentUserProvider.resolve(userId);
        PaymentResponse response = cancelPaymentRequestUseCase.execute(id, actor);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment request cancelled successfully"));
    }
}
