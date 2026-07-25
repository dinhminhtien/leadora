package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID id;
    private String title;
    private String message;
    private String type;
    private String priority;
    private String relatedEntity;
    private UUID relatedId;
    @JsonProperty("isRead")
    private boolean isRead;
    private OffsetDateTime createdAt;
    // Who the notification was sent to — only meaningful in the Manager/Admin
    // aggregate feed (GET /notifications?allUsers=true); harmless elsewhere.
    private UUID recipientId;
    private String recipientName;

    public static NotificationResponse from(NotificationEntity entity) {
        var recipient = entity.getUser();
        return NotificationResponse.builder()
                .id(entity.getNotificationId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .priority(entity.getPriority() != null ? entity.getPriority().name() : null)
                .relatedEntity(entity.getRelatedEntity())
                .relatedId(entity.getRelatedId())
                .isRead(Boolean.TRUE.equals(entity.getIsRead()))
                .createdAt(entity.getCreatedAt())
                .recipientId(recipient != null ? recipient.getUserId() : null)
                .recipientName(recipient != null ? recipient.getFullName() : null)
                .build();
    }
}
