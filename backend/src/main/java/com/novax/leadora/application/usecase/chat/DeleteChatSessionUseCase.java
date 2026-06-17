package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UC — Delete Chat Session (soft delete: status → DELETED, keeps the audit trail). */
@Service
@RequiredArgsConstructor
public class DeleteChatSessionUseCase {

    private final AiChatSessionRepository sessionRepository;

    @Transactional
    public void execute(UUID sessionId, UserEntity user) {
        AiChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Chat session not found: " + sessionId);
        }

        if (session.getStatus() != ChatSessionStatus.DELETED) {
            session.setStatus(ChatSessionStatus.DELETED);
            sessionRepository.save(session);
        }
    }
}
