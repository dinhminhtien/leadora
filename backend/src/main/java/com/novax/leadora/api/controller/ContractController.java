package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ConfirmOtpRequest;
import com.novax.leadora.api.dto.request.UpdateBillingMethodRequest;
import com.novax.leadora.api.dto.response.ContractResponse;
import com.novax.leadora.application.usecase.contract.*;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractController {

    private final GetContractListUseCase getContractListUseCase;
    private final GetContractByIdUseCase getContractByIdUseCase;
    private final SendContractUseCase sendContractUseCase;
    private final CancelContractUseCase cancelContractUseCase;
    private final UpdateContractBillingMethodUseCase updateContractBillingMethodUseCase;
    
    // Public UseCases
    private final GetContractByTokenUseCase getContractByTokenUseCase;
    private final RequestContractOtpUseCase requestContractOtpUseCase;
    private final ConfirmContractOtpUseCase confirmContractOtpUseCase;

    // --- INTERNAL/AUTHENTICATED ENDPOINTS ---

    /** Get all contracts visible to the current user */
    @GetMapping("/api/v1/contracts")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN','RESERVATION') and @access.can('QUOTATION_VIEW')")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> getContracts() {
        List<ContractEntity> contracts = getContractListUseCase.execute();
        List<ContractResponse> response = contracts.stream()
                .map(ContractResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Get contract by ID */
    @GetMapping("/api/v1/contracts/{id}")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','ADMIN','RESERVATION') and @access.can('QUOTATION_VIEW')")
    public ResponseEntity<ApiResponse<ContractResponse>> getContractById(@PathVariable("id") UUID id) {
        ContractEntity contract = getContractByIdUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }

    /** Update contract billing method before sending */
    @PutMapping("/api/v1/contracts/{id}/billing-method")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<ContractResponse>> updateBillingMethod(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateBillingMethodRequest request) {
        ContractEntity contract = updateContractBillingMethodUseCase.execute(id, request.getBillingMethod());
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }

    /** Generate PDF and send contract secure link to customer */
    @PostMapping("/api/v1/contracts/{id}/send")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<ContractResponse>> sendContract(@PathVariable("id") UUID id) {
        ContractEntity contract = sendContractUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }

    /** Cancel contract */
    @PostMapping("/api/v1/contracts/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES','MANAGER') and @access.can('QUOTATION_WRITE')")
    public ResponseEntity<ApiResponse<ContractResponse>> cancelContract(@PathVariable("id") UUID id) {
        ContractEntity contract = cancelContractUseCase.execute(id);
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }

    // --- PUBLIC/PORTAL ENDPOINTS (PermitAll) ---

    /** Public portal access: view contract details by secure token */
    @GetMapping("/api/v1/public/contracts/{id}")
    public ResponseEntity<ApiResponse<ContractResponse>> getPublicContract(
            @PathVariable("id") UUID id,
            @RequestParam("token") String token) {
        ContractEntity contract = getContractByTokenUseCase.execute(id, token);
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }

    /** Request OTP email to verification contact */
    @PostMapping("/api/v1/public/contracts/{id}/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestOtp(
            @PathVariable("id") UUID id,
            @RequestParam("token") String token) {
        requestContractOtpUseCase.execute(id, token);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Submit OTP verification to reach ACKNOWLEDGED state */
    @PostMapping("/api/v1/public/contracts/{id}/confirm-otp")
    public ResponseEntity<ApiResponse<ContractResponse>> confirmOtp(
            @PathVariable("id") UUID id,
            @RequestParam("token") String token,
            @Valid @RequestBody ConfirmOtpRequest request) {
        ContractEntity contract = confirmContractOtpUseCase.execute(id, token, request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(ContractResponse.fromEntity(contract)));
    }
}
