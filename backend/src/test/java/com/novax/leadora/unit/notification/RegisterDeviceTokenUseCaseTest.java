package com.novax.leadora.unit.notification;

import com.novax.leadora.application.usecase.notification.RegisterDeviceTokenUseCase;
import com.novax.leadora.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserDeviceTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterDeviceTokenUseCaseTest {

    @Mock
    private UserDeviceTokenRepository userDeviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RegisterDeviceTokenUseCase registerDeviceTokenUseCase;

    @Test
    @DisplayName("UT-DEVICE-TOKEN-01: Empty or null token should not do anything")
    void testRegisterWithEmptyTokenDoesNothing() {
        registerDeviceTokenUseCase.register(UUID.randomUUID(), "", "Android 14");
        registerDeviceTokenUseCase.register(UUID.randomUUID(), null, "Android 14");

        verifyNoInteractions(userDeviceTokenRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("UT-DEVICE-TOKEN-02: Existing token is updated with new device info")
    void testRegisterExistingTokenUpdatesDeviceInfo() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-123";
        String originalDeviceInfo = "Android 13";
        String newDeviceInfo = "Android 14";

        UserEntity user = UserEntity.builder().userId(userId).build();
        UserDeviceTokenEntity existingEntity = UserDeviceTokenEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fcmToken(token)
                .deviceInfo(originalDeviceInfo)
                .build();

        when(userDeviceTokenRepository.findByUser_UserIdAndFcmToken(userId, token))
                .thenReturn(Optional.of(existingEntity));

        registerDeviceTokenUseCase.register(userId, token, newDeviceInfo);

        assertEquals(newDeviceInfo, existingEntity.getDeviceInfo());
        verify(userDeviceTokenRepository).save(existingEntity);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("UT-DEVICE-TOKEN-03: New token registers successfully")
    void testRegisterNewTokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-123";
        String deviceInfo = "Android 14";

        UserEntity user = UserEntity.builder().userId(userId).build();

        when(userDeviceTokenRepository.findByUser_UserIdAndFcmToken(userId, token))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        registerDeviceTokenUseCase.register(userId, token, deviceInfo);

        verify(userDeviceTokenRepository).save(argThat(entity -> 
            entity.getUser().getUserId().equals(userId) &&
            entity.getFcmToken().equals(token) &&
            entity.getDeviceInfo().equals(deviceInfo)
        ));
    }

    @Test
    @DisplayName("UT-DEVICE-TOKEN-04: Non-existing user throws IllegalArgumentException")
    void testRegisterNewTokenNonExistingUserThrows() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-123";

        when(userDeviceTokenRepository.findByUser_UserIdAndFcmToken(userId, token))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> 
            registerDeviceTokenUseCase.register(userId, token, "Android 14")
        );
        verify(userDeviceTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-DEVICE-TOKEN-05: Unregister removes the token successfully")
    void testUnregisterRemovesToken() {
        UUID userId = UUID.randomUUID();
        String token = "fcm-token-123";

        registerDeviceTokenUseCase.unregister(userId, token);

        verify(userDeviceTokenRepository).deleteByUser_UserIdAndFcmToken(userId, token);
    }
}
