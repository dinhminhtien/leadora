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
  quotationsCreated: number;
  quotationsAccepted: number;
  quotationAcceptanceRate: number;
  bookingsConfirmed: number;
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
};

export type TaskPerformanceReport = {
  dateFrom?: string;
  dateTo?: string;
  totalTasks: number;
  completed: number;
  open: number;
  cancelled: number;
  overdue: number;
  completionRate: number;
  overdueRate: number;
  priorityLow: number;
  priorityMedium: number;
  priorityHigh: number;
  staff: TaskStaffRow[];
};

// ── UC-23.3 SLA Compliance ───────────────────────────────────────────────────
export type SlaActivityBreakdown = {
  activityType: string;
  activityLabel: string;
  total: number;
  resolved: number;
  breached: number;
  warning: number;
  withinSla: number;
  breachRatePct: number;
  avgProcessingHours: number;
};

export type SlaComplianceReport = {
  fromDate?: string;
  toDate?: string;
  totalTracked: number;
  resolvedCount: number;
  breachedCount: number;
  warningCount: number;
  withinSlaCount: number;
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
  avgAgeDays: number;
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
  stages: PipelineStageRow[];
};

// ── UC-23.5 Quotation Outcome ────────────────────────────────────────────────
export type QuotationStatusRow = { status: string; label: string; count: number };

export type QuotationOutcomeReport = {
  dateFrom?: string;
  dateTo?: string;
  total: number;
  sent: number;
  approved: number;
  rejected: number;
  expired: number;
  accepted: number;
  converted: number;
  approvalRate: number;
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

  // UC-23.3 SLA Compliance — served by the SLA module (params are from/to, not dateFrom/dateTo).
  async getSlaCompliance(
    params?: ReportRangeParams,
  ): Promise<ApiResponse<SlaComplianceReport>> {
    const response = await apiClient.get<ApiResponse<SlaComplianceReport>>(
      `/sla/report`,
      { params: { from: params?.dateFrom, to: params?.dateTo } },
    );
    return response.data;
  },
};
