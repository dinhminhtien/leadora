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
export type AppRole = "SALES" | "MANAGER" | "ADMIN" | "FO";

export function getUserRole(user?: { roles?: string[] } | null): AppRole {
  const raw = (user?.roles?.[0] ?? "").toUpperCase();
  if (raw === "ADMIN") return "ADMIN";
  if (raw === "MANAGER") return "MANAGER";
  if (raw === "FO" || raw === "FRONT_OFFICE") return "FO";
  return "SALES";
}

/** The home dashboard URL for each role. Front Office lands directly on its arrival-handover desk. */
export const DASHBOARD_PATHS: Record<AppRole, string> = {
  SALES: "/dashboard/staff",
  MANAGER: "/dashboard/manager",
  ADMIN: "/dashboard/admin",
  FO: ROUTE_PATHS.frontOfficeHandover,
};

export function dashboardPathForRole(role: AppRole): string {
  return DASHBOARD_PATHS[role];
}

// Admin (role-based): account/role administration + payment oversight only.
const ADMIN_ROUTES: string[] = [
  DASHBOARD_PATHS.ADMIN,
  ROUTE_PATHS.identityAccess,
  ROUTE_PATHS.depositPayment,
];

// Front Office (role-based): a simple, focused desk — arrival handovers + their alerts only.
const FO_ROUTES: string[] = [
  ROUTE_PATHS.frontOfficeHandover,
  ROUTE_PATHS.notifications,
];

// Maps each protected route to the permission code that gates it (the screen's VIEW
// permission; the quotation approvals queue needs the dedicated APPROVE permission).
// Longest match wins, so /quotations/pending-approvals resolves to QUOTATION_APPROVE
// rather than the parent /quotations → QUOTATION_VIEW.
const ROUTE_PERMISSION: Record<string, string> = {
  [ROUTE_PATHS.leads]: "LEAD_VIEW",
  [ROUTE_PATHS.customerProfiles]: "CUSTOMER_VIEW",
  [ROUTE_PATHS.followUpTasks]: "TASK_VIEW",
  [ROUTE_PATHS.manageFollowUpTasks]: "TASK_VIEW",
  [ROUTE_PATHS.salesPipeline]: "PIPELINE_VIEW",
  [ROUTE_PATHS.deals]: "DEAL_VIEW",
  [ROUTE_PATHS.pendingApprovals]: "QUOTATION_APPROVE",
  [ROUTE_PATHS.quotations]: "QUOTATION_VIEW",
  [ROUTE_PATHS.interactionTimeline]: "INTERACTION_VIEW",
  [ROUTE_PATHS.bookingConfirmation]: "BOOKING_VIEW",
  [ROUTE_PATHS.reservationStatus]: "RESERVATION_VIEW",
  [ROUTE_PATHS.operationalHandover]: "HANDOVER_VIEW",
  [ROUTE_PATHS.frontOfficeHandover]: "HANDOVER_VIEW",
  [ROUTE_PATHS.depositPayment]: "PAYMENT_VIEW",
  [ROUTE_PATHS.notifications]: "NOTIFICATION_VIEW",
  [ROUTE_PATHS.reminders]: "REMINDER_VIEW",
  [ROUTE_PATHS.reporting]: "REPORTING_VIEW",
  [ROUTE_PATHS.sla]: "SLA_VIEW",
  [ROUTE_PATHS.customerFeedback]: "FEEDBACK_VIEW",
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
  if (pathname === DASHBOARD_PATHS[role]) return true;

  // Profile page is self-service — every authenticated user may access it regardless of role.
  if (pathname === ROUTE_PATHS.profile || pathname.startsWith(ROUTE_PATHS.profile + "/")) return true;

  if (role === "ADMIN") {
    return matchesAny(ADMIN_ROUTES, pathname);
  }

  if (role === "FO") {
    return matchesAny(FO_ROUTES, pathname);
  }

  // SALES / MANAGER — permission driven.
  const required = requiredPermissionFor(pathname);
  return required != null && permissions.includes(required);
}
