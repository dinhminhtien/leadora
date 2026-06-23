import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";
import type { ListQuery } from "@/shared/types/api";

export type ReservationStatus = {
  id: string;
  guestName: string;
  reservationNo: string;
  roomType: string;
  checkInDate: string;
  checkOutDate: string;
  totalAmount: number;
  status: string;
  specialRequests?: string;
  rejectionReason?: string;
  createdAt?: string;
  updatedAt?: string;
  details?: Array<{
    bookingDetailId: string;
    productId: string;
    productName: string;
    roomNumber: string;
    quantity: number;
    unitPrice: number;
    nights: number;
    lineTotal: number;
    inventoryStatus: string;
  }>;
};

const ENDPOINT = "/reservation-status";

export const reservationStatusService = {
  async getReservations(params?: ListQuery & { status?: string; sortBy?: string; sortDir?: string }) {
    const response = await apiClient.get<ApiResponse<PageResponse<ReservationStatus>>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getReservationDetail(id: string) {
    const response = await apiClient.get<ApiResponse<ReservationStatus>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async updateReservationStatus(id: string, status: string, reason?: string) {
    const response = await apiClient.put<ApiResponse<ReservationStatus>>(
      `${ENDPOINT}/${id}/status`,
      { status, reason },
    );
    return response.data;
  },

  async cancelReservation(id: string, reason: string) {
    const response = await apiClient.put<ApiResponse<ReservationStatus>>(
      `${ENDPOINT}/${id}/cancel`,
      { reason },
    );
    return response.data;
  },
};
