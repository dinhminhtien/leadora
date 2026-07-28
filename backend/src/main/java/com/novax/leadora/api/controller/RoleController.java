package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.UpdateRolePermissionsRequest;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.application.usecase.identity.ConfigureRolePermissionsUseCase;
import com.novax.leadora.application.usecase.identity.GetRoleListUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * UC-6.4 — Configure Role Permissions.
 *
 * <p>The system ships a fixed set of job roles, all of them now modelled in
 * {@link com.novax.leadora.common.security.RbacRoles}, so there is deliberately no create-role
 * endpoint: a role invented at runtime would appear in the account form yet match no
 * {@code hasAnyRole(...)} list, leaving its users unable to reach anything.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final GetRoleListUseCase getRoleListUseCase;
    private final ConfigureRolePermissionsUseCase configureRolePermissionsUseCase;

    /** List roles with their assigned permissions and user counts. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(getRoleListUseCase.execute()));
    }

    /** Replace a role's permission set (assign / remove). */
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> configurePermissions(
            @PathVariable Integer roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        RoleResponse role = configureRolePermissionsUseCase.execute(roleId, request);
        return ResponseEntity.ok(ApiResponse.success(role, "Role permissions have been updated successfully."));
    }
}
