import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type BookingConfirmation = Record<string, unknown> & {
  id: string;
  bookingCode?: string;
  status?: string;
};

export type BookingConfirmationPayload = Record<string, unknown>;

const ENDPOINT = "/booking-confirmations";

export const bookingConfirmationService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<BookingConfirmation[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<BookingConfirmation>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: BookingConfirmationPayload) {
    const response = await apiClient.post<ApiResponse<BookingConfirmation>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: BookingConfirmationPayload) {
    const response = await apiClient.put<ApiResponse<BookingConfirmation>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
