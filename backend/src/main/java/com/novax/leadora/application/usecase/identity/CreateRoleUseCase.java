package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.CreateRoleRequest;
import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** UC-6.4 alt-flow A3 — Create New Role (optionally with an initial permission set). */
@Service
@RequiredArgsConstructor
public class CreateRoleUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionDependencyResolver permissionDependencyResolver;
    private final SystemAuditLogService systemAuditLogService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public RoleResponse execute(CreateRoleRequest request) {
        String roleName = request.getRoleName().trim();
        if (roleRepository.existsByRoleNameIgnoreCase(roleName)) {
            throw new IllegalStateException("Role name already exists.");
        }

        RoleEntity role = roleRepository.save(RoleEntity.builder()
                .roleName(roleName)
                .description(request.getDescription())
                .build());

        List<PermissionResponse> permissions = List.of();
        Set<Integer> ids = Set.of();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Integer> requested = new HashSet<>(request.getPermissionIds());
            for (Integer id : requested) {
                if (!permissionRepository.existsById(id)) {
                    throw new ResourceNotFoundException("Permission", id);
                }
            }
            // Same VIEW-prerequisite rule as UC-6.4's configure flow — a role must not be born
            // holding a WRITE without the matching VIEW.
            Set<Integer> resolved = permissionDependencyResolver.prune(requested);
            List<RolePermissionEntity> mappings = resolved.stream()
                    .map(id -> RolePermissionEntity.builder()
                            .role(role)
                            .permission(permissionRepository.getReferenceById(id))
                            .build())
                    .toList();
            rolePermissionRepository.saveAll(mappings);

            permissions = permissionRepository.findAllByOrderByPermissionIdAsc().stream()
                    .filter(p -> resolved.contains(p.getPermissionId()))
                    .map(PermissionResponse::from)
                    .toList();
            ids = resolved;
        }

        // BR-03 / BR-37 — a new role is a permission change in its own right.
        systemAuditLogService.log("IDENTITY", "ROLE",
                ConfigureRolePermissionsUseCase.auditEntityId(role.getRoleId()), "ROLE_CREATED",
                currentUserProvider.resolveQuietly(), null,
                permissions.stream().map(PermissionResponse::getPermissionCode).sorted()
                        .collect(Collectors.joining(", ")),
                "role=" + role.getRoleName() + " (id=" + role.getRoleId() + "), grantedCount=" + ids.size());

        return RoleResponse.from(role, permissions, 0);
    }
}
