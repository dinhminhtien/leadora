package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for UC-5.3 Change Password.
 * The confirm-password match is validated in ChangePasswordUseCase to produce
 * a domain-level error code rather than a generic validation error.
 */
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "New password must be at least 8 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
