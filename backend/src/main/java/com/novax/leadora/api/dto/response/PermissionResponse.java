package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.PermissionEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PermissionResponse {

    private Integer permissionId;
    private String permissionCode;
    private String description;

    public static PermissionResponse from(PermissionEntity p) {
        return PermissionResponse.builder()
                .permissionId(p.getPermissionId())
                .permissionCode(p.getPermissionCode())
                .description(p.getDescription())
                .build();
    }
}
