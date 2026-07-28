package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.LoginRequest;
import com.novax.leadora.api.dto.response.LoginResponse;
import com.novax.leadora.common.security.JwtService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import java.util.UUID;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final LoginActivityService loginActivityService;
        private final EffectivePermissionsService effectivePermissionsService;
        private final ActivityLogPublisher activityLogPublisher;

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    @Transactional
    public LoginResponse execute(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        UserEntity user = null;
        try {
            user = userRepository.findWithRoleByEmailIgnoreCase(email).orElse(null);
            if (user == null) {
                // Log failed login: user not found
                activityLogPublisher.publish(ActivityLogCommand.builder()
                        .actorType(ActorType.SYSTEM)
                        .activityType(ActivityLogType.LOGIN_FAILED)
                        .entityType(EntityType.USER)
                        .entityId(SYSTEM_UUID)
                        .summary("Failed login attempt: Account not found for email: " + email)
                        .build());
                throw new IllegalStateException("Invalid email or password.");
            }

            // Verify credentials first so a wrong password never reveals account status.
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                // Log failed login: incorrect password
                activityLogPublisher.publish(ActivityLogCommand.builder()
                        .actorType(ActorType.SYSTEM)
                        .activityType(ActivityLogType.LOGIN_FAILED)
                        .entityType(EntityType.USER)
                        .entityId(user.getUserId())
                        .summary("Failed login attempt for user: " + user.getFullName() + " (" + email + ") due to incorrect password.")
                        .build());
                throw new IllegalStateException("Invalid email or password.");
            }
        } catch (Exception ex) {
            // If it's already an IllegalStateException, just rethrow
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            // Otherwise, publish general failure if login fails for any other reason
            activityLogPublisher.publish(ActivityLogCommand.builder()
                    .actorType(ActorType.SYSTEM)
                    .activityType(ActivityLogType.LOGIN_FAILED)
                    .entityType(EntityType.USER)
                    .entityId(user != null ? user.getUserId() : SYSTEM_UUID)
                    .summary("Failed login attempt for email: " + email + ". Error: " + ex.getMessage())
                    .build());
            throw ex;
        }

        // LOCKED → reject with the standard message; INACTIVE → reactivate; stamp last-login.
        // The managed entity is persisted on commit.
        loginActivityService.applyOnLogin(user);

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .accessToken(token)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getUserId().toString())
                        .email(user.getEmail())
                        .name(user.getFullName())
                        .roles(List.of(user.getRole() != null ? user.getRole().getRoleName() : "STAFF"))
                        .permissions(effectivePermissionsService.forUser(user))
                        // Keep parity with AuthController#buildUserInfo — omitting this
                        // made the avatar appear only after a later /auth/profile fetch.
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }
}
