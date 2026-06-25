"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  leadService,
  type CreateLeadPayload,
  type UpdateLeadPayload,
  type ConvertLeadPayload,
  type LeadListParams,
} from "@/services/lead_service";

export function useLeads(params?: LeadListParams) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}

export function useLeadDetail(leadId: string | undefined) {
  return useQuery({
    queryKey: ["leads", leadId],
    queryFn: () => leadService.getById(leadId!),
    enabled: !!leadId,
    // Never retry client errors (403 forbidden, 404 not found, malformed id) — retrying
    // them just delays the error state and looks like a stuck loading spinner.
    retry: (failureCount, error: any) => {
      const status = error?.response?.status;
      if (status && status >= 400 && status < 500) return false;
      return failureCount < 1;
    },
  });
}

export function useCreateLead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateLeadPayload) => leadService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
    },
  });
}

export function useUpdateLead(leadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateLeadPayload) => leadService.update(leadId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", leadId] });
    },
  });
}

export function useConvertLead(leadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ConvertLeadPayload) => leadService.convert(leadId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", leadId] });
    },
  });
}
