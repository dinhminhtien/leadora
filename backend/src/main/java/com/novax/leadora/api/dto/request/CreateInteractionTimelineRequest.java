package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class CreateInteractionTimelineRequest {

    @NotBlank(message = "Interaction type is required")
    private String type;           // "call" | "email" | "meeting" | "note"

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Occurred date is required")
    private OffsetDateTime occurredAt;

    private UUID leadId;
    private UUID customerId;
    private UUID dealId;
}
