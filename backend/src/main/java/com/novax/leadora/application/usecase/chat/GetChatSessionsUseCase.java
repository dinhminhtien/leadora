package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatSessionResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC — View Chat Session List (active sessions of the current user, most recent first). */
@Service
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final AiChatSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> execute(UserEntity user) {
        return sessionRepository
                .findByUser_UserIdAndStatusOrderByUpdatedAtDesc(user.getUserId(), ChatSessionStatus.ACTIVE)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }
}
