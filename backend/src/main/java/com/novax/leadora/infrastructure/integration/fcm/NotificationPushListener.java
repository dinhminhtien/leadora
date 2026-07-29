package com.novax.leadora.infrastructure.integration.fcm;

import com.novax.leadora.application.usecase.notification.NotificationCreatedEvent;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final FcmPushService fcmPushService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        NotificationEntity notification = event.getNotification();
        if (notification == null || notification.getUser() == null) {
            return;
        }

        log.info("Received NotificationCreatedEvent for user {}", notification.getUser().getUserId());

        Map<String, String> data = new HashMap<>();
        data.put("id", notification.getNotificationId().toString());
        
        if (notification.getType() != null) {
            data.put("type", notification.getType());
        }
        if (notification.getRelatedEntity() != null) {
            data.put("relatedEntity", notification.getRelatedEntity());
        }
        if (notification.getRelatedId() != null) {
            data.put("relatedId", notification.getRelatedId().toString());
        }

        fcmPushService.sendToUser(
                notification.getUser().getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                data
        );
    }
}
