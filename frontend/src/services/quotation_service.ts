import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type Quotation = Record<string, unknown> & {
  id: string;
  code?: string;
  status?: string;
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
