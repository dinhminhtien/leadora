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

const pct = (n?: number | null) => (n == null ? "—" : `${n.toFixed(1)}%`);

/** Days, or an em dash when the figure is unknown rather than zero. */
const days = (n?: number | null) => (n == null ? "—" : `${n}d`);

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
        ["Stage timings", data.historyMeasured ? "Measured from recorded stage changes" : "No stage-change history recorded"],
        [
          "Win rate basis",
          "Cohort: of the deals opened in this period, those that have since settled. The Sales "
            + "Performance report counts deals by when they closed, so the two figures differ.",
        ],
        ["Generated at", new Date().toLocaleString("vi-VN")],
        ...(data.dataGaps ?? []).map((gap, i) => [`Data gap ${i + 1}`, gap] as [string, string]),
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["Total deals opened", data.totalDeals],
            ["Open deals", data.openDeals],
            ["Closed won", data.closedWon],
            ["Closed lost", data.closedLost],
            ["Cohort win rate (%)", data.cohortWinRate ?? ""],
            ["Cohort deals settled (denominator)", data.cohortDecided ?? 0],
            ["Closed here but opened earlier (not in cohort)", data.closedHereOpenedEarlier ?? 0],
            ["Open pipeline value (VND)", data.pipelineValue],
            ["Bottleneck stage", data.bottleneckStage ?? "—"],
          ],
        },
      ],
      tables: [
        {
          title: "Stage detail",
          headers: [
            "Stage", "Deals", "Value (VND)", "Avg age (days)",
            "Avg days to move on", "Completed exits", "Deals waiting now", "Avg days waiting", "Closed",
          ],
          rows: data.stages.map((s) => [
            s.label, s.count, s.value, s.avgAgeDays,
            s.avgDaysToMoveOn ?? "", s.completedLegs ?? 0,
            s.dealsWaitingNow ?? 0, s.avgDaysWaiting ?? "",
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
            <StatTile label="Deals opened" value={String(data.totalDeals)} sub="This period's cohort" icon={<GitBranch className="size-3.5" />} />
            {/* Named for the population it measures. A bare "Win rate" here read 100% on a window
                where the Sales Performance tab read 75%, because the losses were opened earlier. */}
            <StatTile
              label="Cohort win rate"
              value={pct(data.cohortWinRate)}
              sub={`${data.closedWon} won / ${data.cohortDecided ?? 0} settled`}
              icon={<Trophy className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile label="Closed lost" value={String(data.closedLost)} sub={`${data.openDeals} still open`} icon={<XCircle className="size-3.5" />} accent={VIZ.critical} />
            <StatTile label="Open pipeline" value={vndCompact(data.pipelineValue)} sub="Opened here, still running" icon={<TrendingUp className="size-3.5" />} accent={VIZ.open} />
          </div>

          {data.bottleneckStage && (
            <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
              <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-500" />
              <div>
                <p className="text-xs text-amber-800">
                  Likely bottleneck: <b>{data.bottleneckStage}</b> — deals take longest to get out
                  of this stage.
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
                      <TableHead className="text-right">Avg days to move on</TableHead>
                      <TableHead className="text-right">Exits</TableHead>
                      <TableHead className="text-right">Waiting now</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.stages.map((s) => (
                      <TableRow key={s.stage}>
                        <TableCell className="font-semibold text-slate-700">{s.label}</TableCell>
                        <TableCell className="text-right tabular-nums">{s.count}</TableCell>
                        <TableCell className="text-right tabular-nums">{vndCompact(s.value)}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-500">{s.count > 0 ? `${s.avgAgeDays}d` : "—"}</TableCell>
                        {/* Not driven by `count`: a stage can be slow yet empty right now, and its
                            crossing time is still worth showing. Null renders as a dash — no deal
                            has finished the stage, which is unknown rather than instant. */}
                        <TableCell className="text-right tabular-nums text-slate-500">
                          {days(s.avgDaysToMoveOn)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-slate-400">
                          {s.completedLegs ?? 0}
                        </TableCell>
                        {/* The queue, kept apart from the crossing time: these deals have not
                            finished their stay, so their days are a lower bound, not a duration. */}
                        <TableCell className="text-right tabular-nums text-slate-500">
                          {(s.dealsWaitingNow ?? 0) > 0
                            ? `${s.dealsWaitingNow} · ${days(s.avgDaysWaiting)}`
                            : "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="px-4 pb-3">
                <Note>
                  <b>Avg age</b> is how long a deal has existed — up to now for open stages, up to
                  the close for Closed Won / Closed Lost. <b>Avg days to move on</b> is how long the
                  stage takes, over the <b>Exits</b> that have actually finished; it is what ranks
                  the bottleneck, and a stage crossed only once or twice is pulled toward the overall
                  pace before ranking. <b>Waiting now</b> is the queue — deals still in the stage,
                  and how long they have been there. The two are kept apart on purpose: a stage that
                  clears in a day can still have a fortnight-old queue, and averaging them together
                  produced a figure that tracked the backlog rather than the work.
                  {data.historyMeasured === false && (
                    <> No stage-change history has been recorded for this cohort, so no stage timing
                      can be established.</>
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
