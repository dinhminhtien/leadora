import { apiClient, type ApiResponse, type PageResponse } from "@/services/api_client";

export type ActorType = "USER" | "SYSTEM";
export type RecordOperation = "NORMAL" | "CORRECTED" | "VOIDED";

export type ActorDto = {
  type: ActorType;
  id: string | null;
  fullName: string;
  role: string | null;
  email: string | null;
};

export type EntityDto = {
  type: string;
  id: string;
};

export type ActivityLog = {
  id: string;
  actor: ActorDto;
  activityType: string;
  entity: EntityDto;
  summary: string;
  reason: string | null;
  correlationId: string;
  recordOperation: RecordOperation;
  refActivityId: string | null;
  createdAt: string;
  payload?: any;
};

export type ActivityLogListParams = {
  keyword?: string;
  actorType?: ActorType;
  actorUserId?: string;
  actorRoleSnapshot?: string;
  activityType?: string;
  entityType?: string;
  entityId?: string;
  startDate?: string;
  endDate?: string;
  view?: "RAW" | "EFFECTIVE";
  page?: number;
  size?: number;
  category?: "BUSINESS" | "SECURITY";
};

const ENDPOINT = "/activity-logs";

export const activityLogService = {
  async getList(params?: ActivityLogListParams): Promise<ApiResponse<PageResponse<ActivityLog>>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<ActivityLog>>>(ENDPOINT, { params });
    return data;
  },

  async getById(id: string): Promise<ApiResponse<ActivityLog>> {
    const { data } = await apiClient.get<ApiResponse<ActivityLog>>(`${ENDPOINT}/${id}`);
    return data;
  },
};
