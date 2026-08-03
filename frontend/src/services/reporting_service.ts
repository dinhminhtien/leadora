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
  leadsCreated: number;
  qualifiedLeads: number;
  leadsConverted: number;
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
    params?: ReportRangeParams,
  ): Promise<ApiResponse<SalesPerformanceReport>> {
    const response = await apiClient.get<ApiResponse<SalesPerformanceReport>>(
      `${ENDPOINT}/sales-performance`,
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
