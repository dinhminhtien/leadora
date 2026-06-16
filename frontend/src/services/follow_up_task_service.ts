import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// ── Types ─────────────────────────────────────────────────────────────────────

export type TaskStatus = "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export type Task = {
  taskId: string;
  title: string;
  description: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  dueDate: string | null;
  resultNote: string | null;
  assignedUserId: string | null;
  assignedUserName: string | null;
  createdById: string | null;
  createdByName: string | null;
  leadId: string | null;
  leadName: string | null;
  customerId: string | null;
  customerName: string | null;
  dealId: string | null;
  dealName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UserSummary = {
  userId: string;
  fullName: string;
  email: string;
  roleName: string | null;
};

export type CreateTaskPayload = {
  title: string;
  description?: string;
  assignedUserId: string;
  priority: TaskPriority;
  dueDate: string;
  resultNote?: string;
  leadId?: string;
  customerId?: string;
  dealId?: string;
};

export type UpdateTaskPayload = {
  title?: string;
  description?: string;
  assignedUserId?: string;
  priority?: TaskPriority;
  dueDate?: string;
  status?: TaskStatus;
  resultNote?: string;
  leadId?: string;
  customerId?: string;
  dealId?: string;
};

export type TaskListParams = {
  search?: string;
  status?: string;
  priority?: string;
  assignedUserId?: string;
  overdue?: boolean;
  page?: number;
  size?: number;
};

// ── Service ───────────────────────────────────────────────────────────────────

const ENDPOINT = "/tasks";

export const taskService = {
  async getList(params?: TaskListParams): Promise<ApiResponse<PageResponse<Task>>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<Task>>>(ENDPOINT, { params });
    return data;
  },

  async getById(id: string): Promise<ApiResponse<Task>> {
    const { data } = await apiClient.get<ApiResponse<Task>>(`${ENDPOINT}/${id}`);
    return data;
  },

  async create(payload: CreateTaskPayload): Promise<ApiResponse<Task>> {
    const { data } = await apiClient.post<ApiResponse<Task>>(ENDPOINT, payload);
    return data;
  },

  async update(id: string, payload: UpdateTaskPayload): Promise<ApiResponse<Task>> {
    const { data } = await apiClient.put<ApiResponse<Task>>(`${ENDPOINT}/${id}`, payload);
    return data;
  },
};

export const userService = {
  async getAll(): Promise<ApiResponse<UserSummary[]>> {
    const { data } = await apiClient.get<ApiResponse<UserSummary[]>>("/users");
    return data;
  },
};
