package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelReservationRequest {

    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
