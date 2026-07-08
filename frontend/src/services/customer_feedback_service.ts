import axios from "axios";
import { apiClient, type ApiResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type ReviewStatus = "PENDING" | "REVIEWED" | "DISMISSED";

export interface CustomerFeedback {
  feedbackId: string;
  customerName: string;
  bookingCode: string;
  salesStaffName: string;
  rating: number;
  comment: string;
  reviewStatus: ReviewStatus;
  submittedAt?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface FeedbackTokenValidation {
  valid: boolean;
  bookingCode?: string;
  customerName?: string;
  hotelName?: string;
  checkOutDate?: string;
  salesStaffName?: string;
  salesStaffAvatar?: string;
  expiresAt?: string;
}

export interface SubmitFeedbackPayload {
  rating: number;
  comment: string;
  recommendScore?: number;
}

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8085/api/v1";

// Dedicated public HTTP client to bypass OAuth / 401 login redirects
const publicApiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json",
  },
});

const ENDPOINT = "/feedback";

export const customerFeedbackService = {
  async getList(params?: ListQuery & { reviewStatus?: string; rating?: number; search?: string }) {
    const response = await apiClient.get<ApiResponse<{ content: CustomerFeedback[]; totalElements: number; totalPages: number }>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<CustomerFeedback>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async validateToken(token: string) {
    const response = await publicApiClient.get<ApiResponse<FeedbackTokenValidation>>(
      `${ENDPOINT}/public/${token}/validate`,
    );
    return response.data;
  },

  async submitByToken(token: string, payload: SubmitFeedbackPayload) {
    const response = await publicApiClient.post<ApiResponse<{ success: boolean; message: string }>>(
      `${ENDPOINT}/public/${token}`,
      payload,
    );
    return response.data;
  },

  async updateReviewStatus(id: string, reviewStatus: ReviewStatus) {
    const response = await apiClient.patch<ApiResponse<void>>(
      `${ENDPOINT}/${id}/review-status`,
      { reviewStatus },
    );
    return response.data;
  },
};

