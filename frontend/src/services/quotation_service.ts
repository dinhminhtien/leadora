import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Quotation = {
  id: string;
  quoteNo: string;
  contactName: string;
  dealName: string;
  amount: number;
  expiryDate: string;
  status: "draft" | "sent" | "accepted" | "expired" | "pending_approval" | "pending_revision" | "interested" | "converted" | "closed" | "rejected" | "approved";
  email?: string;
  phone?: string;
  roomType?: string;
  numberOfRooms?: number;
  checkInDate?: string;
  checkOutDate?: string;
  paymentPolicy?: string;
  pricePerNight?: number;
  subtotal?: number;
  discountPercent?: number;
  discountAmount?: number;
  notes?: string;
  version?: number;
};

export type QuotationPayload = Record<string, unknown>;

const ENDPOINT = "/quotations";

export const quotationService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Quotation[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Quotation>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: QuotationPayload) {
    const response = await apiClient.post<ApiResponse<Quotation>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: QuotationPayload) {
    const response = await apiClient.put<ApiResponse<Quotation>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
