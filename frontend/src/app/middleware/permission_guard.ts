import type { User } from "@/shared/types/auth";
import type { Role } from "@/shared/types/role";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

export function getUserRole(user: User | null): Role {
  return user?.roles?.[0] ?? "SALES";
}

export function dashboardPathForRole(role: Role): string {
  switch (role) {
    case "ADMIN":       return ROUTE_PATHS.identityAccess;
    case "MANAGER":     return ROUTE_PATHS.salesPipeline;
    case "FRONT_OFFICE": return ROUTE_PATHS.reservationStatus;
    case "SALES":
    case "STAFF":
    default:            return ROUTE_PATHS.leads;
  }
}

const ROLE_ALLOWED: Record<string, Role[]> = {
  [ROUTE_PATHS.identityAccess]:       ["ADMIN"],
  [ROUTE_PATHS.reporting]:            ["ADMIN", "MANAGER"],
  [ROUTE_PATHS.sla]:                  ["ADMIN", "MANAGER"],
  [ROUTE_PATHS.pendingApprovals]:     ["ADMIN", "MANAGER"],
  [ROUTE_PATHS.aiAssistant]:          ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.leads]:                ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.salesPipeline]:        ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.deals]:                ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.quotations]:           ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.manageFollowUpTasks]:  ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.reminders]:            ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.interactionTimeline]:  ["ADMIN", "MANAGER", "SALES"],
  [ROUTE_PATHS.customerProfiles]:     ["ADMIN", "MANAGER", "SALES", "FRONT_OFFICE"],
  [ROUTE_PATHS.reservationStatus]:    ["ADMIN", "MANAGER", "FRONT_OFFICE"],
  [ROUTE_PATHS.bookingConfirmation]:  ["ADMIN", "MANAGER", "FRONT_OFFICE"],
  [ROUTE_PATHS.operationalHandover]:  ["ADMIN", "MANAGER", "FRONT_OFFICE"],
  [ROUTE_PATHS.frontOfficeHandover]:  ["ADMIN", "MANAGER", "FRONT_OFFICE"],
  [ROUTE_PATHS.depositPayment]:       ["ADMIN", "MANAGER", "FRONT_OFFICE"],
};

export function canAccessPath(role: Role, pathname: string, _permissions: string[] = []): boolean {
  if (role === "ADMIN") return true;
  for (const [route, allowed] of Object.entries(ROLE_ALLOWED)) {
    if (pathname === route || pathname.startsWith(route + "/")) {
      return allowed.includes(role);
    }
  }
  return true;
}

/** Legacy alias kept for existing callers. */
export function canAccessRoute(pathname: string, roles: Role[] = []): boolean {
  return canAccessPath(roles[0] ?? "SALES", pathname);
}
