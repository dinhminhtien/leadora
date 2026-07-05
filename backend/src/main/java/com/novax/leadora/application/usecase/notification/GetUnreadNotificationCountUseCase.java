package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUnreadNotificationCountUseCase {

    private final NotificationRepository notificationRepository;

    /** Lightweight unread badge count — avoids fetching the full unread list. */
    @Transactional(readOnly = true)
    public long execute(UUID userId) {
        return notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
    }
}
