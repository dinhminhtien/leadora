"use client";

import React from "react";
import { Loader2, Users, Gauge, Star, TriangleAlert, Info, Sparkles } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Card, CardContent } from "@/components/ui/Card";
import {
  useReportRange,
  useRepScorecardAiReview,
  useRepScorecardReport,
} from "@/features/reporting/hooks/use_reporting";
import type { RepCoaching, RepScorecardRow } from "@/services/reporting_service";
import {
  StatTile,
  HBarList,
  EmptyReport,
  ReportDateRange,
  ReportHeader,
  ScoreCell,
  ScoreScaleLegend,
  Note,
  VIZ,
  scoreFill,
  vndCompact,
} from "./viz";
import { downloadReportCsv, periodLabel, reportFilename } from "./export";

const num = (n?: number, digits = 1) => (n === null || n === undefined ? "—" : n.toFixed(digits));
const pct = (n?: number) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}%`);
const hours = (n?: number) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}h`);
const days = (n?: number) => (n === null || n === undefined ? "—" : `${n.toFixed(1)}d`);

/** The five axes, in the order they are scored and displayed. */
const AXES = [
  { key: "outcome", label: "Outcome", hint: "Revenue and deals won against target" },
  { key: "efficiency", label: "Efficiency", hint: "Conversion rates, corrected for sample size" },
  { key: "velocity", label: "Velocity", hint: "First response, quotation turnaround, deal cycle" },
  { key: "discipline", label: "Discipline", hint: "SLA, tasks, discount, collection, forecast" },
  { key: "quality", label: "Quality", hint: "Customer feedback ratings" },
] as const;

export function RepScorecardTab() {
  const range = useReportRange();
  const { data, isLoading, isError } = useRepScorecardReport(range.params, range.enabled);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);

  const reps = data?.reps ?? [];
  const selected = reps.find((r) => r.userId === selectedId) ?? reps[0];

  const handleExport = () => {
    if (!data) return;
    downloadReportCsv({
      filename: reportFilename("rep-scorecard", data.dateFrom, data.dateTo),
      meta: [
        ["Report", "Rep Performance Scorecard (UC-23.6)"],
        ["Period", `${data.dateFrom} to ${data.dateTo}`],
        ["Time zone", data.timezone ?? "—"],
        ["Weights", `Outcome ${data.weights.outcome} · Efficiency ${data.weights.efficiency} · Velocity ${data.weights.velocity} · Discipline ${data.weights.discipline} · Quality ${data.weights.quality}`],
        ["Team median score", data.team.medianScore ?? "—"],
        ["Generated at", new Date().toLocaleString("vi-VN")],
      ],
      tables: [
        {
          title: "Scores",
          headers: ["Rank", "Rep", "Total", "Outcome", "Efficiency", "Velocity", "Discipline", "Quality", "Weight covered", "Low confidence", "Ranked"],
          rows: reps.map((r) => [
            r.rank ?? "", r.name, r.score.total ?? "", r.score.outcome ?? "", r.score.efficiency ?? "",
            r.score.velocity ?? "", r.score.discipline ?? "", r.score.quality ?? "",
            r.score.weightCovered, r.lowConfidence ? "yes" : "no", r.ranked ? "yes" : "no",
          ]),
        },
        {
          title: "Raw metrics",
          headers: ["Rep", "Revenue (VND)", "Deals won", "Deals closed", "Win rate (%)", "Leads", "Converted (cohort)",
            "Quotations", "Accepted", "First response (h)", "Deal cycle (d)", "SLA on time", "SLA decided",
            "Tasks total", "Tasks completed", "Tasks overdue", "Avg discount (%)", "CSAT", "CSAT samples", "Active days"],
          rows: reps.map((r) => {
            const m = r.metrics;
            return [r.name, m.revenue ?? 0, m.dealsWon, m.dealsClosed, m.winRate ?? "", m.leadsCreated,
              m.cohortConverted, m.quotationsCreated, m.quotationsAccepted, m.firstResponseHours ?? "",
              m.dealCycleDays ?? "", m.slaOnTime, m.slaDecided, m.tasksTotal, m.tasksCompleted,
              m.tasksOverdue, m.avgDiscountPercent ?? "", m.csat ?? "", m.csatSamples, m.activeDays];
          }),
        },
      ],
    });
  };

  return (
    <div className="space-y-5">
      <ReportHeader
        title="Rep Performance Scorecard"
        period={data ? `${data.dateFrom} to ${data.dateTo}` : periodLabel(range.dateFrom, range.dateTo)}
        onExport={handleExport}
        disabled={reps.length === 0}
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
          <Loader2 className="size-4 animate-spin" /> Scoring the team…
        </div>
      )}
      {isError && <p className="p-4 text-sm text-rose-500">Failed to load the scorecard. Please try again.</p>}

      {data && !isLoading && reps.length === 0 && <EmptyReport message="Nobody has recorded activity in this period." />}

      {data && !isLoading && reps.length > 0 && (
        <>
          {/* A scorecard about people opens with what it is and is not, not with a leaderboard. */}
          <div className="flex items-start gap-2 rounded-lg border border-sky-100 bg-sky-50/60 px-4 py-3">
            <Info className="mt-0.5 size-4 shrink-0 text-sky-600" />
            <p className="text-[11px] leading-relaxed text-sky-900">
              Scores are a summary of the measurements beside them, not a verdict on a person. Rates
              are corrected for sample size, axes with no data are left out rather than scored zero,
              and anything unmeasurable is listed under the rep. Weights and targets are configurable
              — this run used Outcome {data.weights.outcome} · Efficiency {data.weights.efficiency} ·
              Velocity {data.weights.velocity} · Discipline {data.weights.discipline} · Quality{" "}
              {data.weights.quality}, over {data.periodMonths.toFixed(2)} month(s) in{" "}
              {data.timezone ?? "the business time zone"}.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label="Reps scored" value={String(data.team.repCount)} icon={<Users className="size-3.5" />} />
            <StatTile
              label="Team median score"
              value={num(data.team.medianScore, 1)}
              sub="out of 100"
              icon={<Gauge className="size-3.5" />}
              accent={VIZ.open}
            />
            <StatTile label="Team win rate" value={pct(data.team.winRate)} sub="pooled, not averaged" />
            <StatTile
              label="Team CSAT"
              value={num(data.team.csat, 2)}
              sub={data.team.csat === undefined ? "no feedback yet" : "out of 5"}
              icon={<Star className="size-3.5" />}
            />
          </div>

          {/* ── Leaderboard: reps × axes, sequential shading, every cell labeled ── */}
          <Card className="border-slate-100 bg-white shadow-sm">
            <CardContent className="p-0">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
                <h3 className="text-sm font-bold text-slate-700">Scores by axis</h3>
                <ScoreScaleLegend />
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[820px] text-left">
                  <thead>
                    <tr className="border-b border-slate-100 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      <th className="px-4 py-2">#</th>
                      <th className="px-2 py-2">Rep</th>
                      <th className="px-2 py-2 text-center">Total</th>
                      {AXES.map((a) => (
                        <th key={a.key} className="px-1 py-2 text-center">{a.label}</th>
                      ))}
                      <th className="px-3 py-2 text-right">Revenue</th>
                      <th className="px-3 py-2 text-right">Won</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reps.map((rep) => {
                      const isSelected = selected?.userId === rep.userId;
                      return (
                        <tr
                          key={rep.userId}
                          onClick={() => setSelectedId(rep.userId)}
                          className={`cursor-pointer border-b border-slate-50 transition-colors ${
                            isSelected ? "bg-sky-50/70" : "hover:bg-slate-50/70"
                          }`}
                        >
                          <td className="px-4 py-1 text-[11px] tabular-nums text-slate-400">
                            {rep.rank ?? "—"}
                          </td>
                          <td className="px-2 py-1">
                            <div className="flex items-center gap-1.5">
                              <span className="text-xs font-semibold text-slate-700">{rep.name}</span>
                              {rep.lowConfidence && (
                                <span
                                  className="inline-flex items-center gap-0.5 rounded bg-amber-50 px-1.5 py-0.5 text-[9px] font-bold uppercase text-amber-700"
                                  title={`Only ${rep.metrics.sampleSize} settled outcome(s) behind this score`}
                                >
                                  <TriangleAlert className="size-2.5" /> thin
                                </span>
                              )}
                              {!rep.ranked && (
                                <span
                                  className="rounded bg-slate-100 px-1.5 py-0.5 text-[9px] font-bold uppercase text-slate-500"
                                  title={`Active only ${rep.metrics.activeDays} day(s) in this period — measured, but not ranked`}
                                >
                                  unranked
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="px-2 py-1 text-center">
                            <span
                              className="inline-block min-w-[3rem] rounded-md px-2 py-1 text-xs font-extrabold tabular-nums"
                              style={{
                                background: rep.score.total === undefined ? "transparent" : scoreFill(rep.score.total),
                                color: rep.score.total === undefined ? "#cbd5e1" : undefined,
                              }}
                            >
                              {rep.score.total === undefined ? "—" : rep.score.total.toFixed(1)}
                            </span>
                          </td>
                          {AXES.map((a) => (
                            <ScoreCell
                              key={a.key}
                              score={rep.score[a.key]}
                              label={`${rep.name} · ${a.label}`}
                              hint={a.hint}
                            />
                          ))}
                          <td className="px-3 py-1 text-right text-[11px] tabular-nums text-slate-600">
                            {vndCompact(rep.metrics.revenue)}
                          </td>
                          <td className="px-3 py-1 text-right text-[11px] tabular-nums text-emerald-600">
                            {rep.metrics.dealsWon}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <div className="px-4 py-2">
                <Note>
                  A score covering less than the full 100 weight means an axis had nothing to
                  measure; the remaining axes were reweighted rather than the gap counted as zero.
                  Click a row for the evidence behind it.
                </Note>
              </div>
            </CardContent>
          </Card>

          <AiReviewPanel
            dateFrom={data.dateFrom}
            dateTo={data.dateTo}
            selected={selected}
          />

          {selected && <RepDetail rep={selected} teamMedian={data.team.medianScore} />}
        </>
      )}
    </div>
  );
}

/**
 * UC-23.7 — coaching notes written from the scorecard.
 *
 * <p>The disclaimer and the thin-evidence list are rendered from their own response fields, outside
 * and above the model's markdown. They are produced server-side precisely so that they cannot go
 * missing on the run where the model decides to skip them, and putting them inside the same block
 * as the generated text would give that guarantee away again.
 */
function AiReviewPanel({
  dateFrom,
  dateTo,
  selected,
}: {
  dateFrom: string;
  dateTo: string;
  selected?: RepScorecardRow;
}) {
  const review = useRepScorecardAiReview();
  const result = review.data?.data;
  // English by default, because the rest of this screen is in English and a review that disagrees
  // with the labels around it reads as a different product. The toggle is there for a VI-first team.
  const [language, setLanguage] = React.useState<"en" | "vi">("en");

  const run = (userId?: string) => review.mutate({ dateFrom, dateTo, userId, language });

  return (
    <Card className="border-slate-100 bg-white shadow-sm">
      <CardContent className="space-y-3 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-slate-700">
              <Sparkles className="size-3.5 text-violet-500" /> AI coaching notes
            </h3>
            <p className="text-[11px] text-slate-400">
              Written from the figures above — a coaching aid, not a personnel assessment.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value as "en" | "vi")}
              className="rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-[11px] text-slate-600 focus:border-violet-400 focus:outline-none"
              aria-label="Review language"
            >
              <option value="en">English</option>
              <option value="vi">Tiếng Việt</option>
            </select>
            <button
              type="button"
              onClick={() => run(undefined)}
              disabled={review.isPending}
              className="inline-flex items-center gap-1.5 rounded-lg bg-violet-600 px-3 py-1.5 text-[11px] font-semibold text-white transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {review.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <Sparkles className="size-3.5" />}
              Review the team
            </button>
            {selected && (
              <button
                type="button"
                onClick={() => run(selected.userId)}
                disabled={review.isPending}
                className="inline-flex items-center gap-1.5 rounded-lg border border-violet-200 px-3 py-1.5 text-[11px] font-semibold text-violet-700 transition hover:bg-violet-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Review {selected.name}
              </button>
            )}
          </div>
        </div>

        {review.isPending && (
          <p className="text-[11px] text-slate-400">Reading the scorecard…</p>
        )}
        {review.isError && (
          <p className="text-[11px] text-rose-500">Could not reach the assistant. Please try again.</p>
        )}

        {result && (
          <div className="space-y-3">
            <div className="rounded-lg border border-violet-100 bg-violet-50/60 px-3 py-2">
              <p className="text-[11px] leading-relaxed text-violet-900">{result.disclaimer}</p>
            </div>

            {result.lowConfidenceReps && result.lowConfidenceReps.length > 0 && (
              <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-amber-100 bg-amber-50/60 px-3 py-2">
                <TriangleAlert className="size-3.5 shrink-0 text-amber-600" />
                <span className="text-[11px] text-amber-900">Thin evidence for:</span>
                {result.lowConfidenceReps.map((name) => (
                  <span key={name} className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">
                    {name}
                  </span>
                ))}
              </div>
            )}

            {result.generated && result.structured?.reps?.length ? (
              <div className="space-y-3">
                {result.structured.reps.map((rep) => (
                  <CoachingCard key={rep.name} rep={rep} />
                ))}
                {result.structured.teamRead && result.structured.teamRead.length > 0 && (
                  <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3">
                    <p className="mb-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      Team read
                    </p>
                    <ul className="space-y-1">
                      {result.structured.teamRead.map((line) => (
                        <li key={line} className="text-[11px] leading-relaxed text-slate-600">• {line}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : result.generated ? (
              // The model answered in a shape the server could not parse. Rendering the raw text
              // keeps the advice rather than losing it to a layout that could not be built.
              <div className="prose prose-sm max-w-none text-[12px] leading-relaxed text-slate-700 prose-headings:text-[13px] prose-headings:font-bold prose-headings:text-slate-700 prose-strong:text-slate-800 prose-li:my-0.5">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{result.review}</ReactMarkdown>
              </div>
            ) : (
              // Not a generated review — the fallback explanation. Rendered as plain text so a
              // provider error message can never arrive styled as if it were considered advice.
              <p className="rounded-lg bg-slate-50 px-3 py-2 text-[11px] leading-relaxed text-slate-600">
                {result.review}
              </p>
            )}

            <p className="text-[10px] text-slate-400">
              Scope: {result.scope} · {result.dateFrom} to {result.dateTo}
              {result.generated ? "" : " · not generated"}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/** The evidence behind one rep's score: axis bars, the raw numbers, and what was not measurable. */
function RepDetail({ rep, teamMedian }: { rep: RepScorecardRow; teamMedian?: number }) {
  const m = rep.metrics;
  const axisItems = AXES.filter((a) => rep.score[a.key] !== undefined).map((a) => ({
    label: a.label,
    value: Math.round(rep.score[a.key] as number),
    color: scoreFill(rep.score[a.key] as number),
    sub: "/100",
  }));

  return (
    <div className="grid gap-3 lg:grid-cols-2">
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="space-y-3 p-4">
          <div className="flex items-baseline justify-between">
            <h3 className="text-sm font-bold text-slate-700">{rep.name}</h3>
            <span className="text-[11px] text-slate-400">
              {rep.score.total === undefined ? "not scored" : `${rep.score.total.toFixed(1)} / 100`}
              {teamMedian !== undefined && ` · team median ${teamMedian.toFixed(1)}`}
            </span>
          </div>

          {axisItems.length > 0 ? (
            <HBarList items={axisItems} />
          ) : (
            <p className="text-[11px] text-slate-400">Nothing measurable in this period.</p>
          )}

          <div className="rounded-lg bg-slate-50 px-3 py-2">
            <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Confidence</p>
            <p className="mt-1 text-[11px] leading-relaxed text-slate-600">
              {m.sampleSize} settled outcome{m.sampleSize === 1 ? "" : "s"} · active {m.activeDays} day
              {m.activeDays === 1 ? "" : "s"} · {rep.score.weightCovered} of 100 weight measured.
              {rep.lowConfidence && " Too thin to read as a verdict."}
            </p>
          </div>

          {rep.dataGaps && rep.dataGaps.length > 0 && (
            <div className="rounded-lg border border-amber-100 bg-amber-50/60 px-3 py-2">
              <p className="text-[10px] font-bold uppercase tracking-wider text-amber-700">Not measured</p>
              <ul className="mt-1 space-y-0.5">
                {rep.dataGaps.map((gap) => (
                  <li key={gap} className="text-[11px] leading-relaxed text-amber-900">• {gap}</li>
                ))}
              </ul>
            </div>
          )}

          {rep.topLostReasons && rep.topLostReasons.length > 0 && (
            <div>
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Top lost reasons</p>
              <ul className="mt-1 space-y-0.5">
                {rep.topLostReasons.map((r) => (
                  <li key={r.reason} className="flex justify-between text-[11px] text-slate-600">
                    <span>{r.reason}</span>
                    <span className="font-semibold tabular-nums">{r.count}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="p-4">
          <h3 className="mb-2 text-sm font-bold text-slate-700">The numbers behind it</h3>
          <div className="grid grid-cols-2 gap-x-6 gap-y-1.5">
            <MetricRow label="Revenue collected" value={vndCompact(m.revenue)} />
            <MetricRow label="Won value" value={vndCompact(m.wonValue)} />
            <MetricRow label="Deals won / closed" value={`${m.dealsWon} / ${m.dealsClosed}`} sub={pct(m.winRate)} />
            <MetricRow label="Bookings confirmed" value={String(m.bookingsConfirmed)} />
            <MetricRow label="Leads raised" value={String(m.leadsCreated)} sub={`${m.cohortConverted} converted`} />
            <MetricRow label="Lead conversion" value={pct(m.leadConversionRate)} />
            <MetricRow label="Quotations" value={`${m.quotationsAccepted} / ${m.quotationsCreated}`} sub={pct(m.quotationAcceptanceRate)} />
            <MetricRow label="Revisions per quote" value={num(m.revisionsPerQuotation, 2)} />
            <MetricRow label="First response" value={hours(m.firstResponseHours)} sub={`${m.firstResponseSamples} of ${m.leadsCreated} leads`} />
            <MetricRow label="Quotation turnaround" value={hours(m.quotationTurnaroundHours)} />
            <MetricRow label="Deal cycle" value={days(m.dealCycleDays)} />
            <MetricRow label="Lead → conversion" value={days(m.leadToConversionDays)} />
            <MetricRow label="SLA on time" value={`${m.slaOnTime} / ${m.slaDecided}`} sub={pct(m.slaComplianceRate)} />
            <MetricRow label="Tasks completed" value={`${m.tasksCompleted} / ${m.tasksTotal}`} sub={pct(m.taskCompletionRate)} />
            <MetricRow label="Tasks overdue (open now)" value={String(m.tasksOverdue)} sub={pct(m.taskOverdueRate)} />
            <MetricRow label="Tasks finished on time" value={`${m.tasksOnTime} / ${m.tasksOnTime + m.tasksLate}`} sub={pct(m.taskPunctualityRate)} />
            <MetricRow label="Avg discount" value={pct(m.avgDiscountPercent)} />
            <MetricRow label="Paid on time" value={`${m.collectionOnTime} / ${m.collectionTotal}`} sub={pct(m.collectionOnTimeRate)} />
            <MetricRow label="Forecast hit" value={`${m.forecastHit} / ${m.forecastTotal}`} sub={pct(m.forecastAccuracyRate)} />
            <MetricRow label="CSAT" value={num(m.csat, 2)} sub={`${m.csatSamples} response(s)`} />
            <MetricRow label="Attitude / speed / accuracy" value={`${num(m.csatAttitude)} · ${num(m.csatSpeed)} · ${num(m.csatAccuracy)}`} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * One rep's coaching notes, laid out into the frames each part was written for.
 *
 * <p>Strengths and weaknesses sit side by side because they answer the same question from two
 * directions; the three actions get their own numbered cards with the metric each one targets as a
 * chip, so "do this" and "this is how you will know it worked" never drift apart.
 */
function CoachingCard({ rep }: { rep: RepCoaching }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-3 shadow-sm">
      <div className="mb-2 flex flex-wrap items-baseline gap-x-2">
        <h4 className="text-[13px] font-bold text-slate-700">{rep.name}</h4>
        {rep.headline && <p className="text-[11px] text-slate-500">{rep.headline}</p>}
      </div>

      <div className="grid gap-2 sm:grid-cols-2">
        {rep.strengths && rep.strengths.length > 0 && (
          <div className="rounded-lg border border-emerald-100 bg-emerald-50/50 p-2.5">
            <p className="mb-1 text-[10px] font-bold uppercase tracking-wider text-emerald-700">Strengths</p>
            <ul className="space-y-0.5">
              {rep.strengths.map((s) => (
                <li key={s} className="text-[11px] leading-relaxed text-emerald-900">• {s}</li>
              ))}
            </ul>
          </div>
        )}
        {rep.needsWork && rep.needsWork.length > 0 && (
          <div className="rounded-lg border border-amber-100 bg-amber-50/50 p-2.5">
            <p className="mb-1 text-[10px] font-bold uppercase tracking-wider text-amber-700">Needs work</p>
            <ul className="space-y-0.5">
              {rep.needsWork.map((s) => (
                <li key={s} className="text-[11px] leading-relaxed text-amber-900">• {s}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {rep.actions && rep.actions.length > 0 && (
        <div className="mt-2">
          <p className="mb-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">Do this month</p>
          <div className="grid gap-2 sm:grid-cols-3">
            {rep.actions.map((a, i) => (
              <div key={a.action} className="rounded-lg border border-slate-100 bg-slate-50/70 p-2.5">
                <div className="mb-1 flex items-center gap-1.5">
                  <span className="flex size-4 items-center justify-center rounded-full bg-violet-600 text-[9px] font-bold text-white">
                    {i + 1}
                  </span>
                  {a.metric && (
                    <span className="rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-bold uppercase text-violet-700">
                      {a.metric}
                    </span>
                  )}
                </div>
                <p className="text-[11px] leading-relaxed text-slate-700">{a.action}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {rep.evidenceNote && (
        <p className="mt-2 border-t border-slate-50 pt-1.5 text-[10px] leading-relaxed text-slate-400">
          {rep.evidenceNote}
        </p>
      )}
    </div>
  );
}

function MetricRow({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="flex items-baseline justify-between border-b border-slate-50 py-1">
      <span className="text-[11px] text-slate-500">{label}</span>
      <span className="text-right text-[11px] font-semibold tabular-nums text-slate-700">
        {value}
        {sub ? <span className="ml-1 font-normal text-slate-400">{sub}</span> : null}
      </span>
    </div>
  );
}
