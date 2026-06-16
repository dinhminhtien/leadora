package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
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

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    private String resultNote;
    private UUID leadId;
    private UUID customerId;
    private UUID dealId;
}
