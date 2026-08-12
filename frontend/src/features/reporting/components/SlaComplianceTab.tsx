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

/** Null is an em dash: a rate with no settled records is unknown, not 0%. */
const pct = (n?: number | null) => (n == null ? "—" : `${n.toFixed(1)}%`);
/** Null is an em dash, never "0.0h" — an unmeasured duration is not a fast one. */
const hrs = (n?: number | null) => {
  if (n == null) return "—";
  return n >= 48 ? `${(n / 24).toFixed(1)}d` : `${n.toFixed(1)}h`;
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
        [
          "Rate basis",
          "Compliance and breach rates share one denominator — the records whose deadline question "
            + "is settled: resolved on time, resolved late, or past the deadline and still open. "
            + "Only records still inside their deadline are excluded; an unresolved record whose "
            + "deadline has passed already counts as a breach.",
        ],
        ...(data.dataGaps ?? []).map((gap, i) => [`Data gap ${i + 1}`, gap] as [string, string]),
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["SLAs tracked", data.totalTracked],
            ["Resolved", data.resolvedCount],
            ["Resolved on time", data.resolvedOnTimeCount],
            ["Resolved late", data.resolvedLateCount],
            ["Undetermined (no resolution time)", data.undeterminedCount ?? 0],
            ["Deadlines missed (total)", data.breachedCount],
            ["Still open past deadline", data.openBreachedCount],
            ["Still running (warning)", data.warningCount],
            ["Still running (within SLA)", data.withinSlaCount],
            ["Still running (total)", data.inFlightCount],
            ["Settled — denominator of both rates", data.decidedCount ?? 0],
            ["Breach rate (%)", data.breachRatePct ?? ""],
            ["On-time compliance (%)", data.complianceRatePct ?? ""],
            ["Resolution rate (%)", data.resolutionRatePct ?? ""],
            ["Avg hours to resolve", data.avgProcessingHours ?? ""],
            ["Records behind that average", data.processingSamples ?? 0],
            ["Still-open records", data.openAgeSamples ?? 0],
            ["Avg hours those have been open", data.avgOpenAgeHours ?? ""],
          ],
        },
      ],
      tables: [
        {
          title: "By activity type",
          headers: [
            "Activity", "Total", "On time", "Resolved late", "Open past deadline", "Undetermined",
            "Warning", "Within SLA", "Settled", "Compliance (%)", "Breach rate (%)",
            "Avg hours to resolve", "Still open", "Avg hours open",
          ],
          rows: data.byActivityType.map((b) => [
            b.activityLabel, b.total, b.resolvedOnTime, b.resolvedLate ?? 0, b.openBreached ?? 0,
            b.undetermined ?? 0, b.warning, b.withinSla, b.decided ?? 0,
            b.complianceRatePct ?? "", b.breachRatePct ?? "",
            b.avgProcessingHours ?? "", b.openAgeSamples ?? 0, b.avgOpenAgeHours ?? "",
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
          {/* What the period could not establish, said before the figures it qualifies. */}
          {data.dataGaps && data.dataGaps.length > 0 && (
            <Card className="border-amber-100 bg-amber-50/60 shadow-none">
              <CardContent className="p-4">
                <h3 className="mb-1.5 text-xs font-bold uppercase tracking-wide text-amber-700">
                  What this period cannot tell you
                </h3>
                <ul className="list-disc space-y-1 pl-4 text-xs text-amber-900">
                  {data.dataGaps.map((gap) => (
                    <li key={gap}>{gap}</li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="SLAs tracked" value={String(data.totalTracked)} sub={`${data.inFlightCount} still running`} icon={<ShieldCheck className="size-3.5" />} />
            <StatTile
              label="Met on time"
              value={String(data.resolvedOnTimeCount)}
              sub={`of ${data.decidedCount ?? 0} settled`}
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
            {/* Two figures, not one: the resolve time is what the work takes, the queue age is what
                is waiting. Blending them made a stage with a big backlog look fast. */}
            <StatTile
              label="Avg to resolve"
              value={hrs(data.avgProcessingHours)}
              sub={(data.openAgeSamples ?? 0) > 0
                ? `${data.openAgeSamples} open · ${hrs(data.avgOpenAgeHours)} waiting`
                : `over ${data.processingSamples ?? 0} records`}
              icon={<Clock className="size-3.5" />}
              accent={VIZ.open}
            />
          </div>

          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="space-y-4 p-4">
              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-700">On-time compliance</h3>
                  <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.complianceRatePct)}</span>
                </div>
                {/* No meter when nothing has settled: an empty bar reads as "0% compliant", which
                    is indistinguishable from having missed every deadline. */}
                {data.complianceRatePct != null && (
                  <Meter value={data.complianceRatePct} fill={VIZ.good} track={VIZ.trackGreen} />
                )}
                <Note>
                  {data.complianceRatePct == null ? (
                    <>Nothing in this period has reached an outcome yet, so there is no compliance
                      rate to report — not a rate of zero.</>
                  ) : (
                    <>
                  Share of the {data.decidedCount ?? data.resolvedOnTimeCount + data.breachedCount} SLAs
                  that reached an outcome and were met before their deadline. The {data.inFlightCount} still
                  running are excluded — their deadline has not arrived, so they are neither met nor missed.
                  {(data.undeterminedCount ?? 0) > 0 && (
                    <> A further {data.undeterminedCount} are marked resolved but carry no
                      resolution time, so whether they met the deadline cannot be established;
                      they are excluded rather than assumed compliant.</>
                  )}
                    </>
                  )}
                </Note>
              </div>
              <div>
                <div className="mb-1.5 flex items-baseline justify-between">
                  <h3 className="text-sm font-bold text-slate-700">Breach rate</h3>
                  <span className="text-sm font-extrabold" style={{ color: VIZ.critical }}>{pct(data.breachRatePct)}</span>
                </div>
                {data.breachRatePct != null && (
                  <Meter value={data.breachRatePct} fill={VIZ.critical} track={VIZ.trackRed} />
                )}
                <Note>
                  Counts every missed deadline over the same {data.decidedCount ?? 0} settled SLAs
                  the compliance rate uses, so the two are exact complements. Includes the{" "}
                  {data.resolvedLateCount} resolved after the fact — clearing a breach records the
                  fix, it does not undo the miss. Measuring this against all {data.totalTracked}{" "}
                  tracked records instead let the open queue dilute it, which is why the two rates
                  used to add up to less than 100%.
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
                        <TableHead className="text-right">Late</TableHead>
                        <TableHead className="text-right">Overdue</TableHead>
                        <TableHead className="text-right">Settled</TableHead>
                        <TableHead className="text-right">Compliance</TableHead>
                        <TableHead className="text-right">Avg to resolve</TableHead>
                        <TableHead className="text-right">Still open</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.byActivityType.map((b) => (
                        <TableRow key={b.activityType}>
                          <TableCell className="font-semibold text-slate-700">{b.activityLabel}</TableCell>
                          <TableCell className="text-right tabular-nums">{b.total}</TableCell>
                          <TableCell className="text-right tabular-nums text-emerald-600">{b.resolvedOnTime}</TableCell>
                          {/* Late and Overdue split what used to be one "Missed" column, so the row
                              can be added back up to Total and checked against the headline. */}
                          <TableCell className="text-right tabular-nums text-rose-600">{b.resolvedLate ?? 0}</TableCell>
                          <TableCell className="text-right tabular-nums text-rose-600">{b.openBreached ?? 0}</TableCell>
                          <TableCell className="text-right tabular-nums text-slate-400">{b.decided ?? 0}</TableCell>
                          <TableCell className="text-right tabular-nums">{pct(b.complianceRatePct)}</TableCell>
                          <TableCell className="text-right tabular-nums text-slate-500">{hrs(b.avgProcessingHours)}</TableCell>
                          <TableCell className="text-right tabular-nums text-slate-500">
                            {(b.openAgeSamples ?? 0) > 0
                              ? `${b.openAgeSamples} · ${hrs(b.avgOpenAgeHours)}`
                              : "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                <div className="px-4 pb-3">
                  <Note>
                    <b>On time</b> + <b>Late</b> + <b>Overdue</b> + undetermined + still-running adds
                    up to <b>Total</b>. <b>Settled</b> is the denominator of the compliance rate —
                    on time plus late plus overdue. <b>Avg to resolve</b> covers only records that
                    finished; <b>Still open</b> is the queue that has not, with how long it has been
                    waiting. A dash means no record of that kind exists, which is not the same as
                    zero hours.
                  </Note>
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
