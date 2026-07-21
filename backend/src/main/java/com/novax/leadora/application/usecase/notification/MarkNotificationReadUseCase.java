package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public NotificationResponse execute(UUID notificationId, boolean markAsRead) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        // Only the recipient may toggle their own read state — Manager/Admin viewing the
        // aggregate feed (GET /notifications?allUsers=true) can see other users' rows but
        // must not be able to silently mark them read/unread on someone else's behalf.
        UserEntity currentUser = currentUserProvider.resolve(null);
        UserEntity recipient = entity.getUser();
        if (recipient == null || !currentUser.getUserId().equals(recipient.getUserId())) {
            throw new AccessDeniedException("You can only update your own notifications.");
        }

        entity.setIsRead(markAsRead);
        NotificationEntity saved = notificationRepository.save(entity);
        return NotificationResponse.from(saved);
    }
}
