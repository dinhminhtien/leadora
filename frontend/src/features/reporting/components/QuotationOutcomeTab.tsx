"use client";

import React from "react";
import { Loader2, FileText, CheckCircle2, XCircle, Clock, Handshake, ArrowRightLeft, Layers } from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { useReportRange, useQuotationOutcomeReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, HBarList, EmptyReport, ReportDateRange, ReportHeader, Note, VIZ } from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

// Green for positive outcomes, red for negative, blue/grey for in-progress or archived.
const statusColor = (status: string) => {
  if (["APPROVED", "ACCEPTED", "CONVERTED"].includes(status)) return VIZ.good;
  if (["REJECTED", "EXPIRED"].includes(status)) return VIZ.critical;
  if (["DRAFT", "CLOSED", "SUPERSEDED"].includes(status)) return VIZ.muted;
  return VIZ.open; // SENT, PENDING_*, INTERESTED
};

export function QuotationOutcomeTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useQuotationOutcomeReport(range.params, range.enabled);

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("quotation-outcome", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Quotation Outcome (UC-23.5)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Generated at", new Date().toLocaleString("vi-VN")],
        ["Note", "Rates exclude superseded revisions; approvals are counted from the approval timestamp."],
      ],
      sections: [
        {
          title: "Summary",
          rows: [
            ["Live quotations", data.total],
            ["Superseded revisions", data.superseded],
            ["Ever approved", data.approved],
            ["Rejected by approver", data.rejectedByApprover],
            ["Rejected (any source)", data.rejected],
            ["Awaiting dispatch", data.sent],
            ["Expired", data.expired],
            ["Accepted", data.accepted],
            ["Converted", data.converted],
            ["Approval rate (%)", data.approvalRate],
            ["Acceptance rate (%)", data.acceptanceRate],
            ["Conversion rate (%)", data.conversionRate],
          ],
        },
      ],
      tables: [
        {
          title: "Breakdown by current status",
          headers: ["Status", "Count"],
          rows: data.byStatus.map((s) => [s.label, s.count]),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="Quotation Outcome"
        period={periodLabel(range.dateFrom, range.dateTo)}
        onExport={handleExport}
        disabled={!data || data.total === 0}
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

      {data && !isLoading && data.total === 0 && <EmptyReport message="No quotation data found for the selected period." />}

      {data && !isLoading && data.total > 0 && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile
              label="Quotations"
              value={String(data.total)}
              sub={data.superseded > 0 ? `+${data.superseded} superseded revisions` : undefined}
              icon={<FileText className="size-3.5" />}
            />
            <StatTile label="Converted" value={String(data.converted)} sub={`Conv. ${pct(data.conversionRate)}`} icon={<ArrowRightLeft className="size-3.5" />} accent={VIZ.good} />
            <StatTile
              label="Accepted"
              value={String(data.accepted + data.converted)}
              sub={`Accept. ${pct(data.acceptanceRate)}`}
              icon={<Handshake className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Approved"
              value={String(data.approved)}
              sub={`Approval ${pct(data.approvalRate)}`}
              icon={<CheckCircle2 className="size-3.5" />}
              accent={VIZ.good}
            />
            <StatTile
              label="Rejected by approver"
              value={String(data.rejectedByApprover)}
              sub={data.rejected > data.rejectedByApprover
                ? `${data.rejected - data.rejectedByApprover} rejected by customer`
                : undefined}
              icon={<XCircle className="size-3.5" />}
              accent={VIZ.critical}
            />
            <StatTile label="Expired" value={String(data.expired)} icon={<Clock className="size-3.5" />} accent={VIZ.critical} />
            {/* SENT means already with the customer, awaiting their answer. "Awaiting dispatch" is
                the APPROVED status — labelling both the same made the two read as one number. */}
            <StatTile label="Sent, awaiting reply" value={String(data.sent)} icon={<FileText className="size-3.5" />} accent={VIZ.open} />
            {data.superseded > 0 && (
              <StatTile label="Superseded" value={String(data.superseded)} icon={<Layers className="size-3.5" />} accent={VIZ.muted} />
            )}
          </div>

          <div className="grid gap-3 lg:grid-cols-2">
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-4 p-4">
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Quotation → booking</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.conversionRate)}</span>
                  </div>
                  <Meter value={data.conversionRate} fill={VIZ.good} track={VIZ.trackGreen} />
                  <Note>
                    {data.converted} of {data.total} live quotations became a booking.
                    {data.superseded > 0 && (
                      <> The {data.superseded} superseded revision
                        {data.superseded === 1 ? " is" : "s are"} out of the denominator, so a
                        heavily negotiated deal counts once, not once per round.</>
                    )}
                  </Note>
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Approval rate</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.open }}>{pct(data.approvalRate)}</span>
                  </div>
                  <Meter value={data.approvalRate} fill={VIZ.open} track={VIZ.trackBlue} />
                  <Note>
                    {data.approved} approved against {data.rejectedByApprover} turned down by the
                    approver. Read from the approval timestamp, so a quotation that was approved and
                    then sent, accepted or converted still counts here.
                  </Note>
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Customer acceptance</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>{pct(data.acceptanceRate)}</span>
                  </div>
                  <Meter value={data.acceptanceRate} fill={VIZ.good} track={VIZ.trackGreen} />
                  <Note>
                    Accepted and converted quotations together — a booking is the strongest form of
                    acceptance, so it counts on both lines.
                  </Note>
                </div>
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">Breakdown by current status</h3>
                <HBarList
                  items={data.byStatus.map((s) => ({ label: s.label, value: s.count, color: statusColor(s.status) }))}
                />
                <Note>
                  A quotation carries one status at a time and it is overwritten as the quotation
                  advances, so this is a snapshot of where things stand — not a tally of everything
                  that ever reached each step.
                </Note>
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
