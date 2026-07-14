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

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginActivityService loginActivityService;
    private final EffectivePermissionsService effectivePermissionsService;

    @Transactional
    public LoginResponse execute(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        UserEntity user = userRepository.findWithRoleByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Invalid email or password."));

        // Verify credentials first so a wrong password never reveals account status.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalStateException("Invalid email or password.");
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
