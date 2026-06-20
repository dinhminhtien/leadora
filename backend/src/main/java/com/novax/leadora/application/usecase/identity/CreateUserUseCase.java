package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.CreateUserRequest;
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

/**
 * UC-6.2 — Create User Account.
 * Enforces email uniqueness (E5) and a valid role (E6), hashes the initial password with BCrypt,
 * and never echoes the credential back. Admin-only per BR-03 (auth enforcement is a later phase).
 */
@Service
@RequiredArgsConstructor
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserAccountResponse execute(CreateUserRequest request) {
        String email = request.getEmail().trim();

        // E5 — Duplicate email (this system logs in by email, not a separate username).
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Email already exists.");
        }

        // E6 — Invalid role.
        RoleEntity role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", request.getRoleId()));

        UserEntity user = UserEntity.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .avatarUrl(request.getAvatarUrl())
                .build();

        return UserAccountResponse.from(userRepository.save(user));
    }
}
