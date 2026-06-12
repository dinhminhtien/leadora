import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type SalesPipeline = Record<string, unknown> & {
  id: string;
  name?: string;
};

export type SalesPipelinePayload = Record<string, unknown>;

const ENDPOINT = "/sales-pipeline";

export const salesPipelineService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<SalesPipeline[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<SalesPipeline>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: SalesPipelinePayload) {
    const response = await apiClient.post<ApiResponse<SalesPipeline>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: SalesPipelinePayload) {
    const response = await apiClient.put<ApiResponse<SalesPipeline>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
