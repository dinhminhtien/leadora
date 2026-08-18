package com.novax.leadora.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Method-security helper exposed to {@code @PreAuthorize} as {@code @access}.
 *
 * <p>Endpoints are written as {@code hasAnyRole(...) and @access.can('LEAD_VIEW')}: the role list
 * keeps the existing job-role boundary (an Admin still has no business in the sales screens), and
 * the permission code adds the Admin-configurable gate from UC-6.4 on top. The combination can only
 * ever narrow access relative to the role check alone.
 */
@Component("access")
public class AccessExpressions {

    /** {@link RbacRoles#PERMISSION_MANAGED} as Spring Security authority names. */
    private static final Set<String> PERMISSION_MANAGED_AUTHORITIES = RbacRoles.PERMISSION_MANAGED.stream()
            .map(role -> "ROLE_" + role)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Whether the current caller holds {@code permissionCode}. Callers whose role is not
     * permission-managed pass through — the surrounding {@code hasAnyRole(...)} is their gate.
     */
    public boolean can(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean managed = false;
        boolean granted = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (PERMISSION_MANAGED_AUTHORITIES.contains(value)) {
                managed = true;
            } else if (permissionCode.equals(value)) {
                granted = true;
            }
        }
        return granted || !managed;
    }
}
