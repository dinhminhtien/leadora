package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationsUseCase {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> execute(UUID userId, Boolean unreadOnly) {
        var entities = Boolean.TRUE.equals(unreadOnly)
                ? notificationRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
        return entities.stream().map(NotificationResponse::from).toList();
    }
}
