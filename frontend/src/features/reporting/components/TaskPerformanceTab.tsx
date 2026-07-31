"use client";

import React from "react";
import { Loader2, ClipboardList, CheckCircle2, AlertTriangle, XCircle, User } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { useReportRange, useTaskPerformanceReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, SegmentBar, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ } from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

export function TaskPerformanceTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useTaskPerformanceReport(range.params, range.enabled);

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("task-performance", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Follow-up Task Performance (UC-23.2)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Scope", data.ownScope ? "Own assigned tasks" : "Team-wide"],
        ["Generated at", new Date().toLocaleString("vi-VN")],
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["Total tasks", data.totalTasks],
            ["Completed", data.completed],
            ["Open", data.open],
            ["Cancelled", data.cancelled],
            ["Overdue", data.overdue],
            ["Completion rate (%)", data.completionRate],
            ["Overdue rate (%)", data.overdueRate],
            ["Priority high", data.priorityHigh],
            ["Priority medium", data.priorityMedium],
            ["Priority low", data.priorityLow],
          ],
        },
      ],
      tables: [
        {
          title: "Performance by staff",
          headers: ["Staff", "Total", "Completed", "Overdue", "Completion rate (%)"],
          rows: data.staff.map((s) => [s.name, s.total, s.completed, s.overdue, s.completionRate]),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="Follow-up Task Performance"
        period={periodLabel(range.dateFrom, range.dateTo)}
        onExport={handleExport}
        disabled={!data || data.totalTasks === 0}
      />

      <ReportDateRange
        dateFrom={range.dateFrom}
        dateTo={range.dateTo}
        setDateFrom={range.setDateFrom}
        setDateTo={range.setDateTo}
        invalid={range.invalid}
      />

      {/* The scope is decided by the caller's role on the server, so say which one is on screen —
          a staff member comparing their figures against a manager's needs to know why they differ. */}
      {data?.ownScope && (
        <div className="flex items-start gap-2 rounded-xl border border-blue-100 bg-blue-50 px-4 py-2.5">
          <User className="mt-0.5 size-3.5 shrink-0" style={{ color: VIZ.open }} />
          <p className="text-xs text-slate-600">Showing your own assigned tasks only.</p>
        </div>
      )}

      {isLoading && (
        <div className="flex items-center gap-2 p-6 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> Aggregating data…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Failed to load the report. Please try again.</p>}

      {data && !isLoading && data.totalTasks === 0 && <EmptyReport />}

      {data && !isLoading && data.totalTasks > 0 && (
        <>
          {/* KPI tiles — status accents reserved for the good/bad numbers, others stay ink. */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="Total tasks" value={String(data.totalTasks)} icon={<ClipboardList className="size-3.5" />} />
            <StatTile
              label="Completed"
              value={String(data.completed)}
              sub={`Rate ${pct(data.completionRate)}`}
              icon={<CheckCircle2 className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Overdue"
              value={String(data.overdue)}
              sub={`Rate ${pct(data.overdueRate)}`}
              icon={<AlertTriangle className="size-3.5" />}
              accent={VIZ.critical}
            />
            <StatTile
              label="Cancelled"
              value={String(data.cancelled)}
              sub={`Open ${data.open}`}
              icon={<XCircle className="size-3.5" />}
            />
          </div>

          {/* Completion + status composition */}
          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-4 p-4">
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Completion rate</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.completionRate)}</span>
                  </div>
                  <Meter value={data.completionRate} fill={VIZ.good} track={VIZ.trackGreen} />
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Overdue rate</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.critical }}>{pct(data.overdueRate)}</span>
                  </div>
                  <Meter value={data.overdueRate} fill={VIZ.critical} track={VIZ.trackRed} />
                  <Note>
                    Overdue is derived, not stored: past the task&apos;s end time and not yet
                    completed or cancelled. It is evaluated when the report runs.
                  </Note>
                </div>
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">Status breakdown</h3>
                {/* Overdue is a derived flag on OPEN tasks (overdue ⊆ open), so split Open into
                    "On track" (open − overdue) + "Overdue" — mutually exclusive, summing to total. */}
                <SegmentBar
                  segments={[
                    { label: "Completed", value: data.completed, color: VIZ.good },
                    { label: "On track", value: Math.max(0, data.open - data.overdue), color: VIZ.open },
                    { label: "Overdue", value: data.overdue, color: VIZ.critical },
                    { label: "Cancelled", value: data.cancelled, color: VIZ.muted },
                  ]}
                />
              </CardContent>
            </Card>
          </div>

          {/* Priority distribution — ordinal blue ramp (High darkest → Low lightest) */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="space-y-2 p-4">
              <h3 className="text-sm font-bold text-slate-700">Distribution by priority</h3>
              <SegmentBar
                segments={[
                  { label: "High", value: data.priorityHigh, color: VIZ.priHigh },
                  { label: "Medium", value: data.priorityMedium, color: VIZ.priMed },
                  { label: "Low", value: data.priorityLow, color: VIZ.priLow },
                ]}
              />
            </CardContent>
          </Card>

          {/* Per-staff breakdown */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Performance by staff</h3>
              </div>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Staff</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="text-right">Completed</TableHead>
                      <TableHead className="text-right">Overdue</TableHead>
                      <TableHead className="text-right">Completion</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.staff.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="py-6 text-center text-xs text-slate-400">
                          No per-staff data for this period.
                        </TableCell>
                      </TableRow>
                    )}
                    {data.staff.map((s) => (
                      <TableRow key={s.name} className={s.unassigned ? "bg-slate-50/70" : undefined}>
                        <TableCell className={s.unassigned ? "italic text-slate-500" : "font-semibold text-slate-700"}>
                          {s.name}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{s.total}</TableCell>
                        <TableCell className="text-right tabular-nums text-emerald-600">{s.completed}</TableCell>
                        <TableCell className="text-right tabular-nums text-rose-600">{s.overdue}</TableCell>
                        <TableCell className="text-right font-semibold tabular-nums">{pct(s.completionRate)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              {data.staff.some((s) => s.unassigned) && (
                <div className="px-4 pb-3">
                  <Note>
                    Tasks with nobody assigned are grouped into their own row so the column adds up
                    to the {data.totalTasks} in the headline figure.
                  </Note>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
