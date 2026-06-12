import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Deal = Record<string, unknown> & {
  id: string;
  name?: string;
  status?: string;
};

export type DealPayload = Record<string, unknown>;

const ENDPOINT = "/deals";

export const dealService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Deal[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Deal>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  async create(payload: DealPayload) {
    const response = await apiClient.post<ApiResponse<Deal>>(ENDPOINT, payload);
    return response.data;
  },

  async update(id: string, payload: DealPayload) {
    const response = await apiClient.put<ApiResponse<Deal>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
