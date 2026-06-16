"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  taskService,
  userService,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type TaskListParams,
} from "@/services/follow_up_task_service";

const QUERY_KEY = "tasks";

export function useTasks(params?: TaskListParams) {
  return useQuery({
    queryKey: [QUERY_KEY, params],
    queryFn: () => taskService.getList(params),
  });
}

export function useTaskDetail(taskId: string | undefined) {
  return useQuery({
    queryKey: [QUERY_KEY, taskId],
    queryFn: () => taskService.getById(taskId!),
    enabled: !!taskId,
  });
}

export function useCreateTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTaskPayload) => taskService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useUpdateTask(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateTaskPayload) => taskService.update(taskId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY, taskId] });
    },
  });
}

export function useUsers() {
  return useQuery({
    queryKey: ["users"],
    queryFn: () => userService.getAll(),
    staleTime: 5 * 60 * 1000,
  });
}
