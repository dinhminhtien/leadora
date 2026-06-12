import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Reminder = Record<string, unknown> & {
  id: string;
  title?: string;
  dueAt?: string;
};

export type ReminderPayload = Record<string, unknown>;

const ENDPOINT = "/reminders";

export const reminderService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Reminder[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Reminder>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: ReminderPayload) {
    const response = await apiClient.post<ApiResponse<Reminder>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: ReminderPayload) {
    const response = await apiClient.put<ApiResponse<Reminder>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
