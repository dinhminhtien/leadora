import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type FrontOfficeHandover = Record<string, unknown> & {
  id: string;
  title?: string;
  status?: string;
};

export type FrontOfficeHandoverPayload = Record<string, unknown>;

const ENDPOINT = "/front-office-handovers";

export const frontOfficeHandoverService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<FrontOfficeHandover[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<FrontOfficeHandover>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: FrontOfficeHandoverPayload) {
    const response = await apiClient.post<ApiResponse<FrontOfficeHandover>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: FrontOfficeHandoverPayload) {
    const response = await apiClient.put<ApiResponse<FrontOfficeHandover>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
