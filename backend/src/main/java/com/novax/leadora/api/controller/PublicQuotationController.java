package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ConfirmOtpRequest;
import com.novax.leadora.api.dto.request.CustomerRejectRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.*;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * UC-14.x — Public Customer Quotation Acceptance Portal endpoints.
 * Permitted anonymously (configured in Spring Security) and secured via tokenized links.
 */
@RestController
@RequestMapping("/api/v1/public/quotations")
@RequiredArgsConstructor
public class PublicQuotationController {

    private final GetQuotationByTokenUseCase getQuotationByTokenUseCase;
    private final RequestQuotationOtpUseCase requestQuotationOtpUseCase;
    private final ConfirmQuotationOtpUseCase confirmQuotationOtpUseCase;
    private final CustomerRejectQuotationUseCase customerRejectQuotationUseCase;

    /**
     * Retrieve quotation details by secure link token.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuotationResponse>> getPublicQuotation(
            @PathVariable UUID id,
            @RequestParam String token) {
        QuotationEntity quotation = getQuotationByTokenUseCase.execute(id, token);
        return ResponseEntity.ok(ApiResponse.success(QuotationResponse.from(quotation)));
    }

    /**
     * Request OTP for quotation acceptance.
     */
    @PostMapping("/{id}/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestQuotationOtp(
            @PathVariable UUID id,
            @RequestParam String token) {
        requestQuotationOtpUseCase.execute(id, token);
        return ResponseEntity.ok(ApiResponse.success(null, "Verification code sent to registered email."));
    }

    /**
     * Confirm OTP to accept quotation and transition status to RESERVATION_PENDING.
     */
    @PostMapping("/{id}/confirm-otp")
    public ResponseEntity<ApiResponse<QuotationResponse>> confirmQuotationOtp(
            @PathVariable UUID id,
            @RequestParam String token,
            @Valid @RequestBody ConfirmOtpRequest requestBody,
            HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        QuotationEntity quotation = confirmQuotationOtpUseCase.execute(id, token, requestBody.getOtpCode(), ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(QuotationResponse.from(quotation), "Quotation accepted successfully."));
    }

    /**
     * Reject quotation directly via public portal without OTP.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<QuotationResponse>> rejectQuotation(
            @PathVariable UUID id,
            @RequestParam String token,
            @Valid @RequestBody CustomerRejectRequest requestBody,
            HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        QuotationEntity quotation = customerRejectQuotationUseCase.execute(id, token, requestBody.getReason(), ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(QuotationResponse.from(quotation), "Quotation rejected successfully."));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
