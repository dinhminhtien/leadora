package com.novax.leadora.api.dto.response;

import com.novax.leadora.common.security.RbacRoles;
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

    /**
     * Whether an Admin may grant/revoke this role's permissions — true only for Staff and Manager
     * ({@link com.novax.leadora.common.security.RbacRoles}). The screen reads this instead of
     * hard-coding role names, so the two sides can never drift apart.
     */
    private boolean configurable;

    public static RoleResponse from(RoleEntity role, List<PermissionResponse> permissions, long userCount) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userCount(userCount)
                .permissions(permissions)
                .configurable(RbacRoles.isConfigurable(role.getRoleName()))
                .build();
    }
}
