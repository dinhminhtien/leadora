import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

// ── Types ─────────────────────────────────────────────────────────────────────

/**
 * TaskStatus - Simplified to 3 core business statuses.
 * OVERDUE is NOT a status; it is calculated dynamically:
 * - isOverdue = true when status == OPEN && now > endAt
 */
export type TaskStatus = "OPEN" | "COMPLETED" | "CANCELLED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

/**
 * What kind of work a task is — mirrors the backend `ActivityType` enum.
 *
 * This was once inferred by parsing the title ("Call: …"). It is a real column
 * now (`tasks.activity_type`); the title is just a title.
 */
export type ActivityType =
  | "TASK"
  | "CALL"
  | "EMAIL"
  | "MEETING"
  | "SITE_VISIT"
  | "FOLLOW_UP";

export type Task = {
  taskId: string;
  title: string;
  description: string | null;
  /**
   * The backend always sends this, defaulting to TASK for rows written before the
   * activity-type backfill. Typed optional anyway so a response from an older
   * backend can't produce `undefined` at a call site that assumed otherwise.
   */
  activityType?: ActivityType | null;
  priority: TaskPriority;
  status: TaskStatus;
  /** ISO datetime with timezone offset, e.g. "2026-06-24T09:00:00+07:00" */
  startAt: string | null;
  /** ISO datetime with timezone offset */
  endAt: string | null;
  resultNote: string | null;
  assignedUserId: string | null;
  assignedUserName: string | null;
  createdById: string | null;
  createdByName: string | null;
  leadId: string | null;
  leadName: string | null;
  leadPhone: string | null;
  leadEmail: string | null;
  leadCompanyName: string | null;
  customerId: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  customerCompanyName: string | null;
  dealId: string | null;
  dealName: string | null;
  primaryContactName: string | null;
  primaryContactPhone: string | null;
  isOverdue: boolean | null;
  createdAt: string;
  updatedAt: string;
  // Extended linked-record context — only present on the detail response
  // (GET /tasks/{id}); omitted on list rows.
  dealStage?: string | null;
  dealValue?: number | null;
  dealCustomerName?: string | null;
  dealOwnerName?: string | null;
  leadStatus?: string | null;
  leadSource?: string | null;
  leadOwnerName?: string | null;
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
  /** Required by the backend — a new task must say what kind of work it is. */
  activityType: ActivityType;
  resultNote?: string;
  leadId?: string;
  customerId?: string;
  dealId?: string;
  primaryContactName?: string;
  primaryContactPhone?: string;
  startAt?: string;
  endAt?: string;
};

export type UpdateTaskPayload = {
  title?: string;
  description?: string;
  assignedUserId?: string;
  priority?: TaskPriority;
  status?: TaskStatus;
  activityType?: ActivityType;
  resultNote?: string;
  leadId?: string;
  customerId?: string;
  dealId?: string;
  primaryContactName?: string;
  primaryContactPhone?: string;
  startAt?: string;
  endAt?: string;
};

export type ResignTaskPayload = {
  title?: string;
  description?: string;
  priority?: TaskPriority;
  assignedUserId?: string;
  startAt?: string;
  endAt?: string;
  resignNote?: string;
};

export type TaskListParams = {
  search?: string;
  status?: string;
  priority?: string;
  assignedUserId?: string;
  customerId?: string;
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

  async resign(id: string, payload: ResignTaskPayload): Promise<ApiResponse<Task>> {
    const { data } = await apiClient.post<ApiResponse<Task>>(`${ENDPOINT}/${id}/resign`, payload);
    return data;
  },

  async resolve(taskId: string): Promise<ApiResponse<Task>> {
    const { data } = await apiClient.patch<ApiResponse<Task>>(`${ENDPOINT}/${taskId}/resolve`);
    return data;
  },
};

export const userService = {
  async getAll(): Promise<ApiResponse<UserSummary[]>> {
    const { data } = await apiClient.get<ApiResponse<UserSummary[]>>("/users");
    return data;
  },
};
