"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  roomAvailabilityService,
  type PublishAllotmentPayload,
  type ReconcileEntry,
} from "@/services/room_availability_service";

/** The allotment grid for a date window. */
export function useAvailabilityGrid(params: { from: string; to: string; productId?: string }) {
  return useQuery({
    queryKey: ["room-availability", "grid", params],
    queryFn: () => roomAvailabilityService.getGrid(params),
    select: (res) => res.data,
    enabled: !!params.from && !!params.to,
  });
}

/**
 * The one-stay lookup behind the quotation form.
 *
 * Disabled until both dates exist and the stay is at least one night. Firing on a
 * half-filled form would ask the server a question the rep has not finished asking, and the
 * 400 that comes back would surface as an error next to a field they are still typing in.
 */
export function useStayAvailability(params: {
  checkIn?: string;
  checkOut?: string;
  quantity?: number;
  productId?: string;
  enabled?: boolean;
}) {
  const { checkIn, checkOut, quantity, productId, enabled = true } = params;
  const validStay = !!checkIn && !!checkOut && new Date(checkOut) > new Date(checkIn);

  return useQuery({
    queryKey: ["room-availability", "for-stay", checkIn, checkOut, quantity, productId],
    queryFn: () =>
      roomAvailabilityService.getForStay({
        checkIn: checkIn as string,
        checkOut: checkOut as string,
        quantity,
        productId,
      }),
    select: (res) => res.data ?? [],
    enabled: enabled && validStay,
    // Quota moves when other reps sell, so a cached answer goes stale quickly — but the form
    // re-renders on every keystroke, and re-fetching that often would be pointless traffic.
    staleTime: 30_000,
  });
}

/** The Reservation team publishing quota. */
export function usePublishAllotment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: PublishAllotmentPayload) => roomAvailabilityService.publish(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-availability"] });
    },
  });
}

/** The daily reconciliation against the hotel's system. */
export function useReconcileAllotment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { entries: ReconcileEntry[]; asOf?: string }) =>
      roomAvailabilityService.reconcile(vars.entries, vars.asOf),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-availability"] });
    },
  });
}

/** Bulk import from the hotel's spreadsheet. */
export function useImportAllotment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => roomAvailabilityService.importCsv(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-availability"] });
    },
  });
}
