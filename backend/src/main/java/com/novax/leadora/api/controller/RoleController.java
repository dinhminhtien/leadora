package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateRoleRequest;
import com.novax.leadora.api.dto.request.UpdateRolePermissionsRequest;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.application.usecase.identity.ConfigureRolePermissionsUseCase;
import com.novax.leadora.application.usecase.identity.CreateRoleUseCase;
import com.novax.leadora.application.usecase.identity.GetRoleListUseCase;
import com.novax.leadora.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/** UC-6.4 — Configure Role Permissions. */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final GetRoleListUseCase getRoleListUseCase;
    private final ConfigureRolePermissionsUseCase configureRolePermissionsUseCase;
    private final CreateRoleUseCase createRoleUseCase;

    /** List roles with their assigned permissions and user counts. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(getRoleListUseCase.execute()));
    }

    /** Create a new role (alt-flow A3). */
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse role = createRoleUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(role, "Role has been created successfully."));
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
