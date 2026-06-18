package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.InventoryStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDetailResponse {

    private UUID bookingDetailId;
    private UUID productId;
    private String productName;
    private String roomNumber;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer nights;
    private BigDecimal lineTotal;
    private InventoryStatus inventoryStatus;

    public static BookingDetailResponse from(BookingDetailEntity entity) {
        return BookingDetailResponse.builder()
                .bookingDetailId(entity.getBookingDetailId())
                .productId(entity.getProductService() != null ? entity.getProductService().getProductId() : null)
                .productName(entity.getProductService() != null ? entity.getProductService().getName() : null)
                .roomNumber(entity.getRoomNumber())
                .quantity(entity.getQuantity())
                .unitPrice(entity.getUnitPrice())
                .nights(entity.getNights())
                .lineTotal(entity.getLineTotal())
                .inventoryStatus(entity.getInventoryStatus())
                .build();
    }
}
