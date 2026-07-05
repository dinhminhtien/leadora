"use client";

import React, { useState } from "react";
import { Loader2, ShieldCheck, ShieldAlert, AlertTriangle, Clock } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { useSlaComplianceReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, HBarList, EmptyReport, ReportDateRange, VIZ } from "./viz";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;
const hrs = (n?: number) => {
  const h = n ?? 0;
  return h >= 48 ? `${(h / 24).toFixed(1)}d` : `${h.toFixed(1)}h`;
};

export function SlaComplianceTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const { data, isLoading, isError } = useSlaComplianceReport({
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
  });

  return (
    <div className="space-y-5">
      <ReportDateRange dateFrom={dateFrom} dateTo={dateTo} setDateFrom={setDateFrom} setDateTo={setDateTo} />

      {isLoading && (
        <div className="flex items-center gap-2 p-6 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> Aggregating data…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Failed to load the report. Please try again.</p>}

      {data && !isLoading && data.totalTracked === 0 && <EmptyReport message="No SLA data found for the selected period." />}

      {data && !isLoading && data.totalTracked > 0 && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="SLAs tracked" value={String(data.totalTracked)} icon={<ShieldCheck className="size-3.5" />} />
            <StatTile label="Resolved" value={String(data.resolvedCount)} sub={`Resolution ${pct(data.resolutionRatePct)}`} icon={<ShieldCheck className="size-3.5" />} accent={VIZ.good} />
            <StatTile label="Breached" value={String(data.breachedCount)} sub={`Breach ${pct(data.breachRatePct)}`} icon={<ShieldAlert className="size-3.5" />} accent={VIZ.critical} />
            <StatTile label="Avg processing" value={hrs(data.avgProcessingHours)} sub={`Warnings ${data.warningCount}`} icon={<Clock className="size-3.5" />} accent={VIZ.open} />
          </div>

          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="space-y-4 p-4">
              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-700">On-time compliance</h3>
                  <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.complianceRatePct)}</span>
                </div>
                <Meter value={data.complianceRatePct} fill={VIZ.good} track={VIZ.trackGreen} />
                <p className="mt-1 text-[10px] text-slate-400">SLAs met before their deadline (warnings + on-time resolutions).</p>
              </div>
              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-700">Breach rate</h3>
                  <span className="text-sm font-extrabold" style={{ color: VIZ.critical }}>{pct(data.breachRatePct)}</span>
                </div>
                <Meter value={data.breachRatePct} fill={VIZ.critical} track={VIZ.trackRed} />
              </div>
            </CardContent>
          </Card>

          {data.byActivityType.length > 0 && (
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-3 p-4">
                <h3 className="text-sm font-bold text-slate-700">Breaches by activity type</h3>
                <HBarList
                  items={data.byActivityType.map((b) => ({
                    label: b.activityLabel,
                    value: b.breached,
                    color: VIZ.critical,
                    sub: `of ${b.total}`,
                  }))}
                />
              </CardContent>
            </Card>
          )}

          {data.byActivityType.length > 0 && (
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="p-0">
                <div className="border-b border-slate-100 px-4 py-3">
                  <h3 className="text-sm font-bold text-slate-700">Activity detail</h3>
                </div>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Activity</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="text-right">Resolved</TableHead>
                      <TableHead className="text-right">Breached</TableHead>
                      <TableHead className="text-right">Avg time</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.byActivityType.map((b) => (
                      <TableRow key={b.activityType}>
                        <TableCell className="font-semibold text-slate-700">{b.activityLabel}</TableCell>
                        <TableCell className="text-right tabular-nums">{b.total}</TableCell>
                        <TableCell className="text-right tabular-nums text-emerald-600">{b.resolved}</TableCell>
                        <TableCell className="text-right tabular-nums text-rose-600">{b.breached}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-500">{hrs(b.avgProcessingHours)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
