package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomAvailabilityResponse {

    private UUID productId;
    private String name;
    private ProductCategory category;
    private BigDecimal unitPrice;
    private String unit;
    private Integer totalBooked;
    private Boolean isAvailable;
}
