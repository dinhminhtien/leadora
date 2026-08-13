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
export type RoomRequestStatus =
  | "PENDING"
  | "CONFIRMED"
  | "REJECTED"
  | "SUPERSEDED"
  /** Sales withdrew the question before Reservation answered it (UC-26.4). */
  | "CANCELLED";

/**
 * States that no longer represent a live question or a usable answer. Mirrors
 * `RoomRequestStatus.notSpeakingForQuotation()` on the backend.
 */
const NOT_SPEAKING_FOR_QUOTATION: ReadonlySet<RoomRequestStatus> = new Set([
  "SUPERSEDED",
  "CANCELLED",
]);

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

export type RespondRoomRequestPayload = {
  /** Only CONFIRMED or REJECTED; the backend rejects anything else with 400. */
  decision: Extract<RoomRequestStatus, "CONFIRMED" | "REJECTED">;
  /** Required by the backend when rejecting, so Sales can tell the customer why. */
  reservationNote?: string;
  /** Only meaningful when confirming. ISO instant. */
  heldUntil?: string;
};

const ENDPOINT = "/room-requests";

/**
 * There is no `create` and no `cancel`. Both endpoints are gone from the API: a request is raised
 * by the workflow when the customer accepts their quotation, so Sales never asks — or withdraws —
 * by hand. Having both routes meant one quotation could put two questions in the Reservation
 * inbox with no way to tell which was current.
 */
export const roomRequestService = {
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
 * The request that currently speaks for a quotation: the newest one that is neither
 * superseded nor cancelled. Mirrors `RoomConfirmationReader.currentRequest` on the
 * backend — the list arrives newest-first, so this is the first row that still counts.
 *
 * `CANCELLED` is still skipped even though nothing creates one any more: rows written before
 * withdrawing was removed are still in the table, and they were never answers.
 */
export function currentRoomRequest(requests: RoomRequest[] | undefined): RoomRequest | undefined {
  return requests?.find((r) => !NOT_SPEAKING_FOR_QUOTATION.has(r.status));
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
