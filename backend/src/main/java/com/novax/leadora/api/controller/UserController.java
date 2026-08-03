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
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserRepository userRepository;
    private final GetUserListUseCase getUserListUseCase;
    private final GetUserDetailUseCase getUserDetailUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;

    /**
     * Lightweight list of users for assignee dropdowns (leads/tasks/deals, the Front Office
     * roster). Kept as a flat array (not paged) — existing callers depend on this shape.
     *
     * <p>Every authenticated user may call this, because every desk has some dropdown that needs a
     * roster. What narrows it:
     *
     * <ul>
     *   <li>{@code role} — callers that need one team ask for that team instead of pulling the whole
     *       staff directory. The Front Office roster filter was doing this client-side, which meant
     *       downloading every user in the company to display five of them.</li>
     *   <li>{@link UserRepository#findAllWithRole()} already restricts to {@code ACTIVE}, so
     *       suspended and dormant accounts are never offered as assignees. (An explicit
     *       "exclude LOCKED" filter here would be dead code — ACTIVE-only is the stricter rule.)</li>
     * </ul>
     *
     * <p>The payload still carries {@code email}: {@code components/ui/UserSelect} uses it as the
     * selected value, so removing it needs that component keyed on {@code userId} first.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers(
            @RequestParam(required = false) String role
    ) {
        String wanted = role != null ? role.trim().toUpperCase() : null;
        List<UserSummaryResponse> users = userRepository.findAllWithRole()
                .stream()
                .filter(u -> wanted == null || wanted.isEmpty() || matchesRole(u, wanted))
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /** {@code FO} and {@code FRONT_OFFICE} are the same desk under two spellings. */
    private boolean matchesRole(UserEntity user, String wanted) {
        if (user.getRole() == null || user.getRole().getRoleName() == null) {
            return false;
        }
        String actual = user.getRole().getRoleName().trim().toUpperCase();
        if (actual.equals(wanted)) {
            return true;
        }
        boolean bothFrontOffice = Set.of("FO", "FRONT_OFFICE").containsAll(Set.of(actual, wanted));
        return bothFrontOffice;
    }

    /** UC-6.1 — View User Accounts (paged management list). Admin only (BR-03). */
    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
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

    /** UC-6.1 — View one user account. Admin only (BR-03). */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserAccountResponse>> getUserAccount(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(getUserDetailUseCase.execute(userId)));
    }

    /** UC-6.2 — Create User Account. Admin only. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserAccountResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserAccountResponse user = createUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User account has been created successfully."));
    }

    /** UC-6.3 — Update User Account. Admin only. */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserAccountResponse>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserAccountResponse user = updateUserUseCase.execute(userId, request);
        return ResponseEntity.ok(ApiResponse.success(user, "User account has been updated successfully."));
    }
}
