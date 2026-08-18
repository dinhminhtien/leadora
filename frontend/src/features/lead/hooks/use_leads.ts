"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  leadService,
  type CreateLeadPayload,
  type UpdateLeadPayload,
  type ConvertLeadPayload,
  type LinkLeadToCustomerPayload,
  type ReopenLeadPayload,
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
    onSuccess: (updated) => {
      // The PUT answers with the saved record, so the detail cache can be written straight from
      // it — the open drawer repaints in the same tick instead of after a round trip. This used
      // to only invalidate, which left whoever was reading the lead looking at pre-save values
      // until the refetch landed (and, for a surface holding its own copy of the row, forever).
      queryClient.setQueryData(["leads", leadId], updated);
      // Everything else under the prefix — the paged list and the stats tiles — is now stale and
      // has to come from the server: a status or assignee change can move the lead between pages
      // and re-count the tiles. The detail key is excluded because we just wrote the authoritative
      // response into it; re-fetching it would be a request for data we already hold.
      queryClient.invalidateQueries({
        queryKey: ["leads"],
        predicate: (query) => query.queryKey[1] !== leadId,
      });
    },
  });
}

/**
 * UC-8.4 — reopen a lost lead. Manager-only server-side; the button is hidden for everyone else.
 *
 * Same cache handling as {@link useUpdateLead}: the response is the saved lead, so the detail key
 * is written from it and left out of the invalidation. The list and the tiles do have to refetch —
 * the lead just moved out of "Lost" and back into "Active", which changes both counts.
 */
export function useReopenLead(leadId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReopenLeadPayload) => leadService.reopen(leadId, payload),
    onSuccess: (reopened) => {
      queryClient.setQueryData(["leads", leadId], reopened);
      queryClient.invalidateQueries({
        queryKey: ["leads"],
        predicate: (query) => query.queryKey[1] !== leadId,
      });
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
