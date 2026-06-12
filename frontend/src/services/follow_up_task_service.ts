import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type FollowUpTask = Record<string, unknown> & {
  id: string;
  title?: string;
  status?: string;
};

export type FollowUpTaskPayload = Record<string, unknown>;

const ENDPOINT = "/follow-up-tasks";

export const followUpTaskService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<FollowUpTask[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<FollowUpTask>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: FollowUpTaskPayload) {
    const response = await apiClient.post<ApiResponse<FollowUpTask>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: FollowUpTaskPayload) {
    const response = await apiClient.put<ApiResponse<FollowUpTask>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
