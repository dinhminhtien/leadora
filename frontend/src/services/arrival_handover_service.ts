import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type ReadinessStatus =
  | "PENDING_REVIEW"
  | "REVIEWED"
  | "READY_FOR_ARRIVAL"
  | "NEED_CLARIFICATION";

export type HandoverStatus = "SUBMITTED" | "ACKNOWLEDGED" | "READY";

export type RoomLine = {
  productName?: string;
  roomNumber?: string;
  quantity?: number;
  nights?: number;
  inventoryStatus?: string;
};

export type ArrivalHandover = {
  handoverId: string;
  bookingId?: string;
  bookingCode?: string;
  customerName?: string;
  customerPhone?: string;
  checkInDate?: string;
  checkOutDate?: string;
  roomSummary?: string;
  rooms?: RoomLine[];
  specialRequests?: string;
  roomPreferences?: string;
  vipNotes?: string;
  operationalNotes?: string;
  paymentReference?: string;
  status?: HandoverStatus | string;
  readinessStatus?: ReadinessStatus | string;
  clarificationNote?: string;
  submittedAt?: string;
  acknowledgedAt?: string;
  updatedByName?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ArrivalHandoverQuery = {
  search?: string;
  readinessStatus?: string;
  arrivalDate?: string;
  sortBy?: string;
  sortDir?: string;
  page?: number;
  size?: number;
};

export type ArrivalHandoverSummary = {
  total: number;
  pendingReview: number;
  reviewed: number;
  readyForArrival: number;
  needClarification: number;
};

const ENDPOINT = "/arrival-handovers";

export const arrivalHandoverService = {
  async getSummary() {
    const response = await apiClient.get<ApiResponse<ArrivalHandoverSummary>>(
      `${ENDPOINT}/summary`,
    );
    return response.data;
  },

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

  async updateReadiness(
    id: string,
    readinessStatus: ReadinessStatus,
    clarificationNote?: string,
  ) {
    const response = await apiClient.put<ApiResponse<ArrivalHandover>>(
      `${ENDPOINT}/${id}/readiness`,
      { readinessStatus, clarificationNote },
    );
    return response.data;
  },
};
