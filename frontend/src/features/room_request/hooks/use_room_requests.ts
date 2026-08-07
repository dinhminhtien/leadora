"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  roomRequestService,
  type CancelRoomRequestPayload,
  type CreateRoomRequestPayload,
  type RespondRoomRequestPayload,
} from "@/services/room_request_service";

/** The Reservation inbox. */
export function useRoomRequestInbox(params?: { status?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ["room-requests", params],
    queryFn: () => roomRequestService.getInbox(params),
    select: (res) => res.data,
  });
}

/**
 * Room requests raised for one quotation. Skipped until an id exists — the Sales panel
 * renders inside modals that mount before a quotation is selected.
 */
export function useRoomRequestsByQuotation(quotationId?: string) {
  return useQuery({
    queryKey: ["room-requests", "by-quotation", quotationId],
    queryFn: () => roomRequestService.getByQuotation(quotationId as string),
    enabled: !!quotationId,
    select: (res) => res.data ?? [],
  });
}

/** Sales asks the Reservation team. */
export function useCreateRoomRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateRoomRequestPayload) => roomRequestService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-requests"] });
    },
  });
}

/**
 * UC-26.4 — Sales withdraws an unanswered request.
 *
 * Invalidates the quotation list for the same reason answering does: withdrawing changes
 * which request speaks for the quotation, and therefore what the room-confirmation panel
 * reports about Send/Convert.
 */
export function useCancelRoomRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload?: CancelRoomRequestPayload }) =>
      roomRequestService.cancel(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-requests"] });
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}

/** The Reservation team answers CONFIRMED/REJECTED. */
export function useRespondRoomRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: RespondRoomRequestPayload }) =>
      roomRequestService.respond(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["room-requests"] });
      // Answering unblocks (or blocks) Send/Convert on the quotation itself.
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}
