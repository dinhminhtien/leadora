import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type DepositPayment = Record<string, unknown> & {
  id: string;
  amount?: number;
  status?: string;
};

export type DepositPaymentPayload = Record<string, unknown>;

const ENDPOINT = "/deposit-payments";

export const depositPaymentService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<DepositPayment[]>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<DepositPayment>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async create(payload: DepositPaymentPayload) {
    const response = await apiClient.post<ApiResponse<DepositPayment>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async update(id: string, payload: DepositPaymentPayload) {
    const response = await apiClient.put<ApiResponse<DepositPayment>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },
};
