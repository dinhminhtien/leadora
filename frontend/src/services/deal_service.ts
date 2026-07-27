import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export interface Deal {
  id: string;
  title: string;
  contactName: string;
  email?: string;
  phone?: string;
  value: number;
  probability: number;
  stage: "Inquiry" | "Qualification" | "Proposal" | "Negotiation" | "Contract" | "Confirmed";
  owner: string;
  ownerEmail?: string;
  status: "active" | "won" | "lost";
  expectedClose: string;
  createdAt?: string;
  notes?: string;
}

export type DealPayload = Record<string, unknown>;

export interface PipelineDealCardResponse {
  deal: Deal;
  hasActiveQuotation: boolean;
  activeQuotationStatus: string | null;
  hasActiveBooking: boolean;
  activeBookingStatus: string | null;
  paymentStatus: string | null;
}

export interface DealWorkflowSummaryResponse {
  dealId: string;
  dealStatus: string;
  pipelineStage: string;
  activeQuotationId: string | null;
  activeQuotationStatus: string | null;
  activeBookingId: string | null;
  activeBookingStatus: string | null;
  currentPaymentStatus: string | null;
  hasPaidPayment: boolean;
}

const ENDPOINT = "/deals";

export const dealService = {
  async getList(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<Deal[]>>(ENDPOINT, {
      params,
    });
    return response.data;
  },

  async getPipeline(params?: ListQuery) {
    const response = await apiClient.get<ApiResponse<PipelineDealCardResponse[]>>(`${ENDPOINT}/pipeline`, {
      params,
    });
    return response.data;
  },

  async getWorkflowSummary(id: string) {
    const response = await apiClient.get<ApiResponse<DealWorkflowSummaryResponse>>(`${ENDPOINT}/${id}/workflow`);
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Deal>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  async create(payload: DealPayload) {
    const response = await apiClient.post<ApiResponse<Deal>>(ENDPOINT, payload);
    return response.data;
  },

  async update(id: string, payload: DealPayload) {
    const response = await apiClient.put<ApiResponse<Deal>>(
      `${ENDPOINT}/${id}`,
      payload,
    );
    return response.data;
  },

  async syncPipeline(id: string) {
    const response = await apiClient.post<ApiResponse<Deal>>(
      `${ENDPOINT}/${id}/sync-pipeline`
    );
    return response.data;
  },
};


