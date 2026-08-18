import { apiClient, type ApiResponse } from "@/services/api_client";

export type SystemLogEntry = {
  timestamp: string;
  level: "ERROR" | "WARN" | "INFO" | "DEBUG";
  threadName: string;
  loggerName: string;
  message: string;
  exception: string | null;
  userId: string | null;
  userEmail: string | null;
  correlationId: string | null;
};

export type SystemLogParams = {
  level?: string;
  keyword?: string;
};

const ENDPOINT = "/system-logs";

export const systemLogService = {
  async getList(params?: SystemLogParams): Promise<ApiResponse<SystemLogEntry[]>> {
    const { data } = await apiClient.get<ApiResponse<SystemLogEntry[]>>(ENDPOINT, { params });
    return data;
  },

  async clear(): Promise<ApiResponse<void>> {
    const { data } = await apiClient.delete<ApiResponse<void>>(ENDPOINT);
    return data;
  },
};
