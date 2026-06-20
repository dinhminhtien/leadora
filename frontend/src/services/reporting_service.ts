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

const ENDPOINT = "/reporting";

export const reportingService = {
  async saveReportLog(payload: ReportLogPayload): Promise<ApiResponse<ReportLog>> {
    const response = await apiClient.post<ApiResponse<ReportLog>>(
      `${ENDPOINT}/logs`,
      payload,
    );
    return response.data;
  },
};
