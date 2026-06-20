import { apiClient, type ApiResponse } from "@/services/api_client";

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
  | "pending_revision";

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
  roomType?: string;
  numberOfRooms?: number;
  checkInDate?: string;
  checkOutDate?: string;
  nights?: number;
  pricePerNight?: number;
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
};

export type CloseQuotationPayload = {
  reason: string;
  notes?: string;
  closedByName?: string;
  closedByRole?: string;
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
  recordedByName?: string;
  recordedByRole?: string;
};

export type ReviseQuotationPayload = {
  roomType: string;
  checkInDate: string;
  checkOutDate: string;
  numberOfRooms: number;
  pricePerNight: number;
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
  roomType: string;
  checkInDate: string;
  checkOutDate: string;
  numberOfRooms: number;
  pricePerNight: number;
  discountPercent: number;
  paymentPolicy: string;
  validUntil: string;
  notes?: string;
};

export type ProcessApprovalPayload = {
  action: "APPROVE" | "REJECT" | "REQUEST_CHANGES";
  managerName: string;
  managerRole?: string;
  notes?: string;
};

export type SendQuotationPayload = {
  sendMethod: "EMAIL" | "WHATSAPP" | "PDF";
  recipientName: string;
  recipientEmail?: string;
  recipientPhone?: string;
  sentByName?: string;
  sentByRole?: string;
  personalMessage?: string;
};

const ENDPOINT = "/quotations";

export const quotationService = {
  async getList(): Promise<ApiResponse<Quotation[]>> {
    const response = await apiClient.get<ApiResponse<Quotation[]>>(ENDPOINT);
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
};