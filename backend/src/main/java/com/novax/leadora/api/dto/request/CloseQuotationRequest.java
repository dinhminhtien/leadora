package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CloseQuotationRequest {

    @NotBlank(message = "Closure reason is required")
    private String reason;

    private String notes;

    // closedByName/closedByRole intentionally removed — actor is resolved server-side
    // from the authenticated session (BR-37), never trusted from the client.
}
