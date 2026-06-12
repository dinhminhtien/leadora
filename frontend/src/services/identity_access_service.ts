import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type IdentityAccessRecord = Record<string, unknown> & {
  id: string;
  name?: string;
};

export type IdentityAccessPayload = Record<string, unknown>;

const ENDPOINT = "/identity-access";

export const identityAccessService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<IdentityAccessRecord[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<IdentityAccessRecord>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: IdentityAccessPayload) {
    const response = await apiClient.post<ApiResponse<IdentityAccessRecord>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: IdentityAccessPayload) {
    const response = await apiClient.put<ApiResponse<IdentityAccessRecord>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
