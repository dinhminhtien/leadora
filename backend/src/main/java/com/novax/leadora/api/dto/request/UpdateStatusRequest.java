package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateStatusRequest {

    @NotBlank(message = "Booking status is required")
    private String status;

    private String reason;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;
}
