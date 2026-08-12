package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRejectRequest {
    @NotBlank(message = "Rejection reason is mandatory.")
    private String reason;
}
