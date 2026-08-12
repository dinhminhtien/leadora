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
  roomType: string;
  numberOfRooms: number;
  pricePerNight: number;
};

export type RoomLineDetail = RoomLine & {
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

  async publicRequestOtp(id: string, token: string): Promise<ApiResponse<void>> {
    const response = await apiClient.post<ApiResponse<void>>(`/public/quotations/${id}/request-otp?token=${token}`);
    return response.data;
  },

  async publicConfirmOtp(id: string, token: string, otpCode: string): Promise<ApiResponse<Quotation>> {
    const response = await apiClient.post<ApiResponse<Quotation>>(`/public/quotations/${id}/confirm-otp?token=${token}`, { otpCode });
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