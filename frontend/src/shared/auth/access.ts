import { ROUTE_PATHS } from "@/app/routes/route_paths";

/**
 * Role-based access control (frontend).
 *
 * The backend `roles` table only contains three codes — ADMIN, SALES, MANAGER
 * (see the RBAC matrix). Everything else (SALES_STAFF/STAFF/…) is normalised to
 * SALES. Each role lands on its own dashboard and may only reach the routes
 * granted by the Screen Authorization matrix.
 */
export type AppRole = "SALES" | "MANAGER" | "ADMIN";

export function getUserRole(user?: { roles?: string[] } | null): AppRole {
  const raw = (user?.roles?.[0] ?? "").toUpperCase();
  if (raw === "ADMIN") return "ADMIN";
  if (raw === "MANAGER") return "MANAGER";
  // SALES, SALES_STAFF, STAFF or anything unknown → treat as Sales Staff.
  return "SALES";
}

/** The home dashboard URL for each role. */
export const DASHBOARD_PATHS: Record<AppRole, string> = {
  SALES: "/dashboard/staff",
  MANAGER: "/dashboard/manager",
  ADMIN: "/dashboard/admin",
};

export function dashboardPathForRole(role: AppRole): string {
  return DASHBOARD_PATHS[role];
}

// Sales Staff: the full sales-to-operations workflow, but only their own records
// (record-level scoping is enforced by the backend, e.g. lead owner-scoping).
const SALES_ROUTES: string[] = [
  DASHBOARD_PATHS.SALES,
  ROUTE_PATHS.leads,
  ROUTE_PATHS.customerProfiles,
  ROUTE_PATHS.followUpTasks,
  ROUTE_PATHS.salesPipeline,
  ROUTE_PATHS.deals,
  ROUTE_PATHS.interactionTimeline,
  ROUTE_PATHS.manageFollowUpTasks,
  ROUTE_PATHS.quotations,
  ROUTE_PATHS.bookingConfirmation,
  ROUTE_PATHS.reservationStatus,
  ROUTE_PATHS.operationalHandover,
  ROUTE_PATHS.frontOfficeHandover,
  ROUTE_PATHS.depositPayment,
  ROUTE_PATHS.notifications,
  ROUTE_PATHS.reminders,
  ROUTE_PATHS.aiAssistant,
];

// Sales Manager: everything a Sales Staff can reach (team-wide), plus approvals,
// team task management, reporting, SLA configuration and feedback review.
const MANAGER_ROUTES: string[] = [
  DASHBOARD_PATHS.MANAGER,
  ...SALES_ROUTES.filter((r) => r !== DASHBOARD_PATHS.SALES),
  ROUTE_PATHS.manageFollowUpTasks,
  ROUTE_PATHS.reporting,
  ROUTE_PATHS.sla,
  ROUTE_PATHS.customerFeedback,
];

// Admin: account/role administration and payment oversight only — no sales workflow.
const ADMIN_ROUTES: string[] = [
  DASHBOARD_PATHS.ADMIN,
  ROUTE_PATHS.identityAccess,
  ROUTE_PATHS.depositPayment,
];

export const ROLE_ROUTES: Record<AppRole, string[]> = {
  SALES: SALES_ROUTES,
  MANAGER: MANAGER_ROUTES,
  ADMIN: ADMIN_ROUTES,
};

// Sub-paths that fall under a route a Sales Staff can otherwise reach (e.g. the
// quotations module) but are reserved for managers — quotation approval. Denied
// to SALES even though the parent prefix is allowed.
const MANAGER_ONLY_PATHS: string[] = [ROUTE_PATHS.pendingApprovals];

/**
 * Whether `role` may open `pathname`. Matches a route exactly or as a path
 * prefix (so `/leads/123` is covered by `/leads`). The bare `/dashboard`
 * dispatcher is allowed for everyone — it only redirects to the role home.
 */
export function canAccessPath(role: AppRole, pathname: string): boolean {
  if (pathname === ROUTE_PATHS.dashboard) return true;
  // Manager-only sub-paths are denied to Sales Staff despite the allowed parent prefix.
  if (
    role === "SALES" &&
    MANAGER_ONLY_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/"))
  ) {
    return false;
  }
  return ROLE_ROUTES[role].some(
    (p) => pathname === p || pathname.startsWith(p + "/"),
  );
}
