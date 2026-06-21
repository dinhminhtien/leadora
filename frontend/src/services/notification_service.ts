import { apiClient, type ApiResponse } from "@/services/api_client";

export type NotificationType =
  | "LEAD_ASSIGNED"
  | "QUOTATION_APPROVAL"
  | "QUOTATION_SENT"
  | "CUSTOMER_RESPONSE"
  | "BOOKING_UPDATE"
  | "PAYMENT_REMINDER"
  | "SLA_WARNING"
  | "TASK_OVERDUE"
  | string;

export type Notification = {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  relatedEntity?: string;
  relatedId?: string;
  isRead: boolean;
  createdAt: string;
};

const ENDPOINT = "/notifications";

export const notificationService = {
  async getList(userId: string, unreadOnly = false): Promise<ApiResponse<Notification[]>> {
    const response = await apiClient.get<ApiResponse<Notification[]>>(ENDPOINT, {
      params: { userId, unreadOnly },
    });
    return response.data;
  },

  async getById(id: string): Promise<ApiResponse<Notification>> {
    const response = await apiClient.get<ApiResponse<Notification>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  async markRead(id: string, read = true): Promise<ApiResponse<Notification>> {
    const response = await apiClient.patch<ApiResponse<Notification>>(
      `${ENDPOINT}/${id}/read`,
      null,
      { params: { read } },
    );
    return response.data;
  },

  async markAllRead(userId: string): Promise<ApiResponse<{ markedCount: number }>> {
    const response = await apiClient.patch<ApiResponse<{ markedCount: number }>>(
      `${ENDPOINT}/mark-all-read`,
      null,
      { params: { userId } },
    );
    return response.data;
  },
};
