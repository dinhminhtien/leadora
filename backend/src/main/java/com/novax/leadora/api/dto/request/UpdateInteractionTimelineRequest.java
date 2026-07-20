package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UpdateInteractionTimelineRequest {

    @NotBlank(message = "Interaction type is required")
    @Pattern(
            regexp = "^(CALL|EMAIL|MEETING|NOTE|SITE_VISIT|OTHER)$",
            message = "Type must be one of: CALL, EMAIL, MEETING, NOTE, SITE_VISIT, OTHER"
    )
    private String type;           // CALL | EMAIL | MEETING | NOTE | SITE_VISIT | OTHER

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Occurred date is required")
    @PastOrPresent(message = "Occurred date cannot be in the future")
    private OffsetDateTime occurredAt;
}
