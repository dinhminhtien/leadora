package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse execute(UUID notificationId, boolean markAsRead) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        entity.setIsRead(markAsRead);
        NotificationEntity saved = notificationRepository.save(entity);
        return NotificationResponse.from(saved);
    }
}
