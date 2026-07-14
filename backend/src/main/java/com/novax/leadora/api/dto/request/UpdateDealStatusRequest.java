package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Close an open deal as won or lost without walking the stage funnel.
 *
 * <p>Accepts either the response wire strings ("active"/"won"/"lost") or the
 * entity enum names ("OPEN"/"WON"/"LOST"); {@code DealMapper.mapStatusToEnum}
 * resolves both. The pattern keeps an unknown value a 400 rather than letting
 * the mapper silently fall back to OPEN.
 */
@Getter
@Setter
public class UpdateDealStatusRequest {

    @NotBlank(message = "Deal status is required")
    @Pattern(
            regexp = "(?i)^(active|open|won|lost)$",
            message = "Status must be one of: active, won, lost")
    private String status;
}
