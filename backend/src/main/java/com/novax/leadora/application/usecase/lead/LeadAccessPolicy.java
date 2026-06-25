package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Owner-scoping for leads (UC-8.x). Role codes are the <b>as-built</b> ones in the
 * {@code roles} table (the source of truth): {@code ADMIN}, {@code SALES}, {@code MANAGER}
 * — note it is <b>SALES</b>, not the {@code SALES_STAFF} spelled in the SRS/RBAC doc.
 * <ul>
 *   <li><b>SALES</b> — only leads they own (assigned to them or created by them).</li>
 *   <li><b>MANAGER</b> — team-wide visibility (all leads). Default "all" until team
 *       grouping is introduced.</li>
 *   <li><b>ADMIN</b> — oversight read access (all leads).</li>
 *   <li>Any other role — no lead access → 403.</li>
 * </ul>
 *
 * <p>Centralised here so the list and detail use cases enforce identical rules.
 */
@Component
@RequiredArgsConstructor
public class LeadAccessPolicy {

    /** Roles that may see every lead regardless of ownership. */
    private static final Set<String> FULL_ACCESS_ROLES = Set.of("MANAGER", "ADMIN");

    /** Roles scoped to their own/assigned leads. Both spellings accepted for resilience. */
    private static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    private final CurrentUserProvider currentUserProvider;

    /** The authenticated caller, or {@link AccessDeniedException} if none can be resolved. */
    public UserEntity currentUser() {
        return currentUserProvider.resolve(null);
    }

    private static String roleName(UserEntity user) {
        return user.getRole() != null && user.getRole().getRoleName() != null
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
    }

    /**
     * The owner id a list query must be restricted to, or {@code null} for full access.
     * Throws {@link AccessDeniedException} for roles with no lead access at all.
     */
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (FULL_ACCESS_ROLES.contains(role)) {
            return null; // no restriction
        }
        if (SCOPED_ROLES.contains(role)) {
            return user.getUserId();
        }
        throw new AccessDeniedException("You do not have permission to view leads.");
    }

    /** Enforces that {@code user} may view {@code lead}; throws 403 otherwise. */
    public void assertCanView(UserEntity user, LeadEntity lead) {
        String role = roleName(user);
        if (FULL_ACCESS_ROLES.contains(role)) {
            return;
        }
        if (SCOPED_ROLES.contains(role) && owns(user, lead)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this lead.");
    }

    private static boolean owns(UserEntity user, LeadEntity lead) {
        UUID uid = user.getUserId();
        boolean assignedToMe = lead.getAssignedUser() != null
                && uid.equals(lead.getAssignedUser().getUserId());
        boolean createdByMe = lead.getCreatedBy() != null
                && uid.equals(lead.getCreatedBy().getUserId());
        return assignedToMe || createdByMe;
    }
}
