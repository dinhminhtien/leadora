package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.UpdateUserRequest;
import com.novax.leadora.api.dto.response.UserAccountResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * UC-6.3 — Update User Account.
 * Partial update: only non-null fields are applied. Enforces email uniqueness (E5), a valid role
 * (E6), and a safety guard that the system can never lose its last active Admin (BR-03 spirit —
 * prevents an Admin from accidentally locking everyone out by demoting/deactivating themselves).
 */
@Service
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "user-roles", allEntries = true)
    public UserAccountResponse execute(UUID userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findWithRoleByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName().trim());
        }

        if (StringUtils.hasText(request.getEmail())) {
            String email = request.getEmail().trim();
            if (userRepository.existsByEmailIgnoreCaseAndUserIdNot(email, userId)) {
                throw new IllegalStateException("Email already exists.");
            }
            user.setEmail(email);
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (StringUtils.hasText(request.getPassword())) {
            validatePasswordComplexity(request.getPassword());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // Resolve the role + status the account WOULD have after this update, then guard the
        // "last active Admin" invariant before mutating.
        RoleEntity newRole = user.getRole();
        if (request.getRoleId() != null && !request.getRoleId().equals(user.getRole().getRoleId())) {
            newRole = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", request.getRoleId()));
        }
        UserStatus newStatus = request.getStatus() != null ? request.getStatus() : user.getStatus();

        guardLastActiveAdmin(user, newRole, newStatus);

        user.setRole(newRole);
        user.setStatus(newStatus);

        return UserAccountResponse.from(userRepository.save(user));
    }

    /**
     * Block the change if {@code user} is currently the only ACTIVE Admin and the update would stop
     * it from being one (demotion off ADMIN, or deactivation/lock).
     */
    private void guardLastActiveAdmin(UserEntity user, RoleEntity newRole, UserStatus newStatus) {
        boolean currentlyActiveAdmin =
                ADMIN_ROLE.equalsIgnoreCase(user.getRole().getRoleName()) && user.getStatus() == UserStatus.ACTIVE;
        if (!currentlyActiveAdmin) {
            return;
        }
        boolean staysActiveAdmin =
                ADMIN_ROLE.equalsIgnoreCase(newRole.getRoleName()) && newStatus == UserStatus.ACTIVE;
        if (staysActiveAdmin) {
            return;
        }
        long activeAdmins = userRepository.countByRole_RoleNameAndStatus(ADMIN_ROLE, UserStatus.ACTIVE);
        if (activeAdmins <= 1) {
            throw new IllegalStateException(
                    "Cannot deactivate or demote the last active Admin account.");
        }
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalStateException("Password must be at least 6 characters.");
        }
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));

        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSymbol) {
            throw new IllegalStateException("Password must contain at least one lowercase letter, one uppercase letter, one digit, and one symbol.");
        }
    }
}
