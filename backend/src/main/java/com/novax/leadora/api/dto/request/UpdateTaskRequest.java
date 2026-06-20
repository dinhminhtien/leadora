package com.novax.leadora.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    private String primaryContactName;
    private String primaryContactPhone;
}
