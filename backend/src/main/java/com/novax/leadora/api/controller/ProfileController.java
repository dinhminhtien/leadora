package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.ChangePasswordRequest;
import com.novax.leadora.api.dto.request.UpdateProfileRequest;
import com.novax.leadora.api.dto.response.ProfileResponse;
import com.novax.leadora.application.usecase.identity.ChangePasswordUseCase;
import com.novax.leadora.application.usecase.identity.GetMyProfileUseCase;
import com.novax.leadora.application.usecase.identity.UpdateMyProfileUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * UC-5 Profile Management Controller.
 * Exposes self-service endpoints for the authenticated user to view and manage their
 * own profile. Admin-only user management remains in UserController.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    /** UC-5.1 — View own profile. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.success(getMyProfileUseCase.execute(userId)));
    }

    /** UC-5.2 — Update own editable fields (fullName, phone, avatarUrl). */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                updateMyProfileUseCase.execute(userId, request),
                "Profile updated successfully."));
    }

    /** UC-5.3 — Change own password after verifying the current one. */
    @PutMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordUseCase.execute(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully."));
    }
}
