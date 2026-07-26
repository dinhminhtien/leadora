import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

/**
 * Room availability requests between Sales and the Reservation team.
 *
 * This CRM owns no room inventory — the Reservation team answers from the hotel's real
 * PMS. `heldUntil` is their commitment recorded verbatim, not something computed here.
 *
 * Status wire values are UPPERCASE (the backend serializes the enum directly, same as
 * `Booking.status`). Note this differs from `Quotation.status`, which is lowercased by
 * `QuotationResponse` — a pre-existing quirk of that endpoint only.
 */
export type RoomRequestStatus = "PENDING" | "CONFIRMED" | "REJECTED" | "SUPERSEDED";

export type RoomRequest = {
  requestId: string;
  quotationId: string;
  quoteNo?: string;
  customerName?: string;
  roomTypeRequested?: string;
  checkInDate: string;
  checkOutDate: string;
  quantity: number;
  status: RoomRequestStatus;
  reservationNote?: string;
  /** ISO instant the Reservation team promised to hold the rooms until. */
  heldUntil?: string;
  requestedByName?: string;
  respondedByName?: string;
  respondedAt?: string;
  createdAt: string;
};

/** Room type and dates come from the quotation server-side, so only quantity is sent. */
export type CreateRoomRequestPayload = {
  quotationId: string;
  quantity: number;
};

export type RespondRoomRequestPayload = {
  /** Only CONFIRMED or REJECTED; the backend rejects anything else with 400. */
  decision: Extract<RoomRequestStatus, "CONFIRMED" | "REJECTED">;
  /** Required by the backend when rejecting, so Sales can tell the customer why. */
  reservationNote?: string;
  /** Only meaningful when confirming. ISO instant. */
  heldUntil?: string;
};

const ENDPOINT = "/room-requests";

export const roomRequestService = {
  /** Sales asks the Reservation team about a quotation's rooms. */
  async create(payload: CreateRoomRequestPayload): Promise<ApiResponse<RoomRequest>> {
    const { data } = await apiClient.post(ENDPOINT, payload);
    return data;
  },

  /** The Reservation inbox — oldest first, so the longest-waiting rep is served first. */
  async getInbox(params?: {
    status?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<PageResponse<RoomRequest>>> {
    const { data } = await apiClient.get(ENDPOINT, { params });
    return data;
  },

  /** Every request raised for one quotation, newest first. */
  async getByQuotation(quotationId: string): Promise<ApiResponse<RoomRequest[]>> {
    const { data } = await apiClient.get(`${ENDPOINT}/by-quotation/${quotationId}`);
    return data;
  },

  /** The Reservation team answers. */
  async respond(
    requestId: string,
    payload: RespondRoomRequestPayload,
  ): Promise<ApiResponse<RoomRequest>> {
    const { data } = await apiClient.put(`${ENDPOINT}/${requestId}/respond`, payload);
    return data;
  },
};

/**
 * The request that currently speaks for a quotation: the newest non-superseded one.
 * Mirrors `RoomConfirmationGate.currentRequest` on the backend — the list arrives
 * newest-first, so this is the first row that has not been superseded.
 */
export function currentRoomRequest(requests: RoomRequest[] | undefined): RoomRequest | undefined {
  return requests?.find((r) => r.status !== "SUPERSEDED");
}

/**
 * Whether a confirmed answer still covers what the quotation now says. Mirrors the
 * backend's staleness check so the UI can warn before the user hits a 409 on Send.
 */
export function isRoomConfirmationUsable(
  request: RoomRequest | undefined,
  quotation: { roomType?: string | null; checkInDate?: string | null; checkOutDate?: string | null },
): boolean {
  if (!request || request.status !== "CONFIRMED") return false;
  if (request.heldUntil && new Date(request.heldUntil).getTime() < Date.now()) return false;

  const sameRoomType =
    (request.roomTypeRequested ?? "").trim().toLowerCase() ===
    (quotation.roomType ?? "").trim().toLowerCase();

  return (
    sameRoomType &&
    request.checkInDate === quotation.checkInDate &&
    request.checkOutDate === quotation.checkOutDate
  );
}
