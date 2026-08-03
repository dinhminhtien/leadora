package com.novax.leadora.infrastructure.integration.fcm;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
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
    private final FcmMessageSender fcmMessageSender;

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

                fcmMessageSender.sendMessage(messageBuilder.build());
                log.info("Successfully sent FCM notification to user {} on token: {}", userId, token);

            } catch (PermanentFcmException e) {
                log.warn("Permanent FCM failure for token {} (user {}): {}", token, userId, e.getMessage());
                log.info("Removing invalid/unregistered token {} for user {}", token, userId);
                userDeviceTokenRepository.deleteByFcmToken(token);
            } catch (FirebaseMessagingException e) {
                log.warn("Transient FCM failure (retries exhausted) for token {} (user {}): {}", token, userId, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error sending FCM notification to token {} for user {}: {}", token, userId, e.getMessage(), e);
            }
        }
    }
}
