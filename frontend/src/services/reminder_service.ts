import { apiClient, type ApiResponse } from "@/services/api_client";

export type ReminderStatus = "PENDING" | "DONE" | "OVERDUE" | "CANCELLED";
export type ReminderPriority = "HIGH" | "MEDIUM" | "LOW";

export type Reminder = {
  reminderId: string;
  title: string;
  description?: string;
  remindAt: string;
  priority: ReminderPriority;
  status: ReminderStatus;
  relatedEntity: string;
  relatedId: string;
  assignedUserId?: string;
  assignedUserName?: string;
  createdByUserId?: string;
  createdByName?: string;
  createdAt: string;
};

export type CreateReminderPayload = {
  title: string;
  description?: string;
  remindAt: string;          // ISO 8601 with offset, e.g. "2026-06-25T10:00:00+07:00"
  priority?: ReminderPriority;
  relatedEntity: string;     // QUOTATION | LEAD | BOOKING | DEPOSIT
  relatedId: string;
  assignedUserId?: string;   // optional — defaults to creator (resolved server-side from JWT)
};

export type UpdateReminderPayload = {
  title?: string;
  description?: string;
  remindAt?: string;
  priority?: ReminderPriority;
  markAsDone?: boolean;
  forceIfDone?: boolean;
};

const ENDPOINT = "/reminders";

export const reminderService = {
  async getList(params?: { userId?: string; status?: string }): Promise<ApiResponse<Reminder[]>> {
    const response = await apiClient.get<ApiResponse<Reminder[]>>(ENDPOINT, { params });
    return response.data;
  },

  async create(payload: CreateReminderPayload): Promise<ApiResponse<Reminder>> {
    const response = await apiClient.post<ApiResponse<Reminder>>(ENDPOINT, payload);
    return response.data;
  },

  async dismiss(reminderId: string): Promise<ApiResponse<null>> {
    const response = await apiClient.patch<ApiResponse<null>>(`${ENDPOINT}/${reminderId}/dismiss`);
    return response.data;
  },

  async update(reminderId: string, payload: UpdateReminderPayload): Promise<ApiResponse<Reminder>> {
    const response = await apiClient.put<ApiResponse<Reminder>>(`${ENDPOINT}/${reminderId}`, payload);
    return response.data;
  },

  async escalate(reminderId: string): Promise<ApiResponse<null>> {
    const response = await apiClient.post<ApiResponse<null>>(`${ENDPOINT}/${reminderId}/escalate`);
    return response.data;
  },
};
