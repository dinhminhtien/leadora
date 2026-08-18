package com.novax.leadora.api.controller;

import com.novax.leadora.application.usecase.notification.RegisterDeviceTokenUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserDeviceTokenRepository;
import com.novax.leadora.infrastructure.integration.fcm.FcmPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/device-tokens")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DeviceTokenController {

    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final FcmPushService fcmPushService;

    @GetMapping("/diagnostics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDiagnostics() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            UserEntity currentUser = currentUserProvider.resolve(null);
            report.put("userId", currentUser.getUserId());
            report.put("userEmail", currentUser.getEmail());
            
            var tokens = userDeviceTokenRepository.findByUser_UserId(currentUser.getUserId());
            report.put("registeredTokensCount", tokens.size());
            List<Map<String, String>> tokensList = new ArrayList<>();
            for (var t : tokens) {
                Map<String, String> tMap = new HashMap<>();
                tMap.put("deviceInfo", t.getDeviceInfo());
                String raw = t.getFcmToken();
                String masked = raw.length() > 10 ? raw.substring(0, 5) + "..." + raw.substring(raw.length() - 5) : raw;
                tMap.put("tokenMasked", masked);
                tokensList.add(tMap);
            }
            report.put("tokens", tokensList);
        } catch (Exception e) {
            report.put("userError", e.getMessage());
        }

        report.put("firebaseAppsInitialized", !com.google.firebase.FirebaseApp.getApps().isEmpty());
        List<String> appNames = new ArrayList<>();
        for (var app : com.google.firebase.FirebaseApp.getApps()) {
            appNames.add(app.getName());
        }
        report.put("firebaseAppNames", appNames);

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance();
            report.put("firebaseMessagingStatus", "AVAILABLE");
        } catch (Exception e) {
            report.put("firebaseMessagingStatus", "ERROR");
            report.put("firebaseMessagingError", e.getMessage());
        }

        report.put("hasGoogleApplicationCredentials", System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null);
        report.put("googleCloudProject", System.getenv("GOOGLE_CLOUD_PROJECT"));
        report.put("geminiProjectId", System.getenv("GEMINI_PROJECT_ID"));

        return ResponseEntity.ok(ApiResponse.success(report, "Firebase diagnostics report retrieved successfully."));
    }

    @PostMapping("/test-push")
    public ResponseEntity<ApiResponse<String>> testPush() {
        UserEntity currentUser = currentUserProvider.resolve(null);
        var tokens = userDeviceTokenRepository.findByUser_UserId(currentUser.getUserId());
        if (tokens.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No registered device tokens found for current user. Please turn on notifications on mobile first."));
        }
        
        try {
            fcmPushService.sendToUser(
                currentUser.getUserId(), 
                "LeadOra Test Notification", 
                "This is a test notification from LeadOra backend!", 
                Map.of("test", "true", "click_action", "FLUTTER_NOTIFICATION_CLICK")
            );
            return ResponseEntity.ok(ApiResponse.success("Test notification request dispatched. Check backend logs or device."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to trigger test push: " + e.getMessage()));
        }
    }

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
