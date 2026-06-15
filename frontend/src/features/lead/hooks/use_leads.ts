"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

import { leadService, type LeadPayload } from "@/services/lead_service";
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

