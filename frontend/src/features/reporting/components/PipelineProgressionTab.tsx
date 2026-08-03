"use client";

import React from "react";
import { Loader2, GitBranch, Trophy, XCircle, TrendingUp, AlertTriangle } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { useReportRange, usePipelineProgressionReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, HBarList, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ, vndCompact } from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

export function PipelineProgressionTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = usePipelineProgressionReport(range.params, range.enabled);

  const stageColor = (stage: string) =>
    stage === "CLOSED_WON" ? VIZ.good : stage === "CLOSED_LOST" ? VIZ.critical : VIZ.open;

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("pipeline-progression", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Sales Pipeline Progression (UC-23.4)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Cohort", "Deals opened in the period"],
        ["Stage timings", data.historyMeasured ? "Measured from recorded stage changes" : "Fallback: idle time (no history yet)"],
        ["Generated at", new Date().toLocaleString("vi-VN")],
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["Total deals", data.totalDeals],
            ["Open deals", data.openDeals],
            ["Closed won", data.closedWon],
            ["Closed lost", data.closedLost],
            ["Win rate (%)", data.winRate],
            ["Open pipeline value (VND)", data.pipelineValue],
            ["Bottleneck stage", data.bottleneckStage ?? "—"],
          ],
        },
      ],
      tables: [
        {
          title: "Stage detail",
          headers: ["Stage", "Deals", "Value (VND)", "Avg age (days)", "Avg time in stage (days)", "Stage visits", "Closed"],
          rows: data.stages.map((s) => [
            s.label, s.count, s.value, s.avgAgeDays, s.avgDaysInStage, s.dwellSamples ?? 0,
            s.closed ? "yes" : "no",
          ]),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="Sales Pipeline Progression"
        period={periodLabel(range.dateFrom, range.dateTo)}
        onExport={handleExport}
        disabled={!data || data.totalDeals === 0}
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

      {data && !isLoading && data.totalDeals === 0 && <EmptyReport message="No pipeline data found for the selected period." />}

      {data && !isLoading && data.totalDeals > 0 && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="Total deals" value={String(data.totalDeals)} icon={<GitBranch className="size-3.5" />} />
            <StatTile label="Closed won" value={String(data.closedWon)} sub={`Win rate ${pct(data.winRate)}`} icon={<Trophy className="size-3.5" />} accent={VIZ.good} />
            <StatTile label="Closed lost" value={String(data.closedLost)} icon={<XCircle className="size-3.5" />} accent={VIZ.critical} />
            <StatTile label="Open pipeline" value={vndCompact(data.pipelineValue)} sub={`${data.openDeals} open deals`} icon={<TrendingUp className="size-3.5" />} accent={VIZ.open} />
          </div>

          {data.bottleneckStage && (
            <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
              <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-500" />
              <div>
                <p className="text-xs text-amber-800">
                  Likely bottleneck: <b>{data.bottleneckStage}</b> — deals spend longest here before
                  moving on.
                </p>
                {data.bottleneckBasis && (
                  <p className="mt-1 text-[10px] leading-relaxed text-amber-700/80">
                    {data.bottleneckBasis}
                  </p>
                )}
              </div>
            </div>
          )}

          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="space-y-2 p-4">
              <h3 className="text-sm font-bold text-slate-700">Deals by stage</h3>
              <HBarList
                items={data.stages.map((s) => ({
                  label: s.label,
                  value: s.count,
                  color: stageColor(s.stage),
                  sub: s.count > 0 ? `${s.avgAgeDays}d old` : undefined,
                }))}
              />
            </CardContent>
          </Card>

          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Stage detail</h3>
              </div>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Stage</TableHead>
                      <TableHead className="text-right">Deals</TableHead>
                      <TableHead className="text-right">Value</TableHead>
                      <TableHead className="text-right">Avg age</TableHead>
                      <TableHead className="text-right">Avg time in stage</TableHead>
                      <TableHead className="text-right">Visits</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.stages.map((s) => (
                      <TableRow key={s.stage}>
                        <TableCell className="font-semibold text-slate-700">{s.label}</TableCell>
                        <TableCell className="text-right tabular-nums">{s.count}</TableCell>
                        <TableCell className="text-right tabular-nums">{vndCompact(s.value)}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-500">{s.count > 0 ? `${s.avgAgeDays}d` : "—"}</TableCell>
                        {/* Driven by dwellSamples, not by `count`: a stage can be slow yet empty
                            right now, and its timing is still worth showing. */}
                        <TableCell className="text-right tabular-nums text-slate-500">
                          {(s.dwellSamples ?? 0) > 0 ? `${s.avgDaysInStage}d` : "—"}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-slate-400">
                          {s.dwellSamples ?? 0}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="px-4 pb-3">
                <Note>
                  <b>Avg age</b> is how long a deal has existed — up to now for open stages, up to
                  the close for Closed Won / Closed Lost. <b>Avg time in stage</b> is measured from
                  recorded stage changes and is what ranks the bottleneck; <b>Visits</b> is how many
                  stage entries that average covers, counting every deal that passed through, not
                  only those sitting there now.
                  {data.historyMeasured === false && (
                    <> No stage-change history has been recorded yet, so these timings currently
                      fall back to idle time since the last edit.</>
                  )}
                </Note>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
