package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.ChangePasswordRequest;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-5.3 — Change Password.
 * Validates the current password, enforces the project password policy, ensures
 * the new
 * password differs from the current one, and persists the BCrypt-encoded hash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogPublisher activityLogPublisher;

    @Transactional
    public void execute(String headerUserId, ChangePasswordRequest request) {
        UserEntity user = currentUserProvider.resolve(headerUserId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    "INCORRECT_CURRENT_PASSWORD",
                    "Current password is incorrect.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    "SAME_PASSWORD",
                    "New password must be different from your current password.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        PasswordPolicy.validate(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", user.getEmail());

        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.USER)
                .actorUserId(user.getUserId())
                .actorRoleSnapshot(user.getRole() != null ? user.getRole().getRoleName() : null)
                .activityType(ActivityLogType.PASSWORD_CHANGED)
                .entityType(EntityType.USER)
                .entityId(user.getUserId())
                .summary(user.getFullName() + " (" + user.getEmail() + ") changed their password.")
                .build());
    }
}
