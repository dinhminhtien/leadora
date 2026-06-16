"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

import { leadService, type LeadPayload } from "@/services/lead_service";
import { interactionTimelineService } from "@/services/interaction_timeline_service";
import type { ListQuery } from "@/shared/types/api";

export function useLeads(params?: ListQuery) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}

export function useCreateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LeadPayload) => leadService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });
}

export function useLeadDetail(id: string) {
  return useQuery({
    queryKey: ["leads", id],
    queryFn: () => leadService.getById(id),
    enabled: !!id,
  });
}

export function useUpdateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: LeadPayload }) =>
      leadService.update(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", variables.id] });
    },
  });
}

export function useInteractions() {
  return useQuery({
    queryKey: ["interactions"],
    queryFn: () => interactionTimelineService.getList(),
  });
}

export function useCreateInteraction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Record<string, any>) =>
      interactionTimelineService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["interactions"] });
    },
  });
}

