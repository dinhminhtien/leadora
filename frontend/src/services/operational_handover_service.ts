import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";
import type { ArrivalHandover, HandoverStatus } from "./arrival_handover_service";

export type { ArrivalHandover as OperationalHandover, HandoverStatus };

export type OperationalHandoverPayload = {
  bookingId?: string;
  specialRequests?: string;
  roomPreferences?: string;
  vipNotes?: string;
  operationalNotes?: string;
  assignedFoUserId?: string;
  status: "DRAFT" | "SUBMITTED";
};

export type OperationalHandoverQuery = {
  search?: string;
  status?: string;
  arrivalDate?: string;
  sortBy?: string;
  sortDir?: string;
  page?: number;
  size?: number;
};

const ENDPOINT = "/operational-handovers";

export const operationalHandoverService = {
  async getList(params?: OperationalHandoverQuery) {
    const response = await apiClient.get<ApiResponse<PageResponse<ArrivalHandover>>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<ArrivalHandover>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: OperationalHandoverPayload) {
    const response = await apiClient.post<ApiResponse<ArrivalHandover>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: OperationalHandoverPayload) {
    const response = await apiClient.put<ApiResponse<ArrivalHandover>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
