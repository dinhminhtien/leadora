package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatSessionResponse {

    private UUID sessionId;
    private String title;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ChatSessionResponse from(AiChatSessionEntity session) {
        return ChatSessionResponse.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .status(session.getStatus() != null ? session.getStatus().name() : null)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
