package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {

    private UUID taskId;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalDate dueDate;
    private String resultNote;

    private UUID assignedUserId;
    private String assignedUserName;

    private UUID createdById;
    private String createdByName;

    private UUID leadId;
    private String leadName;

    private UUID customerId;
    private String customerName;

    private UUID dealId;
    private String dealName;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TaskResponse from(TaskEntity entity) {
        return TaskResponse.builder()
                .taskId(entity.getTaskId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .dueDate(entity.getDueDate())
                .resultNote(entity.getResultNote())
                .assignedUserId(entity.getAssignedUser() != null ? entity.getAssignedUser().getUserId() : null)
                .assignedUserName(entity.getAssignedUser() != null ? entity.getAssignedUser().getFullName() : null)
                .createdById(entity.getCreatedBy() != null ? entity.getCreatedBy().getUserId() : null)
                .createdByName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : null)
                .leadId(entity.getLead() != null ? entity.getLead().getLeadId() : null)
                .leadName(entity.getLead() != null ? entity.getLead().getFullName() : null)
                .customerId(entity.getCustomer() != null ? entity.getCustomer().getCustomerId() : null)
                .customerName(entity.getCustomer() != null ? entity.getCustomer().getFullName() : null)
                .dealId(entity.getDeal() != null ? entity.getDeal().getDealId() : null)
                .dealName(entity.getDeal() != null ? entity.getDeal().getDealName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
