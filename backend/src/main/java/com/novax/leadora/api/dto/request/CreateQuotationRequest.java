package com.novax.leadora.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateQuotationRequest {

    @NotNull(message = "Deal ID is required")
    private UUID dealId;

    @NotEmpty(message = "At least one room type is required")
    @Valid
    private List<RoomLineRequest> roomLines;

    @NotNull(message = "Check-in date is required")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is required")
    private LocalDate checkOutDate;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercent;

    private String paymentPolicy;

    @NotNull(message = "Valid until date is required")
    private LocalDate validUntil;

    private String notes;
}
