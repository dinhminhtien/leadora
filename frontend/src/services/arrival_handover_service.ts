import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type ReadinessStatus =
  | "PENDING_REVIEW"
  | "REVIEWED"
  | "READY_FOR_ARRIVAL"
  | "NEED_CLARIFICATION";

export type HandoverStatus = "SUBMITTED" | "ACKNOWLEDGED" | "READY";

/**
 * The readiness workflow, mirroring ALLOWED_TRANSITIONS in UpdateHandoverReadinessUseCase.
 * The server is the authority — this exists so the dropdown greys out a move it would refuse
 * rather than letting the front desk pick it and read an error afterwards.
 *
 * PENDING_REVIEW cannot be chosen at all (only Sales re-submitting sets it), and
 * NEED_CLARIFICATION is a dead end for Front Office: POST-4 requires Sales/Reservation to
 * update and re-submit before readiness can be confirmed again.
 */
export const READINESS_TRANSITIONS: Record<ReadinessStatus, ReadinessStatus[]> = {
  PENDING_REVIEW: ["REVIEWED", "NEED_CLARIFICATION"],
  REVIEWED: ["REVIEWED", "READY_FOR_ARRIVAL", "NEED_CLARIFICATION"],
  READY_FOR_ARRIVAL: ["READY_FOR_ARRIVAL", "NEED_CLARIFICATION"],
  NEED_CLARIFICATION: ["NEED_CLARIFICATION"],
};

/** Booking states that still accept a readiness change (BR-44). */
export function isBookingActive(bookingStatus?: string): boolean {
  return bookingStatus == null || ["CONFIRMED", "CHECKED_IN"].includes(bookingStatus);
}

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
  bookingStatus?: string;
  assignedFoUserId?: string;
  assignedFoName?: string;
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
  /** Filter by responsible Front Office staff (UC-22.1 step 5). Supervisors only — the API
   *  ignores it for Front Office Staff, whose visibility is decided server-side. */
  assignedFoUserId?: string;
  /** Front Office Staff see their own queue by default; set this to take the whole desk. */
  deskWide?: boolean;
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
  /**
   * Counts for the KPI cards. The filters must match the list's, or the cards count rows the
   * table cannot show — except `readinessStatus`, which is deliberately not sent: the cards are
   * how you apply that filter, so counting under it would zero the other three.
   */
  async getSummary(params?: Pick<ArrivalHandoverQuery, "search" | "arrivalDate" | "assignedFoUserId" | "deskWide">) {
    const response = await apiClient.get<ApiResponse<ArrivalHandoverSummary>>(
      `${ENDPOINT}/summary`,
      { params },
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
