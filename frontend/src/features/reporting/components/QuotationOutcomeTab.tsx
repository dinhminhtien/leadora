"use client";

import React from "react";
import {
  Loader2,
  FileText,
  CheckCircle2,
  XCircle,
  Clock,
  Handshake,
  ArrowRightLeft,
  Layers,
  Send,
  RotateCcw,
  MessageSquare,
} from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { useReportRange, useQuotationOutcomeReport } from "@/features/reporting/hooks/use_reporting";
import {
  StatTile,
  Meter,
  SegmentBar,
  HBarList,
  EmptyReport,
  ReportDateRange,
  ReportHeader,
  Note,
  VIZ,
  vndCompact,
} from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const pct = (n?: number) => `${(n ?? 0).toFixed(1)}%`;
/** A rate the period could not establish renders as a dash. 0% would be a claim, and a false one. */
const optPct = (n?: number | null) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}%`);
const hrs = (n?: number | null) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}h`);

// Green for positive outcomes, red for negative, blue/grey for in-progress or archived.
const statusColor = (status: string) => {
  if (["APPROVED", "ACCEPTED", "CONVERTED"].includes(status)) return VIZ.good;
  if (["REJECTED", "EXPIRED"].includes(status)) return VIZ.critical;
  if (["DRAFT", "CLOSED", "SUPERSEDED"].includes(status)) return VIZ.muted;
  return VIZ.open; // SENT, PENDING_*, INTERESTED
};

/** Discount bands are a magnitude scale, so they run light → dark in one hue. */
const DISCOUNT_COLORS: Record<string, string> = {
  D0: VIZ.muted,
  D1_10: VIZ.priLow,
  D11_20: VIZ.priMed,
  D21_PLUS: VIZ.priHigh,
};

export function QuotationOutcomeTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useQuotationOutcomeReport(range.params, range.enabled);

  // The two sections answer different questions, so a period can be empty for one and busy for the
  // other. Hiding everything when no quotation was written would hide the month's approvals too.
  const hasCohort = !!data && data.total > 0;
  const hasActivity =
    !!data &&
    data.decisions + data.replies + data.sentInPeriod + data.convertedInPeriod +
      data.approvalsStamped + data.closedInPeriod + data.expiredInPeriod > 0;

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("quotation-outcome", range.dateFrom, range.dateTo),
      meta: [
        ["Report", "Quotation Outcome (UC-23.5)"],
        ["Period", periodLabel(range.dateFrom, range.dateTo)],
        ["Time zone", data.timezone ?? "—"],
        ["Generated at", new Date().toLocaleString("vi-VN")],
        [
          "Note",
          "Cohort figures follow the quotations written in the period. Activity figures count "
            + "approvals, replies, dispatches and conversions dated in the period whenever the "
            + "quotation was written. The two are not meant to reconcile.",
        ],
        ...(data.dataGaps ?? []).map((gap, i) => [`Data gap ${i + 1}`, gap] as [string, string]),
      ],
      sections: [
        {
          title: "Cohort — quotations written in this period",
          rows: [
            ["Live quotations", data.total],
            ["Excluded, replaced by a revision", data.revisedAway],
            ["Won", data.won],
            ["Lost (dispatched, no sale)", data.lost],
            ["Abandoned (never dispatched)", data.abandoned],
            ["Still open", data.stillOpen],
            ["Quotation win rate, of those decided (%)", data.winRate ?? ""],
            ["Won value", data.wonValue ?? 0],
            ["Lost value", data.lostValue ?? 0],
            ["Abandoned value", data.abandonedValue ?? 0],
            ["Converted", data.cohortConverted],
            ["Conversion rate (%)", data.conversionRate],
            ["Ever approved", data.cohortApproved],
            ["Never approved", data.cohortNeverApproved],
            ["Dispatched", data.cohortSent],
            ["Never dispatched", data.cohortNeverSent],
            ["Avg hours to dispatch", data.avgHoursToSend ?? ""],
            ["Expired with no reply", data.expiredNoReply],
            ["Expired after a reply", data.expiredAfterReply],
            ["Expired before it was ever sent", data.expiredNeverSent],
          ],
        },
        {
          title: "Activity — what happened in this period",
          rows: [
            ["Approval decisions logged", data.decisions],
            ["  Approved", data.decisionsApproved],
            ["  Rejected", data.decisionsRejected],
            ["  Sent back for revision", data.decisionsRevisionRequested],
            ["First-pass approval rate (%)", data.firstPassApprovalRate ?? ""],
            ["Approval timestamps in period", data.approvalsStamped],
            ["Avg hours to approve", data.avgHoursToApprove ?? ""],
            ["Customer replies", data.replies],
            ["  Accepted", data.repliesAccepted],
            ["  Rejected", data.repliesRejected],
            ["  Interested", data.repliesInterested],
            ["  Asked for a revision", data.repliesNeedRevision],
            ["Reply acceptance rate (%)", data.replyAcceptanceRate ?? ""],
            ["Avg hours to reply", data.avgHoursToReply ?? ""],
            ["Replies that could be timed", data.repliesTimed],
            ["Dispatched in period", data.sentInPeriod],
            ["Converted in period", data.convertedInPeriod],
            ["Converted value", data.convertedValue ?? 0],
            ["Closed in period", data.closedInPeriod],
            ["Expired in period", data.expiredInPeriod],
          ],
        },
      ],
      tables: [
        {
          title: "Cohort by current status",
          headers: ["Status", "Count"],
          rows: data.byStatus.map((s) => [s.label, s.count]),
        },
        {
          title: "Discount bands",
          headers: ["Band", "Quotations", "Value"],
          rows: data.discountBands.map((d) => [d.label, d.count, d.value ?? 0]),
        },
        {
          title: "Recorded loss reasons",
          headers: ["Reason", "Count"],
          rows: data.lostReasons.map((r) => [r.label, r.count]),
        },
        {
          title: "By preparer",
          headers: [
            "Preparer", "Written", "Won", "Lost", "Abandoned", "Quotation win rate (%)", "Won value",
            "Avg hours to send",
          ],
          rows: data.staff.map((s) => [
            s.name,
            s.prepared,
            s.won,
            s.lost,
            s.abandoned,
            s.winRate ?? "",
            s.wonValue ?? 0,
            s.avgHoursToSend ?? "",
          ]),
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
        disabled={!data || (!hasCohort && !hasActivity)}
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

      {data && !isLoading && !hasCohort && !hasActivity && (
        <EmptyReport message="No quotations were written and nothing was decided in the selected period." />
      )}

      {data && !isLoading && (hasCohort || hasActivity) && (
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

          {/* ── Cohort ─────────────────────────────────────────────────────── */}
          <section className="space-y-3">
            <div>
              <h2 className="text-sm font-extrabold text-slate-800">
                Quotations written in this period
              </h2>
              <p className="text-xs text-slate-500">
                Follows this batch of work to see what became of it.
              </p>
            </div>

            {!hasCohort ? (
              <EmptyReport message="No quotations were written in this period." />
            ) : (
              <>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <StatTile
                    label="Written"
                    value={String(data.total)}
                    sub={
                      data.revisedAway > 0
                        ? `${data.revisedAway} replaced by a revision, excluded`
                        : undefined
                    }
                    icon={<FileText className="size-3.5" />}
                  />
                  <StatTile
                    label="Won"
                    value={String(data.won)}
                    sub={`${optPct(data.winRate)} of ${data.won + data.lost} decided`}
                    icon={<Handshake className="size-3.5" />}
                    accent={VIZ.good}
                  />
                  <StatTile
                    label="Lost"
                    value={String(data.lost)}
                    sub={data.expiredNoReply > 0 ? `${data.expiredNoReply} expired unanswered` : "Sent, then closed with no sale"}
                    icon={<XCircle className="size-3.5" />}
                    accent={VIZ.critical}
                  />
                  <StatTile
                    label="Still open"
                    value={String(data.stillOpen)}
                    sub="Not yet a win or a loss"
                    icon={<Clock className="size-3.5" />}
                    accent={VIZ.open}
                  />
                  {/* Rejected at approval or expired as a draft. Its own tile because folding it
                      into "Lost" blames the rep for work no customer ever saw. */}
                  <StatTile
                    label="Never sent out"
                    value={String(data.abandoned)}
                    sub={data.abandoned > 0 ? "Outside the win rate" : undefined}
                    icon={<Layers className="size-3.5" />}
                    accent={VIZ.muted}
                  />
                  <StatTile
                    label="Converted"
                    value={String(data.cohortConverted)}
                    sub={`Conv. ${pct(data.conversionRate)}`}
                    icon={<ArrowRightLeft className="size-3.5" />}
                    accent={VIZ.good}
                  />
                  <StatTile
                    label="Won value"
                    value={vndCompact(data.wonValue)}
                    sub={`vs ${vndCompact(data.lostValue)} lost`}
                    icon={<Handshake className="size-3.5" />}
                    accent={VIZ.good}
                  />
                  <StatTile
                    label="Time to dispatch"
                    value={hrs(data.avgHoursToSend)}
                    sub={`${data.cohortSent} of ${data.total} dispatched`}
                    icon={<Send className="size-3.5" />}
                  />
                  <StatTile
                    label="Ever approved"
                    value={String(data.cohortApproved)}
                    sub={data.cohortNeverApproved > 0 ? `${data.cohortNeverApproved} never were` : undefined}
                    icon={<CheckCircle2 className="size-3.5" />}
                    accent={VIZ.good}
                  />
                </div>

                <div className="grid gap-3 lg:grid-cols-2">
                  <Card className="border-slate-100 bg-white shadow-sm">
                    <CardContent className="space-y-4 p-4">
                      <div>
                        <div className="mb-1.5 flex items-baseline justify-between">
                          <h3 className="text-sm font-bold text-slate-700">Quotation win rate</h3>
                          <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>
                            {optPct(data.winRate)}
                          </span>
                        </div>
                        <Meter value={data.winRate ?? 0} fill={VIZ.good} track={VIZ.trackGreen} />
                        <Note>
                          {data.won} won against {data.lost} lost. The {data.stillOpen} quotation
                          {data.stillOpen === 1 ? "" : "s"} nobody has answered yet
                          {data.stillOpen === 1 ? " is" : " are"} out of the denominator — counting
                          them as losses would push this rate down hardest in the most recent period,
                          which is exactly when it gets read. Quotations, not deals — the deal win
                          rate on the Sales Performance and Rep Scorecard tabs counts a different
                          object, and one deal can carry several quotations, so the two are not
                          meant to match.
                          {data.abandoned > 0 && (
                            <>
                              {" "}
                              {data.abandoned} more {data.abandoned === 1 ? "was" : "were"} rejected
                              at approval or expired as a draft; no customer saw
                              {data.abandoned === 1 ? " it" : " them"}, so
                              {data.abandoned === 1 ? " it is" : " they are"} out too.
                            </>
                          )}
                          {data.revisedAway > 0 && (
                            <>
                              {" "}
                              {data.revisedAway} row{data.revisedAway === 1 ? "" : "s"} replaced by a
                              revision {data.revisedAway === 1 ? "is" : "are"} excluded, so a
                              negotiation counts once rather than once per round.
                            </>
                          )}
                        </Note>
                      </div>
                      <div>
                        <div className="mb-1.5 flex items-baseline justify-between">
                          <h3 className="text-sm font-bold text-slate-700">Quotation → booking</h3>
                          <span className="text-sm font-extrabold" style={{ color: VIZ.good }}>
                            {pct(data.conversionRate)}
                          </span>
                        </div>
                        <Meter value={data.conversionRate} fill={VIZ.good} track={VIZ.trackGreen} />
                        <Note>
                          {data.cohortConverted} of {data.total} became a booking. Measured over
                          everything written, so it falls while a period is still young.
                        </Note>
                      </div>
                      {(data.expiredNoReply > 0 || data.expiredAfterReply > 0 ||
                        data.expiredNeverSent > 0) && (
                        <div>
                          <h3 className="mb-1.5 text-sm font-bold text-slate-700">How they expired</h3>
                          <SegmentBar
                            segments={[
                              {
                                label: "No reply ever",
                                value: data.expiredNoReply,
                                color: VIZ.critical,
                              },
                              {
                                label: "After a reply",
                                value: data.expiredAfterReply,
                                color: VIZ.muted,
                              },
                              {
                                label: "Never sent",
                                value: data.expiredNeverSent,
                                color: VIZ.priHigh,
                              },
                            ]}
                          />
                          <Note>
                            Three different failures. An expiry after a reply is a negotiation that
                            ran out of time; one with no reply is a follow-up that stopped; one that
                            was never sent never left the building. Only the first two involved a
                            customer at all, so they need different action and are counted apart.
                          </Note>
                        </div>
                      )}
                    </CardContent>
                  </Card>

                  <Card className="border-slate-100 bg-white shadow-sm">
                    <CardContent className="space-y-4 p-4">
                      <div className="space-y-2">
                        <h3 className="text-sm font-bold text-slate-700">By current status</h3>
                        <HBarList
                          items={data.byStatus.map((s) => ({
                            label: s.label,
                            value: s.count,
                            color: statusColor(s.status),
                          }))}
                        />
                        <Note>
                          A quotation carries one status at a time and it is overwritten as the
                          quotation advances, so this is where things stand — not a tally of
                          everything that ever reached each step.
                        </Note>
                      </div>
                      {data.discountBands.length > 0 && (
                        <div className="space-y-2">
                          <h3 className="text-sm font-bold text-slate-700">Discount given</h3>
                          <HBarList
                            items={data.discountBands.map((d) => ({
                              label: `${d.label} · ${vndCompact(d.value)}`,
                              value: d.count,
                              color: DISCOUNT_COLORS[d.key] ?? VIZ.open,
                            }))}
                          />
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </>
            )}
          </section>

          {/* ── Activity ───────────────────────────────────────────────────── */}
          <section className="space-y-3">
            <div>
              <h2 className="text-sm font-extrabold text-slate-800">What happened in this period</h2>
              <p className="text-xs text-slate-500">
                Decisions, replies, dispatches and conversions dated in the period — whenever the
                quotation was written. These deliberately do not add up to the section above.
              </p>
            </div>

            {!hasActivity ? (
              <EmptyReport message="Nothing was approved, sent, answered or converted in this period." />
            ) : (
              <>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <StatTile
                    label="Approval decisions"
                    value={String(data.decisions)}
                    sub={`First-pass ${optPct(data.firstPassApprovalRate)}`}
                    icon={<CheckCircle2 className="size-3.5" />}
                  />
                  <StatTile
                    label="Sent back for revision"
                    value={String(data.decisionsRevisionRequested)}
                    sub={data.decisionsRejected > 0 ? `${data.decisionsRejected} rejected outright` : undefined}
                    icon={<RotateCcw className="size-3.5" />}
                    accent={VIZ.critical}
                  />
                  <StatTile
                    label="Time to approve"
                    value={hrs(data.avgHoursToApprove)}
                    sub={`${data.approvalsStamped} approval${data.approvalsStamped === 1 ? "" : "s"}`}
                    icon={<Clock className="size-3.5" />}
                  />
                  <StatTile
                    label="Dispatched"
                    value={String(data.sentInPeriod)}
                    icon={<Send className="size-3.5" />}
                    accent={VIZ.open}
                  />
                  <StatTile
                    label="Customer replies"
                    value={String(data.replies)}
                    sub={`Accepted ${optPct(data.replyAcceptanceRate)}`}
                    icon={<MessageSquare className="size-3.5" />}
                  />
                  <StatTile
                    label="Time to reply"
                    value={hrs(data.avgHoursToReply)}
                    sub={
                      data.replies > 0 ? `${data.repliesTimed} of ${data.replies} could be timed` : undefined
                    }
                    icon={<Clock className="size-3.5" />}
                  />
                  <StatTile
                    label="Converted"
                    value={String(data.convertedInPeriod)}
                    sub={vndCompact(data.convertedValue)}
                    icon={<ArrowRightLeft className="size-3.5" />}
                    accent={VIZ.good}
                  />
                  <StatTile
                    label="Closed / expired"
                    value={`${data.closedInPeriod} / ${data.expiredInPeriod}`}
                    icon={<Layers className="size-3.5" />}
                    accent={VIZ.muted}
                  />
                </div>

                <div className="grid gap-3 lg:grid-cols-2">
                  {data.decisions > 0 && (
                    <Card className="border-slate-100 bg-white shadow-sm">
                      <CardContent className="space-y-2 p-4">
                        <h3 className="text-sm font-bold text-slate-700">Approval decisions</h3>
                        <SegmentBar
                          segments={[
                            { label: "Approved", value: data.decisionsApproved, color: VIZ.good },
                            {
                              label: "Revision asked",
                              value: data.decisionsRevisionRequested,
                              color: VIZ.open,
                            },
                            { label: "Rejected", value: data.decisionsRejected, color: VIZ.critical },
                          ]}
                        />
                        <Note>
                          A revision request is in the denominator of the first-pass rate: it is a
                          decision that did not approve. Leaving it out would score a period in which
                          everything was sent back as 100%.
                        </Note>
                      </CardContent>
                    </Card>
                  )}

                  {data.replies > 0 && (
                    <Card className="border-slate-100 bg-white shadow-sm">
                      <CardContent className="space-y-2 p-4">
                        <h3 className="text-sm font-bold text-slate-700">Customer replies</h3>
                        <SegmentBar
                          segments={[
                            { label: "Accepted", value: data.repliesAccepted, color: VIZ.good },
                            { label: "Interested", value: data.repliesInterested, color: VIZ.open },
                            {
                              label: "Wants changes",
                              value: data.repliesNeedRevision,
                              color: VIZ.muted,
                            },
                            { label: "Rejected", value: data.repliesRejected, color: VIZ.critical },
                          ]}
                        />
                        <Note>
                          Read from the recorded replies rather than from the quotation status, which
                          is overwritten by whatever happens next and so cannot say what the customer
                          actually answered.
                        </Note>
                        {data.lostReasons.length > 0 && (
                          <div className="pt-1">
                            <h4 className="mb-1 text-xs font-bold text-slate-600">Reasons given for a loss</h4>
                            <HBarList
                              items={data.lostReasons.map((r) => ({
                                label: r.label,
                                value: r.count,
                                color: VIZ.critical,
                              }))}
                            />
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  )}
                </div>
              </>
            )}
          </section>

          {/* ── By preparer ────────────────────────────────────────────────── */}
          {hasCohort && (
            <Card className="border-slate-100 bg-white shadow-sm">
              <CardContent className="p-0">
                <div className="border-b border-slate-100 px-4 py-3">
                  <h3 className="text-sm font-bold text-slate-700">By preparer</h3>
                </div>
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Preparer</TableHead>
                        <TableHead className="text-right">Written</TableHead>
                        <TableHead className="text-right">Won</TableHead>
                        <TableHead className="text-right">Never sent</TableHead>
                        <TableHead className="text-right">Win rate</TableHead>
                        <TableHead className="text-right">Won value</TableHead>
                        <TableHead className="text-right">Time to send</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.staff.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={7} className="py-6 text-center text-xs text-slate-400">
                            No per-preparer data for this period.
                          </TableCell>
                        </TableRow>
                      )}
                      {data.staff.map((s, i) => (
                        <TableRow key={`${s.name}-${i}`} className={s.unattributed ? "bg-slate-50/70" : undefined}>
                          <TableCell
                            className={
                              s.unattributed ? "italic text-slate-500" : "font-semibold text-slate-700"
                            }
                          >
                            {s.name}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{s.prepared}</TableCell>
                          <TableCell className="text-right tabular-nums text-emerald-600">{s.won}</TableCell>
                          {/* Abandoned work is a discipline signal, not a lost negotiation, so it
                              sits outside the win rate but still has to be visible against a name. */}
                          <TableCell
                            className={`text-right tabular-nums ${
                              s.abandoned > 0 ? "text-amber-600" : "text-slate-400"
                            }`}
                          >
                            {s.abandoned}
                          </TableCell>
                          {/* The counts are the numerator and denominator of the rate beside them,
                              so they sit under it rather than in two more columns. */}
                          <TableCell className="text-right tabular-nums">
                            <span className="font-semibold">{optPct(s.winRate)}</span>
                            {s.won + s.lost > 0 && (
                              <span className="ml-1 font-normal text-slate-400">
                                {s.won}/{s.won + s.lost}
                              </span>
                            )}
                          </TableCell>
                          <TableCell className="text-right tabular-nums text-slate-600">
                            {vndCompact(s.wonValue)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums text-slate-500">
                            {hrs(s.avgHoursToSend)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                <div className="px-4 pb-3">
                  <Note>
                    Attributed to whoever wrote the quotation, and ranked by win rate rather than by
                    how many were written — volume measures activity, not outcome. A dash means that
                    person has nothing decided yet in this period.
                    {data.staffTruncated
                      ? ` Only the top ${data.staff.filter((s) => !s.unattributed).length} preparers are listed, so the column does not add up to the ${data.total} in the headline.`
                      : data.staff.some((s) => s.unattributed)
                        ? ` Quotations with no recorded preparer are grouped into their own row so the column adds up to the ${data.total} in the headline.`
                        : ""}
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
