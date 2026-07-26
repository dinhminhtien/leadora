package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessApprovalRequest {

    @NotBlank(message = "action is required")
    private String action; // APPROVE | REJECT | REQUEST_CHANGES

    // managerName/managerRole intentionally removed — actor is resolved server-side
    // from the authenticated session (BR-37), never trusted from the client.

    private String notes;
}
