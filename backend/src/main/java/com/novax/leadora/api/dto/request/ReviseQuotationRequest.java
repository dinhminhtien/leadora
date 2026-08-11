package com.novax.leadora.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReviseQuotationRequest {

    @NotEmpty(message = "At least one room type is required")
    @Valid
    private List<RoomLineRequest> roomLines;

    @NotNull(message = "checkInDate is required")
    private LocalDate checkInDate;

    @NotNull(message = "checkOutDate is required")
    private LocalDate checkOutDate;

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
