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
    /**
     * Units this CRM has already committed for the requested date range. NOT a
     * remaining-availability figure: the CRM does not know the hotel's capacity, so it
     * cannot compute one. The Reservation team reconciles this against the real PMS.
     *
     * <p>The former {@code isAvailable} flag was removed with the invented per-name
     * capacity table it was derived from.
     */
    private Integer totalBooked;
}
