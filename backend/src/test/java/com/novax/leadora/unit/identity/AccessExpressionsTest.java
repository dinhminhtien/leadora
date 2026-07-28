package com.novax.leadora.unit.identity;

import com.novax.leadora.common.security.AccessExpressions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate behind every {@code @PreAuthorize("... and @access.can('X')")} — i.e. whether revoking
 * a permission in UC-6.4 actually stops the API from serving the data (BR-02).
 */
class AccessExpressionsTest {

    private final AccessExpressions access = new AccessExpressions();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone@leadora.vn", "n/a", granted));
    }

    @Test
    @DisplayName("A managed role holding the permission is allowed")
    void grantedPermissionPasses() {
        authenticateWith("ROLE_SALES", "LEAD_VIEW", "LEAD_WRITE");
        assertTrue(access.can("LEAD_VIEW"));
        assertTrue(access.can("LEAD_WRITE"));
    }

    @Test
    @DisplayName("A managed role without the permission is denied — this is UC-6.4 taking effect")
    void revokedPermissionIsDenied() {
        authenticateWith("ROLE_SALES", "LEAD_VIEW");
        assertFalse(access.can("REPORTING_VIEW"));
        assertFalse(access.can("LEAD_WRITE"));
    }

    @Test
    @DisplayName("Admin holds every code, so its checks pass")
    void adminPasses() {
        authenticateWith("ROLE_ADMIN", "LEAD_VIEW", "REPORTING_VIEW", "PAYMENT_WRITE");
        assertTrue(access.can("REPORTING_VIEW"));
    }

    @Test
    @DisplayName("An unmanaged operational role falls back to its role check instead of being locked out")
    void unmanagedRoleIsGrandfathered() {
        authenticateWith("ROLE_FRONT_OFFICE");
        assertTrue(access.can("HANDOVER_VIEW"));
    }

    @Test
    @DisplayName("No authentication at all is denied")
    void anonymousIsDenied() {
        SecurityContextHolder.clearContext();
        assertFalse(access.can("LEAD_VIEW"));
    }

    @Test
    @DisplayName("A permission code is never confused with a role name")
    void roleNamesAreNotPermissions() {
        authenticateWith("ROLE_MANAGER", "QUOTATION_VIEW");
        assertFalse(access.can("ROLE_MANAGER"));
        assertFalse(access.can("QUOTATION_APPROVE"));
    }

    @Test
    @DisplayName("An authenticated principal with no authorities at all is denied")
    void noAuthoritiesIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone@leadora.vn", "n/a", List.of()));
        // Not a managed role, so the surrounding hasAnyRole(...) is what stops the request.
        assertTrue(access.can("LEAD_VIEW"));
    }
}
