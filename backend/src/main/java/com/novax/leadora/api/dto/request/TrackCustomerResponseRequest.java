package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TrackCustomerResponseRequest {

    @NotBlank(message = "Customer response is required")
    private String customerResponse; // ACCEPTED | REJECTED | INTERESTED | NEED_REVISION

    private String lostReason;
    private String notes;

    // recordedByName/recordedByRole intentionally removed — actor is resolved server-side
    // from the authenticated session (BR-37), never trusted from the client.
}
