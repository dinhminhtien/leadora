package com.novax.leadora.api.controller;

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
    private final CustomerAcceptQuotationUseCase customerAcceptQuotationUseCase;
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
     * The customer accepts, and the quotation moves to RESERVATION_PENDING.
     *
     * <p>One click. This used to be two endpoints — request a one-time code, then send it back —
     * which put the customer's inbox in the middle of the moment they had decided to say yes, for
     * a verification Report 1 asks for nowhere. The link's token is the credential: single-use,
     * 24-hour, and validated inside the use case; the acceptance is recorded with IP and user
     * agent either way. Symmetrical with {@link #rejectQuotation}, which never had a code.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<QuotationResponse>> acceptQuotation(
            @PathVariable UUID id,
            @RequestParam String token,
            HttpServletRequest request) {
        QuotationEntity quotation = customerAcceptQuotationUseCase.execute(
                id, token, getClientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(
                ApiResponse.success(QuotationResponse.from(quotation), "Quotation accepted successfully."));
    }

    /**
     * The customer declines, with a reason.
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
