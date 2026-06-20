package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.CreateRoleRequest;
import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
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

/** UC-6.4 alt-flow A3 — Create New Role (optionally with an initial permission set). */
@Service
@RequiredArgsConstructor
public class CreateRoleUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

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
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Integer> ids = new HashSet<>(request.getPermissionIds());
            for (Integer id : ids) {
                if (!permissionRepository.existsById(id)) {
                    throw new ResourceNotFoundException("Permission", id);
                }
            }
            List<RolePermissionEntity> mappings = ids.stream()
                    .map(id -> RolePermissionEntity.builder()
                            .role(role)
                            .permission(permissionRepository.getReferenceById(id))
                            .build())
                    .toList();
            rolePermissionRepository.saveAll(mappings);

            permissions = permissionRepository.findAllByOrderByPermissionIdAsc().stream()
                    .filter(p -> ids.contains(p.getPermissionId()))
                    .map(PermissionResponse::from)
                    .toList();
        }

        return RoleResponse.from(role, permissions, 0);
    }
}
