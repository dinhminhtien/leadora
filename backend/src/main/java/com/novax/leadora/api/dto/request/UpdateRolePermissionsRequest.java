package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * UC-6.4 — Configure Role Permissions.
 * The full desired permission set for a role (replace semantics): the backend removes mappings
 * not in this list and adds the ones that are missing. An empty list strips all permissions.
 */
@Getter
@Setter
public class UpdateRolePermissionsRequest {

    @NotNull(message = "permissionIds is required (use [] to clear all permissions)")
    private List<Integer> permissionIds;
}
