import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type ReadinessStatus = "PENDING" | "IN_PROGRESS" | "READY";
export type HandoverStatus = "SUBMITTED" | "ACKNOWLEDGED" | "READY";

export type ArrivalHandover = {
  handoverId: string;
  bookingId?: string;
  bookingCode?: string;
  customerName?: string;
  customerPhone?: string;
  checkInDate?: string;
  checkOutDate?: string;
  specialRequests?: string;
  roomPreferences?: string;
  vipNotes?: string;
  operationalNotes?: string;
  status?: HandoverStatus | string;
  readinessStatus?: ReadinessStatus | string;
  submittedAt?: string;
  acknowledgedAt?: string;
  updatedByName?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ArrivalHandoverQuery = {
  search?: string;
  readinessStatus?: string;
  sortBy?: string;
  sortDir?: string;
  page?: number;
  size?: number;
};

const ENDPOINT = "/arrival-handovers";

export const arrivalHandoverService = {
  async getList(params?: ArrivalHandoverQuery) {
    const response = await apiClient.get<ApiResponse<PageResponse<ArrivalHandover>>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getDetail(id: string) {
    const response = await apiClient.get<ApiResponse<ArrivalHandover>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async updateReadiness(id: string, readinessStatus: ReadinessStatus) {
    const response = await apiClient.put<ApiResponse<ArrivalHandover>>(
      `${ENDPOINT}/${id}/readiness`,
      { readinessStatus },
    );
    return response.data;
  },
};
