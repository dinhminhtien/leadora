package com.novax.leadora.unit.notification;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.novax.leadora.infrastructure.integration.fcm.FcmMessageSender;
import com.novax.leadora.infrastructure.integration.fcm.FcmPushService;
import com.novax.leadora.infrastructure.integration.fcm.PermanentFcmException;
import com.novax.leadora.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserDeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.*;

class FcmPushServiceTest {

    private UserDeviceTokenRepository userDeviceTokenRepository;
    private FcmMessageSender fcmMessageSender;
    private FcmPushService fcmPushService;

    @BeforeEach
    void setUp() {
        userDeviceTokenRepository = mock(UserDeviceTokenRepository.class);
        fcmMessageSender = mock(FcmMessageSender.class);
        fcmPushService = new FcmPushService(userDeviceTokenRepository, fcmMessageSender);
    }

    @Test
    void testSendToUserSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity tokenEntity = new UserDeviceTokenEntity();
        tokenEntity.setFcmToken("valid-token");
        tokenEntity.setId(UUID.randomUUID());

        when(userDeviceTokenRepository.findByUser_UserId(userId))
                .thenReturn(Collections.singletonList(tokenEntity));

        fcmPushService.sendToUser(userId, "Test Title", "Test Body", new HashMap<>());

        verify(fcmMessageSender, times(1)).sendMessage(any(Message.class));
        verify(userDeviceTokenRepository, never()).deleteByFcmToken(anyString());
    }

    @Test
    void testSendToUserPermanentExceptionDeletesToken() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity tokenEntity = new UserDeviceTokenEntity();
        tokenEntity.setFcmToken("invalid-token");

        when(userDeviceTokenRepository.findByUser_UserId(userId))
                .thenReturn(Collections.singletonList(tokenEntity));

        FirebaseMessagingException mockFcmEx = mock(FirebaseMessagingException.class);
        doThrow(new PermanentFcmException(mockFcmEx))
                .when(fcmMessageSender).sendMessage(any(Message.class));

        fcmPushService.sendToUser(userId, "Test Title", "Test Body", new HashMap<>());

        verify(userDeviceTokenRepository, times(1)).deleteByFcmToken("invalid-token");
    }

    @Test
    void testSendToUserTransientExceptionDoesNotDeleteToken() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity tokenEntity = new UserDeviceTokenEntity();
        tokenEntity.setFcmToken("transient-token");

        when(userDeviceTokenRepository.findByUser_UserId(userId))
                .thenReturn(Collections.singletonList(tokenEntity));

        FirebaseMessagingException mockFcmEx = mock(FirebaseMessagingException.class);
        doThrow(mockFcmEx)
                .when(fcmMessageSender).sendMessage(any(Message.class));

        fcmPushService.sendToUser(userId, "Test Title", "Test Body", new HashMap<>());

        verify(userDeviceTokenRepository, never()).deleteByFcmToken("transient-token");
    }
}
