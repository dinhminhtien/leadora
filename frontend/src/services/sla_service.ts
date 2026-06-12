import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type SlaRule = Record<string, unknown> & {
  id: string;
  name?: string;
  status?: string;
};

export type SlaRulePayload = Record<string, unknown>;

const ENDPOINT = "/sla";

export const slaService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<SlaRule[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<SlaRule>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: SlaRulePayload) {
    const response = await apiClient.post<ApiResponse<SlaRule>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: SlaRulePayload) {
    const response = await apiClient.put<ApiResponse<SlaRule>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
