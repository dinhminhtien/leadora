package com.novax.leadora.api.controller;

import com.novax.leadora.application.usecase.customer.SearchCustomersUseCase;
import com.novax.leadora.application.usecase.customer.SearchCustomersUseCase.CustomerSearchResult;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class CustomerController {

    private final SearchCustomersUseCase searchCustomersUseCase;

    /**
     * Search customers for autocomplete — returns lightweight DTOs.
     * Matches frontend CustomerProfile type: {id, name, email, phone, company}
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerSearchResult>>> searchCustomers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<CustomerSearchResult> results = searchCustomersUseCase.execute(search, size);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
