package com.novax.leadora.api.dto.response;

import com.novax.leadora.common.security.RbacRoles;
import com.novax.leadora.common.security.RolePermissionScope;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * A role together with its assigned permissions and how many users hold it.
 * Backs the UC-6.4 "Configure Role Permissions" screen.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * The permission codes this role has an actual function behind — see
     * {@link RolePermissionScope}. The grid offers only these and shows the rest as "not part of
     * this job", so an Admin can no longer grant a role something that would change nothing (the
     * AI assistant to Front Office, say). The server prunes to the same set on save, so this is a
     * description of the rule rather than the rule itself.
     */
    private List<String> applicablePermissionCodes;

    /**
     * The permission set the role ships with. Backs the Reset control, which restores the initial
     * setup in the browser and leaves it to Save changes to persist — so an Admin can see what
     * resetting would do before committing to it.
     */
    private List<String> defaultPermissionCodes;

    public static RoleResponse from(RoleEntity role, List<PermissionResponse> permissions, long userCount) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userCount(userCount)
                .permissions(permissions)
                .configurable(RbacRoles.isConfigurable(role.getRoleName()))
                .applicablePermissionCodes(sorted(RolePermissionScope.applicableCodes(role.getRoleName())))
                .defaultPermissionCodes(sorted(RolePermissionScope.defaultCodes(role.getRoleName())))
                .build();
    }

    /** Stable order, so a client diffing two responses does not see a reshuffle as a change. */
    private static List<String> sorted(Set<String> codes) {
        return codes.stream().sorted().toList();
    }
}
