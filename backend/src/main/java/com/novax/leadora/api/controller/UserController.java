package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.request.CreateUserRequest;
import com.novax.leadora.api.dto.request.UpdateUserRequest;
import com.novax.leadora.api.dto.response.UserAccountResponse;
import com.novax.leadora.api.dto.response.UserSummaryResponse;
import com.novax.leadora.application.usecase.identity.CreateUserUseCase;
import com.novax.leadora.application.usecase.identity.GetUserDetailUseCase;
import com.novax.leadora.application.usecase.identity.GetUserListUseCase;
import com.novax.leadora.application.usecase.identity.UpdateUserUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final GetUserListUseCase getUserListUseCase;
    private final GetUserDetailUseCase getUserDetailUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;

    /**
     * Lightweight list of all users for assignee dropdowns (leads/tasks/deals).
     * Kept as a flat array (not paged) — existing callers depend on this shape.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers() {
        List<UserSummaryResponse> users = userRepository.findAllWithRole()
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /** UC-6.1 — View User Accounts (paged management list). */
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<Page<UserAccountResponse>>> getUserAccounts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer roleId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<UserAccountResponse> users = getUserListUseCase.execute(search, roleId, status, sortBy, sortDir, page, size);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /** UC-6.1 — View one user account. */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserAccountResponse>> getUserAccount(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(getUserDetailUseCase.execute(userId)));
    }

    /** UC-6.2 — Create User Account. */
    @PostMapping
    public ResponseEntity<ApiResponse<UserAccountResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserAccountResponse user = createUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User account has been created successfully."));
    }

    /** UC-6.3 — Update User Account. */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserAccountResponse>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserAccountResponse user = updateUserUseCase.execute(userId, request);
        return ResponseEntity.ok(ApiResponse.success(user, "User account has been updated successfully."));
    }
}
