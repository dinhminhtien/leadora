package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.BillingMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBillingMethodRequest {
    @NotNull(message = "Billing method cannot be null")
    private BillingMethod billingMethod;
}
