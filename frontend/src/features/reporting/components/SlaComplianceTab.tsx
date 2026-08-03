"use client";

import React from "react";
import { Loader2, ShieldCheck, ShieldAlert, Clock, Hourglass } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { useReportRange, useSlaComplianceReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, HBarList, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ } from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;
const hrs = (n?: number) => {
  const h = n ?? 0;
  return h >= 48 ? `${(h / 24).toFixed(1)}d` : `${h.toFixed(1)}h`;
};

export function SlaComplianceTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useSlaComplianceReport(range.params, range.enabled);

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("sla-compliance", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "SLA Compliance (UC-23.3)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Generated at", new Date().toLocaleString("vi-VN")],
        ["Note", "A missed deadline counts as a breach even after the record was resolved."],
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["SLAs tracked", data.totalTracked],
            ["Resolved", data.resolvedCount],
            ["Resolved on time", data.resolvedOnTimeCount],
            ["Resolved late", data.resolvedLateCount],
            ["Deadlines missed (total)", data.breachedCount],
            ["Still open past deadline", data.openBreachedCount],
            ["Still running (warning)", data.warningCount],
            ["Still running (within SLA)", data.withinSlaCount],
            ["Still running (total)", data.inFlightCount],
            ["Breach rate (%)", data.breachRatePct],
            ["On-time compliance (%)", data.complianceRatePct],
            ["Resolution rate (%)", data.resolutionRatePct],
            ["Avg processing hours", data.avgProcessingHours],
          ],
        },
      ],
      tables: [
        {
          title: "By activity type",
          headers: ["Activity", "Total", "On time", "Missed", "Warning", "Within SLA", "Compliance (%)", "Breach rate (%)", "Avg hours"],
          rows: data.byActivityType.map((b) => [
            b.activityLabel, b.total, b.resolvedOnTime, b.breached, b.warning,
            b.withinSla, b.complianceRatePct, b.breachRatePct, b.avgProcessingHours,
          ]),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="SLA Compliance"
        period={periodLabel(range.dateFrom, range.dateTo)}
        onExport={handleExport}
        disabled={!data || data.totalTracked === 0}
      />

      <ReportDateRange
        dateFrom={range.dateFrom}
        dateTo={range.dateTo}
        setDateFrom={range.setDateFrom}
        setDateTo={range.setDateTo}
        invalid={range.invalid}
      />

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
            <StatTile label="SLAs tracked" value={String(data.totalTracked)} sub={`${data.inFlightCount} still running`} icon={<ShieldCheck className="size-3.5" />} />
            <StatTile
              label="Met on time"
              value={String(data.resolvedOnTimeCount)}
              sub={`Resolution ${pct(data.resolutionRatePct)}`}
              icon={<ShieldCheck className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Deadlines missed"
              value={String(data.breachedCount)}
              sub={`${data.resolvedLateCount} resolved late · ${data.openBreachedCount} still open`}
              icon={<ShieldAlert className="size-3.5" />}
              accent={VIZ.critical}
            />
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
                <Note>
                  Share of the {data.resolvedOnTimeCount + data.breachedCount} SLAs that reached an
                  outcome and were met before their deadline. The {data.inFlightCount} still running
                  are excluded — their deadline has not arrived, so they are neither met nor missed.
                  {(data.undeterminedCount ?? 0) > 0 && (
                    <> A further {data.undeterminedCount} are marked resolved but carry no
                      resolution time, so whether they met the deadline cannot be established;
                      they are excluded rather than assumed compliant.</>
                  )}
                </Note>
              </div>
              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-700">Breach rate</h3>
                  <span className="text-sm font-extrabold" style={{ color: VIZ.critical }}>{pct(data.breachRatePct)}</span>
                </div>
                <Meter value={data.breachRatePct} fill={VIZ.critical} track={VIZ.trackRed} />
                <Note>
                  Counts every missed deadline over all {data.totalTracked} tracked SLAs, including
                  the {data.resolvedLateCount} that were resolved after the fact — clearing a breach
                  records the fix, it does not undo the miss.
                </Note>
              </div>
            </CardContent>
          </Card>

          {data.inFlightCount > 0 && (
            <div className="flex items-start gap-2 rounded-xl border border-blue-100 bg-blue-50 px-4 py-3">
              <Hourglass className="mt-0.5 size-4 shrink-0" style={{ color: VIZ.open }} />
              <p className="text-xs text-slate-600">
                <b>{data.inFlightCount}</b> SLA{data.inFlightCount === 1 ? "" : "s"} still open
                ({data.withinSlaCount} within target, {data.warningCount} past the warning
                threshold). These have no outcome yet and can still end up in either column.
              </p>
            </div>
          )}

          {data.byActivityType.length > 0 && (
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-3 p-4">
                <h3 className="text-sm font-bold text-slate-700">Missed deadlines by activity type</h3>
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
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Activity</TableHead>
                        <TableHead className="text-right">Total</TableHead>
                        <TableHead className="text-right">On time</TableHead>
                        <TableHead className="text-right">Missed</TableHead>
                        <TableHead className="text-right">Compliance</TableHead>
                        <TableHead className="text-right">Avg time</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.byActivityType.map((b) => (
                        <TableRow key={b.activityType}>
                          <TableCell className="font-semibold text-slate-700">{b.activityLabel}</TableCell>
                          <TableCell className="text-right tabular-nums">{b.total}</TableCell>
                          <TableCell className="text-right tabular-nums text-emerald-600">{b.resolvedOnTime}</TableCell>
                          <TableCell className="text-right tabular-nums text-rose-600">{b.breached}</TableCell>
                          <TableCell className="text-right tabular-nums">{pct(b.complianceRatePct)}</TableCell>
                          <TableCell className="text-right tabular-nums text-slate-500">{hrs(b.avgProcessingHours)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
