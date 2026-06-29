package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Shared sign-in policy, applied for both email/password (UC-1) and Google OAuth logins:
 * <ul>
 *   <li>LOCKED accounts are rejected with the standard message.</li>
 *   <li>INACTIVE accounts (dormant from 7-day inactivity) are reactivated to ACTIVE on login.</li>
 *   <li>{@code lastLoginAt} is stamped so the idle-inactivation job has a fresh window.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LoginActivityService {

    public static final String LOCKED_MESSAGE =
            "Your account has been locked. Please contact the Admin for assistance.";

    private final UserRepository userRepository;

    /**
     * Apply the policy to an already-loaded, managed entity (e.g. inside the email-login
     * transaction). The caller's transaction persists the changes.
     */
    public void applyOnLogin(UserEntity user) {
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE); // re-activate dormant account on sign-in
        }
        user.setLastLoginAt(OffsetDateTime.now());
    }

    /** Load + apply the policy in its own transaction (e.g. from the OAuth verify endpoint). */
    @Transactional
    public void recordLogin(UUID userId) {
        userRepository.findWithRoleByUserId(userId).ifPresent(this::applyOnLogin);
    }
}
