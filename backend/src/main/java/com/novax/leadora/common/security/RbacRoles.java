package com.novax.leadora.common.security;

import java.util.Set;

/**
 * The single source of truth for which roles participate in the permission model (UC-6.4).
 *
 * <p>The system ships a fixed set of job roles. Only some of them are driven by
 * {@code role_permissions}:
 *
 * <ul>
 *   <li><b>SALES, MANAGER, FO, RESERVATION</b> — configurable. An Admin grants and revokes their
 *       permissions in the Roles &amp; Permissions screen, and the API enforces the result. The two
 *       operational desks hold a narrow set matching their job (arrivals and payments for Front
 *       Office; room requests, bookings and reservations for Reservation) rather than the broad
 *       sales set.</li>
 *   <li><b>ADMIN</b> — permission-managed but <i>not</i> configurable: it implicitly holds every
 *       permission code, so there is nothing to toggle.</li>
 *   <li><b>Anything else</b> — not permission-managed, so a permission check passes and the
 *       surrounding {@code hasAnyRole(...)} is the only gate. Nothing occupies this bucket now that
 *       all five job roles are modelled; it exists so an unmodelled role added straight to the
 *       database cannot silently lock its users out of the whole application.</li>
 * </ul>
 */
public final class RbacRoles {

    public static final String ADMIN = "ADMIN";

    /**
     * Roles an Admin can grant/revoke permissions for in UC-6.4.
     *
     * <p>{@code FRONT_OFFICE} is the legacy spelling of {@code FO}: the {@code roles} table uses
     * {@code FO}, but several {@code @PreAuthorize} lists accept both, so both are recognised here.
     */
    public static final Set<String> CONFIGURABLE =
            Set.of("SALES", "MANAGER", "FO", "FRONT_OFFICE", "RESERVATION");

    /** Roles whose API access is decided by permission codes rather than the role name alone. */
    public static final Set<String> PERMISSION_MANAGED =
            Set.of(ADMIN, "SALES", "MANAGER", "FO", "FRONT_OFFICE", "RESERVATION");

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
