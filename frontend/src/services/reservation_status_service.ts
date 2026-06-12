import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type ReservationStatus = Record<string, unknown> & {
  id: string;
  reservationCode?: string;
  status?: string;
};

export type ReservationStatusPayload = Record<string, unknown>;

const ENDPOINT = "/reservation-status";

export const reservationStatusService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<ReservationStatus[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<ReservationStatus>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: ReservationStatusPayload) {
    const response = await apiClient.post<ApiResponse<ReservationStatus>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: ReservationStatusPayload) {
    const response = await apiClient.put<ApiResponse<ReservationStatus>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
