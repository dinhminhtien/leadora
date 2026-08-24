"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  activityLogService,
  type ActivityLogListParams,
} from "@/services/activity_log_service";
import {
  systemLogService,
  type SystemLogParams,
} from "@/services/system_log_service";

const QUERY_KEY = "activity-logs";

export function useActivityLogs(params?: ActivityLogListParams) {
  return useQuery({
    queryKey: [QUERY_KEY, params],
    queryFn: () => activityLogService.getList(params),
    staleTime: 30_000,
  });
}

export function useActivityLogDetail(id: string | undefined) {
  return useQuery({
    queryKey: [QUERY_KEY, id],
    queryFn: () => activityLogService.getById(id!),
    enabled: !!id,
    staleTime: 60_000,
  });
}

export function useSystemLogs(params?: SystemLogParams, enabled = true) {
  return useQuery({
    queryKey: ["system-logs", params],
    queryFn: () => systemLogService.getList(params),
    enabled,
    staleTime: 30_000,
  });
}

export function useClearSystemLogs() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => systemLogService.clear(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["system-logs"] });
    },
  });
}
