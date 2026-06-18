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
    private String closedByName;
    private String closedByRole;
}
