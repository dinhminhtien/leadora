package com.novax.leadora.api.controller;

import com.novax.leadora.application.usecase.notification.RegisterDeviceTokenUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DeviceTokenController {

    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserEntity currentUser = currentUserProvider.resolve(null);
        registerDeviceTokenUseCase.register(currentUser.getUserId(), request.getFcmToken(), request.getDeviceInfo());
        return ResponseEntity.ok(ApiResponse.success(null, "Device token registered successfully."));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @RequestParam String fcmToken) {
        UserEntity currentUser = currentUserProvider.resolve(null);
        registerDeviceTokenUseCase.unregister(currentUser.getUserId(), fcmToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Device token unregistered successfully."));
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "FCM token must not be blank")
        private String fcmToken;
        private String deviceInfo;
    }
}
