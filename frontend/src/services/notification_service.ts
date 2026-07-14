import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type NotificationType =
  | "LEAD_ASSIGNED"
  | "QUOTATION_APPROVAL"
  | "QUOTATION_SENT"
  | "CUSTOMER_RESPONSE"
  | "BOOKING_UPDATE"
  | "SLA_WARNING"
  | "SLA_BREACH"
  | "TASK_OVERDUE"
  | "REMINDER"
  | "REMINDER_ESCALATED"
  | "REMINDER_OVERDUE"
  | "HANDOVER"
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

export type NotificationListParams = {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
};

const ENDPOINT = "/notifications";

export const notificationService = {
  async getList(params: NotificationListParams = {}): Promise<ApiResponse<PageResponse<Notification>>> {
    const { unreadOnly = false, page = 0, size = 20 } = params;
    const response = await apiClient.get<ApiResponse<PageResponse<Notification>>>(ENDPOINT, {
      params: { unreadOnly, page, size },
    });
    return response.data;
  },

  async getUnreadCount(): Promise<ApiResponse<{ count: number }>> {
    const response = await apiClient.get<ApiResponse<{ count: number }>>(`${ENDPOINT}/unread-count`);
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

  async markAllRead(): Promise<ApiResponse<{ markedCount: number }>> {
    const response = await apiClient.patch<ApiResponse<{ markedCount: number }>>(
      `${ENDPOINT}/mark-all-read`,
      null,
    );
    return response.data;
  },
};
