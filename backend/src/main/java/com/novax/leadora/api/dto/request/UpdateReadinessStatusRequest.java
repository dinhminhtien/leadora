package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** UC-22.3 — Front Office updates the arrival readiness of a handover. */
@Getter
@Setter
public class UpdateReadinessStatusRequest {

    /** ReadinessStatus: REVIEWED | READY_FOR_ARRIVAL | NEED_CLARIFICATION. */
    @NotBlank(message = "Readiness status is required")
    private String readinessStatus;

    /** Required when readinessStatus = NEED_CLARIFICATION (UC-22.3, E7.2). */
    private String clarificationNote;
}
