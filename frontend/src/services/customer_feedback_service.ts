import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type CustomerFeedback = Record<string, unknown> & {
  id: string;
  rating?: number;
  comment?: string;
};

export type CustomerFeedbackPayload = Record<string, unknown>;

const ENDPOINT = "/customer-feedback";

export const customerFeedbackService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<CustomerFeedback[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<CustomerFeedback>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: CustomerFeedbackPayload) {
    const response = await apiClient.post<ApiResponse<CustomerFeedback>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: CustomerFeedbackPayload) {
    const response = await apiClient.put<ApiResponse<CustomerFeedback>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },

  async submitByToken(token: string, payload: CustomerFeedbackPayload) {
    const response = await apiClient.post<ApiResponse<CustomerFeedback>>(
      `${ENDPOINT}/public/${token}`,
      payload,
    );
    return response.data;
  },
};
