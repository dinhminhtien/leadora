package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.application.usecase.identity.GetPermissionListUseCase;
import com.novax.leadora.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/** UC-6.4 — the permission catalogue the Admin assigns to roles. */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final GetPermissionListUseCase getPermissionListUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success(getPermissionListUseCase.execute()));
    }
}
