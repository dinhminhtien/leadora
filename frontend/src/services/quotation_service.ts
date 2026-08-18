import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type QuotationStatus =
  | "draft"
  | "pending_approval"
  | "sent"
  | "approved"
  | "rejected"
  | "expired"
  | "closed"
  | "converted"
  | "interested"
  | "accepted"
  | "pending_revision"
  | "pending_customer_response"
  | "accepted_by_customer"
  | "booking_request"
  | "reservation_pending"
  | "reservation_rejected";

export type RoomLine = {
  /** The room's identity. Required on create/revise — allotment is keyed on the product. */
  productId: string;
  /** Display label only; the server rewrites it from the product it points at. */
  roomType?: string;
  numberOfRooms: number;
  pricePerNight: number;
};

/**
 * A room line as it comes back from the server. {@link RoomLine} is the request shape, where the
 * product is required and the label is decorative; here it is the other way round — the label is
 * always present, and productId may be missing on quotations created before the link existed.
 */
export type RoomLineDetail = Omit<RoomLine, "productId" | "roomType"> & {
  productId?: string;
  roomType: string;
  nights?: number;
  lineTotal?: number;
};

export type Quotation = {
  id: string;
  quoteNo: string;
  contactName: string;
  dealName: string;
  amount: number;
  expiryDate: string;
  status: QuotationStatus;
  dealId?: string;
  customerId?: string;
  email?: string;
  phone?: string;
  /** Summary string ("Deluxe Suite" or "Deluxe Suite +1 more") — see `roomLines` for the full breakdown. */
  roomType?: string;
  /** Aggregate room count across all lines. */
  numberOfRooms?: number;
  checkInDate?: string;
  checkOutDate?: string;
  nights?: number;
  /** Only set when the quotation has exactly one room line. */
  pricePerNight?: number;
  roomLines?: RoomLineDetail[];
  paymentPolicy?: string;
  subtotal?: number;
  discountPercent?: number;
  discountAmount?: number;
  totalAmount?: number;
  notes?: string;
  version?: number;
  validUntil?: string;
  parentQuotationId?: string;
  changeReason?: string;
  reservationDecision?: "APPROVED" | "REJECTED" | null;
};

export type CloseQuotationPayload = {
  reason: string;
  notes?: string;
};

export type ExpireOverduePayload = {
  expiredByName?: string;
  expiredByRole?: string;
};

export type ExpireOverdueResult = {
  expiredCount: number;
  expiredIds: string[];
};

export type ConvertToBookingPayload = {
  contactName: string;
  email: string;
  phone: string;
  roomType: string;
  checkInDate: string;
  checkOutDate: string;
  specialRequests?: string;
  convertedByName?: string;
  convertedByRole?: string;
};

export type BookingResult = {
  bookingId: string;
  bookingCode: string;
  status: string;
  quotationId: string;
  customerName: string;
  checkInDate: string;
  checkOutDate: string;
  totalAmount: number;
};

export type TrackCustomerResponsePayload = {
  customerResponse: "ACCEPTED" | "REJECTED" | "INTERESTED" | "NEED_REVISION";
  lostReason?: string;
  notes?: string;
};

export type ReviseQuotationPayload = {
  roomLines: RoomLine[];
  checkInDate: string;
  checkOutDate: string;
  discountPercent: number;
  paymentPolicy: string;
  validUntil: string;
  notes?: string;
  changeReason: string;
  revisedByName?: string;
  revisedByRole?: string;
};

export type CreateQuotationPayload = {
  dealId: string;
  roomLines: RoomLine[];
  checkInDate: string;
  checkOutDate: string;
  discountPercent: number;
  paymentPolicy: string;
  validUntil: string;
  notes?: string;
};

export type SubmitQuotationPayload = {
  submittedByName?: string;
  submittedByRole?: string;
};

export type ProcessApprovalPayload = {
  action: "APPROVE" | "REJECT" | "REQUEST_CHANGES";
  notes?: string;
};

export type SendQuotationPayload = {
  sendMethod: "EMAIL" | "WHATSAPP" | "PDF";
  recipientName: string;
  recipientEmail?: string;
  recipientPhone?: string;
  personalMessage?: string;
};

/**
 * Whether one action is currently available on a quotation, and the reason it is not.
 *
 * The reason is the backend's own sentence, produced by the very policy the write endpoint
 * enforces — so a disabled button says exactly what an attempt would have said. Screens render
 * `reason` verbatim rather than composing their own wording, which is what let the old UI offer
 * "Convert to Booking" on every accepted quotation while the server also required a contract the
 * customer had acknowledged.
 */
export type ActionEligibility = {
  allowed: boolean;
  /** Matches the `errorCode` the write endpoint would return. */
  errorCode?: string;
  /** Present only when `allowed` is false. */
  reason?: string;
  /** Dotted path of the input to correct, e.g. `customer.email`. */
  field?: string;
};

export type QuotationEligibility = {
  quotationId: string;
  status: string;
  /** Status and customer identity — what every delivery method needs. */
  send: ActionEligibility;
  sendByEmail: ActionEligibility;
  sendByWhatsApp: ActionEligibility;
  requestAvailability: ActionEligibility;
  convert: ActionEligibility;
};

export type QuotationListParams = {
  status?: string;
  statuses?: string[];
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
};

const ENDPOINT = "/quotations";

export const quotationService = {
  async getList(params?: QuotationListParams): Promise<ApiResponse<PageResponse<Quotation>>> {
    const response = await apiClient.get<ApiResponse<PageResponse<Quotation>>>(ENDPOINT, { params });
    return response.data;
  },

  async getById(id: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.get<ApiResponse<Quotation>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  /** What this quotation currently allows, and why it does not allow the rest. */
  async getEligibility(id: string): Promise<ApiResponse<QuotationEligibility>> {
    const response = await apiClient.get<ApiResponse<QuotationEligibility>>(
      `${ENDPOINT}/${id}/eligibility`,
    );
    return response.data;
  },

  async create(payload: CreateQuotationPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(ENDPOINT, payload);
    return response.data;
  },

  async submit(id: string, payload: SubmitQuotationPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/submit`, payload);
    return response.data;
  },

  async getPendingApprovals(): Promise<ApiResponse<Quotation[]>> {
    const response = await apiClient.get<ApiResponse<Quotation[]>>(`${ENDPOINT}/pending-approvals`);
    return response.data;
  },

  async processApproval(id: string, payload: ProcessApprovalPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/process-approval`, payload);
    return response.data;
  },

  async send(id: string, payload: SendQuotationPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/send`, payload);
    return response.data;
  },

  async resend(id: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/resend`);
    return response.data;
  },

  async revise(id: string, payload: ReviseQuotationPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/revise`, payload);
    return response.data;
  },

  async trackResponse(id: string, payload: TrackCustomerResponsePayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/track-response`, payload);
    return response.data;
  },

  async convert(id: string, payload: ConvertToBookingPayload): Promise<ApiResponse<BookingResult>> {
    const response = await apiClient.post<ApiResponse<BookingResult>>(`${ENDPOINT}/${id}/convert`, payload);
    return response.data;
  },

  async close(id: string, payload: CloseQuotationPayload): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/close`, payload);
    return response.data;
  },

  async expireOverdue(payload: ExpireOverduePayload): Promise<ApiResponse<ExpireOverdueResult>> {
    const response = await apiClient.post<ApiResponse<ExpireOverdueResult>>(`${ENDPOINT}/expire-overdue`, payload);
    return response.data;
  },

  // Public Endpoints (Customer Portal)
  async publicGetById(id: string, token: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.get<ApiResponse<Quotation>>(`/public/quotations/${id}?token=${token}`);
    return response.data;
  },

  /**
   * The customer accepts, in one call.
   *
   * Replaces `request-otp` + `confirm-otp`. Acceptance no longer routes through a code emailed to
   * the customer: the link's token is the credential, and Report 1 requires no such verification
   * anywhere in the quotation workflow. Rejection never had one, so the two are now symmetrical.
   */
  async publicAccept(id: string, token: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(
      `/public/quotations/${id}/accept?token=${token}`,
    );
    return response.data;
  },

  async publicReject(id: string, token: string, reason: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`/public/quotations/${id}/reject?token=${token}`, { reason });
    return response.data;
  },

  async reservationApprove(id: string): Promise<ApiResponse<BookingResult>> {
    const response = await apiClient.post<ApiResponse<BookingResult>>(`${ENDPOINT}/${id}/reservation-approve`);
    return response.data;
  },

  async reservationReject(id: string, payload: { reason: string; note: string }): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`${ENDPOINT}/${id}/reservation-reject`, payload);
    return response.data;
  },
};