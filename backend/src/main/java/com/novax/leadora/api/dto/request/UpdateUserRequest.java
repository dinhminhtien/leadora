package com.novax.leadora.api.dto.request;

import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * UC-6.3 — Update User Account. All fields optional (partial update / PATCH-style PUT):
 * only non-null fields are applied. {@code password} is changed only when a non-blank value is sent.
 */
@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 255)
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    /**
     * Blank/null = keep current password. When present it must satisfy {@code PasswordPolicy}
     * (≥ 6 chars, with an uppercase, a lowercase, a digit and a symbol) — that check runs in
     * the use case rather than as {@code @Size} here, so an empty string sent by the form means
     * "unchanged" instead of failing bean validation.
     */
    @Size(max = 100)
    private String password;

    @Pattern(
            regexp = LeadFieldLimits.PHONE_PATTERN,
            message = LeadFieldLimits.PHONE_MESSAGE
    )
    private String phone;

    private Integer roleId;

    private UserStatus status;

    @Size(max = 500)
    private String avatarUrl;
}
