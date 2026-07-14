package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    /** UC-15.1 — paginated notification list, newest first */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> execute(UUID userId, Boolean unreadOnly, Pageable pageable) {
        Page<com.novax.leadora.infrastructure.persistence.entity.NotificationEntity> entities =
                Boolean.TRUE.equals(unreadOnly)
                        ? notificationRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                        : notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
        return entities.map(NotificationResponse::from);
    }
}
