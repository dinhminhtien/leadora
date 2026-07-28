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
  /** Wire value is lower-case: `active` | `won` | `lost` (see `DealMapper`). */
  status: string;
  /** Friendly stage label: `Inquiry` … `Confirmed`. */
  stage?: string;
  expectedClose: string | null;
};

/**
 * `DealMapper.mapStatusToString` emits **lower-case** display values —
 * `OPEN → "active"`, `WON → "won"`, `LOST → "lost"` — not the enum name. The
 * previous filter compared against `"OPEN"`, which never matched, so the picker
 * silently returned an empty list and the Create Quotation form had nothing to
 * select. Both spellings are accepted here so the picker keeps working if the
 * mapper is ever changed to emit the enum directly.
 */
function isOpenDeal(deal: DealOption): boolean {
  const status = (deal.status ?? "").toLowerCase();
  return status === "active" || status === "open";
}

/**
 * A quotation is built from the deal's linked customer:
 * `CreateQuotationUseCase` reads `deal.getCustomer()` and rejects the request
 * with *"The selected deal does not have a linked customer"* when it is absent.
 * `DealMapper` writes the literal `"N/A"` into `contactName` for exactly that
 * case, so a customer-less deal is detectable here and excluded — offering it
 * would only produce a 400 the user cannot fix from the quotation form.
 */
function hasLinkedCustomer(deal: DealOption): boolean {
  const contact = (deal.contactName ?? "").trim();
  return contact.length > 0 && contact !== "N/A";
}

/**
 * Deals eligible for a new quotation.
 *
 * **Owner scoping is not applied here on purpose.** `GET /deals` already runs
 * through `DealAccessPolicy.listScopeOwnerId`, so a SALES user is served only
 * the deals assigned to or created by them; MANAGER/ADMIN are unscoped.
 * Re-filtering by owner in the browser would duplicate a server rule and, worse,
 * would drift from it the moment the policy changes.
 *
 * Two conditions are applied, both mirroring the server:
 *  1. the deal is still **open** — a WON/LOST deal is immutable
 *     (`UpdateDealUseCase`: *"Closed deals cannot be modified."*), so raising a
 *     fresh quotation against one makes no sense;
 *  2. the deal has a **linked customer**, without which creation fails.
 *
 * The expected-close date is deliberately *not* a filter. The old code hid any
 * deal whose close date had passed, which is backwards: a deal that slipped its
 * date is usually the one most in need of a quotation. It is used for ordering
 * instead — soonest close first — so the urgent deals surface at the top.
 */
export function useDealsForQuotation() {
  return useQuery({
    queryKey: ["deals-for-quotation"],
    queryFn: async () => {
      const res = await dealService.getList();
      const items = (res.data as unknown as DealOption[]) ?? [];

      return items
        .filter((d) => isOpenDeal(d) && hasLinkedCustomer(d))
        .sort((a, b) => {
          // Undated deals sort last; they carry no urgency signal.
          const aDue = a.expectedClose ?? "9999-12-31";
          const bDue = b.expectedClose ?? "9999-12-31";
          return aDue.localeCompare(bDue);
        });
    },
    staleTime: 60_000,
  });
}