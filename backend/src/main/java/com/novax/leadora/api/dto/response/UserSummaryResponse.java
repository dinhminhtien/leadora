package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserSummaryResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private String roleName;

    public static UserSummaryResponse from(UserEntity entity) {
        return UserSummaryResponse.builder()
                .userId(entity.getUserId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .roleName(entity.getRole() != null ? entity.getRole().getRoleName() : null)
                .build();
    }
}
