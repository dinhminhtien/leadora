package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * UC-20.4 — Request to update an operational handover.
 */
@Getter
@Setter
public class UpdateHandoverRequest {

    private String specialRequests;
    private String roomPreferences;
    private String vipNotes;
    private String operationalNotes;

    private UUID assignedFoUserId;

    /** HandoverStatus: DRAFT | SUBMITTED. */
    @NotBlank(message = "Handover status is required")
    @Pattern(regexp = "^(DRAFT|SUBMITTED)$", message = "Status must be DRAFT or SUBMITTED")
    private String status;
}
