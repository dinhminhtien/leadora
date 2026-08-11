"use client";

import React from "react";
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
import {
  useReportRange,
  useSalesPerformanceFilters,
  useSalesPerformanceReport,
  useSalesReps,
} from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, SegmentBar, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ, compact, vndCompact } from "./viz";
import { SalesPerformanceFilters } from "./SalesPerformanceFilters";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const vnd = (n?: number) => `${(n ?? 0).toLocaleString("vi-VN")} ₫`;
const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

const segmentLabel = (corporate: string) =>
  corporate === "true" ? "Corporate" : corporate === "false" ? "Individual" : "All";

export function SalesPerformanceTab() {
  const range = useReportRange();
  const filters = useSalesPerformanceFilters();
  const params = React.useMemo(
    () => ({ ...range.params, ...filters.params }),
    [range.params, filters.params],
  );
  const { data, isLoading, isError } = useSalesPerformanceReport(params, range.enabled);
  // Cached under the same query key the filter bar already uses, so this costs no extra request.
  const { data: reps = [] } = useSalesReps();
  const repLabel =
    reps.find((r) => r.userId === filters.assignedUserId)?.fullName ?? filters.assignedUserId;

  // Revenue and closed deals belong in this check too: a period spent collecting on bookings sold
  // earlier has no new leads or quotations, and the old test declared that an empty report while
  // real money sat in the response.
  const hasData =
    !!data &&
    data.leadsCreated + data.dealsTotal + data.quotationsCreated + data.bookingsConfirmed
      + data.dealsWon + data.dealsLost + (data.revenue ?? 0) > 0;

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("sales-performance", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Sales Performance Statistics (UC-23.1)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Time zone", data.timezone ?? "—"],
        ["Sales rep", filters.assignedUserId ? repLabel : "Whole team"],
        ["Lead source", filters.source || "Any"],
        ["Service", filters.interestedService || "Any"],
        ["Segment", segmentLabel(filters.corporate)],
        ["Generated at", new Date().toLocaleString("vi-VN")],
      ],
      sections: [
        {
          title: "Raised in period (by creation date)",
          rows: [
            ["New leads", data.leadsCreated],
            ["…of which have since converted", data.cohortConverted],
            ["Lead conversion rate (%)", data.leadConversionRate],
            ["Deals opened", data.dealsTotal],
            ["Still open", data.dealsOpen],
            ["Open pipeline value (VND)", data.pipelineValue],
            ["Quotations created", data.quotationsCreated],
            ["Quotations accepted", data.quotationsAccepted],
            ["Quotation acceptance rate (%)", data.quotationAcceptanceRate],
            ["Quotation to booking rate (%)", data.quotationToBookingRate],
          ],
        },
        {
          title: "Happened in period (by event date)",
          rows: [
            ["Leads qualified", data.qualifiedLeads],
            ["Leads converted", data.leadsConverted],
            ["Bookings confirmed", data.bookingsConfirmed],
            ["Deals won", data.dealsWon],
            ["Deals lost", data.dealsLost],
            ["Win rate (%)", data.winRate],
            ["Won value (VND)", data.wonValue],
          ],
        },
        {
          title: "Collected in period (by payment date)",
          rows: [["Revenue (VND)", data.revenue]],
        },
      ],
      tables: [
        {
          title: "Performance by rep",
          headers: ["Rep", "Leads", "Deals won", "Won value (VND)", "Bookings", "Revenue (VND)"],
          rows: data.reps.map((r) => [r.name, r.leads, r.dealsWon, r.wonValue, r.bookings, r.revenue]),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="Sales Performance Statistics"
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

      <SalesPerformanceFilters {...filters} />

      {isLoading && (
        <div className="flex items-center gap-2 p-6 text-sm text-slate-400">
          <Loader2 className="size-4 animate-spin" /> Aggregating data…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Failed to load the report. Please try again.</p>}

      {data && !isLoading && !hasData && <EmptyReport />}

      {data && !isLoading && hasData && (
        <>
          {/* KPI tiles */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile
              label="New leads (raised in period)"
              value={compact(data.leadsCreated)}
              sub={`${data.cohortConverted} have since converted · ${pct(data.leadConversionRate)}`}
              icon={<Users className="size-3.5" />}
            />
            {/* Counted on when the lead was qualified, not on what its status happens to be now:
                qualifying a lead and then converting it used to remove it from this tile. */}
            <StatTile
              label="Leads qualified (in period)"
              value={compact(data.qualifiedLeads)}
              sub={`${data.leadsConverted} converted in the same window`}
              icon={<Users className="size-3.5" />}
            />
            {/* Won/lost are counted by close date while "deals opened" is counted by creation
                date, so the label has to say so — `3/10` would read as a ratio of one population. */}
            <StatTile
              label="Deals won (closed in period)"
              value={String(data.dealsWon)}
              sub={`Win rate ${pct(data.winRate)} · lost ${data.dealsLost}`}
              icon={<BriefcaseBusiness className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Won value (closed in period)"
              value={vndCompact(data.wonValue)}
              sub={`Open pipeline ${vndCompact(data.pipelineValue)}`}
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
              label="Bookings confirmed (in period)"
              value={compact(data.bookingsConfirmed)}
              icon={<Hotel className="size-3.5" />}
            />
            <StatTile
              label="Deals opened (raised in period)"
              value={compact(data.dealsTotal)}
              sub={`${data.dealsOpen} still open`}
              icon={<BriefcaseBusiness className="size-3.5" />}
            />
            <StatTile
              label="Lead conversion rate"
              value={pct(data.leadConversionRate)}
              sub={`${data.cohortConverted} of ${data.leadsCreated} raised here`}
              icon={<TrendingUp className="size-3.5" />}
            />
          </div>

          <Note>
            Three time axes, deliberately. <strong>Raised in period</strong> counts what was created
            (new leads, deals opened, quotations). <strong>Happened in period</strong> counts events
            on their own date — a lead qualified, a booking confirmed, a deal won — whenever the
            record was raised. <strong>Collected</strong> follows the payment date. Day boundaries
            are cut in {data.timezone ?? "the business time zone"}.
          </Note>

          {/* Deal outcomes + conversion meters */}
          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">Deals closed in period</h3>
                <SegmentBar
                  segments={[
                    { label: "Won", value: data.dealsWon, color: VIZ.good },
                    { label: "Lost", value: data.dealsLost, color: VIZ.critical },
                  ]}
                />
                <Note>
                  Deals that reached a decision inside this period, whenever they were opened.
                  Separately, {data.dealsTotal} deal{data.dealsTotal === 1 ? " was" : "s were"}
                  {" "}opened in the period and {data.dealsOpen} of those are still running — those
                  are a different set and are not shown here.
                </Note>
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
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Quotation → booking</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.open }}>{pct(data.quotationToBookingRate)}</span>
                  </div>
                  <Meter value={data.quotationToBookingRate} fill={VIZ.open} track={VIZ.trackBlue} />
                  <Note>
                    Share of the {data.quotationsCreated} quotations raised in this period that went
                    on to become a booking. Superseded revisions are excluded, so this matches the
                    Quotation Outcome tab.
                  </Note>
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
              <div className="overflow-x-auto">
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
                      <TableRow key={r.name} className={r.unassigned ? "bg-slate-50/70" : undefined}>
                        <TableCell className={r.unassigned ? "italic text-slate-500" : "font-semibold text-slate-700"}>
                          {r.name}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{r.leads}</TableCell>
                        <TableCell className="text-right tabular-nums text-emerald-600">{r.dealsWon}</TableCell>
                        <TableCell className="text-right tabular-nums">{vnd(r.wonValue)}</TableCell>
                        <TableCell className="text-right tabular-nums">{r.bookings}</TableCell>
                        <TableCell className="text-right font-semibold tabular-nums text-blue-600">{vnd(r.revenue)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              {data.reps.some((r) => r.unassigned) && (
                <div className="px-4 pb-3">
                  <Note>
                    Records with nobody assigned are grouped into their own row rather than dropped,
                    so these columns add up to the KPI tiles above.
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
