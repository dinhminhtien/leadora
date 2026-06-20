"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  taskService,
  userService,
  type Task,
  type TaskStatus,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type TaskListParams,
  type WorkflowAction,
} from "@/services/follow_up_task_service";

const QUERY_KEY = "tasks";

// Maps each workflow action to the status it produces — used for optimistic updates.
const ACTION_TO_STATUS: Record<WorkflowAction, TaskStatus> = {
  START:    "IN_PROGRESS",
  COMPLETE: "COMPLETED",
  CANCEL:   "CANCELLED",
  REOPEN:   "OPEN",
};

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

/** Sends a workflow action with optimistic UI update. */
export function useTransitionWorkflow(taskId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (action: WorkflowAction) => taskService.transition(taskId, action),

    onMutate: async (action) => {
      // Prevent in-flight refetches from overwriting our optimistic state
      await queryClient.cancelQueries({ queryKey: [QUERY_KEY] });

      // Snapshot every matching cache entry for rollback
      const snapshot = queryClient.getQueriesData({ queryKey: [QUERY_KEY] });

      const newStatus = ACTION_TO_STATUS[action];
      queryClient.setQueriesData({ queryKey: [QUERY_KEY] }, (old) =>
        patchTaskInCache(old, taskId, { status: newStatus })
      );

      return { snapshot };
    },

    onError: (_err, _action, context) => {
      // Roll back to the snapshot on failure
      context?.snapshot?.forEach(([key, data]) => {
        queryClient.setQueryData(key, data);
      });
    },

    onSettled: () => {
      // Always re-sync from server to ensure consistency
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
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
