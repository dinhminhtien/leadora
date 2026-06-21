import { apiClient, type ApiResponse } from "@/services/api_client";

export type SlaActivityType =
  | "LEAD_RESPONSE"
  | "QUOTATION_SENT"
  | "FOLLOW_UP_TASK"
  | "BOOKING_CONFIRM";

export type SlaRule = {
  id: string;
  activityType: SlaActivityType;
  name: string;
  deadlineHours: number;
  warningThreshold: number;
  escalationThreshold: number;
  active: boolean;
};

export type SlaRulePayload = Omit<SlaRule, "id">;

export type SlaDisplayStatus = "WITHIN_SLA" | "WARNING" | "BREACHED";

export type SlaReportParams = {
  from?: string;
  to?: string;
  activityType?: string;
  entityType?: string;
};

export type SlaActivityBreakdown = {
  activityType: string;
  activityLabel: string;
  total: number;
  resolved: number;
  breached: number;
  warning: number;
  withinSla: number;
  breachRatePct: number;
  avgProcessingHours: number;
};

export type SlaReport = {
  fromDate: string;
  toDate: string;
  totalTracked: number;
  resolvedCount: number;
  breachedCount: number;
  warningCount: number;
  withinSlaCount: number;
  breachRatePct: number;
  complianceRatePct: number;
  resolutionRatePct: number;
  avgProcessingHours: number;
  byActivityType: SlaActivityBreakdown[];
};

export type SlaTracking = {
  trackingId: string;
  entityType: string;
  entityId: string;
  activityType: string;
  startedAt: string;
  deadlineAt: string;
  warningAt: string;
  displayStatus: SlaDisplayStatus;
  hoursRemaining: number;
};

const ENDPOINT = "/sla";

export const slaService = {
  async getList(): Promise<ApiResponse<SlaRule[]>> {
    const response = await apiClient.get<ApiResponse<SlaRule[]>>(ENDPOINT);
    return response.data;
  },

  async getById(id: string): Promise<ApiResponse<SlaRule>> {
    const response = await apiClient.get<ApiResponse<SlaRule>>(`${ENDPOINT}/${id}`);
    return response.data;
  },

  async create(payload: SlaRulePayload): Promise<ApiResponse<SlaRule>> {
    const response = await apiClient.post<ApiResponse<SlaRule>>(ENDPOINT, payload);
    return response.data;
  },

  async update(id: string, payload: SlaRulePayload): Promise<ApiResponse<SlaRule>> {
    const response = await apiClient.put<ApiResponse<SlaRule>>(`${ENDPOINT}/${id}`, payload);
    return response.data;
  },

  async getMonitoring(entityType?: string, displayStatus?: string): Promise<ApiResponse<SlaTracking[]>> {
    const response = await apiClient.get<ApiResponse<SlaTracking[]>>(`${ENDPOINT}/monitoring`, {
      params: { entityType, displayStatus },
    });
    return response.data;
  },

  async resolve(trackingId: string): Promise<ApiResponse<null>> {
    const response = await apiClient.patch<ApiResponse<null>>(
      `${ENDPOINT}/tracking/${trackingId}/resolve`,
    );
    return response.data;
  },

  async getReport(params?: SlaReportParams): Promise<ApiResponse<SlaReport>> {
    const response = await apiClient.get<ApiResponse<SlaReport>>(`${ENDPOINT}/report`, { params });
    return response.data;
  },
};
