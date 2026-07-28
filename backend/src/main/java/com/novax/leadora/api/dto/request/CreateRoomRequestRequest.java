package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Sales asks the Reservation team whether rooms are available for a quotation.
 *
 * <p>Room type and dates are deliberately NOT accepted here — they are read from the
 * quotation so the question can never drift from what the customer is being quoted.
 * Change the quotation first if the room type or dates need to change.
 */
@Getter
@Setter
public class CreateRoomRequestRequest {

    @NotNull(message = "Quotation ID is required")
    private UUID quotationId;

    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "Number of rooms must be at least 1")
    private Integer quantity;
}
