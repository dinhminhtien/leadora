"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { quotationService, type CreateQuotationPayload, type SubmitQuotationPayload, type SendQuotationPayload, type ReviseQuotationPayload, type TrackCustomerResponsePayload, type ConvertToBookingPayload, type CloseQuotationPayload, type ExpireOverduePayload } from "@/services/quotation_service";
import { dealService } from "@/services/deal_service";

// Quotation list
export function useQuotations() {
  return useQuery({
    queryKey: ["quotations"],
    queryFn: () => quotationService.getList(),
    select: (res) => res.data ?? [],
  });
}

// Create quotation mutation
export function useCreateQuotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateQuotationPayload) => quotationService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Submit a DRAFT quotation — discount ≤10% → APPROVED, >10% → PENDING_APPROVAL (UC-14.1)
export function useSubmitQuotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SubmitQuotationPayload }) =>
      quotationService.submit(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Send quotation to customer (UC-14.4)
export function useSendQuotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SendQuotationPayload }) =>
      quotationService.send(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Manually close a quotation (UC-14.8)
export function useCloseQuotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CloseQuotationPayload }) =>
      quotationService.close(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Batch expire all overdue quotations (UC-14.8 auto-expire)
export function useExpireOverdue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ExpireOverduePayload) => quotationService.expireOverdue(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Convert accepted quotation to confirmed booking (UC-14.7)
export function useConvertToBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ConvertToBookingPayload }) =>
      quotationService.convert(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Track customer response — Accept / Reject / Interested / Need Revision (UC-14.6)
export function useTrackCustomerResponse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: TrackCustomerResponsePayload }) =>
      quotationService.trackResponse(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Get single quotation by ID (UC-14.5 pre-populate revision form)
export function useQuotationById(id: string) {
  return useQuery({
    queryKey: ["quotation", id],
    queryFn: () => quotationService.getById(id),
    select: (res) => res.data,
    enabled: !!id,
  });
}

// Revise quotation — creates a new version (UC-14.5)
export function useReviseQuotation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ReviseQuotationPayload }) =>
      quotationService.revise(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

// Deal option shape returned by GET /api/v1/deals
export type DealOption = {
  id: string;
  title: string;
  contactName: string;
  email: string;
  phone: string;
  status: string;
  expectedClose: string | null;
};

// Fetch deals for the quotation deal selector — a quotation can only be raised
// against a deal still open in the pipeline with an expected-close date that
// hasn't already passed. Closed (WON/LOST) or overdue deals are hidden here so
// the picker can't be pointed at a deal that no longer makes sense to quote.
export function useDealsForQuotation() {
  return useQuery({
    queryKey: ["deals-for-quotation"],
    queryFn: async () => {
      const res = await dealService.getList();
      const items = (res.data as unknown as DealOption[]) ?? [];
      const today = new Date().toISOString().split("T")[0];
      return items.filter(
        (d) => d.status === "OPEN" && (!d.expectedClose || d.expectedClose >= today)
      );
    },
    staleTime: 60_000,
  });
}