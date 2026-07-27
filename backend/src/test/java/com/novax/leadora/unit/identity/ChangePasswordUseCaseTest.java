package com.novax.leadora.unit.identity;

import com.novax.leadora.api.dto.request.ChangePasswordRequest;
import com.novax.leadora.application.usecase.identity.ChangePasswordUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordUseCaseTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ChangePasswordUseCase changePasswordUseCase;

    private UserEntity buildUser() {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .email("staff@leadora.vn")
                .passwordHash("$2a$10$encodedOldPassword")
                .build();
    }

    private ChangePasswordRequest buildRequest(String current, String newPwd) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(newPwd);
        return req;
    }

    @Test
    @DisplayName("UT-CHANGEPWD-01: Correct current password and valid new password → success")
    void testChangePasswordSuccess() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("OldPass123!", "NewPass456@");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("NewPass456@", user.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewPass456@")).thenReturn("$2a$10$encodedNewPassword");

        assertDoesNotThrow(() -> changePasswordUseCase.execute("user-id", request));
        verify(userRepository, times(1)).save(user);
        assertEquals("$2a$10$encodedNewPassword", user.getPasswordHash());
    }

    @Test
    @DisplayName("UT-CHANGEPWD-02: Incorrect current password → throws INCORRECT_CURRENT_PASSWORD")
    void testIncorrectCurrentPasswordThrows() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("WrongPass!", "NewPass456@");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("WrongPass!", user.getPasswordHash())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changePasswordUseCase.execute("user-id", request));
        assertEquals("INCORRECT_CURRENT_PASSWORD", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CHANGEPWD-03: New password same as current → throws SAME_PASSWORD")
    void testSamePasswordThrows() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("SamePass123!", "SamePass123!");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("SamePass123!", user.getPasswordHash())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changePasswordUseCase.execute("user-id", request));
        assertEquals("SAME_PASSWORD", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CHANGEPWD-04: Weak password (no uppercase) → throws WEAK_PASSWORD")
    void testWeakPasswordNoUppercase() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("OldPass123!", "weakpass1!");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("weakpass1!", user.getPasswordHash())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changePasswordUseCase.execute("user-id", request));
        assertEquals("WEAK_PASSWORD", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHANGEPWD-05: Weak password (no digit) → throws WEAK_PASSWORD")
    void testWeakPasswordNoDigit() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("OldPass123!", "WeakPass!");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("WeakPass!", user.getPasswordHash())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changePasswordUseCase.execute("user-id", request));
        assertEquals("WEAK_PASSWORD", ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-CHANGEPWD-06: Weak password (no symbol) → throws WEAK_PASSWORD")
    void testWeakPasswordNoSymbol() {
        UserEntity user = buildUser();
        ChangePasswordRequest request = buildRequest("OldPass123!", "WeakPass1");

        when(currentUserProvider.resolve(anyString())).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("WeakPass1", user.getPasswordHash())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> changePasswordUseCase.execute("user-id", request));
        assertEquals("WEAK_PASSWORD", ex.getErrorCode());
    }
}
