package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * UC-21.4 — Request to update payment status manually.
 */
@Getter
@Setter
public class UpdatePaymentStatusRequest {

    @NotNull(message = "Payment status is required")
    private PaymentStatus status;

    private String verificationNote;
}
