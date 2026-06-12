import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type CustomerProfile = Record<string, unknown> & {
  id: string;
  name?: string;
  email?: string;
};

export type CustomerProfilePayload = Record<string, unknown>;

const ENDPOINT = "/customers";

export const customerProfileService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<CustomerProfile[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<CustomerProfile>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: CustomerProfilePayload) {
    const response = await apiClient.post<ApiResponse<CustomerProfile>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: CustomerProfilePayload) {
    const response = await apiClient.put<ApiResponse<CustomerProfile>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
