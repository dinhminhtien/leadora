package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatSessionResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UC — Rename Chat Session. */
@Service
@RequiredArgsConstructor
public class RenameChatSessionUseCase {

    private final AiChatSessionRepository sessionRepository;

    @Transactional
    public ChatSessionResponse execute(UUID sessionId, UserEntity user, String title) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session", sessionId));

        if (session.getStatus() == ChatSessionStatus.DELETED
                || !session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Chat session", sessionId);
        }

        session.setTitle(title.trim());
        return ChatSessionResponse.from(sessionRepository.save(session));
    }
}
