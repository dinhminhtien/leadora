import { apiClient, type ApiResponse } from "@/services/api_client";

/**
 * Room allotment — how many rooms the hotel has released to this CRM to sell.
 *
 * This is a **quota**, not the hotel's occupancy. Hotels do not disclose how full they are;
 * they hand a block to the sales channel and the channel sells within it. So "none left" means
 * our allocation is spent, not that the hotel is full — which is why running out routes to the
 * Reservation team rather than ending the enquiry. Every label in the UI has to say so.
 */

/** Why a night has no number, kept explicit so clients never have to infer it. */
export type NightStatus = "PUBLISHED" | "NOT_PUBLISHED" | "CLOSED";

export type AvailabilityNight = {
  date: string;
  /** Null when unpublished — deliberately not zero (BR-48). */
  allotted?: number;
  booked: number;
  held: number;
  /** Null when unpublished. Render as "—", never as "sold out". */
  available?: number;
  closed: boolean;
  status: NightStatus;
  /** When the Reservation team last vouched for the figure. */
  asOf?: string;
  stale: boolean;
};

export type AvailabilityRoomRow = {
  productId: string;
  roomType: string;
  unitPrice?: number;
  unit?: string;
  days: AvailabilityNight[];
};

export type AvailabilityGrid = {
  from: string;
  to: string;
  rooms: AvailabilityRoomRow[];
};

export type StayAvailability = {
  productId: string;
  roomType: string;
  unitPrice?: number;
  /** Minimum across the stay's nights; null when any night is unpublished. */
  availableForStay?: number;
  /** Whether the requested rooms can be taken without asking Reservation. */
  bookableNow: boolean;
  /**
   * The nights that set the minimum. This is what turns a refusal into a sale — "full on the
   * 11th, open either side" gives the rep something to offer.
   */
  limitingDates: string[];
  closedDates: string[];
  unpublishedDates: string[];
  stale: boolean;
  asOf?: string;
};

export type PublishAllotmentPayload = {
  productId: string;
  dateFrom: string;
  /** Inclusive last night, not a check-out date. */
  dateTo: string;
  allottedQty: number;
  /** Stop-sell. Distinct from a quota of zero, which Reservation may still be able to extend. */
  closed?: boolean;
  /** When the hotel's figures were true, if not now. Drives the staleness warning. */
  asOf?: string;
  note?: string;
  /** `MONDAY`…`SUNDAY`; empty means every day. Weekend quota rarely matches midweek. */
  weekdays?: string[];
};

/** One night the desk has checked against the hotel's system. */
export type ReconcileEntry = {
  productId: string;
  stayDate: string;
  /** Rooms the hotel's system shows as still free to us. The server adds back what is sold. */
  actualAvailable: number;
};

export type ReconciliationChange = {
  productId: string;
  roomType: string;
  stayDate: string;
  /** Null when the night had never been published. */
  previousAllotted?: number;
  newAllotted: number;
  booked: number;
  observedAvailable: number;
};

export type ReconciliationResult = {
  nightsReconciled: number;
  /** Nights whose allotment actually moved — how far the CRM had drifted. */
  nightsChanged: number;
  changes: ReconciliationChange[];
};

export type AllotmentImportRejection = {
  /** 1-based line number in the uploaded file, header included. */
  line: number;
  content: string;
  reason: string;
};

export type AllotmentImportResult = {
  rowsRead: number;
  nightsImported: number;
  rowsRejected: number;
  rejected: AllotmentImportRejection[];
};

const ENDPOINT = "/room-availability";

export const roomAvailabilityService = {
  /** The grid: one row per room type, one cell per night. `from`/`to` inclusive. */
  async getGrid(params: {
    from: string;
    to: string;
    productId?: string;
  }): Promise<ApiResponse<AvailabilityGrid>> {
    const { data } = await apiClient.get(ENDPOINT, { params });
    return data;
  },

  /** One answer per room type for a stay. `checkOut` is exclusive — departure is not a night. */
  async getForStay(params: {
    checkIn: string;
    checkOut: string;
    quantity?: number;
    productId?: string;
  }): Promise<ApiResponse<StayAvailability[]>> {
    const { data } = await apiClient.get(`${ENDPOINT}/for-stay`, { params });
    return data;
  },

  /**
   * Publishes quota for a range. Returns the nights that are now **below** what has already
   * been sold: the hotel is entitled to take an allocation back, so this succeeds rather than
   * refusing (BR-49), and the affected reps are notified server-side.
   */
  async publish(payload: PublishAllotmentPayload): Promise<ApiResponse<string[]>> {
    const { data } = await apiClient.put(ENDPOINT, payload);
    return data;
  },

  /**
   * The daily reconciliation. Send what the hotel's system shows as free; the server
   * reconstructs the block from it (`allotted = observed free + already sold`), because a PMS
   * reports availability and never states the size of the block it came from.
   */
  /**
   * Bulk import of the hotel's spreadsheet (CSV). Rows are matched to room types by name —
   * the hotel's file has no idea what our product ids are — and anything unmatched comes back
   * in `rejected` rather than being guessed at.
   */
  async importCsv(file: File): Promise<ApiResponse<AllotmentImportResult>> {
    const form = new FormData();
    form.append("file", file);
    const { data } = await apiClient.post(`${ENDPOINT}/import`, form);
    return data;
  },

  async reconcile(
    entries: ReconcileEntry[],
    asOf?: string,
  ): Promise<ApiResponse<ReconciliationResult>> {
    const { data } = await apiClient.post(`${ENDPOINT}/reconcile`, { entries, asOf });
    return data;
  },
};
