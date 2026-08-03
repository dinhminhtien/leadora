package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Who a Front Office Staff sees on the arrival desk (UC-22.1 step 3: handovers that "are assigned
 * to or accessible by the logged-in Front Office Staff").
 *
 * <p>Kept separate from {@link HandoverAccessPolicy} on purpose: that policy governs the
 * Sales/Reservation operational endpoints and deliberately denies Front Office outright. This one
 * governs the arrival desk, where Front Office is the primary actor.
 *
 * <p><b>Why the default is "mine <em>plus unassigned</em>" rather than strictly mine.</b> Two
 * reasons, and the second is the one that matters:
 * <ol>
 *   <li>{@code assigned_fo_user_id} is nullable and is only mandatory on submit, so handovers
 *       predating the column hold NULL. Scoping strictly to the assignee would make those rows
 *       invisible to <em>every</em> Front Office user — a guest with nobody preparing the room.</li>
 *   <li>UC-22.1's own wording is "assigned to <em>or accessible by</em>". A front desk is a shift
 *       rota, not a set of private inboxes: when the assignee goes off shift the arrival still has
 *       to be prepared by whoever is on duty.</li>
 * </ol>
 *
 * <p>That second point is also why {@code deskWide} exists — an explicit, non-default request to
 * see the whole desk. A hard per-assignee scope with no override would be worse than the leak it
 * closes: the guest arrives and nobody on shift can see the handover.
 */
@Component
public class ArrivalDeskScope {

    /** Roles that supervise the desk and therefore see every arrival. */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");

    /**
     * The Front Office user id a query must be narrowed to, or {@code null} for "every arrival".
     *
     * @param user     the authenticated caller — never {@code null}; callers resolve it through
     *                 {@code CurrentUserProvider}, which already rejects an unauthenticated request
     * @param deskWide the caller explicitly asked for the whole desk rather than their own queue
     * @throws AccessDeniedException if no caller was supplied. {@code null} is this method's value
     *                               for "unscoped", so returning it for an unknown user would hand
     *                               out the whole desk — the one direction this must not fail in.
     */
    public UUID scopeFor(UserEntity user, boolean deskWide) {
        if (user == null) {
            throw new AccessDeniedException("Could not determine who is asking for this list.");
        }
        String role = user.getRole() != null && user.getRole().getRoleName() != null
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
        if (FULL_SCOPE_ROLES.contains(role)) {
            return null; // supervisors are never scoped; they narrow with the assignee filter
        }
        return deskWide ? null : user.getUserId();
    }

    /**
     * Whether {@code user} may filter the list by an arbitrary Front Office assignee (UC-22.1
     * step 5). Only supervisors: for a Front Office Staff the parameter would be a way to read a
     * colleague's queue while the scope above says otherwise.
     */
    public boolean canFilterByAssignee(UserEntity user) {
        if (user == null || user.getRole() == null || user.getRole().getRoleName() == null) {
            return false;
        }
        return FULL_SCOPE_ROLES.contains(user.getRole().getRoleName().trim().toUpperCase());
    }
}
