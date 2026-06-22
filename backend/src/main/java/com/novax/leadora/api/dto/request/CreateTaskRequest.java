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
public class CreateTaskRequest {

    @NotBlank(message = "Task title is required")
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull(message = "Assigned user is required")
    private UUID assignedUserId;

    @NotNull(message = "Priority is required")
    private String priority;

    private String resultNote;
    private UUID leadId;
    private UUID customerId;
    private UUID dealId;

    private String primaryContactName;
    private String primaryContactPhone;

    /** Timeline scheduling — both optional; null means task has no scheduled time block. */
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
}
