package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomLineRequest {

    @NotBlank(message = "Room type is required")
    private String roomType;

    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "Number of rooms must be at least 1")
    private Integer numberOfRooms;

    @NotNull(message = "Price per night is required")
    @DecimalMin(value = "0.0", message = "Price per night must be non-negative")
    private BigDecimal pricePerNight;
}
