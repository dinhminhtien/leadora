package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatSessionResponse;
import com.novax.leadora.infrastructure.persistence.entity.AiChatSessionEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ChatSessionStatus;
import com.novax.leadora.infrastructure.persistence.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** UC — Create New Chat Session. */
@Service
@RequiredArgsConstructor
public class CreateChatSessionUseCase {

    private static final String DEFAULT_TITLE = "Cuộc trò chuyện mới";

    private final AiChatSessionRepository sessionRepository;

    @Transactional
    public ChatSessionResponse execute(UserEntity user, String title) {
        AiChatSessionEntity session = AiChatSessionEntity.builder()
                .user(user)
                .title(StringUtils.hasText(title) ? title.trim() : DEFAULT_TITLE)
                .status(ChatSessionStatus.ACTIVE)
                .build();
        return ChatSessionResponse.from(sessionRepository.save(session));
    }
}
