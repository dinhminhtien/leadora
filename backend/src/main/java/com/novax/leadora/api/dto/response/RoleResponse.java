package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * A role together with its assigned permissions and how many users hold it.
 * Backs the UC-6.4 "Configure Role Permissions" screen.
 */
@Getter
@Builder
public class RoleResponse {

    private Integer roleId;
    private String roleName;
    private String description;
    private long userCount;
    private List<PermissionResponse> permissions;

    public static RoleResponse from(RoleEntity role, List<PermissionResponse> permissions, long userCount) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userCount(userCount)
                .permissions(permissions)
                .build();
    }
}
