package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.AcceptQuotationRequest;
import com.novax.leadora.api.dto.request.RejectQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.quotation.AcceptQuotationUseCase;
import com.novax.leadora.application.usecase.quotation.RejectQuotationUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/quotations")
@RequiredArgsConstructor
public class PublicQuotationController {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final AcceptQuotationUseCase acceptQuotationUseCase;
    private final RejectQuotationUseCase rejectQuotationUseCase;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<QuotationResponse>> getQuotationByToken(@PathVariable String token) {
        QuotationEntity quotation = quotationRepository.findByAcceptanceToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation token", token));

        List<QuotationDetailEntity> details = quotationDetailRepository.findByQuotation_QuotationId(quotation.getQuotationId());
        QuotationDetailEntity detail = details.isEmpty() ? null : details.get(0);
        int nights = detail != null ? detail.getNights() : 0;
        int numberOfRooms = detail != null ? detail.getQuantity() : 0;
        BigDecimal pricePerNight = detail != null ? detail.getUnitPrice() : BigDecimal.ZERO;

        QuotationResponse response = QuotationResponse.fromWithDetail(quotation, nights, numberOfRooms, pricePerNight);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptQuotation(
            @PathVariable String token,
            @RequestBody AcceptQuotationRequest request,
            HttpServletRequest servletRequest
    ) {
        String ipAddress = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        acceptQuotationUseCase.execute(token, request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(null, "Quotation accepted successfully"));
    }

    @PostMapping("/{token}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectQuotation(
            @PathVariable String token,
            @RequestBody RejectQuotationRequest request,
            HttpServletRequest servletRequest
    ) {
        String ipAddress = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        rejectQuotationUseCase.execute(token, request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(null, "Quotation rejected successfully"));
    }
}
