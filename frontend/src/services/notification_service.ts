import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Notification = Record<string, unknown> & {
  id: string;
  title?: string;
  read?: boolean;
};

export type NotificationPayload = Record<string, unknown>;

const ENDPOINT = "/notifications";

export const notificationService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Notification[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Notification>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: NotificationPayload) {
    const response = await apiClient.post<ApiResponse<Notification>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: NotificationPayload) {
    const response = await apiClient.put<ApiResponse<Notification>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
