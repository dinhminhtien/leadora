package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.LoginRequest;
import com.novax.leadora.api.dto.request.ForgotPasswordRequest;
import com.novax.leadora.api.dto.request.ResetPasswordRequest;
import com.novax.leadora.api.dto.response.LoginResponse;
import com.novax.leadora.application.usecase.identity.LoginUseCase;
import com.novax.leadora.application.usecase.identity.ForgotPasswordUseCase;
import com.novax.leadora.application.usecase.identity.ResetPasswordUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(loginUseCase.execute(request), "Logged in successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
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
        UserEntity user = currentUserProvider.resolve(userId);
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getUserId().toString())
                .email(user.getEmail())
                .name(user.getFullName())
                .roles(List.of(user.getRole() != null ? user.getRole().getRoleName() : "STAFF"))
                .build();
        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }
}
