"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  interactionTimelineService,
  type InteractionTimelineQuery,
  type CreateInteractionTimelinePayload,
  type UpdateInteractionTimelinePayload,
} from "@/services/interaction_timeline_service";

const QUERY_KEY = "interactions";

export function useInteractions(params?: InteractionTimelineQuery) {
  return useQuery({
    queryKey: [QUERY_KEY, params],
    queryFn: () => interactionTimelineService.getList(params),
    staleTime: 30_000,
  });
}

export function useInteractionDetail(id: string | undefined) {
  return useQuery({
    queryKey: [QUERY_KEY, id],
    queryFn: () => interactionTimelineService.getById(id!),
    enabled: !!id,
  });
}

export function useInteractionAuditLogs(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: [QUERY_KEY, id, "audit-logs"],
    queryFn: () => interactionTimelineService.getAuditLogs(id!),
    enabled: !!id && enabled,
    staleTime: 60_000,
  });
}

export function useCreateInteraction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateInteractionTimelinePayload) =>
      interactionTimelineService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}

export function useUpdateInteraction(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateInteractionTimelinePayload) =>
      interactionTimelineService.update(id, payload),
    onSuccess: (updated) => {
      if (updated?.data) {
        queryClient.setQueryData([QUERY_KEY, id], updated);
      }
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY, id, "audit-logs"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}
