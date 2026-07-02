package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves the effective permission codes for a user: ADMIN implicitly holds every permission;
 * any other role holds exactly what its {@code role_permissions} grant. Returned with the
 * login/profile payload so the frontend can gate the sidebar and routes.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionsService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public List<String> forUser(UserEntity user) {
        if (user.getRole() == null) {
            return List.of();
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
            return permissionRepository.findAllCodes();
        }
        return rolePermissionRepository.findPermissionCodesByRoleId(user.getRole().getRoleId());
    }
}
