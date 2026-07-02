package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.LoginRequest;
import com.novax.leadora.api.dto.request.ForgotPasswordRequest;
import com.novax.leadora.api.dto.request.ResetPasswordRequest;
import com.novax.leadora.api.dto.response.LoginResponse;
import com.novax.leadora.application.usecase.identity.LoginUseCase;
import com.novax.leadora.application.usecase.identity.LoginActivityService;
import com.novax.leadora.application.usecase.identity.EffectivePermissionsService;
import com.novax.leadora.application.usecase.identity.ForgotPasswordUseCase;
import com.novax.leadora.application.usecase.identity.ResetPasswordUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.security.TokenBlacklistService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginActivityService loginActivityService;
    private final EffectivePermissionsService effectivePermissionsService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(loginUseCase.execute(request), "Logged in successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            tokenBlacklistService.blacklistToken(token);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        forgotPasswordUseCase.execute(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "Reset instructions printed to server console."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.execute(request.getToken(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully."));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> getProfile(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.success(buildUserInfo(currentUserProvider.resolve(userId))));
    }

    /** Called after Google OAuth callback — rejects emails not provisioned by Admin. */
    @GetMapping("/oauth/verify")
    public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> verifyOAuthAccess() {
        UserEntity user = currentUserProvider.resolve(null);
        // Same sign-in policy as email login: LOCKED → reject, INACTIVE → reactivate, stamp last-login.
        loginActivityService.recordLogin(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(buildUserInfo(user)));
    }

    private LoginResponse.UserInfo buildUserInfo(UserEntity user) {
        return LoginResponse.UserInfo.builder()
                .id(user.getUserId().toString())
                .email(user.getEmail())
                .name(user.getFullName())
                .roles(List.of(user.getRole() != null ? user.getRole().getRoleName() : "STAFF"))
                .permissions(effectivePermissionsService.forUser(user))
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
