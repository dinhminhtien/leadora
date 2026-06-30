package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.ReminderEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReminderResponse {

    private UUID reminderId;
    private String title;
    private String description;
    private OffsetDateTime remindAt;
    private String priority;
    private String status;
    private String relatedEntity;
    private UUID relatedId;

    private UUID assignedUserId;
    private String assignedUserName;

    private UUID createdByUserId;
    private String createdByName;

    private OffsetDateTime createdAt;

    public static ReminderResponse from(ReminderEntity e) {
        return ReminderResponse.builder()
                .reminderId(e.getReminderId())
                .title(e.getTitle())
                .description(e.getDescription())
                .remindAt(e.getRemindAt())
                .priority(e.getPriority() != null ? e.getPriority().name() : "MEDIUM")
                .status(e.getStatus().name())
                .relatedEntity(e.getRelatedEntity())
                .relatedId(e.getRelatedId())
                .assignedUserId(e.getUser() != null ? e.getUser().getUserId() : null)
                .assignedUserName(e.getUser() != null ? e.getUser().getFullName() : null)
                .createdByUserId(e.getCreatedBy() != null ? e.getCreatedBy().getUserId() : null)
                .createdByName(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : null)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
