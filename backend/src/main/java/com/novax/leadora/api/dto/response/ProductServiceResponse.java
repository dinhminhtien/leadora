package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductServiceResponse {

    private UUID productId;
    private String name;
    private ProductCategory category;
    private String description;
    private BigDecimal unitPrice;
    private String unit;
    private ProductStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ProductServiceResponse from(ProductServiceEntity entity) {
        if (entity == null) return null;
        return ProductServiceResponse.builder()
                .productId(entity.getProductId())
                .name(entity.getName())
                .category(entity.getCategory())
                .description(entity.getDescription())
                .unitPrice(entity.getUnitPrice())
                .unit(entity.getUnit())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
