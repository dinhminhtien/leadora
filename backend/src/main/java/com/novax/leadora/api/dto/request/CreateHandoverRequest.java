package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * UC-20.1 — Request to create a new operational handover.
 */
@Getter
@Setter
public class CreateHandoverRequest {

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    private String specialRequests;
    private String roomPreferences;
    private String vipNotes;
    private String operationalNotes;

    private UUID assignedFoUserId;

    /** HandoverStatus: DRAFT | SUBMITTED. */
    @NotBlank(message = "Handover status is required")
    private String status;
}
