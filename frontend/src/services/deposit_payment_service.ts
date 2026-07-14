import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED" | "EXPIRED";
export type PaymentType = "DEPOSIT" | "FULL_PAYMENT";

export type Payment = {
  paymentId: string;
  bookingId: string;
  bookingCode: string;
  customerId?: string;
  customerName?: string;
  createdById?: string;
  createdByName?: string;
  paymentMethod?: string;
  gatewayProvider?: string;
  gatewayTransactionId?: string;
  amount: number;
  paymentType: PaymentType;
  status: PaymentStatus;
  dueDate?: string;
  paidAt?: string;
  qrCodeUrl?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
};

export type GeneratePaymentPayload = {
  bookingId: string;
  amount: number;
  paymentType: PaymentType;
  paymentMethod?: string;
  notes?: string;
  dueDate?: string;
};

export type UpdatePaymentStatusPayload = {
  status: PaymentStatus;
  verificationNote?: string;
};

export type PaymentQuery = {
  search?: string;
  status?: string;
  paymentType?: string;
  sortBy?: string;
  sortDir?: string;
  page?: number;
  size?: number;
};

const ENDPOINT = "/payments";

export const depositPaymentService = {
  async getList(params?: PaymentQuery) {
    const response = await apiClient.get<ApiResponse<PageResponse<Payment>>>(
      ENDPOINT,
      { params }
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Payment>>(
      `${ENDPOINT}/${id}`
    );
    return response.data;
  },

  async generate(payload: GeneratePaymentPayload) {
    const response = await apiClient.post<ApiResponse<Payment>>(
      ENDPOINT,
      payload
    );
    return response.data;
  },

  async updateStatus(id: string, payload: UpdatePaymentStatusPayload) {
    const response = await apiClient.patch<ApiResponse<Payment>>(
      `${ENDPOINT}/${id}/status`,
      payload
    );
    return response.data;
  },

  async cancel(id: string) {
    const response = await apiClient.patch<ApiResponse<Payment>>(
      `${ENDPOINT}/${id}/cancel`
    );
    return response.data;
  }
};
