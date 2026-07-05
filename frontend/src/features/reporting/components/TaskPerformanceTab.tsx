"use client";

import React, { useState } from "react";
import { Loader2, ClipboardList, CheckCircle2, AlertTriangle, XCircle } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useTaskPerformanceReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, SegmentBar, VIZ } from "./viz";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

export function TaskPerformanceTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const { data, isLoading, isError } = useTaskPerformanceReport({
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
  });

  return (
    <div className="space-y-5">
      {/* Date range */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="flex flex-col gap-3 p-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Date From</label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </div>
          <div className="flex-1">
            <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Date To</label>
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </div>
          <p className="text-[11px] text-slate-400 sm:pb-2">Leave empty = all time</p>
        </CardContent>
      </Card>

      {isLoading && (
        <div className="flex items-center gap-2 p-6 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> Aggregating data…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Failed to load the report. Please try again.</p>}

      {data && !isLoading && (
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
                    <TableRow key={s.name}>
                      <TableCell className="font-semibold text-slate-700">{s.name}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.total}</TableCell>
                      <TableCell className="text-right tabular-nums text-emerald-600">{s.completed}</TableCell>
                      <TableCell className="text-right tabular-nums text-rose-600">{s.overdue}</TableCell>
                      <TableCell className="text-right font-semibold tabular-nums">{pct(s.completionRate)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
