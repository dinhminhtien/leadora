"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  taskService,
  userService,
  type Task,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type ResignTaskPayload,
  type TaskListParams,
} from "@/services/follow_up_task_service";

const QUERY_KEY = "tasks";

/** Patch a task inside any cached page/list or single-task response. */
function patchTaskInCache(old: unknown, taskId: string, patch: Partial<Task>): unknown {
  if (!old || typeof old !== "object") return old;
  const data = (old as { data?: unknown }).data;
  if (!data) return old;

  // PageResponse — list query
  if (typeof data === "object" && data !== null && "content" in data) {
    const page = data as { content: Task[] };
    return {
      ...old,
      data: {
        ...page,
        content: page.content.map((t: Task) =>
          t.taskId === taskId ? { ...t, ...patch } : t
        ),
      },
    };
  }

  // Single task detail query
  if (typeof data === "object" && data !== null && "taskId" in data) {
    const t = data as Task;
    if (t.taskId === taskId) return { ...old, data: { ...t, ...patch } };
  }

  return old;
}

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

export function useResignTask(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ResignTaskPayload) => taskService.resign(taskId, payload),
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

export function useResolveTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => taskService.resolve(taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: ["sla-monitoring"] });
    },
  });
}
