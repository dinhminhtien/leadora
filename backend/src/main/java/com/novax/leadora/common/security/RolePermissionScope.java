package com.novax.leadora.common.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which permission codes each configurable role can actually <em>use</em> (UC-6.4).
 *
 * <p><b>The problem this solves.</b> {@code permissions} is a flat catalogue, so the Roles &amp;
 * Permissions grid used to offer every code to every role. An Admin could grant {@code CHAT_VIEW}
 * to Front Office and the toggle would save happily — but no Front Office user would ever see a
 * chat screen, because {@code ChatController} is {@code hasAnyRole('SALES','MANAGER')} and the
 * frontend gives that desk no route. The grant was a permission with no function behind it: it
 * changed nothing, it could not be tested, and it left the screen describing an authorization
 * model the application does not implement.
 *
 * <p><b>What "applicable" means here.</b> A code is applicable to a role when the role can reach a
 * working surface guarded by it — i.e. some {@code @PreAuthorize} names both the role and the code,
 * <em>and</em> a screen exists that a user in that role can open. Both halves matter. Front Office
 * appears in {@code PaymentController}'s role list, but no Front Office route reaches the payments
 * screen, so {@code PAYMENT_*} is not applicable to it — which is also what SRS §2.2.2 says, where
 * UC-21.x lists only Sales Staff, Sales Manager and Admin.
 *
 * <p><b>Keeping this honest.</b> These sets are a hand-maintained mirror of the annotations, and
 * nothing in the compiler ties the two together. {@code RolePermissionScopeTest} reads the codes
 * out of the {@code @PreAuthorize} expressions by reflection and fails if a role is declared
 * applicable for a code no endpoint pairs it with — so the drift is caught by the build rather than
 * by a user wondering why a toggle did nothing.
 */
public final class RolePermissionScope {

    // ── Sales Staff ─────────────────────────────────────────────────────────────────────────────
    // Everything the sales desk touches. Excluded on purpose: QUOTATION_APPROVE (hasRole('MANAGER')
    // — approving your own discount is the one thing the flow exists to prevent), RESERVATION_WRITE
    // and ROOM_REQUEST_APPROVE (the Reservation desk answers those; Sales only asks), SLA_WRITE and
    // FEEDBACK_WRITE and SLA_VIEW (ADMIN/MANAGER endpoints).
    private static final Set<String> SALES = Set.of(
            "LEAD_VIEW", "LEAD_WRITE",
            "CUSTOMER_VIEW", "CUSTOMER_WRITE",
            "TASK_VIEW", "TASK_WRITE",
            "PIPELINE_VIEW",
            "DEAL_VIEW", "DEAL_WRITE",
            "QUOTATION_VIEW", "QUOTATION_WRITE",
            "INTERACTION_VIEW", "INTERACTION_WRITE",
            "BOOKING_VIEW", "BOOKING_WRITE",
            "RESERVATION_VIEW",
            // Sales raises a room request from inside a quotation; the queue screen itself is the
            // Reservation desk's, so this is VIEW only.
            "ROOM_REQUEST_VIEW",
            "HANDOVER_VIEW", "HANDOVER_WRITE",
            "PAYMENT_VIEW", "PAYMENT_WRITE",
            "NOTIFICATION_VIEW",
            "REMINDER_VIEW", "REMINDER_WRITE",
            // UC-23.2 only — a Sales Staff sees their own task-performance report and nothing else.
            "REPORTING_VIEW",
            "FEEDBACK_VIEW",
            "CHAT_VIEW"
    );

    // ── Sales Manager ───────────────────────────────────────────────────────────────────────────
    // The sales set plus everything supervisory. Only ROOM_REQUEST_APPROVE is withheld: answering a
    // room request needs PMS access, which is the Reservation desk's, not the Manager's.
    private static final Set<String> MANAGER = Set.of(
            "LEAD_VIEW", "LEAD_WRITE",
            "CUSTOMER_VIEW", "CUSTOMER_WRITE",
            "TASK_VIEW", "TASK_WRITE",
            "PIPELINE_VIEW",
            "DEAL_VIEW", "DEAL_WRITE",
            "QUOTATION_VIEW", "QUOTATION_WRITE", "QUOTATION_APPROVE",
            "INTERACTION_VIEW", "INTERACTION_WRITE",
            "BOOKING_VIEW", "BOOKING_WRITE",
            "RESERVATION_VIEW", "RESERVATION_WRITE",
            "ROOM_REQUEST_VIEW",
            "HANDOVER_VIEW", "HANDOVER_WRITE",
            "PAYMENT_VIEW", "PAYMENT_WRITE",
            "NOTIFICATION_VIEW",
            "REMINDER_VIEW", "REMINDER_WRITE",
            "REPORTING_VIEW",
            "SLA_VIEW", "SLA_WRITE",
            "FEEDBACK_VIEW", "FEEDBACK_WRITE",
            "CHAT_VIEW"
    );

    // ── Front Office desk ───────────────────────────────────────────────────────────────────────
    // Three codes, and that is the whole job (SRS §2.2.2: Front Office Handover Management is the
    // only feature listing Front Office Staff as an actor, plus Notification Management).
    // PAYMENT_* is deliberately absent — see the class javadoc.
    private static final Set<String> FRONT_OFFICE = Set.of(
            "HANDOVER_VIEW", "HANDOVER_WRITE",
            "NOTIFICATION_VIEW"
    );

    // ── Reservation desk ────────────────────────────────────────────────────────────────────────
    // Answer room requests, confirm the booking that follows, keep the reservation and its handover
    // current, and settle the deposit. No lead/deal/quotation surface exists for this desk.
    private static final Set<String> RESERVATION = Set.of(
            "ROOM_REQUEST_VIEW", "ROOM_REQUEST_APPROVE",
            "BOOKING_VIEW", "BOOKING_WRITE",
            "RESERVATION_VIEW", "RESERVATION_WRITE",
            "HANDOVER_VIEW", "HANDOVER_WRITE",
            "PAYMENT_VIEW", "PAYMENT_WRITE",
            "NOTIFICATION_VIEW",
            "QUOTATION_VIEW"
    );

    /**
     * The permission set each role ships with — the "initial setup" the UC-6.4 Reset control
     * restores. Mirrors the grants in {@code db/user_management_rbac.sql}, which is what a fresh
     * install actually writes.
     *
     * <p>Read through {@link #defaultCodes(String)}, which intersects with the applicable set. The
     * seed is broader than the application in two places — it grants Sales {@code RESERVATION_WRITE}
     * and Front Office {@code PAYMENT_*}, neither of which any endpoint will honour for that role —
     * and the intersection is what keeps this from re-proposing those dead grants.
     */
    private static final Set<String> SALES_DEFAULT = Set.of(
            "LEAD_VIEW", "LEAD_WRITE", "CUSTOMER_VIEW", "CUSTOMER_WRITE", "TASK_VIEW", "TASK_WRITE",
            "PIPELINE_VIEW", "DEAL_VIEW", "DEAL_WRITE", "QUOTATION_VIEW", "QUOTATION_WRITE",
            "INTERACTION_VIEW", "INTERACTION_WRITE", "BOOKING_VIEW", "BOOKING_WRITE",
            "RESERVATION_VIEW", "RESERVATION_WRITE", "HANDOVER_VIEW", "HANDOVER_WRITE",
            "ROOM_REQUEST_VIEW", "PAYMENT_VIEW", "PAYMENT_WRITE", "NOTIFICATION_VIEW",
            "REMINDER_VIEW", "REMINDER_WRITE", "CHAT_VIEW", "REPORTING_VIEW"
    );

    private static final Set<String> MANAGER_DEFAULT = Set.of(
            "LEAD_VIEW", "LEAD_WRITE", "CUSTOMER_VIEW", "CUSTOMER_WRITE", "TASK_VIEW", "TASK_WRITE",
            "PIPELINE_VIEW", "DEAL_VIEW", "DEAL_WRITE", "QUOTATION_VIEW", "QUOTATION_WRITE",
            "QUOTATION_APPROVE", "INTERACTION_VIEW", "INTERACTION_WRITE", "BOOKING_VIEW",
            "BOOKING_WRITE", "RESERVATION_VIEW", "RESERVATION_WRITE", "HANDOVER_VIEW",
            "HANDOVER_WRITE", "PAYMENT_VIEW", "PAYMENT_WRITE", "NOTIFICATION_VIEW", "REMINDER_VIEW",
            "REMINDER_WRITE", "CHAT_VIEW", "ROOM_REQUEST_VIEW", "REPORTING_VIEW", "SLA_VIEW",
            "SLA_WRITE", "FEEDBACK_VIEW", "FEEDBACK_WRITE"
    );

    private static final Set<String> FRONT_OFFICE_DEFAULT = Set.of(
            "HANDOVER_VIEW", "HANDOVER_WRITE", "PAYMENT_VIEW", "PAYMENT_WRITE", "NOTIFICATION_VIEW"
    );

    private static final Set<String> RESERVATION_DEFAULT = Set.of(
            "ROOM_REQUEST_VIEW", "ROOM_REQUEST_APPROVE", "BOOKING_VIEW", "BOOKING_WRITE",
            "RESERVATION_VIEW", "RESERVATION_WRITE", "HANDOVER_VIEW", "HANDOVER_WRITE",
            "PAYMENT_VIEW", "PAYMENT_WRITE", "NOTIFICATION_VIEW", "QUOTATION_VIEW"
    );

    private RolePermissionScope() {
    }

    private static String normalise(String roleName) {
        return roleName == null ? "" : roleName.trim().toUpperCase();
    }

    /**
     * The codes {@code roleName} can exercise.
     *
     * <p>Admin is not configured through this screen — it holds every code implicitly — so it is
     * not listed. An unmodelled role returns the empty set: nothing is offered rather than
     * everything, because the safe answer to "we don't know what this role does" is to grant it
     * nothing.
     */
    public static Set<String> applicableCodes(String roleName) {
        return switch (normalise(roleName)) {
            case "SALES" -> SALES;
            case "MANAGER" -> MANAGER;
            case "FO", "FRONT_OFFICE" -> FRONT_OFFICE;
            case "RESERVATION" -> RESERVATION;
            default -> Set.of();
        };
    }

    /** Whether {@code roleName} has any function behind {@code permissionCode}. */
    public static boolean isApplicable(String roleName, String permissionCode) {
        return applicableCodes(roleName).contains(permissionCode);
    }

    /** The shipped default grant for {@code roleName}, narrowed to what the role can actually use. */
    public static Set<String> defaultCodes(String roleName) {
        Set<String> seeded = switch (normalise(roleName)) {
            case "SALES" -> SALES_DEFAULT;
            case "MANAGER" -> MANAGER_DEFAULT;
            case "FO", "FRONT_OFFICE" -> FRONT_OFFICE_DEFAULT;
            case "RESERVATION" -> RESERVATION_DEFAULT;
            default -> Set.of();
        };
        Set<String> applicable = applicableCodes(roleName);
        Set<String> result = new LinkedHashSet<>(seeded);
        result.retainAll(applicable);
        return result;
    }
}
