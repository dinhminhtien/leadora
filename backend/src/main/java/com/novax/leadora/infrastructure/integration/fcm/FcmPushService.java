package com.novax.leadora.infrastructure.integration.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.MessagingErrorCode;
import com.novax.leadora.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final UserDeviceTokenRepository userDeviceTokenRepository;

    @Transactional
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<UserDeviceTokenEntity> tokens = userDeviceTokenRepository.findByUser_UserId(userId);
        if (tokens.isEmpty()) {
            log.debug("No registered device tokens found for user {}", userId);
            return;
        }

        for (UserDeviceTokenEntity deviceToken : tokens) {
            String token = deviceToken.getFcmToken();
            try {
                Message.Builder messageBuilder = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build());

                if (data != null && !data.isEmpty()) {
                    // Filter out null values to prevent NPEs in Firebase Message Builder
                    data.forEach((key, val) -> {
                        if (key != null && val != null) {
                            messageBuilder.putData(key, val);
                        }
                    });
                }

                FirebaseMessaging.getInstance().send(messageBuilder.build());
                log.info("Successfully sent FCM notification to user {} on token: {}", userId, token);

            } catch (FirebaseMessagingException e) {
                log.warn("Failed to send FCM notification to token {} for user {}: {}", token, userId, e.getMessage());
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.info("Removing invalid/unregistered token {} for user {}", token, userId);
                    userDeviceTokenRepository.deleteByFcmToken(token);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM notification to token {} for user {}: {}", token, userId, e.getMessage(), e);
            }
        }
    }
}
