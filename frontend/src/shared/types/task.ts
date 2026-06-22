import type { TaskStatus } from "./status";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type Task = {
  taskId: string;
  title: string;
  description?: string;
  priority: TaskPriority;
  status: TaskStatus;
  resultNote?: string;

  assignedUserId?: string;
  assignedUserName?: string;

  createdById?: string;
  createdByName?: string;

  leadId?: string;
  leadName?: string;

  customerId?: string;
  customerName?: string;

  dealId?: string;
  dealName?: string;

  startAt?: string;
  endAt?: string;

  primaryContactName?: string;
  primaryContactPhone?: string;

  isOverdue?: boolean;

  createdAt?: string;
  updatedAt?: string;
};

export type CreateTaskPayload = {
  title: string;
  description?: string;
  priority: TaskPriority;
  assignedUserId: string;
  customerId?: string;
  leadId?: string;
  dealId?: string;
  startAt?: string;
  endAt?: string;
  primaryContactName?: string;
  primaryContactPhone?: string;
  resultNote?: string;
};

export type UpdateTaskPayload = {
  title?: string;
  description?: string;
  priority?: TaskPriority;
  status?: TaskStatus;
  assignedUserId?: string;
  customerId?: string;
  leadId?: string;
  dealId?: string;
  startAt?: string;
  endAt?: string;
  primaryContactName?: string;
  primaryContactPhone?: string;
  resultNote?: string;
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

export type TaskFilter = {
  search?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assignedUserId?: string;
  overdue?: boolean;
  page?: number;
  size?: number;
};

export type TaskListResponse = {
  content: Task[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
};
