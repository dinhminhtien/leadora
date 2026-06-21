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
};
