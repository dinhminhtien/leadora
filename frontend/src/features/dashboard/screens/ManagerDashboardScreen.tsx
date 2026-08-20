"use client";

/**
 * Sales Manager home — Website UI Blueprint §8.2.
 *
 * Everything a Sales Staff sees (team-wide), plus the management-only tools:
 * quotation approvals, team task oversight, SLA control, reporting and feedback
 * review. Membership of this list is dictated by the RBAC matrix (§14) and is
 * unchanged by the redesign — only the presentation moved to `ToolCardGrid`.
 */

import {
  CalendarCheck,
  FileText,
  Gauge,
  MessageSquareText,
  ShieldCheck,
} from "lucide-react";

import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { ToolCardGrid, type ToolCardItem } from "@/components/ui/tool-card";
import { DashboardScreen } from "@/features/reporting/screens/DashboardScreen";
import { canAccessPath, getUserRole } from "@/shared/auth/access";
import { useAuthStore } from "@/stores/auth_store";

const MANAGER_TOOLS: ToolCardItem[] = [
  {
    href: ROUTE_PATHS.pendingApprovals,
    label: "Pending Approvals",
    description: "Approve or reject quotations",
    icon: ShieldCheck,
    tone: "teal",
  },
  {
    href: ROUTE_PATHS.reporting,
    label: "Reporting",
    description: "Sales & SLA reports",
    icon: FileText,
    tone: "brand",
  },
  {
    href: ROUTE_PATHS.sla,
    label: "SLA Control",
    description: "Configure & monitor SLAs",
    icon: Gauge,
    tone: "warning",
  },
  {
    href: ROUTE_PATHS.manageFollowUpTasks,
    label: "Team Tasks",
    description: "Assign & reassign tasks",
    icon: CalendarCheck,
    tone: "info",
  },
  {
    href: ROUTE_PATHS.customerFeedback,
    label: "Feedback",
    description: "Review customer feedback",
    icon: MessageSquareText,
    tone: "danger",
  },
];

export function ManagerDashboardScreen() {
  const user = useAuthStore((s) => s.user);
  const role = getUserRole(user);
  const permissions = user?.permissions ?? [];

  // Filtered through the same gate as the sidebar and command palette. These cards were a fixed
  // list, so revoking a Manager's SLA_VIEW or FEEDBACK_VIEW in UC-6.4 left the tile on their home
  // screen — clicking it bounced straight back here via the layout's route guard. The dashboard is
  // the one surface that still described the old permission set.
  const tools = MANAGER_TOOLS.filter((t) => canAccessPath(role, t.href, permissions));

  return (
    <div className="space-y-6">
      {tools.length > 0 && (
        <ToolCardGrid title="Manager tools" icon={ShieldCheck} items={tools} />
      )}

      {/* Team-wide sales pipeline analytics (shared dashboard foundation). */}
      <DashboardScreen />
    </div>
  );
}
