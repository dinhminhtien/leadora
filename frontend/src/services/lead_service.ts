import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Lead = Record<string, unknown> & {
  id: string;
  name?: string;
  status?: string;
};

export type LeadPayload = Record<string, unknown>;

const ENDPOINT = "/leads";

export const leadService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Lead[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Lead>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  async create(payload: LeadPayload) {
    const response = await apiClient.post<ApiResponse<Lead>>(ENDPOINT, payload);
    return response.data;
  },

  async update(id: string, payload: LeadPayload) {
    const response = await apiClient.put<ApiResponse<Lead>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
