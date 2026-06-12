import { apiClient, type ApiResponse } from "@/services/api_client";

export type ReportQuery = {
  from?: string;
  to?: string;
};

export type ReportMetric = Record<string, unknown>;

const ENDPOINT = "/reporting";

export const reportingService = {
  async getSalesPerformance(params?: ReportQuery) {
    const response = await apiClient.get<ApiResponse<ReportMetric>>(
      `${ENDPOINT}/sales-performance`,
      { params },
    );
    return response.data;
  },

  async getFollowUpTaskPerformance(params?: ReportQuery) {
    const response = await apiClient.get<ApiResponse<ReportMetric>>(
      `${ENDPOINT}/follow-up-task-performance`,
      { params },
    );
    return response.data;
  },

  async getSlaCompliance(params?: ReportQuery) {
    const response = await apiClient.get<ApiResponse<ReportMetric>>(
      `${ENDPOINT}/sla-compliance`,
      { params },
    );
    return response.data;
  },

  async getPipelineProgression(params?: ReportQuery) {
    const response = await apiClient.get<ApiResponse<ReportMetric>>(
      `${ENDPOINT}/pipeline-progression`,
      { params },
    );
    return response.data;
  },

  async getQuotationOutcome(params?: ReportQuery) {
    const response = await apiClient.get<ApiResponse<ReportMetric>>(
      `${ENDPOINT}/quotation-outcome`,
      { params },
    );
    return response.data;
  },
};
