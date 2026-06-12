import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type OperationalHandover = Record<string, unknown> & {
  id: string;
  title?: string;
  status?: string;
};

export type OperationalHandoverPayload = Record<string, unknown>;

const ENDPOINT = "/operational-handovers";

export const operationalHandoverService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<OperationalHandover[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<OperationalHandover>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: OperationalHandoverPayload) {
    const response = await apiClient.post<ApiResponse<OperationalHandover>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: OperationalHandoverPayload) {
    const response = await apiClient.put<ApiResponse<OperationalHandover>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
