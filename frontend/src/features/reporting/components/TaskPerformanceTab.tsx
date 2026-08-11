"use client";

import React from "react";
import { Loader2, ClipboardList, CheckCircle2, AlertTriangle, User, Timer, Unlink } from "lucide-react";
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
import type { TaskCountRow } from "@/services/reporting_service";
import { StatTile, Meter, SegmentBar, HBarList, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ } from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;
const optPct = (n?: number) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}%`);
const hrs = (n?: number) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}h`);

/** Aging bands run light → dark with severity; a single hue, because this is a magnitude scale. */
const AGING_COLORS: Record<string, string> = {
  D1_3: VIZ.priLow,
  D4_7: VIZ.priMed,
  D8_PLUS: VIZ.priHigh,
};
const PRIORITY_COLORS: Record<string, string> = {
  HIGH: VIZ.priHigh,
  MEDIUM: VIZ.priMed,
  LOW: VIZ.priLow,
};

const asBars = (rows: TaskCountRow[] | undefined, colors: Record<string, string>, fallback: string) =>
  (rows ?? []).map((r) => ({ label: r.label, value: r.count, color: colors[r.key] ?? fallback }));

export function TaskPerformanceTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useTaskPerformanceReport(range.params, range.enabled);

  // A period can have no tasks raised and still have real work in it: tasks opened earlier and
  // finished here. Emptiness has to be judged on all three axes, not just the first.
  const hasData = !!data && (data.totalTasks > 0 || data.resolvedTotal > 0 || data.openOverdue > 0);

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("task-performance", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Follow-up Task Performance (UC-23.2)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Time zone", data.timezone ?? "—"],
        ["Scope", data.ownScope ? "Own assigned tasks" : "Team-wide"],
        ["Generated at", new Date().toLocaleString("vi-VN")],
      ],
      sections: [
        {
          title: "Raised in period (by creation date)",
          rows: [
            ["Total tasks", data.totalTasks],
            ["…of which completed", data.completed],
            ["Still open", data.open],
            ["Cancelled", data.cancelled],
            ["Completion rate (%)", data.completionRate],
            ["Priority high", data.priorityHigh],
            ["Priority medium", data.priorityMedium],
            ["Priority low", data.priorityLow],
            ["Not linked to any lead/customer/deal", data.orphanTasks],
            ["Orphan rate (%)", data.orphanRate],
          ],
        },
        {
          title: "Resolved in period (by completion date)",
          rows: [
            ["Tasks finished", data.resolvedTotal],
            ["Finished on time", data.resolvedOnTime],
            ["Finished late", data.resolvedLate],
            ["Finished with no deadline set", data.resolvedNoDeadline],
            ["Punctuality rate (%)", data.punctualityRate ?? "not measurable"],
            ["Average cycle time (hours)", data.avgCycleHours],
            ["Completed without a completion time", data.completedUndated],
            ["Punctuality coverage (%)", data.punctualityCoverage],
          ],
        },
        {
          title: "Open queue right now",
          rows: [
            ["Open and overdue", data.openOverdue],
            ["Overdue rate of open tasks (%)", data.overdueRate],
            ["Average days overdue", data.avgDaysOverdue],
            ...(data.overdueByPriority ?? []).map(
              (r) => [`Overdue — ${r.label} priority`, r.count] as [string, number],
            ),
          ],
        },
        {
          title: "SLA",
          rows: [
            ["SLAs with a settled outcome", data.slaDecided],
            ["Met on time", data.slaOnTime],
            ["Compliance rate (%)", data.slaComplianceRate],
          ],
        },
      ],
      tables: [
        {
          title: "Performance by staff",
          headers: ["Staff", "Raised", "Completed", "Completion (%)", "Open overdue",
            "Finished on time", "Finished late", "Punctuality (%)", "Avg cycle (h)"],
          rows: data.staff.map((s) => [
            s.name, s.total, s.completed, s.completionRate, s.openOverdue,
            s.resolvedOnTime, s.resolvedLate, s.punctualityRate ?? "", s.avgCycleHours ?? "",
          ]),
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
        disabled={!hasData}
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

      {data && !isLoading && !hasData && <EmptyReport />}

      {data && !isLoading && hasData && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile
              label="Tasks raised (in period)"
              value={String(data.totalTasks)}
              sub={`${data.completed} since completed · ${pct(data.completionRate)}`}
              icon={<ClipboardList className="size-3.5" />}
            />
            {/* The figure the report previously could not produce: finishing a task late used to
                remove it from the overdue count entirely. */}
            <StatTile
              label="Finished on time (in period)"
              value={String(data.resolvedOnTime)}
              sub={`${data.resolvedLate} late · ${optPct(data.punctualityRate)} punctual`}
              icon={<CheckCircle2 className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Raised here & still overdue"
              value={String(data.openOverdue)}
              sub={`${pct(data.overdueRate)} of open · avg ${data.avgDaysOverdue.toFixed(1)}d late`}
              icon={<AlertTriangle className="size-3.5" />}
              accent={VIZ.critical}
            />
            <StatTile
              label="Average cycle time"
              value={hrs(data.avgCycleHours)}
              sub={`${data.resolvedTotal} tasks finished`}
              icon={<Timer className="size-3.5" />}
            />
          </div>

          <Note>
            Three questions, three answers. <strong>Raised</strong> counts follow-up work that came
            in, by creation date. <strong>Finished</strong> counts work completed in this period and
            whether it beat its deadline, whenever the task was raised. <strong>Open &amp;
            overdue</strong> is measured against the clock right now and belongs to no period. A
            single &ldquo;overdue&rdquo; figure used to stand for all three, and it dropped a task
            the moment it was completed — so a period in which everything was finished late scored
            zero. Day boundaries are cut in {data.timezone ?? "the business time zone"}.
          </Note>

          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-4 p-4">
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Punctuality</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{optPct(data.punctualityRate)}</span>
                  </div>
                  <Meter value={data.punctualityRate ?? 0} fill={VIZ.good} track={VIZ.trackGreen} />
                  <Note>
                    {data.punctualityRate === undefined || data.punctualityRate === null
                      ? "Nothing finished in this period carried a deadline, so punctuality cannot be judged either way."
                      : `${data.resolvedOnTime} of ${data.resolvedOnTime + data.resolvedLate} finished tasks beat their deadline.`}
                    {data.resolvedNoDeadline > 0 &&
                      ` ${data.resolvedNoDeadline} more had no deadline set and are held out rather than counted as on time.`}
                    {data.completedUndated > 0 &&
                      ` Punctuality is measurable for ${pct(data.punctualityCoverage)} of completed tasks — ${data.completedUndated} were finished before completion times were recorded.`}
                  </Note>
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Overdue share of the open queue</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.critical }}>{pct(data.overdueRate)}</span>
                  </div>
                  <Meter value={data.overdueRate} fill={VIZ.critical} track={VIZ.trackRed} />
                  <Note>
                    Derived, never stored (BR-17): past the task&apos;s end time and not yet
                    completed or cancelled, evaluated when the report runs.
                  </Note>
                </div>
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-3 p-4">
                <h3 className="text-sm font-bold text-slate-700">Status of the tasks raised here</h3>
                <SegmentBar
                  segments={[
                    { label: "Completed", value: data.completed, color: VIZ.good },
                    { label: "On track", value: Math.max(0, data.open - data.openOverdue), color: VIZ.open },
                    { label: "Overdue", value: data.openOverdue, color: VIZ.critical },
                    { label: "Cancelled", value: data.cancelled, color: VIZ.muted },
                  ]}
                />
                <div className="pt-1">
                  <h3 className="mb-2 text-sm font-bold text-slate-700">How overdue, and how urgent</h3>
                  {data.openOverdue > 0 ? (
                    <div className="space-y-3">
                      <HBarList items={asBars(data.overdueAging, AGING_COLORS, VIZ.critical)} />
                      <HBarList items={asBars(data.overdueByPriority, PRIORITY_COLORS, VIZ.open)} />
                      <Note>
                        Three of four high-priority tasks overdue is a different situation from three
                        of forty low-priority ones. The report used to show priority and overdue as
                        separate totals, so that difference could not be seen.
                      </Note>
                    </div>
                  ) : (
                    <p className="text-[11px] text-slate-400">Nothing in the queue is running late.</p>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">What kind of follow-up work</h3>
                {data.activityMix && data.activityMix.length > 0 ? (
                  <HBarList items={asBars(data.activityMix, {}, VIZ.open)} />
                ) : (
                  <p className="text-[11px] text-slate-400">No activity type recorded on these tasks.</p>
                )}
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-3 p-4">
                <h3 className="text-sm font-bold text-slate-700">Priority mix &amp; linkage</h3>
                <SegmentBar
                  segments={[
                    { label: "High", value: data.priorityHigh, color: VIZ.priHigh },
                    { label: "Medium", value: data.priorityMedium, color: VIZ.priMed },
                    { label: "Low", value: data.priorityLow, color: VIZ.priLow },
                  ]}
                />
                <div className="flex items-start gap-2 rounded-lg bg-slate-50 px-3 py-2">
                  <Unlink className="mt-0.5 size-3.5 shrink-0 text-slate-400" />
                  <p className="text-[11px] leading-relaxed text-slate-600">
                    <strong>{data.orphanTasks}</strong> task{data.orphanTasks === 1 ? "" : "s"} (
                    {pct(data.orphanRate)}) are attached to no lead, customer or deal — effort that
                    never shows up against an opportunity.
                  </p>
                </div>
                {data.slaDecided > 0 && (
                  <p className="text-[11px] text-slate-500">
                    SLA: <strong>{data.slaOnTime}</strong> of {data.slaDecided} settled on time (
                    {pct(data.slaComplianceRate)}) — classified by the same rules as the SLA
                    Compliance tab, so a breach resolved late still counts as a breach.
                  </p>
                )}
              </CardContent>
            </Card>
          </div>

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
                      <TableHead className="text-right">Raised</TableHead>
                      <TableHead className="text-right">Completed</TableHead>
                      <TableHead className="text-right">Open overdue</TableHead>
                      <TableHead className="text-right">On time</TableHead>
                      <TableHead className="text-right">Late</TableHead>
                      <TableHead className="text-right">Punctuality</TableHead>
                      <TableHead className="text-right">Avg cycle</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.staff.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={8} className="py-6 text-center text-xs text-slate-400">
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
                        <TableCell className="text-right tabular-nums text-rose-600">{s.openOverdue}</TableCell>
                        <TableCell className="text-right tabular-nums text-emerald-600">{s.resolvedOnTime}</TableCell>
                        <TableCell className="text-right tabular-nums text-amber-600">{s.resolvedLate}</TableCell>
                        <TableCell className="text-right font-semibold tabular-nums">{optPct(s.punctualityRate)}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-500">{hrs(s.avgCycleHours)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="px-4 pb-3">
                <Note>
                  Ranked by punctuality, not by how many tasks each person raised — volume measures
                  how work was recorded rather than how it went. A dash means nobody could be judged:
                  that person finished nothing with a recorded deadline in this period.
                  {data.staff.some((s) => s.unassigned) &&
                    ` Tasks with nobody assigned are grouped into their own row so the columns add up to the ${data.totalTasks} in the headline figure.`}
                </Note>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
