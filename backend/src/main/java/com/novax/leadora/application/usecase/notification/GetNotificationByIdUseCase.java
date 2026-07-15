package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.api.dto.response.NotificationResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationByIdUseCase {

    private static final Set<String> FULL_ACCESS_ROLES = Set.of("MANAGER", "ADMIN");

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public NotificationResponse execute(UUID notificationId) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        UserEntity currentUser = currentUserProvider.resolve(null);
        UserEntity recipient = entity.getUser();
        boolean isOwn = recipient != null && currentUser.getUserId().equals(recipient.getUserId());
        if (!isOwn) {
            String role = currentUser.getRole() != null && currentUser.getRole().getRoleName() != null
                    ? currentUser.getRole().getRoleName().trim().toUpperCase() : "";
            if (!FULL_ACCESS_ROLES.contains(role)) {
                throw new AccessDeniedException("You do not have permission to access this notification.");
            }
        }

        // UC-15.2 POST-3: auto mark as read on access — only for your own notification;
        // a Manager/Admin browsing someone else's must not flip their read state.
        if (isOwn && !Boolean.TRUE.equals(entity.getIsRead())) {
            entity.setIsRead(true);
            notificationRepository.save(entity);
        }
        return NotificationResponse.from(entity);
    }
}
