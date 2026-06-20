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
    private String recordedByName;
    private String recordedByRole;
}
