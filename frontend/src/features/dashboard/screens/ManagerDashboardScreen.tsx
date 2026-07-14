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
  { href: ROUTE_PATHS.pendingApprovals,      label: "Pending Approvals", desc: "Approve or reject quotations", Icon: ShieldCheck,       color: "text-emerald-600 bg-emerald-50 border-emerald-100" },
  { href: ROUTE_PATHS.reporting,             label: "Reporting",         desc: "Sales & SLA reports",          Icon: FileText,          color: "text-blue-600 bg-blue-50 border-blue-100" },
  { href: ROUTE_PATHS.sla,                   label: "SLA Control",       desc: "Configure & monitor SLAs",     Icon: Gauge,             color: "text-amber-600 bg-amber-50 border-amber-100" },
  { href: ROUTE_PATHS.manageFollowUpTasks,   label: "Team Tasks",        desc: "Assign & reassign tasks",      Icon: CalendarCheck,     color: "text-violet-600 bg-violet-50 border-violet-100" },
  { href: ROUTE_PATHS.customerFeedback,      label: "Feedback",          desc: "Review customer feedback",     Icon: MessageSquareText, color: "text-rose-600 bg-rose-50 border-rose-100" },
];

export function ManagerDashboardScreen() {
  return (
    <div className="space-y-6">
      {/* Manager-only quick tools */}
      <div className="bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-xs p-4">
        <div className="flex items-center gap-2 mb-3">
          <ShieldCheck className="size-4 text-primary" />
          <h2 className="text-sm font-bold text-slate-800 dark:text-zinc-100">Manager Tools</h2>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          {MANAGER_TOOLS.map((t) => (
            <Link key={t.href} href={t.href}
              className="group relative flex flex-col gap-2 p-3 rounded-xl border border-slate-100 dark:border-zinc-800 hover:border-slate-200 hover:shadow-sm transition bg-slate-50/40 dark:bg-zinc-800/40">
              <span className={`flex items-center justify-center size-8 rounded-lg border ${t.color}`}>
                <t.Icon className="size-4" />
              </span>
              <div>
                <p className="text-xs font-bold text-slate-700 dark:text-zinc-100">{t.label}</p>
                <p className="text-[10px] text-slate-400 mt-0.5 leading-tight">{t.desc}</p>
              </div>
              <ArrowUpRight className="absolute top-3 right-3 size-3.5 text-slate-300 opacity-0 group-hover:opacity-100 transition" />
            </Link>
          ))}
        </div>
      </div>

      {/* Team-wide sales pipeline analytics (shared dashboard) */}
      <DashboardScreen />
    </div>
  );
}
