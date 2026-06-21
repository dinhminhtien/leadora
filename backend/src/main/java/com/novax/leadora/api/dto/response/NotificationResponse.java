package com.novax.leadora.api.dto.response;

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
    private String relatedEntity;
    private UUID relatedId;
    private boolean isRead;
    private OffsetDateTime createdAt;

    public static NotificationResponse from(NotificationEntity entity) {
        return NotificationResponse.builder()
                .id(entity.getNotificationId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .relatedEntity(entity.getRelatedEntity())
                .relatedId(entity.getRelatedId())
                .isRead(Boolean.TRUE.equals(entity.getIsRead()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
