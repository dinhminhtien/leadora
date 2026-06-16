package com.novax.leadora.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class UpdateTaskRequest {

    private String title;
    private String description;
    private UUID assignedUserId;
    private String priority;
    private LocalDate dueDate;
    private String status;
    private String resultNote;
    private UUID leadId;
    private UUID customerId;
    private UUID dealId;
}
