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
  taskOverdueRate?: number;
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
  overdue: number;
  completionRate: number;
  unassigned?: boolean;
};

export type TaskPerformanceReport = {
  dateFrom?: string;
  dateTo?: string;
  totalTasks: number;
  completed: number;
  open: number;
  cancelled: number;
  /** BR-17 derived flag: past end_at and not finished. Never a stored status. */
  overdue: number;
  completionRate: number;
  overdueRate: number;
  priorityLow: number;
  priorityMedium: number;
  priorityHigh: number;
  /** True when the caller is scoped to their own tasks rather than the whole team. */
  ownScope?: boolean;
  staff: TaskStaffRow[];
};

// ── UC-23.3 SLA Compliance ───────────────────────────────────────────────────
export type SlaActivityBreakdown = {
  activityType: string;
  activityLabel: string;
  total: number;
  resolved: number;
  resolvedOnTime: number;
  breached: number;
  warning: number;
  withinSla: number;
  breachRatePct: number;
  complianceRatePct: number;
  avgProcessingHours: number;
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
  breachRatePct: number;
  complianceRatePct: number;
  resolutionRatePct: number;
  avgProcessingHours: number;
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
  /** Measured average days a deal spends in this stage, from recorded stage changes. */
  avgDaysInStage: number;
  /** How many stage visits avgDaysInStage averages over; 0 means no history for this stage. */
  dwellSamples?: number;
  closed: boolean;
};

export type PipelineProgressionReport = {
  dateFrom?: string;
  dateTo?: string;
  totalDeals: number;
  openDeals: number;
  closedWon: number;
  closedLost: number;
  winRate: number;
  pipelineValue: number;
  bottleneckStage?: string;
  /** How the bottleneck was chosen — rendered so the claim is qualified where it is made. */
  bottleneckBasis?: string;
  /** True when timings come from recorded stage changes rather than the idle-time fallback. */
  historyMeasured?: boolean;
  stages: PipelineStageRow[];
};

// ── UC-23.5 Quotation Outcome ────────────────────────────────────────────────
export type QuotationStatusRow = { status: string; label: string; count: number };

export type QuotationOutcomeReport = {
  dateFrom?: string;
  dateTo?: string;
  /** Live quotations — superseded revisions excluded from every rate denominator (BR-22). */
  total: number;
  superseded: number;
  sent: number;
  /** Ever approved, read from approved_at rather than from rows still parked at status APPROVED. */
  approved: number;
  /** Raw REJECTED count — mixes approver and customer rejections; see rejectedByApprover. */
  rejected: number;
  rejectedByApprover: number;
  expired: number;
  accepted: number;
  converted: number;
  approvalRate: number;
  /** (accepted + converted) / total — matches the acceptance figure on UC-23.1. */
  acceptanceRate: number;
  conversionRate: number;
  byStatus: QuotationStatusRow[];
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
