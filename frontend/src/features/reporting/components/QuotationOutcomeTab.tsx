"use client";

import React, { useState } from "react";
import { Loader2, FileText, CheckCircle2, XCircle, Clock, Handshake, ArrowRightLeft } from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { useQuotationOutcomeReport } from "@/features/reporting/hooks/use_reporting";
import { StatTile, Meter, HBarList, EmptyReport, ReportDateRange, VIZ } from "./viz";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;

// Green for positive outcomes, red for negative, blue/grey for in-progress.
const statusColor = (status: string) => {
  if (["APPROVED", "ACCEPTED", "CONVERTED"].includes(status)) return VIZ.good;
  if (["REJECTED", "EXPIRED"].includes(status)) return VIZ.critical;
  if (["DRAFT", "CLOSED"].includes(status)) return VIZ.muted;
  return VIZ.open; // SENT, PENDING_*, INTERESTED
};

export function QuotationOutcomeTab() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const { data, isLoading, isError } = useQuotationOutcomeReport({
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

      {data && !isLoading && data.total === 0 && <EmptyReport message="No quotation data found for the selected period." />}

      {data && !isLoading && data.total > 0 && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="Quotations" value={String(data.total)} icon={<FileText className="size-3.5" />} />
            <StatTile label="Converted" value={String(data.converted)} sub={`Conv. ${pct(data.conversionRate)}`} icon={<ArrowRightLeft className="size-3.5" />} accent={VIZ.good} />
            <StatTile label="Accepted" value={String(data.accepted)} sub={`Accept. ${pct(data.acceptanceRate)}`} icon={<Handshake className="size-3.5" />} accent={VIZ.good} />
            <StatTile label="Approved" value={String(data.approved)} sub={`Approval ${pct(data.approvalRate)}`} icon={<CheckCircle2 className="size-3.5" />} accent={VIZ.good} />
            <StatTile label="Rejected" value={String(data.rejected)} icon={<XCircle className="size-3.5" />} accent={VIZ.critical} />
            <StatTile label="Expired" value={String(data.expired)} icon={<Clock className="size-3.5" />} accent={VIZ.critical} />
            <StatTile label="Sent" value={String(data.sent)} icon={<FileText className="size-3.5" />} accent={VIZ.open} />
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
                </div>
                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <h3 className="text-sm font-bold text-slate-700">Approval rate</h3>
                    <span className="text-sm font-extrabold" style={{ color: VIZ.open }}>{pct(data.approvalRate)}</span>
                  </div>
                  <Meter value={data.approvalRate} fill={VIZ.open} track={VIZ.trackBlue} />
                </div>
              </CardContent>
            </Card>

            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="space-y-2 p-4">
                <h3 className="text-sm font-bold text-slate-700">Breakdown by status</h3>
                <HBarList
                  items={data.byStatus.map((s) => ({ label: s.label, value: s.count, color: statusColor(s.status) }))}
                />
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
