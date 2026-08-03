package com.novax.leadora.infrastructure.integration.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FcmMessageSender {

    @Retryable(
        retryFor = { FirebaseMessagingException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendMessage(Message message) throws FirebaseMessagingException {
        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Permanent FCM error (code: {}): {}. Skipping retry.", errorCode, e.getMessage());
                throw new PermanentFcmException(e);
            }
            log.warn("Transient FCM error (code: {}): {}. Attempting retry...", errorCode, e.getMessage());
            throw e;
        }
    }
}
