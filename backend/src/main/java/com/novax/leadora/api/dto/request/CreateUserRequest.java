package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** UC-6.2 — Create User Account. */
@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Password is required")
    // 6, not 8: SRS 3.3.2 specifies "Min 6 chars, uppercase, lowercase, digit, symbol" and
    // the form says so in its own placeholder. Bean validation runs BEFORE the use case, so
    // an 8 here silently overrode the specified rule - a valid 6-character password was
    // refused with a 400 that PasswordPolicy never got the chance to disagree with.
    // The complexity half of the rule stays in PasswordPolicy: it cannot be expressed as @Size.
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;

    @Pattern(
            regexp = LeadFieldLimits.PHONE_PATTERN,
            message = LeadFieldLimits.PHONE_MESSAGE
    )
    private String phone;

    @NotNull(message = "Role is required")
    private Integer roleId;

    /** Optional — defaults to ACTIVE when omitted. */
    private UserStatus status;

    @Size(max = 500)
    private String avatarUrl;
}
