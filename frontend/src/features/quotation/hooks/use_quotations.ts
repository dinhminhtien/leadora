"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { quotationService, type CreateQuotationPayload, type SubmitQuotationPayload, type SendQuotationPayload, type ReviseQuotationPayload, type TrackCustomerResponsePayload, type ConvertToBookingPayload, type CloseQuotationPayload, type ExpireOverduePayload } from "@/services/quotation_service";

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

/**
 * Deals eligible for a new quotation are no longer assembled here.
 *
 * This used to fetch **every** deal the user could see and filter it in the browser so a
 * `<select>` could be populated with the survivors. That cost a full-table read on every
 * visit to the form, it did not scale past a few dozen deals, and mobile never applied
 * the same filter, so the two clients disagreed about what was quotable.
 *
 * Eligibility now lives in `DealSpecification.quotable` behind `GET /deals/quotable`,
 * which is paged and searched server-side. It is one condition: the deal is still
 * **active** — won and lost deals are closed and never returned. Note there is
 * deliberately no expected-close-date condition; a deal that slipped its date is usually
 * the one most in need of a quotation, so the date orders the results (soonest first)
 * instead of filtering them.
 *
 * Consume it through `DealSearchPicker`, which owns the debounce, paging and caching.
 *
 * @see {@link file://../components/DealSearchPicker.tsx}
 */
export function usePublicQuotation(id: string, token: string) {
  return useQuery({
    queryKey: ["public-quotation", id, token],
    queryFn: () => quotationService.publicGetById(id, token),
    select: (res) => res.data,
    enabled: !!id && !!token,
  });
}

export function usePublicRequestQuotationOtp() {
  return useMutation({
    mutationFn: ({ id, token }: { id: string; token: string }) =>
      quotationService.publicRequestOtp(id, token),
  });
}

export function usePublicConfirmQuotationOtp() {
  return useMutation({
    mutationFn: ({ id, token, otpCode }: { id: string; token: string; otpCode: string }) =>
      quotationService.publicConfirmOtp(id, token, otpCode),
  });
}