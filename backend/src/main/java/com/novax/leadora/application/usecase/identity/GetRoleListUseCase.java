package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UC-6.4 (step 2) — list every role with its assigned permissions and how many users hold it.
 * Loads all role→permission mappings once, then groups in memory to avoid an N+1 over permissions.
 */
@Service
@RequiredArgsConstructor
public class GetRoleListUseCase {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> execute() {
        List<RoleEntity> roles = roleRepository.findAllByOrderByRoleIdAsc();

        Map<Integer, List<PermissionResponse>> permsByRole = rolePermissionRepository.findAllWithPermission()
                .stream()
                .collect(Collectors.groupingBy(
                        rp -> rp.getRole().getRoleId(),
                        Collectors.mapping(
                                (RolePermissionEntity rp) -> PermissionResponse.from(rp.getPermission()),
                                Collectors.toList())));

        return roles.stream()
                .map(role -> RoleResponse.from(
                        role,
                        permsByRole.getOrDefault(role.getRoleId(), List.of()),
                        userRepository.countByRole_RoleId(role.getRoleId())))
                .toList();
    }
}
