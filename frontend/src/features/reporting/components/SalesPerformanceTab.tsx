"use client";

import React, { useState } from "react";
import { Loader2, TrendingUp, Users, BriefcaseBusiness, ReceiptText, Hotel, Banknote } from "lucide-react";
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
import { useSalesPerformanceReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, SegmentBar, VIZ, compact, vndCompact } from "./viz";

const vnd = (n?: number) => `${(n ?? 0).toLocaleString("en-US")} ₫`;
const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

export function SalesPerformanceTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const { data, isLoading, isError } = useSalesPerformanceReport({
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
          <p className="text-[11px] text-slate-400 sm:pb-2">
            Leave empty = all time
          </p>
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
          {/* KPI tiles */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile
              label="New leads"
              value={compact(data.leadsCreated)}
              sub={`Converted ${data.leadsConverted} · ${pct(data.leadConversionRate)}`}
              icon={<Users className="size-3.5" />}
            />
            <StatTile
              label="Deals won"
              value={`${data.dealsWon}/${data.dealsTotal}`}
              sub={`Win rate ${pct(data.winRate)} · lost ${data.dealsLost}`}
              icon={<BriefcaseBusiness className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Won value"
              value={vndCompact(data.wonValue)}
              sub={`Pipeline (OPEN) ${vndCompact(data.pipelineValue)}`}
              icon={<TrendingUp className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Revenue (collected)"
              value={vndCompact(data.revenue)}
              icon={<Banknote className="size-3.5" />}
              accent={VIZ.open}
            />
            <StatTile
              label="Quotations"
              value={compact(data.quotationsCreated)}
              sub={`Accepted ${data.quotationsAccepted} · ${pct(data.quotationAcceptanceRate)}`}
              icon={<ReceiptText className="size-3.5" />}
            />
            <StatTile
              label="Bookings confirmed"
              value={compact(data.bookingsConfirmed)}
              icon={<Hotel className="size-3.5" />}
            />
            <StatTile
              label="Open deals"
              value={compact(data.dealsOpen)}
              icon={<BriefcaseBusiness className="size-3.5" />}
            />
            <StatTile
              label="Lead conversion rate"
              value={pct(data.leadConversionRate)}
              icon={<TrendingUp className="size-3.5" />}
            />
          </div>

          {/* Deal outcomes + conversion meters */}
          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">Deal outcomes</h3>
                <SegmentBar
                  segments={[
                    { label: "Won", value: data.dealsWon, color: VIZ.good },
                    { label: "Open", value: data.dealsOpen, color: VIZ.open },
                    { label: "Lost", value: data.dealsLost, color: VIZ.critical },
                  ]}
                />
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-4 p-4">
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Win rate</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.winRate)}</span>
                  </div>
                  <Meter value={data.winRate} fill={VIZ.good} track={VIZ.trackGreen} />
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Quotation acceptance</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.open }}>{pct(data.quotationAcceptanceRate)}</span>
                  </div>
                  <Meter value={data.quotationAcceptanceRate} fill={VIZ.open} track={VIZ.trackBlue} />
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Per-rep breakdown */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Performance by rep</h3>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Rep</TableHead>
                    <TableHead className="text-right">Leads</TableHead>
                    <TableHead className="text-right">Deals won</TableHead>
                    <TableHead className="text-right">Won value</TableHead>
                    <TableHead className="text-right">Bookings</TableHead>
                    <TableHead className="text-right">Revenue</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.reps.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="py-6 text-center text-xs text-slate-400">
                        No per-rep data for this period.
                      </TableCell>
                    </TableRow>
                  )}
                  {data.reps.map((r) => (
                    <TableRow key={r.name}>
                      <TableCell className="font-semibold text-slate-700">{r.name}</TableCell>
                      <TableCell className="text-right tabular-nums">{r.leads}</TableCell>
                      <TableCell className="text-right tabular-nums text-emerald-600">{r.dealsWon}</TableCell>
                      <TableCell className="text-right tabular-nums">{vnd(r.wonValue)}</TableCell>
                      <TableCell className="text-right tabular-nums">{r.bookings}</TableCell>
                      <TableCell className="text-right font-semibold tabular-nums text-blue-600">{vnd(r.revenue)}</TableCell>
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
