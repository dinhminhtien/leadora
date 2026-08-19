package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.UpdateRolePermissionsRequest;
import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.security.RbacRoles;
import com.novax.leadora.common.security.RolePermissionScope;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-6.4 — Configure Role Permissions (replace semantics).
 * Computes the diff between the role's current and requested permission sets,
 * so unchanged grants
 * keep their original {@code granted_at} (audit-friendly). Validates every
 * permission id (E6).
 * Changes apply immediately to all users holding the role (POST-2) — no
 * per-user copy exists.
 */
@Service
@RequiredArgsConstructor
public class ConfigureRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final PermissionDependencyResolver permissionDependencyResolver;
    private final SystemAuditLogService systemAuditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final com.novax.leadora.common.security.JwtAuthoritiesResolver jwtAuthoritiesResolver;

    @Transactional
    public RoleResponse execute(Integer roleId, UpdateRolePermissionsRequest request) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        // Only the permission-driven job roles can be reconfigured - SALES, MANAGER, FO and
        // RESERVATION, per RbacRoles.CONFIGURABLE. Admin is
        // refused with its
        // own message because the reason differs: it is not "unsupported", it already
        // holds
        // everything. See RbacRoles for why the operational desks are excluded.
        if (!RbacRoles.isConfigurable(role.getRoleName())) {
            throw new IllegalStateException(RbacRoles.isAdmin(role.getRoleName())
                    ? "The Admin role has full access by default and cannot be reconfigured."
                    : "This role is not part of the permission model and cannot be configured.");
        }

        Set<Integer> requested = new HashSet<>(request.getPermissionIds());

        // E6 — every requested permission must exist.
        for (Integer permissionId : requested) {
            if (!permissionRepository.existsById(permissionId)) {
                throw new ResourceNotFoundException("Permission", permissionId);
            }
        }

        // Drop anything this role has no function behind (RolePermissionScope). Granting a desk a
        // code no endpoint pairs it with produced a row that changed nothing — the toggle saved,
        // the screen stayed shut, and role_permissions accumulated claims the application does not
        // honour. Pruning rather than rejecting is deliberate: the grid already hides these cells,
        // so a request carrying one is a stale client or a direct API call, and the useful answer
        // to both is the configuration that will actually take effect.
        Set<String> applicable = RolePermissionScope.applicableCodes(role.getRoleName());
        requested.removeIf(id -> permissionRepository.findById(id)
                .map(p -> !applicable.contains(p.getPermissionCode()))
                .orElse(true));

        Set<Integer> desired = permissionDependencyResolver.prune(requested);

        List<RolePermissionEntity> current = rolePermissionRepository.findByRole_RoleId(roleId);
        Set<Integer> currentIds = new HashSet<>();
        for (RolePermissionEntity rp : current) {
            currentIds.add(rp.getPermission().getPermissionId());
        }

        // Remove mappings no longer desired.
        List<RolePermissionEntity> toRemove = current.stream()
                .filter(rp -> !desired.contains(rp.getPermission().getPermissionId()))
                .toList();
        if (!toRemove.isEmpty()) {
            rolePermissionRepository.deleteAll(toRemove);
        }

        // Add newly desired mappings.
        List<RolePermissionEntity> toAdd = desired.stream()
                .filter(id -> !currentIds.contains(id))
                .map(id -> RolePermissionEntity.builder()
                        .role(role)
                        .permission(permissionRepository.getReferenceById(id))
                        .build())
                .toList();
        if (!toAdd.isEmpty()) {
            rolePermissionRepository.saveAll(toAdd);
        }

        // Build the fresh view from the desired set.
        List<PermissionResponse> permissions = permissionRepository.findAllByOrderByPermissionIdAsc().stream()
                .filter(p -> desired.contains(p.getPermissionId()))
                .map(PermissionResponse::from)
                .toList();

        long userCount = userRepository.countByRole_RoleId(roleId);

        // BR-03 / BR-37 — permission changes are logged with the before/after code
        // sets, so an
        // auditor can see exactly which grants an Admin added or removed and when.
        systemAuditLogService.log("IDENTITY", "ROLE", auditEntityId(roleId), "PERMISSIONS_CONFIGURED",
                currentUserProvider.resolveQuietly(),
                codesOf(currentIds), codesOf(desired),
                "role=" + role.getRoleName() + " (id=" + roleId + "), affectedUsers=" + userCount);

        jwtAuthoritiesResolver.invalidateAll();

        return RoleResponse.from(role, permissions, userCount);
    }

    /**
     * {@code system_audit_logs.entity_id} is a non-null UUID, but roles use an
     * INTEGER identity.
     * Derive a stable UUID from the role id so role rows are still addressable and
     * greppable.
     */
    private static UUID auditEntityId(Integer roleId) {
        return UUID.nameUUIDFromBytes(("ROLE:" + roleId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Permission ids rendered as their human-readable codes, sorted, for the audit
     * payload.
     */
    private String codesOf(Set<Integer> permissionIds) {
        return permissionRepository.findAllByOrderByPermissionIdAsc().stream()
                .filter(p -> permissionIds.contains(p.getPermissionId()))
                .map(p -> p.getPermissionCode())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
