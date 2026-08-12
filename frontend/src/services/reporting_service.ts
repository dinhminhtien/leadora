import { apiClient, type ApiResponse } from "@/services/api_client";

export type ReportLogPayload = {
  generatedByName: string;
  generatedByRole?: string;
  filterDateFrom?: string;
  filterDateTo?: string;
  filterRoomType?: string;
  filterDiscountThreshold: number;
  resultCount: number;
  // BR-37: audit fields
  action?: string;
  result?: string;
  reason?: string;
};

export type ReportLog = {
  logId: string;
  generatedByName: string;
  generatedByRole?: string;
  filterDateFrom?: string;
  filterDateTo?: string;
  filterRoomType?: string;
  filterDiscountThreshold: number;
  resultCount: number;
  generatedAt: string;
};

export type StageSummary = {
  stage: string;
  count: number;
  value: number;
};

export type LeaderboardEntry = {
  name: string;
  actionCount: number;
};

export type MonthlyForecast = {
  month: string;
  value: number;
};

export type DashboardSummary = {
  activeLeadsCount: number;
  totalLeadsCount: number;
  activeLeadsGrowthPct?: number;
  activeDealsCount: number;
  activeDealsValue: number;
  weightedPipelineValue: number;
  totalDealsValue: number;
  pendingTasksCount: number;
  overdueTasksCount: number;
  slaComplianceRatePct?: number;
  avgResponseHours?: number;
  avgDealSize?: number;
  avgDealSizeGrowthPct?: number;
  winRatePct?: number;
  winRateBenchmarkLabel?: string;
  funnelStages: StageSummary[];
  leaderboard?: LeaderboardEntry[];
  monthlyForecasts?: MonthlyForecast[];
};

export type PublicStats = {
  pipelineValueLogged: number;
  weeklySalesGrowthPct: number;
  corporateSlaRatingPct: number;
  directChannelConversionPct: number;
};

export const publicStatsService = {
  async getPublicStats(): Promise<ApiResponse<PublicStats>> {
    const response = await apiClient.get<ApiResponse<PublicStats>>("/public/stats");
    return response.data;
  },
};

// ── UC-23.1 Sales Performance Statistics ─────────────────────────────────────
export type SalesRepRow = {
  name: string;
  leads: number;
  dealsWon: number;
  wonValue: number;
  bookings: number;
  revenue: number;
  /** The synthetic bucket for records with no assignee — present so the rows reconcile with the KPIs. */
  unassigned?: boolean;
};

export type SalesPerformanceReport = {
  dateFrom?: string;
  dateTo?: string;
  /** Business time zone the day boundaries were cut in — shown so the numbers can be checked. */
  timezone?: string;
  /** Leads raised in the period. */
  leadsCreated: number;
  /** Leads that *reached* QUALIFIED in the period, dated by the event, not by current status. */
  qualifiedLeads: number;
  /** Conversions that *happened* in the period, whenever the lead was raised. */
  leadsConverted: number;
  /** Of the leads raised in this period, how many have ever converted — the rate's numerator. */
  cohortConverted: number;
  /** % cohortConverted / leadsCreated: one population compared with itself. */
  leadConversionRate: number;
  dealsTotal: number;
  dealsOpen: number;
  dealsWon: number;
  dealsLost: number;
  winRate: number;
  wonValue: number;
  pipelineValue: number;
  /** Live quotations — superseded revisions (BR-22) are excluded, as in UC-23.5. */
  quotationsCreated: number;
  /** ACCEPTED + CONVERTED. */
  quotationsAccepted: number;
  quotationAcceptanceRate: number;
  bookingsConfirmed: number;
  /** CONVERTED quotations over quotations created — one population, so it cannot exceed 100%. */
  quotationToBookingRate: number;
  revenue: number;
  reps: SalesRepRow[];
};

// ── UC-23.6 Rep Performance Scorecard ────────────────────────────────────────

/** Raw measurements. A null field means "not measurable in this period", never zero. */
export type RepMetrics = {
  revenue?: number;
  wonValue?: number;
  dealsWon: number;
  dealsLost: number;
  bookingsConfirmed: number;
  leadsConverted: number;
  leadsCreated: number;
  cohortConverted: number;
  leadConversionRate?: number;
  dealsClosed: number;
  winRate?: number;
  quotationsCreated: number;
  quotationsAccepted: number;
  /** Written but never dispatched — inside the denominator, outside the accepted count. */
  quotationsAbandoned: number;
  quotationAcceptanceRate?: number;
  firstResponseHours?: number;
  firstResponseSamples: number;
  firstResponseCoveragePct?: number;
  leadToConversionDays?: number;
  quotationTurnaroundHours?: number;
  dealCycleDays?: number;
  slaDecided: number;
  slaOnTime: number;
  slaComplianceRate?: number;
  tasksTotal: number;
  tasksCompleted: number;
  tasksOverdue: number;
  taskCompletionRate?: number;
  /** Share of the still-open queue running past its deadline. */
  taskOverdueRate?: number;
  tasksOnTime: number;
  tasksLate: number;
  /** % finished on time. Not derivable from taskOverdueRate — that one only sees open work. */
  taskPunctualityRate?: number;
  avgDiscountPercent?: number;
  quotationRoots: number;
  quotationRevisions: number;
  revisionsPerQuotation?: number;
  collectionTotal: number;
  collectionOnTime: number;
  collectionOnTimeRate?: number;
  forecastTotal: number;
  forecastHit: number;
  forecastAccuracyRate?: number;
  csat?: number;
  csatSamples: number;
  csatAttitude?: number;
  csatSpeed?: number;
  csatAccuracy?: number;
  activeDays: number;
  sampleSize: number;
};

/** Axis scores out of 100; null where the axis had nothing to measure and was left out. */
export type RepScore = {
  outcome?: number;
  efficiency?: number;
  velocity?: number;
  discipline?: number;
  quality?: number;
  total?: number;
  /** Sum of the weights that actually contributed, out of 100. */
  weightCovered: number;
};

export type RepScorecardRow = {
  userId: string;
  name: string;
  metrics: RepMetrics;
  score: RepScore;
  /** Thin evidence — read the score as a hint, not a verdict. */
  lowConfidence: boolean;
  /** False when the rep was active too few days in the period to compare with the others. */
  ranked: boolean;
  rank?: number;
  dataGaps?: string[];
  topLostReasons?: { reason: string; count: number }[];
};

export type RepScorecardReport = {
  dateFrom: string;
  dateTo: string;
  timezone?: string;
  periodMonths: number;
  weights: { outcome: number; efficiency: number; velocity: number; discipline: number; quality: number };
  team: {
    repCount: number;
    winRate?: number;
    leadConversionRate?: number;
    quotationAcceptanceRate?: number;
    slaComplianceRate?: number;
    taskCompletionRate?: number;
    taskPunctualityRate?: number;
    collectionOnTimeRate?: number;
    forecastAccuracyRate?: number;
    csat?: number;
    medianScore?: number;
  };
  reps: RepScorecardRow[];
};

// ── UC-23.7 AI review of the scorecard ───────────────────────────────────────

export type CoachingAction = { action: string; metric?: string };

export type RepCoaching = {
  name: string;
  headline?: string;
  strengths?: string[];
  needsWork?: string[];
  actions?: CoachingAction[];
  evidenceNote?: string;
};

/** The review as data. Null when the model answered in a shape the server could not parse. */
export type AiCoachingReview = {
  reps?: RepCoaching[];
  teamRead?: string[];
};

export type RepScorecardAiReview = {
  dateFrom: string;
  dateTo: string;
  /** "Whole team" or the reviewed rep's name. */
  scope: string;
  language: string;
  /** Server-generated, never written by the model — render it outside the review text. */
  disclaimer: string;
  /** Named server-side so the warning survives the model ignoring its prompt. */
  lowConfidenceReps?: string[];
  /** Laid out into cards when present; `review` is the fallback when it is not. */
  structured?: AiCoachingReview;
  /** The model's raw answer, or a plain explanation when the provider was unavailable. */
  review: string;
  /** False when `review` is the fallback explanation rather than a generated review. */
  generated: boolean;
};

export type RepScorecardReviewParams = ReportRangeParams & {
  userId?: string;
  language?: "vi" | "en";
};

// ── UC-23.2 Follow-up Task Performance ───────────────────────────────────────
export type TaskStaffRow = {
  name: string;
  total: number;
  completed: number;
  completionRate: number;
  /** Still open and past the deadline. */
  openOverdue: number;
  /** Finished, but after the deadline — the column the old report had no way to show. */
  resolvedLate: number;
  resolvedOnTime: number;
  /** Null when this person finished nothing datable in the period. */
  punctualityRate?: number;
  avgCycleHours?: number;
  unassigned?: boolean;
};

/** A labelled count — activity kinds, overdue aging bands, priorities. */
export type TaskCountRow = { key: string; label: string; count: number };

/**
 * UC-23.2 — three questions on three axes, deliberately not one figure.
 *
 * `raised` counts work that came in, `resolved` counts work finished and whether it was on time,
 * and the open-queue figures are measured against the clock and belong to no period at all.
 */
export type TaskPerformanceReport = {
  dateFrom?: string;
  dateTo?: string;
  timezone?: string;

  // Raised in the period (created_at)
  totalTasks: number;
  completed: number;
  open: number;
  cancelled: number;
  completionRate: number;
  priorityLow: number;
  priorityMedium: number;
  priorityHigh: number;
  activityMix?: TaskCountRow[];
  /** Tasks attached to no lead, customer or deal — effort that never reaches the pipeline. */
  orphanTasks: number;
  orphanRate: number;

  // Resolved in the period (completed_at)
  resolvedTotal: number;
  resolvedOnTime: number;
  /** Finished late. Invisible in the old report, which stopped counting a task once it closed. */
  resolvedLate: number;
  /** Finished with no deadline recorded — held out of punctuality rather than credited as on time. */
  resolvedNoDeadline: number;
  /** Null, never zero, when nothing finished in the period carried a deadline. */
  punctualityRate?: number;
  avgCycleHours: number;
  /** Completed tasks carrying no completion time, from before the column existed. */
  completedUndated: number;
  /** % of completed tasks that could be judged for punctuality at all. */
  punctualityCoverage: number;

  // The open queue right now (BR-17, derived from the clock)
  openOverdue: number;
  /** % openOverdue / open. */
  overdueRate: number;
  avgDaysOverdue: number;
  overdueAging?: TaskCountRow[];
  overdueByPriority?: TaskCountRow[];

  // SLA, classified by the same rules as UC-23.3
  slaDecided: number;
  slaOnTime: number;
  slaComplianceRate: number;

  /** True when the caller is scoped to their own tasks rather than the whole team. */
  ownScope?: boolean;
  staff: TaskStaffRow[];
};

// ── UC-23.3 SLA Compliance ───────────────────────────────────────────────────
export type SlaActivityBreakdown = {
  activityType: string;
  activityLabel: string;
  /** Partitioned exactly by resolvedOnTime + resolvedLate + undetermined + openBreached + warning + withinSla. */
  total: number;
  resolved: number;
  resolvedOnTime: number;
  resolvedLate?: number;
  openBreached?: number;
  undetermined?: number;
  /** resolvedLate + openBreached. */
  breached: number;
  warning: number;
  withinSla: number;
  /** Denominator shared by both rates below: resolvedOnTime + breached. */
  decided?: number;
  /** Null when this activity type has nothing settled — unknown, not a clean sheet. */
  breachRatePct?: number | null;
  complianceRatePct?: number | null;
  /** Null when nothing of this type has been resolved with usable timestamps. */
  avgProcessingHours?: number | null;
  processingSamples?: number;
  /** Null when nothing of this type is still running. */
  avgOpenAgeHours?: number | null;
  openAgeSamples?: number;
};

export type SlaComplianceReport = {
  dateFrom?: string;
  dateTo?: string;
  totalTracked: number;
  /** Finished, on time or late. */
  resolvedCount: number;
  resolvedOnTimeCount: number;
  /** Finished after the deadline — still counted as a breach. */
  resolvedLateCount: number;
  /** Marked resolved with no timestamp: outcome unknowable, excluded from the compliance rate. */
  undeterminedCount?: number;
  /** Every missed deadline: late resolutions plus records still running over. */
  breachedCount: number;
  openBreachedCount: number;
  warningCount: number;
  withinSlaCount: number;
  /** Still running, so they have no outcome yet and are excluded from the compliance rate. */
  inFlightCount: number;
  /** Records whose deadline question is settled — the denominator of BOTH rates below. */
  decidedCount?: number;
  /** breached / decided. The exact complement of complianceRatePct. Null when nothing settled. */
  breachRatePct?: number | null;
  complianceRatePct?: number | null;
  resolutionRatePct?: number | null;
  /** Average hours to resolve, over finished records only. Null when none finished. */
  avgProcessingHours?: number | null;
  processingSamples?: number;
  /** How long the still-running records have been open. Null when none are. */
  avgOpenAgeHours?: number | null;
  openAgeSamples?: number;
  /** What this period could not establish, in words. Rendered verbatim. */
  dataGaps?: string[];
  byActivityType: SlaActivityBreakdown[];
};

// ── UC-23.4 Sales Pipeline Progression ───────────────────────────────────────
export type PipelineStageRow = {
  stage: string;
  label: string;
  count: number;
  value: number;
  /** Deal lifetime: created → now for open stages, created → closed for terminal ones. */
  avgAgeDays: number;
  /**
   * Average days to get *out* of this stage, over visits that have ended. Null when no deal has
   * completed the stage — unknown, not instant. Terminal stages never carry one.
   */
  avgDaysToMoveOn?: number | null;
  /** How many ended visits avgDaysToMoveOn averages over. */
  completedLegs?: number;
  /** Deals sitting in this stage right now — a queue, not a crossing time. */
  dealsWaitingNow?: number;
  /** How long that queue has been waiting on average. Null when there is none. */
  avgDaysWaiting?: number | null;
  closed: boolean;
};

export type PipelineProgressionReport = {
  dateFrom?: string;
  dateTo?: string;
  totalDeals: number;
  openDeals: number;
  closedWon: number;
  closedLost: number;
  /**
   * Won / settled *within the opening cohort*. Deliberately not `winRate`: SalesPerformanceReport
   * publishes a figure of that name over deals closed in the period, whenever they were opened, and
   * the two routinely differ. Null when nothing in the cohort has settled.
   */
  cohortWinRate?: number | null;
  /** The denominator behind cohortWinRate. */
  cohortDecided?: number;
  /** Deals closed in the period but opened before it — outside this cohort by construction. */
  closedHereOpenedEarlier?: number;
  pipelineValue: number;
  bottleneckStage?: string;
  /** How the bottleneck was chosen — rendered so the claim is qualified where it is made. */
  bottleneckBasis?: string;
  /** True when timings come from recorded stage changes rather than the idle-time fallback. */
  historyMeasured?: boolean;
  /** What this period could not establish, in words. Rendered verbatim. */
  dataGaps?: string[];
  stages: PipelineStageRow[];
};

// ── UC-23.5 Quotation Outcome ────────────────────────────────────────────────
export type QuotationStatusRow = { status: string; label: string; count: number };

/** A named bucket with a count and, where the metric carries money, a total. */
export type QuotationCountRow = { key: string; label: string; count: number; value?: number };

export type QuotationStaffRow = {
  name: string;
  prepared: number;
  won: number;
  lost: number;
  abandoned: number;
  /** null when this preparer has nothing decided yet — not the same as a 0% win rate. */
  winRate?: number | null;
  wonValue?: number;
  sent: number;
  avgHoursToSend?: number | null;
  unattributed: boolean;
};

/**
 * Two sections that are not meant to reconcile.
 *
 * <p>`total…` and the other cohort fields follow the quotations *written* in the period.
 * The `decisions…`, `replies…`, `sentInPeriod`, `convertedInPeriod` fields count what *happened*
 * in the period regardless of when the quotation was written — work that a created_at-only
 * report could not see at all.
 *
 * Nullable rates are null when the period holds nothing that could establish them; render them
 * as "—", never as 0.
 */
export type QuotationOutcomeReport = {
  dateFrom?: string;
  dateTo?: string;
  timezone?: string;

  // Cohort — quotations created in the period.
  /** Live quotations; rows a revision replaced are excluded structurally, not by status (BR-22). */
  total: number;
  /** Rows excluded from `total` because a revision replaced them. */
  revisedAway: number;
  won: number;
  /** Dispatched to the customer and closed without a sale. */
  lost: number;
  /** Terminal but never dispatched — rejected at approval, or expired as a draft. */
  abandoned: number;
  stillOpen: number;
  wonValue?: number;
  lostValue?: number;
  abandonedValue?: number;
  openValue?: number;
  /** won / (won + lost). `stillOpen` and `abandoned` are both outside the denominator. */
  winRate?: number | null;
  cohortConverted: number;
  conversionRate: number;
  cohortApproved: number;
  cohortNeverApproved: number;
  cohortSent: number;
  cohortNeverSent: number;
  avgHoursToSend?: number | null;
  /** Expired without the customer ever replying — a follow-up failure, not a lost negotiation. */
  expiredNoReply: number;
  expiredAfterReply: number;
  /** Expired while still a draft or awaiting approval — it never went out. */
  expiredNeverSent: number;
  discountBands: QuotationCountRow[];
  byStatus: QuotationStatusRow[];

  // Activity — decisions and replies dated in the period.
  decisions: number;
  decisionsApproved: number;
  decisionsRejected: number;
  decisionsRevisionRequested: number;
  /** approved / decisions, revision requests included in the denominator. */
  firstPassApprovalRate?: number | null;
  approvalsStamped: number;
  avgHoursToApprove?: number | null;
  replies: number;
  repliesAccepted: number;
  repliesRejected: number;
  repliesInterested: number;
  repliesNeedRevision: number;
  replyAcceptanceRate?: number | null;
  avgHoursToReply?: number | null;
  /** How many replies could be timed — the rest have no dispatch timestamp. */
  repliesTimed: number;
  sentInPeriod: number;
  convertedInPeriod: number;
  convertedValue?: number;
  closedInPeriod: number;
  expiredInPeriod: number;
  lostReasons: QuotationCountRow[];

  /** What this period could not establish, in words. Render it; that is the point of it. */
  dataGaps?: string[];
  staff: QuotationStaffRow[];
  /** True when the preparer table was capped, so it no longer sums to `total`. */
  staffTruncated?: boolean;
};

export type ReportRangeParams = { dateFrom?: string; dateTo?: string };

/**
 * UC-23.1 also filters by rep and by lead segment. The segment fields describe the *lead* a record
 * came from: for a booking or a payment that resolves through the customer, since neither carries a
 * source of its own.
 */
export type SalesPerformanceParams = ReportRangeParams & {
  assignedUserId?: string;
  source?: string;
  interestedService?: string;
  corporate?: boolean;
};

const ENDPOINT = "/reporting";

export const reportingService = {
  async saveReportLog(payload: ReportLogPayload): Promise<ApiResponse<ReportLog>> {
    const response = await apiClient.post<ApiResponse<ReportLog>>(
      `${ENDPOINT}/logs`,
      payload,
    );
    return response.data;
  },

  async getDashboardSummary(): Promise<ApiResponse<DashboardSummary>> {
    const response = await apiClient.get<ApiResponse<DashboardSummary>>(
      `${ENDPOINT}/dashboard-summary`,
    );
    return response.data;
  },

  async getSalesPerformance(
    params?: SalesPerformanceParams,
  ): Promise<ApiResponse<SalesPerformanceReport>> {
    const response = await apiClient.get<ApiResponse<SalesPerformanceReport>>(
      `${ENDPOINT}/sales-performance`,
      { params },
    );
    return response.data;
  },

  async getRepScorecard(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<RepScorecardReport>> {
    const response = await apiClient.get<ApiResponse<RepScorecardReport>>(
      `${ENDPOINT}/rep-scorecard`,
      { params },
    );
    return response.data;
  },

  /** POST, not GET: each call spends LLM quota, and a GET invites a retry or prefetch to respend it. */
  async requestRepScorecardReview(
    params: RepScorecardReviewParams,
  ): Promise<ApiResponse<RepScorecardAiReview>> {
    const response = await apiClient.post<ApiResponse<RepScorecardAiReview>>(
      `${ENDPOINT}/rep-scorecard/ai-review`,
      null,
      { params },
    );
    return response.data;
  },

  async getTaskPerformance(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<TaskPerformanceReport>> {
    const response = await apiClient.get<ApiResponse<TaskPerformanceReport>>(
      `${ENDPOINT}/task-performance`,
      { params },
    );
    return response.data;
  },

  // UC-23.4 Sales Pipeline Progression
  async getPipelineProgression(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<PipelineProgressionReport>> {
    const response = await apiClient.get<ApiResponse<PipelineProgressionReport>>(
      `${ENDPOINT}/pipeline-progression`,
      { params },
    );
    return response.data;
  },

  // UC-23.5 Quotation Outcome
  async getQuotationOutcome(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<QuotationOutcomeReport>> {
    const response = await apiClient.get<ApiResponse<QuotationOutcomeReport>>(
      `${ENDPOINT}/quotation-outcome`,
      { params },
    );
    return response.data;
  },

  // UC-23.3 SLA Compliance — served by the SLA module, but on the same dateFrom/dateTo contract as
  // the other four reports (it used to take from/to, which forced a rename here on every call).
  async getSlaCompliance(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<SlaComplianceReport>> {
    const response = await apiClient.get<ApiResponse<SlaComplianceReport>>(
      `/sla/report`,
      { params },
    );
    return response.data;
  },
};
