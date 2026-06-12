"use client";

import { useQuery } from "@tanstack/react-query";

import { followUpTaskService } from "@/services/follow_up_task_service";
import type { ListQuery } from "@/shared/types/api";

export function useFollowUpTasks(params?: ListQuery) {
  return useQuery({
    queryKey: ["follow-up-tasks", params],
    queryFn: () => followUpTaskService.getList(params),
  });
}
