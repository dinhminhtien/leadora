package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateCustomerRequest;
import com.novax.leadora.api.dto.request.UpdateCustomerRequest;
import com.novax.leadora.api.dto.response.CustomerHistoryItem;
import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.application.usecase.customer.CreateCustomerUseCase;
import com.novax.leadora.application.usecase.customer.GetCustomerDetailUseCase;
import com.novax.leadora.application.usecase.customer.GetCustomerHistoryUseCase;
import com.novax.leadora.application.usecase.customer.GetCustomerListUseCase;
import com.novax.leadora.application.usecase.customer.GetCustomerStatsUseCase;
import com.novax.leadora.application.usecase.customer.SearchCustomersUseCase;
import com.novax.leadora.application.usecase.customer.SearchCustomersUseCase.CustomerSearchResult;
import com.novax.leadora.application.usecase.customer.UpdateCustomerUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final SearchCustomersUseCase searchCustomersUseCase;
    private final GetCustomerListUseCase getCustomerListUseCase;
    private final GetCustomerDetailUseCase getCustomerDetailUseCase;
    private final GetCustomerHistoryUseCase getCustomerHistoryUseCase;
    private final GetCustomerStatsUseCase getCustomerStatsUseCase;
    private final CreateCustomerUseCase createCustomerUseCase;
    private final UpdateCustomerUseCase updateCustomerUseCase;

    /** Autocomplete search — lightweight DTOs for dropdowns. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerSearchResult>>> searchCustomers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(searchCustomersUseCase.execute(search, size)));
    }

    /** Global counts — not affected by search/filter state. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<GetCustomerStatsUseCase.CustomerStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(getCustomerStatsUseCase.execute()));
    }

    /** Full paginated customer list with filters — for the Customer Profiles screen. */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> listCustomers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<CustomerResponse> result = getCustomerListUseCase.execute(
                search, customerType, status, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Customer detail. */
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable UUID customerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(getCustomerDetailUseCase.execute(customerId)));
    }

    /** Create a new customer profile. */
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createCustomerUseCase.execute(request)));
    }

    /** Full activity history — deals, bookings, quotations — sorted newest first. */
    @GetMapping("/{customerId}/history")
    public ResponseEntity<ApiResponse<List<CustomerHistoryItem>>> getCustomerHistory(
            @PathVariable UUID customerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(getCustomerHistoryUseCase.execute(customerId)));
    }

    /** Update an existing customer profile. */
    @PutMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(updateCustomerUseCase.execute(customerId, request)));
    }
}
