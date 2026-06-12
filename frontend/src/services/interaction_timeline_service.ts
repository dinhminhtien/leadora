import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type InteractionTimelineEntry = Record<string, unknown> & {
  id: string;
  title?: string;
};

export type InteractionTimelinePayload = Record<string, unknown>;

const ENDPOINT = "/interaction-timeline";

export const interactionTimelineService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<InteractionTimelineEntry[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<InteractionTimelineEntry>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: InteractionTimelinePayload) {
    const response = await apiClient.post<ApiResponse<InteractionTimelineEntry>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: InteractionTimelinePayload) {
    const response = await apiClient.put<ApiResponse<InteractionTimelineEntry>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
