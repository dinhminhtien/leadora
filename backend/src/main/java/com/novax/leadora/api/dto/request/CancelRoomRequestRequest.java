package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body for UC-26.4 cancel. Optional in full — the SRS asks only for a confirmation step,
 * not a justification — but when a reason is given it is carried into the audit trail so
 * the withdrawal is explicable months later.
 */
@Getter
@Setter
public class CancelRoomRequestRequest {

    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    private String reason;
}
