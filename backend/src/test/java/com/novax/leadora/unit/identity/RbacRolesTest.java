package com.novax.leadora.unit.identity;

import com.novax.leadora.common.security.RbacRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The role boundaries of the permission model (UC-6.4). */
class RbacRolesTest {

    @Test
    @DisplayName("Only Staff and Manager can have their permissions configured")
    void onlyStaffAndManagerAreConfigurable() {
        assertTrue(RbacRoles.isConfigurable("SALES"));
        assertTrue(RbacRoles.isConfigurable("MANAGER"));
        assertFalse(RbacRoles.isConfigurable("ADMIN"));
        assertFalse(RbacRoles.isConfigurable("FO"));
        assertFalse(RbacRoles.isConfigurable("FRONT_OFFICE"));
        assertFalse(RbacRoles.isConfigurable("RESERVATION"));
        assertFalse(RbacRoles.isConfigurable("SOME_CUSTOM_ROLE"));
    }

    @Test
    @DisplayName("Admin is permission-managed but never configurable — it holds everything implicitly")
    void adminIsManagedButNotConfigurable() {
        assertTrue(RbacRoles.isPermissionManaged("ADMIN"));
        assertFalse(RbacRoles.isConfigurable("ADMIN"));
        assertTrue(RbacRoles.isAdmin("admin"));
    }

    @Test
    @DisplayName("The operational desks stay outside the permission model, so they are never locked out")
    void operationalDesksAreNotPermissionManaged() {
        assertFalse(RbacRoles.isPermissionManaged("FO"));
        assertFalse(RbacRoles.isPermissionManaged("RESERVATION"));
    }

    @Test
    @DisplayName("Role names are matched case-insensitively and trimmed")
    void nameMatchingIsForgiving() {
        assertTrue(RbacRoles.isConfigurable("  sales  "));
        assertTrue(RbacRoles.isConfigurable("Manager"));
        assertFalse(RbacRoles.isConfigurable(null));
        assertFalse(RbacRoles.isAdmin(null));
    }

    @Test
    @DisplayName("Every configurable role is also permission-managed — otherwise its grants would be inert")
    void configurableImpliesManaged() {
        RbacRoles.CONFIGURABLE.forEach(role -> assertTrue(RbacRoles.isPermissionManaged(role), role));
    }
}
