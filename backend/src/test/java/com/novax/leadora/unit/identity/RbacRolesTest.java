package com.novax.leadora.unit.identity;

import com.novax.leadora.common.security.RbacRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The role boundaries of the permission model (UC-6.4). */
class RbacRolesTest {

    @Test
    @DisplayName("Every job role except Admin can have its permissions configured")
    void allJobRolesAreConfigurable() {
        assertTrue(RbacRoles.isConfigurable("SALES"));
        assertTrue(RbacRoles.isConfigurable("MANAGER"));
        assertTrue(RbacRoles.isConfigurable("FO"));
        assertTrue(RbacRoles.isConfigurable("FRONT_OFFICE"));
        assertTrue(RbacRoles.isConfigurable("RESERVATION"));
        assertFalse(RbacRoles.isConfigurable("ADMIN"));
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
    @DisplayName("The operational desks are permission-managed too — each with its own narrow set")
    void operationalDesksArePermissionManaged() {
        assertTrue(RbacRoles.isPermissionManaged("FO"));
        assertTrue(RbacRoles.isPermissionManaged("FRONT_OFFICE"));
        assertTrue(RbacRoles.isPermissionManaged("RESERVATION"));
    }

    @Test
    @DisplayName("A role invented outside the model still falls through to its hasAnyRole check")
    void unmodelledRoleIsNotLockedOut() {
        assertFalse(RbacRoles.isPermissionManaged("SOME_CUSTOM_ROLE"));
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
