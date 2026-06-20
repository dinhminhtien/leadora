package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** UC-6.4 alt-flow A3 — Create New Role (with an optional initial permission set). */
@Getter
@Setter
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 50)
    private String roleName;

    @Size(max = 255)
    private String description;

    /** Optional initial permissions. */
    private List<Integer> permissionIds;
}
