package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReviseQuotationRequest {

    @NotBlank(message = "roomType is required")
    private String roomType;

    @NotNull(message = "checkInDate is required")
    private LocalDate checkInDate;

    @NotNull(message = "checkOutDate is required")
    private LocalDate checkOutDate;

    @NotNull(message = "numberOfRooms is required")
    @Min(value = 1, message = "At least 1 room")
    private Integer numberOfRooms;

    @NotNull(message = "pricePerNight is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal pricePerNight;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercent;

    private String paymentPolicy;

    @NotNull(message = "Valid until date is required")
    private LocalDate validUntil;

    private String notes;

    @NotBlank(message = "changeReason is required")
    private String changeReason;

    private String revisedByName;
    private String revisedByRole;
}
