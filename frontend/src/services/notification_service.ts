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

export type NotificationPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

export type Notification = {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  priority?: NotificationPriority;
  relatedEntity?: string;
  relatedId?: string;
  isRead: boolean;
  createdAt: string;
  // Populated only in the Manager/Admin aggregate feed (allUsers=true).
  recipientId?: string;
  recipientName?: string;
};

export type NotificationListParams = {
  unreadOnly?: boolean;
  /** Manager/Admin only — org-wide feed across every user. Ignored for other roles server-side. */
  allUsers?: boolean;
  type?: string;
  priority?: NotificationPriority;
  createdFrom?: string;
  createdTo?: string;
  /** "priority" (URGENT→LOW, ties newest-first) or omitted for newest-first. */
  sortBy?: string;
  page?: number;
  size?: number;
};

const ENDPOINT = "/notifications";

export const notificationService = {
  async getList(params: NotificationListParams = {}): Promise<ApiResponse<PageResponse<Notification>>> {
    const { unreadOnly = false, allUsers = false, type, priority, createdFrom, createdTo, sortBy, page = 0, size = 20 } = params;
    const response = await apiClient.get<ApiResponse<PageResponse<Notification>>>(ENDPOINT, {
      params: { unreadOnly, allUsers, type, priority, createdFrom, createdTo, sortBy, page, size },
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
