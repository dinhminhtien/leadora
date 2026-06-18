import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type BookingDetail = {
  bookingDetailId: string;
  productId: string;
  productName: string;
  roomNumber?: string;
  quantity: number;
  unitPrice: number;
  nights: number;
  lineTotal: number;
  inventoryStatus: "ALLOCATED" | "AVAILABLE" | "RELEASED";
};

export type Booking = {
  bookingId: string;
  quotationId: string;
  customerId: string;
  customerName: string;
  assignedUserId?: string;
  assignedUserName?: string;
  bookingCode: string;
  checkInDate: string;
  checkOutDate: string;
  status: "PENDING" | "CONFIRMED" | "CHECKED_IN" | "CHECKED_OUT" | "CANCELLED" | "NO_SHOW" | "REJECTED";
  specialRequests?: string;
  rejectionReason?: string;
  totalAmount: number;
  details?: BookingDetail[];
  createdAt: string;
  updatedAt: string;
};

export type CreateBookingPayload = {
  quotationId: string;
  customerId: string;
  assignedUserId?: string;
  checkInDate: string;
  checkOutDate: string;
  specialRequests?: string;
  details: {
    productId: string;
    roomNumber?: string;
    quantity: number;
    unitPrice: number;
    nights: number;
  }[];
};

export type RoomAvailability = {
  productId: string;
  name: string;
  category: "ROOM" | "EVENT_SPACE" | "SERVICE";
  unitPrice: number;
  unit?: string;
  totalBooked: number;
  isAvailable: boolean;
};

const ENDPOINT = "/bookings";

export const bookingConfirmationService = {
  async getList(params?: {
    search?: string;
    status?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }) {
    const response = await apiClient.get<ApiResponse<PageResponse<Booking>>>(
      ENDPOINT,
      { params },
    );
    return response.data;
  },

  async getById(id: string) {
    const response = await apiClient.get<ApiResponse<Booking>>(
      `${ENDPOINT}/${id}`,
    );
    return response.data;
  },

  async submitRequest(payload: CreateBookingPayload) {
    const response = await apiClient.post<ApiResponse<Booking>>(
      ENDPOINT,
      payload,
    );
    return response.data;
  },

  async processRequest(id: string, payload: { status: "CONFIRMED" | "REJECTED"; rejectionReason?: string }) {
    const response = await apiClient.put<ApiResponse<Booking>>(
      `${ENDPOINT}/${id}/process`,
      payload,
    );
    return response.data;
  },

  async checkAvailability(params: { checkInDate: string; checkOutDate: string; productId?: string }) {
    const response = await apiClient.get<ApiResponse<RoomAvailability[]>>(
      `${ENDPOINT}/check-availability`,
      { params },
    );
    return response.data;
  },
};
