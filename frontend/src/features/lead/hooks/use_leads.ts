"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  leadService,
  type CreateLeadPayload,
  type UpdateLeadPayload,
  type ConvertLeadPayload,
  type LinkLeadToCustomerPayload,
  type LeadListParams,
  type LeadStatsParams,
} from "@/services/lead_service";

export function useLeads(params?: LeadListParams) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}

/**
 * Summary counts for the tiles above the list.
 *
 * Deliberately a separate query from {@link useLeads}: the list is paged, these are not, and
 * deriving them from the loaded page is what made them wrong. Kept under the same `["leads"]` key
 * prefix so every mutation that invalidates the list refreshes the tiles with it — otherwise
 * creating a lead would leave the totals a page-refresh behind.
 */
export function useLeadStats(params?: LeadStatsParams) {
  return useQuery({
    queryKey: ["leads", "stats", params],
    queryFn: () => leadService.getStats(params),
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
      // A customer profile now exists — the customer list and its counters are stale.
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      queryClient.invalidateQueries({ queryKey: ["customer-stats"] });
    },
  });
}

/**
 * UC-8.5 exception E6 — the conversion was refused because the person is already a customer, and
 * the user chose to attach the lead to that profile instead of creating a second one.
 */
export function useLinkLeadToCustomer(leadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LinkLeadToCustomerPayload) => leadService.linkCustomer(leadId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["leads"] });
      queryClient.invalidateQueries({ queryKey: ["leads", leadId] });
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      queryClient.invalidateQueries({ queryKey: ["customer-stats"] });
    },
  });
}
