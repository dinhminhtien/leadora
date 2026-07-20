package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.ChangePasswordRequest;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-5.3 — Change Password.
 * Validates the current password, enforces the project password policy, ensures the new
 * password differs from the current one, and persists the BCrypt-encoded hash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        validatePasswordComplexity(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", user.getEmail());
    }

    /** Mirrors the policy enforced in CreateUserUseCase / UpdateUserUseCase. */
    private void validatePasswordComplexity(String password) {
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars()
                .anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));

        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSymbol) {
            throw new BusinessException(
                    "WEAK_PASSWORD",
                    "Password must contain at least one lowercase letter, one uppercase letter, one digit, and one symbol.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
