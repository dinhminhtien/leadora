package com.novax.leadora.common.security;

import java.util.Set;

/**
 * The single source of truth for which roles participate in the permission model (UC-6.4).
 *
 * <p>The system ships a fixed set of job roles. Only some of them are driven by
 * {@code role_permissions}:
 *
 * <ul>
 *   <li><b>SALES, MANAGER</b> — configurable. An Admin grants and revokes their permissions in the
 *       Roles &amp; Permissions screen, and the API enforces the result.</li>
 *   <li><b>ADMIN</b> — permission-managed but <i>not</i> configurable: it implicitly holds every
 *       permission code, so there is nothing to toggle.</li>
 *   <li><b>Everything else</b> (Front Office, Reservation, any custom role) — not permission-managed.
 *       They hold no grants, so gating them on a permission code would lock them out of screens
 *       their job role already allows. They stay purely role-gated until they are seeded and
 *       promoted into {@link #PERMISSION_MANAGED}.</li>
 * </ul>
 */
public final class RbacRoles {

    public static final String ADMIN = "ADMIN";

    /** Roles an Admin can grant/revoke permissions for in UC-6.4. */
    public static final Set<String> CONFIGURABLE = Set.of("SALES", "MANAGER");

    /** Roles whose API access is decided by permission codes rather than the role name alone. */
    public static final Set<String> PERMISSION_MANAGED = Set.of(ADMIN, "SALES", "MANAGER");

    private RbacRoles() {
    }

    private static String normalise(String roleName) {
        return roleName == null ? "" : roleName.trim().toUpperCase();
    }

    public static boolean isAdmin(String roleName) {
        return ADMIN.equals(normalise(roleName));
    }

    public static boolean isConfigurable(String roleName) {
        return CONFIGURABLE.contains(normalise(roleName));
    }

    public static boolean isPermissionManaged(String roleName) {
        return PERMISSION_MANAGED.contains(normalise(roleName));
    }
}
