package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessBookingRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String rejectionReason;
}
