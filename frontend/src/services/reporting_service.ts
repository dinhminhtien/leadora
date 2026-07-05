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

export type DashboardSummary = {
  activeLeadsCount: number;
  totalLeadsCount: number;
  activeDealsCount: number;
  activeDealsValue: number;
  weightedPipelineValue: number;
  totalDealsValue: number;
  pendingTasksCount: number;
  overdueTasksCount: number;
  funnelStages: StageSummary[];
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
};
