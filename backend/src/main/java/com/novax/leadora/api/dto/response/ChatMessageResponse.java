package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.AiChatMessageEntity;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatMessageResponse {

    private UUID messageId;
    private UUID sessionId;
    private String role;
    private String content;
    /** The intent the guardrail/classifier matched for this turn (assistant messages only). */
    private String intentMatched;
    private OffsetDateTime createdAt;

    public static ChatMessageResponse from(AiChatMessageEntity message) {
        return ChatMessageResponse.builder()
                .messageId(message.getMessageId())
                .sessionId(message.getSession() != null ? message.getSession().getSessionId() : null)
                .role(message.getRole() != null ? message.getRole().name() : null)
                .content(message.getContent())
                .intentMatched(message.getIntentMatched())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
