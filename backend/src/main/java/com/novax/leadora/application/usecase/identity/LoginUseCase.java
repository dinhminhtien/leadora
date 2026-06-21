package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.LoginRequest;
import com.novax.leadora.api.dto.response.LoginResponse;
import com.novax.leadora.common.security.JwtService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse execute(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        UserEntity user = userRepository.findWithRoleByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Invalid email or password."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Your account is deactivated or locked. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalStateException("Invalid email or password.");
        }

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .accessToken(token)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getUserId().toString())
                        .email(user.getEmail())
                        .name(user.getFullName())
                        .roles(List.of(user.getRole() != null ? user.getRole().getRoleName() : "STAFF"))
                        .build())
                .build();
    }
}
