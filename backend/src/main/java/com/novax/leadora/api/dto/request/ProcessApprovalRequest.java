package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessApprovalRequest {

    @NotBlank(message = "action is required")
    private String action; // APPROVE | REJECT | REQUEST_CHANGES

    @NotBlank(message = "managerName is required")
    private String managerName;

    private String managerRole;

    private String notes;
}
