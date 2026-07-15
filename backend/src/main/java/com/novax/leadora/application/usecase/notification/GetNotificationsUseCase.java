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

    /**
     * UC-15.1 — paginated notification list, newest first.
     *
     * @param userId whose notifications to list, or {@code null} for the Manager/Admin
     *               aggregate feed across every user (see {@code NotificationController}
     *               for the role check that gates {@code null}).
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> execute(UUID userId, Boolean unreadOnly, Pageable pageable) {
        boolean unread = Boolean.TRUE.equals(unreadOnly);
        Page<com.novax.leadora.infrastructure.persistence.entity.NotificationEntity> entities;
        if (userId == null) {
            entities = unread
                    ? notificationRepository.findByIsReadFalseOrderByCreatedAtDesc(pageable)
                    : notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            entities = unread
                    ? notificationRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                    : notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return entities.map(NotificationResponse::from);
    }
}
