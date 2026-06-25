package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.ProductServiceResponse;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.repository.ProductServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/product-services")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SALES','MANAGER')")
public class ProductServiceController {

    private final ProductServiceRepository productServiceRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductServiceResponse>>> getProductServices(
            @RequestParam(required = false) String category
    ) {
        List<ProductServiceEntity> products;
        if (category != null && !category.trim().isEmpty()) {
            try {
                ProductCategory productCategory = ProductCategory.valueOf(category.trim().toUpperCase());
                products = productServiceRepository.findByCategory(productCategory);
            } catch (IllegalArgumentException e) {
                products = productServiceRepository.findAll();
            }
        } else {
            products = productServiceRepository.findAll();
        }
        List<ProductServiceResponse> responses = products.stream()
                .map(ProductServiceResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
