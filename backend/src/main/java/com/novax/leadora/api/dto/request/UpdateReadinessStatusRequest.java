package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** UC-22.3 — Front Office updates the arrival readiness of a handover. */
@Getter
@Setter
public class UpdateReadinessStatusRequest {

    /** ReadinessStatus: PENDING | IN_PROGRESS | READY. */
    @NotBlank(message = "Trạng thái sẵn sàng không được để trống")
    private String readinessStatus;
}
