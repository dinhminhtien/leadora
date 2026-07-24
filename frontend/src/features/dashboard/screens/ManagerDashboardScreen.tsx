"use client";

import Link from "next/link";
import { ShieldCheck, FileText, Gauge, MessageSquareText, CalendarCheck, ArrowUpRight } from "lucide-react";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { DashboardScreen } from "@/features/reporting/screens/DashboardScreen";

/**
 * Sales Manager home: everything a Sales Staff sees (team-wide), plus the
 * management-only tools — quotation approvals, team task oversight, SLA control,
 * reporting and feedback review (per the RBAC matrix).
 */
const MANAGER_TOOLS: { href: string; label: string; desc: string; Icon: React.ElementType; color: string }[] = [
  { href: ROUTE_PATHS.pendingApprovals,      label: "Pending Approvals", desc: "Approve or reject quotations", Icon: ShieldCheck,       color: "text-teal bg-teal/10 border-teal/20" },
  { href: ROUTE_PATHS.reporting,             label: "Reporting",         desc: "Sales & SLA reports",          Icon: FileText,          color: "text-primary bg-primary/10 border-primary/20" },
  { href: ROUTE_PATHS.sla,                   label: "SLA Control",       desc: "Configure & monitor SLAs",     Icon: Gauge,             color: "text-warning bg-warning/10 border-warning/20" },
  { href: ROUTE_PATHS.manageFollowUpTasks,   label: "Team Tasks",        desc: "Assign & reassign tasks",      Icon: CalendarCheck,     color: "text-info bg-info/10 border-info/20" },
  { href: ROUTE_PATHS.customerFeedback,      label: "Feedback",          desc: "Review customer feedback",     Icon: MessageSquareText, color: "text-destructive bg-destructive/10 border-destructive/20" },
];

export function ManagerDashboardScreen() {
  return (
    <div className="space-y-6">
      {/* Manager-only quick tools */}
      <div className="card-elev p-4">
        <div className="flex items-center gap-2 mb-3">
          <ShieldCheck className="size-4 text-primary" />
          <h2 className="text-sm font-bold text-foreground">Manager Tools</h2>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          {MANAGER_TOOLS.map((t) => (
            <Link key={t.href} href={t.href}
              className="group relative flex flex-col gap-2 p-3 rounded-xl border border-border hover:border-primary/20 hover:shadow-elev-2 transition-all duration-150 bg-muted/40">
              <span className={`flex items-center justify-center size-8 rounded-lg border ${t.color}`}>
                <t.Icon className="size-4" />
              </span>
              <div>
                <p className="text-xs font-bold text-foreground">{t.label}</p>
                <p className="text-[10px] text-muted-foreground mt-0.5 leading-tight">{t.desc}</p>
              </div>
              <ArrowUpRight className="absolute top-3 right-3 size-3.5 text-muted-foreground/50 opacity-0 group-hover:opacity-100 transition" />
            </Link>
          ))}
        </div>
      </div>

      {/* Team-wide sales pipeline analytics (shared dashboard) */}
      <DashboardScreen />
    </div>
  );
}
