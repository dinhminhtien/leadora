package com.novax.leadora.common.security;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;
import java.util.UUID;

/**
 * Global base access policy for role-scoping and visibility checks (UC-8.x, UC-12.x).
 * Shared across multiple modules (e.g., Lead, Deal).
 */
@RequiredArgsConstructor
public abstract class BaseAccessPolicy<T> {

    protected static final Set<String> FULL_ACCESS_ROLES = Set.of("MANAGER", "ADMIN");
    protected static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    protected final CurrentUserProvider currentUserProvider;

    /**
     * Resolves the authenticated caller.
     */
    public UserEntity currentUser() {
        return currentUserProvider.resolve(null);
    }

    protected static String roleName(UserEntity user) {
        return user.getRole() != null && user.getRole().getRoleName() != null
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
    }

    /**
     * The owner id a list query must be restricted to, or {@code null} for full access.
     */
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (FULL_ACCESS_ROLES.contains(role)) {
            return null; // unscoped / full access
        }
        if (SCOPED_ROLES.contains(role)) {
            return user.getUserId();
        }
        throw new AccessDeniedException("You do not have permission to view this resource.");
    }

    /**
     * Enforces that the current user has access to view/edit the given entity.
     */
    public void assertCanView(UserEntity user, T entity) {
        String role = roleName(user);
        if (FULL_ACCESS_ROLES.contains(role)) {
            return;
        }
        if (SCOPED_ROLES.contains(role) && owns(user, entity)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this resource.");
    }

    /**
     * Enforces that the caller has full (unscoped) access — Manager/Admin only.
     * Used for privileged actions such as reassigning ownership (BR-18).
     */
    public void assertFullAccess(UserEntity user) {
        if (!FULL_ACCESS_ROLES.contains(roleName(user))) {
            throw new AccessDeniedException("Only a manager can perform this action.");
        }
    }

    /**
     * Abstract method that must be implemented by subclasses to check entity ownership.
     */
    protected abstract boolean owns(UserEntity user, T entity);
}
