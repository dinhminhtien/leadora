package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class CreateReminderRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    private String description;

    /** E4: validated in use case to be in the future */
    @NotNull(message = "Due date/time is required")
    private OffsetDateTime remindAt;

    /** HIGH | MEDIUM | LOW — defaults to MEDIUM */
    private String priority;

    /** PRE-2: the entity this reminder is linked to — QUOTATION, LEAD, BOOKING, DEPOSIT */
    @NotBlank(message = "Related entity type is required")
    private String relatedEntity;

    @NotNull(message = "Related entity ID is required")
    private UUID relatedId;

    /** Optional — if omitted, reminder is self-assigned to the creator */
    private UUID assignedUserId;
}
