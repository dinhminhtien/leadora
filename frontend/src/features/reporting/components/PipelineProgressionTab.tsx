"use client";

import React, { useState } from "react";
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
import { usePipelineProgressionReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, HBarList, EmptyReport, ReportDateRange, VIZ, vndCompact } from "./viz";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

export function PipelineProgressionTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const { data, isLoading, isError } = usePipelineProgressionReport({
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
  });

  const stageColor = (stage: string) =>
    stage === "CLOSED_WON" ? VIZ.good : stage === "CLOSED_LOST" ? VIZ.critical : VIZ.open;

  return (
    <div className="space-y-5">
      <ReportDateRange dateFrom={dateFrom} dateTo={dateTo} setDateFrom={setDateFrom} setDateTo={setDateTo} />

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
              <p className="text-xs text-amber-800">
                Likely bottleneck: <b>{data.bottleneckStage}</b> — deals sit here longest before moving on.
              </p>
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
                  sub: s.count > 0 ? `${s.avgAgeDays}d avg` : undefined,
                }))}
              />
            </CardContent>
          </Card>

          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Stage detail</h3>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Stage</TableHead>
                    <TableHead className="text-right">Deals</TableHead>
                    <TableHead className="text-right">Value</TableHead>
                    <TableHead className="text-right">Avg age</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.stages.map((s) => (
                    <TableRow key={s.stage}>
                      <TableCell className="font-semibold text-slate-700">{s.label}</TableCell>
                      <TableCell className="text-right tabular-nums">{s.count}</TableCell>
                      <TableCell className="text-right tabular-nums">{vndCompact(s.value)}</TableCell>
                      <TableCell className="text-right tabular-nums text-slate-500">{s.count > 0 ? `${s.avgAgeDays}d` : "—"}</TableCell>
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
