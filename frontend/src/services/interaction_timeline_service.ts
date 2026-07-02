import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type InteractionTimelineEntry = {
  id: string;
  type: "call" | "email" | "meeting" | "note";
  description: string;
  agentName: string;
  agentId?: string;
  linkedName: string;
  linkedType?: "lead" | "customer" | "deal" | "N/A";
  linkedId?: string;
  occurredAt: string;
  createdAt: string;
};

export type InteractionTimelineQuery = ListQuery & {
  type?: string;
  agentId?: string;
};

export type CreateInteractionTimelinePayload = {
  type: "call" | "email" | "meeting" | "note";
  description: string;
  occurredAt: string; // ISO datetime
  leadId?: string;
  customerId?: string;
  dealId?: string;
};

export type UpdateInteractionTimelinePayload = {
  type: "call" | "email" | "meeting" | "note";
  description: string;
  occurredAt: string; // ISO datetime
};

const ENDPOINT = "/interaction-timeline";

export const interactionTimelineService = {
  async getList(params?: InteractionTimelineQuery) {
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

  async create(payload: CreateInteractionTimelinePayload) {
    const response = await apiClient.post<ApiResponse<InteractionTimelineEntry>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: UpdateInteractionTimelinePayload) {
    const response = await apiClient.put<ApiResponse<InteractionTimelineEntry>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};

