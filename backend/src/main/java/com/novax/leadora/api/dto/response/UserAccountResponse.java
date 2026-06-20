package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full user-account view for the Admin User Management screens (UC-6.1 / UC-6.3).
 * Deliberately omits {@code passwordHash} — credentials are never serialized to clients.
 */
@Getter
@Builder
public class UserAccountResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private String phone;
    private Integer roleId;
    private String roleName;
    private UserStatus status;
    private String avatarUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static UserAccountResponse from(UserEntity u) {
        return UserAccountResponse.builder()
                .userId(u.getUserId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .roleId(u.getRole() != null ? u.getRole().getRoleId() : null)
                .roleName(u.getRole() != null ? u.getRole().getRoleName() : null)
                .status(u.getStatus())
                .avatarUrl(u.getAvatarUrl())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
