package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatMessageRepository;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** UC — Continue Existing Chat Session: load the full message history of a session. */
@Service
@RequiredArgsConstructor
public class GetChatMessagesUseCase {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> execute(UUID sessionId, UserEntity user) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        if (session.getStatus() == ChatSessionStatus.DELETED
                || !session.getUser().getUserId().equals(user.getUserId())) {
            // Hide existence of sessions the caller does not own.
            throw new ResourceNotFoundException("Chat session not found: " + sessionId);
        }

        return messageRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
