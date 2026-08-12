import { ROUTE_PATHS } from "@/app/routes/route_paths";

/**
 * Role + permission based access control (frontend).
 *
 * Roles (as-built DB codes): ADMIN, SALES, MANAGER. Each lands on its own dashboard.
 * - ADMIN is gated by role (account/role administration + payment oversight only).
 * - SALES / MANAGER are gated by their **effective permissions** (configured by Admin in
 *   Roles & Permissions). A screen is reachable only if the user holds its VIEW permission,
 *   so hiding a permission also blocks the route — sidebar item AND direct URL.
 */
export type AppRole = "SALES" | "MANAGER" | "ADMIN" | "FO" | "RESERVATION";

export function getUserRole(user?: { roles?: string[] } | null): AppRole {
  const raw = (user?.roles?.[0] ?? "").toUpperCase();
  if (raw === "ADMIN") return "ADMIN";
  if (raw === "MANAGER") return "MANAGER";
  if (raw === "FO" || raw === "FRONT_OFFICE") return "FO";
  // Must be matched before the SALES fallback: a Reservation user treated as Sales
  // would land on the sales dashboard and never see their room-request inbox.
  if (raw === "RESERVATION") return "RESERVATION";
  return "SALES";
}

/**
 * Whether the user acts on every record or only their own.
 *
 * Mirrors `BaseAccessPolicy.FULL_ACCESS_ROLES` on the backend (MANAGER, ADMIN).
 * Everyone else is scoped to what they own, so a control that reaches into
 * someone else's record — assigning work, reassigning it, re-pointing it at a
 * different customer — must be hidden from them rather than left to 403.
 */
export function hasFullAccess(user?: { roles?: string[] } | null): boolean {
  const role = getUserRole(user);
  return role === "MANAGER" || role === "ADMIN";
}

/** The home dashboard URL for each role. Front Office lands directly on its arrival-handover desk. */
export const DASHBOARD_PATHS: Record<AppRole, string> = {
  SALES: "/dashboard/staff",
  MANAGER: "/dashboard/manager",
  ADMIN: "/dashboard/admin",
  FO: ROUTE_PATHS.frontOfficeHandover,
  // Reservation lands on the queue that is their whole job: rooms Sales is waiting on.
  RESERVATION: ROUTE_PATHS.roomRequests,
};

export function dashboardPathForRole(role: AppRole): string {
  return DASHBOARD_PATHS[role];
}

// Admin (role-based): account/role administration + payment oversight only.
const ADMIN_ROUTES: string[] = [
  DASHBOARD_PATHS.ADMIN,
  ROUTE_PATHS.identityAccess,
  ROUTE_PATHS.depositPayment,
  ROUTE_PATHS.activityLogs,
];

// Front Office: a simple, focused desk — arrival handovers + their alerts only.
const FO_ROUTES: string[] = [
  ROUTE_PATHS.frontOfficeHandover,
  ROUTE_PATHS.depositPayment,
  ROUTE_PATHS.notifications,
];

// Reservation: answer room requests, confirm the bookings that follow, then keep the reservation
// and its handover current through to check-in.
//
// `reservationStatus` and `operationalHandover` were missing, and the omission was invisible from
// this file: the desk is granted RESERVATION_* and HANDOVER_* by the seed, and the backend names
// RESERVATION in both controllers' @PreAuthorize lists — so the API answered while no route would
// open. SRS §2.2.2 assigns UC-19.x and UC-20.x to Reservation Staff and rbac-matrix §2 lists both
// rows under RS, which is why the fix is to restore the screens rather than revoke the permissions.
const RESERVATION_ROUTES: string[] = [
  ROUTE_PATHS.roomRequests,
  ROUTE_PATHS.bookingConfirmation,
  ROUTE_PATHS.reservationStatus,
  ROUTE_PATHS.operationalHandover,
  ROUTE_PATHS.depositPayment,
  ROUTE_PATHS.notifications,
];

/**
 * The desks are gated twice: the route must belong to the desk AND the user must hold the
 * screen's permission. The route list is the fixed shape of the job; the permission is what an
 * Admin can take away in UC-6.4. Checking only the route would let a revoked permission leave a
 * nav link whose every request 403s — the same mismatch the handover screens used to have.
 */
function canAccessDeskRoute(routes: string[], pathname: string, permissions: string[]): boolean {
  if (!matchesAny(routes, pathname)) return false;
  const required = requiredPermissionFor(pathname);
  return required == null || permissions.includes(required);
}

// Maps each protected route to the permission code that gates it (the screen's VIEW
// permission; the quotation approvals queue needs the dedicated APPROVE permission).
// Longest match wins, so /quotations/pending-approvals resolves to QUOTATION_APPROVE
// rather than the parent /quotations → QUOTATION_VIEW.
const ROUTE_PERMISSION: Record<string, string> = {
  [ROUTE_PATHS.leads]: "LEAD_VIEW",
  [ROUTE_PATHS.customerProfiles]: "CUSTOMER_VIEW",
  [ROUTE_PATHS.followUpTasks]: "TASK_VIEW",
  [ROUTE_PATHS.manageFollowUpTasks]: "TASK_VIEW",
  // Blueprint §14 groups `/tasks` and `/calendar` under the same gate: the
  // calendar renders tasks, so seeing it requires being allowed to see them.
  [ROUTE_PATHS.calendar]: "TASK_VIEW",
  [ROUTE_PATHS.salesPipeline]: "PIPELINE_VIEW",
  [ROUTE_PATHS.deals]: "DEAL_VIEW",
  [ROUTE_PATHS.pendingApprovals]: "QUOTATION_APPROVE",
  [ROUTE_PATHS.quotations]: "QUOTATION_VIEW",
  [ROUTE_PATHS.contracts]: "QUOTATION_VIEW",
  [ROUTE_PATHS.interactionTimeline]: "INTERACTION_VIEW",
  [ROUTE_PATHS.bookingConfirmation]: "BOOKING_VIEW",
  [ROUTE_PATHS.roomRequests]: "ROOM_REQUEST_VIEW",
  [ROUTE_PATHS.reservationStatus]: "RESERVATION_VIEW",
  [ROUTE_PATHS.operationalHandover]: "HANDOVER_VIEW",
  [ROUTE_PATHS.frontOfficeHandover]: "HANDOVER_VIEW",
  [ROUTE_PATHS.depositPayment]: "PAYMENT_VIEW",
  [ROUTE_PATHS.notifications]: "NOTIFICATION_VIEW",
  [ROUTE_PATHS.reminders]: "REMINDER_VIEW",
  [ROUTE_PATHS.reporting]: "REPORTING_VIEW",
  [ROUTE_PATHS.sla]: "SLA_VIEW",
  [ROUTE_PATHS.customerFeedback]: "FEEDBACK_VIEW",
  [ROUTE_PATHS.sentimentAnalytics]: "FEEDBACK_VIEW",
  [ROUTE_PATHS.aiAssistant]: "CHAT_VIEW",
};

// Keys ordered longest-first for specific-match-wins resolution.
const ROUTE_PERMISSION_KEYS = Object.keys(ROUTE_PERMISSION).sort((a, b) => b.length - a.length);

/** The permission required to open a route, or null if the route isn't permission-gated. */
export function requiredPermissionFor(pathname: string): string | null {
  const key = ROUTE_PERMISSION_KEYS.find(
    (p) => pathname === p || pathname.startsWith(p + "/"),
  );
  return key ? ROUTE_PERMISSION[key] : null;
}

function matchesAny(routes: string[], pathname: string): boolean {
  return routes.some((p) => pathname === p || pathname.startsWith(p + "/"));
}

/**
 * Whether the user may open `pathname`. ADMIN is role-gated; SALES/MANAGER are gated by
 * their effective `permissions`. The bare `/dashboard` dispatcher and the user's own
 * dashboard home are always allowed.
 */
export function canAccessPath(
  role: AppRole,
  pathname: string,
  permissions: string[] = [],
): boolean {
  if (pathname === ROUTE_PATHS.dashboard) return true;
  // A role's own home stays reachable even if its permission was revoked. The layout guard
  // redirects a denied route to this path, so gating it would spin in a redirect loop; the page
  // loads and its data calls surface the 403 instead.
  if (pathname === DASHBOARD_PATHS[role]) return true;

  // Profile page is self-service — every authenticated user may access it regardless of role.
  if (pathname === ROUTE_PATHS.profile || pathname.startsWith(ROUTE_PATHS.profile + "/")) return true;

  if (role === "ADMIN") {
    return matchesAny(ADMIN_ROUTES, pathname);
  }

  if (role === "FO") {
    return canAccessDeskRoute(FO_ROUTES, pathname, permissions);
  }

  if (role === "RESERVATION") {
    return canAccessDeskRoute(RESERVATION_ROUTES, pathname, permissions);
  }

  // SALES / MANAGER — permission driven.
  const required = requiredPermissionFor(pathname);
  // HANDOVER_VIEW used to be a free pass here. It no longer can be: the handover APIs now
  // enforce that permission server-side, so waving the route through would surface a nav link
  // whose every request 403s. Front Office is unaffected — it returns above, via FO_ROUTES.
  //
  // SLA is the opposite case and stays a free pass: its monitoring reads are deliberately
  // open to any authenticated user on the backend, so gating the route here would hide a
  // screen the API would happily serve. Basic operational visibility (view-only; the
  // Configure tab is separately gated to ADMIN/MANAGER).
  if (required === "SLA_VIEW") return true;
  // Leads are role-gated on the backend (@PreAuthorize hasAnyRole('SALES','MANAGER')),
  // not permission-gated. A Manager is authorised for every lead endpoint regardless
  // of whether anyone ever granted LEAD_VIEW, and the Manager lead screen is a
  // distinct surface — it assigns owners and spans the whole team rather than one
  // rep's pipeline. Gating it on a permission the API ignores just hid a working
  // screen, so the role decides here exactly as it does server-side.
  if (required === "LEAD_VIEW" && role === "MANAGER") return true;
  // Quotation approval is Manager-only on the backend (@PreAuthorize hasRole('MANAGER'))
  // — enforce that here too, so a stray QUOTATION_APPROVE grant to a non-manager role
  // can't surface a nav link / route whose API calls would just 403 anyway.
  if (required === "QUOTATION_APPROVE" && role !== "MANAGER") return false;
  // The Room Requests queue belongs to the Reservation team (handled by RESERVATION_ROUTES
  // above). Neither Manager nor Sales staff can answer one — they have no PMS access — so
  // the page stays hidden for them even if someone grants ROOM_REQUEST_VIEW. Sales still
  // sees a quotation's own room state through the panel inside the quotation.
  if (required === "ROOM_REQUEST_VIEW") return false;
  // Two different screens map to HANDOVER_VIEW, so the permission alone cannot tell them apart:
  // `/operational-handover` (Sales/Reservation author the handover) and `/front-office-handover`
  // (the arrival desk). The arrival desk belongs to Front Office **alone** — rbac-matrix §2 puts
  // the "Arrival Handover / Arrival Handover Detail (view, update readiness)" row under the FO
  // column only, and the backend now gates /api/v1/arrival-handovers on hasAnyRole('FO',
  // 'FRONT_OFFICE') to match.
  //
  // Sales holds HANDOVER_VIEW for its own screen and so used to reach this one too, where every
  // request 403'd — and the desk rendered those 403s as "No arrival requests yet", so a Sales user
  // saw an empty front desk rather than a refusal. Manager was allowed here as a supervisor, which
  // the matrix does not grant: UC-22.1/22.2/22.3 are Front Office duties, and Manager oversight of
  // arrivals is served by the handover log on `/operational-handover`. Front Office itself never
  // reaches this line — it returns above, via FO_ROUTES.
  if (matchesAny([ROUTE_PATHS.frontOfficeHandover], pathname)) {
    return false;
  }
  return required != null && permissions.includes(required);
}
