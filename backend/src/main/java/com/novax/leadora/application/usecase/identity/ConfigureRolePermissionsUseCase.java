package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.UpdateRolePermissionsRequest;
import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UC-6.4 — Configure Role Permissions (replace semantics).
 * Computes the diff between the role's current and requested permission sets, so unchanged grants
 * keep their original {@code granted_at} (audit-friendly). Validates every permission id (E6).
 * Changes apply immediately to all users holding the role (POST-2) — no per-user copy exists.
 */
@Service
@RequiredArgsConstructor
public class ConfigureRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoleResponse execute(Integer roleId, UpdateRolePermissionsRequest request) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        // Admin is full-access by default — its permission set is implicit and not configurable.
        if ("ADMIN".equalsIgnoreCase(role.getRoleName())) {
            throw new IllegalStateException("The Admin role has full access by default and cannot be reconfigured.");
        }

        Set<Integer> desired = new HashSet<>(request.getPermissionIds());

        // E6 — every requested permission must exist.
        for (Integer permissionId : desired) {
            if (!permissionRepository.existsById(permissionId)) {
                throw new ResourceNotFoundException("Permission", permissionId);
            }
        }

        // Dependency cascade (server-side integrity): a WRITE/APPROVE permission cannot be granted
        // without its prerequisite VIEW. Drop any permission whose `depends_on` is not also granted,
        // repeating until stable. This enforces "no View → no Write" even if a client sends an
        // inconsistent set.
        java.util.Map<Integer, Integer> dependsOn = new java.util.HashMap<>();
        for (var p : permissionRepository.findAll()) {
            dependsOn.put(p.getPermissionId(), p.getDependsOnId());
        }
        boolean changed = true;
        while (changed) {
            changed = desired.removeIf(id -> {
                Integer parent = dependsOn.get(id);
                return parent != null && !desired.contains(parent);
            });
        }

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
        return RoleResponse.from(role, permissions, userCount);
    }
}
