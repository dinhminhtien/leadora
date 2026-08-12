package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.ReservationRejectReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationRejectRequest {
    @NotNull(message = "Rejection reason is mandatory.")
    private ReservationRejectReason reason;

    private String note;
}
