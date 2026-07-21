package com.novax.leadora.unit.identity;

import com.novax.leadora.api.dto.request.LoginRequest;
import com.novax.leadora.api.dto.response.LoginResponse;
import com.novax.leadora.application.usecase.identity.EffectivePermissionsService;
import com.novax.leadora.application.usecase.identity.LoginActivityService;
import com.novax.leadora.application.usecase.identity.LoginUseCase;
import com.novax.leadora.common.security.JwtService;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtService jwtService;

        @Mock
        private LoginActivityService loginActivityService;

        @Mock
        private EffectivePermissionsService effectivePermissionsService;

        @InjectMocks
        private LoginUseCase loginUseCase;

        private UserEntity buildUser() {
                RoleEntity role = RoleEntity.builder().roleName("STAFF").build();
                return UserEntity.builder()
                                .userId(UUID.randomUUID())
                                .email("staff@leadora.vn")
                                .fullName("Nguyen Van Staff")
                                .passwordHash("$2a$10$encodedHash")
                                .role(role)
                                .build();
        }

        @Test
        @DisplayName("UT-LOGIN-01: Valid credentials → returns LoginResponse with JWT token")
        void testLoginSuccess() {
                UserEntity user = buildUser();
                LoginRequest request = LoginRequest.builder()
                                .email("staff@leadora.vn")
                                .password("Password123!")
                                .build();

                when(userRepository.findWithRoleByEmailIgnoreCase("staff@leadora.vn")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("Password123!", user.getPasswordHash())).thenReturn(true);
                when(jwtService.generateToken(user)).thenReturn("jwt-token-123");
                when(effectivePermissionsService.forUser(user)).thenReturn(Collections.emptyList());
                doNothing().when(loginActivityService).applyOnLogin(user);

                LoginResponse response = loginUseCase.execute(request);

                assertNotNull(response);
                assertEquals("jwt-token-123", response.getAccessToken());
                assertNotNull(response.getUser());
                assertEquals("staff@leadora.vn", response.getUser().getEmail());
                verify(loginActivityService).applyOnLogin(user);
        }

        @Test
        @DisplayName("UT-LOGIN-02: Invalid email (not found) → throws IllegalStateException")
        void testLoginInvalidEmailThrows() {
                LoginRequest request = LoginRequest.builder()
                                .email("unknown@leadora.vn")
                                .password("Password123!")
                                .build();

                when(userRepository.findWithRoleByEmailIgnoreCase("unknown@leadora.vn")).thenReturn(Optional.empty());

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> loginUseCase.execute(request));
                assertEquals("Invalid email or password.", ex.getMessage());
        }

        @Test
        @DisplayName("UT-LOGIN-03: Wrong password → throws IllegalStateException")
        void testLoginWrongPasswordThrows() {
                UserEntity user = buildUser();
                LoginRequest request = LoginRequest.builder()
                                .email("staff@leadora.vn")
                                .password("WrongPassword!")
                                .build();

                when(userRepository.findWithRoleByEmailIgnoreCase("staff@leadora.vn")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("WrongPassword!", user.getPasswordHash())).thenReturn(false);

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                                () -> loginUseCase.execute(request));
                assertEquals("Invalid email or password.", ex.getMessage());
        }

        @Test
        @DisplayName("UT-LOGIN-04: Email with whitespace → trimmed before lookup")
        void testLoginTrimmedEmail() {
                UserEntity user = buildUser();
                LoginRequest request = LoginRequest.builder()
                                .email("  staff@leadora.vn  ")
                                .password("Password123!")
                                .build();

                when(userRepository.findWithRoleByEmailIgnoreCase("staff@leadora.vn")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("Password123!", user.getPasswordHash())).thenReturn(true);
                when(jwtService.generateToken(user)).thenReturn("jwt-token-trimmed");
                when(effectivePermissionsService.forUser(user)).thenReturn(Collections.emptyList());
                doNothing().when(loginActivityService).applyOnLogin(user);

                LoginResponse response = loginUseCase.execute(request);

                assertNotNull(response);
                assertEquals("jwt-token-trimmed", response.getAccessToken());
        }
}
