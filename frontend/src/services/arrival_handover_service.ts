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

/** Mirror of `BookingStatus.EDITABLE_BY_SALES` — narrower than {@link isBookingActive} on purpose. */
const EDITABLE_BY_SALES = ["CONFIRMED"];

/**
 * Why Sales/Reservation may **not** author this handover right now, or `null` when they may.
 *
 * <p>Mirrors `HandoverEditPolicy.assertAuthorable` on the backend, which stays the authority — this
 * exists so the user is told before filling a form in, not after saving it. Both gates, in the
 * server's order:
 *
 * 1. the arrival date has passed (BR-26) — absolute, no exception;
 * 2. the booking is not `CONFIRMED` (BR-44), unless the handover is answering a Front Office
 *    clarification on a booking that is still live.
 *
 * Dates are compared as `YYYY-MM-DD` strings, which is what the API sends for a `LocalDate`.
 * Parsing them with `new Date()` would read them as UTC midnight and, west of Greenwich, close the
 * gate a day early — on the very day the desk is most likely to be correcting the sheet.
 */
export function handoverAuthoringBlockReason(subject: {
  checkInDate?: string;
  bookingStatus?: string;
  readinessStatus?: string;
}): string | null {
  const { checkInDate, bookingStatus, readinessStatus } = subject;

  // "sv-SE" is the terse way to get a local YYYY-MM-DD out of Intl.
  const today = new Date().toLocaleDateString("sv-SE");
  if (checkInDate && checkInDate < today) {
    return `The arrival date (${checkInDate}) has passed.`;
  }

  if (!bookingStatus) return null;

  const answeringClarification =
    readinessStatus === "NEED_CLARIFICATION" && isBookingActive(bookingStatus);
  if (!EDITABLE_BY_SALES.includes(bookingStatus) && !answeringClarification) {
    return bookingStatus === "CHECKED_IN"
      ? "The guest has already checked in."
      : `This booking is ${bookingStatus}.`;
  }

  return null;
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
