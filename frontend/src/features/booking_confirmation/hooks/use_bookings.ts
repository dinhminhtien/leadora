"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  bookingConfirmationService,
} from "@/services/booking_confirmation_service";

const QUERY_KEY = "bookings";

export function useBookings(params?: {
  search?: string;
  status?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}) {
  return useQuery({
    queryKey: [QUERY_KEY, params],
    queryFn: () => bookingConfirmationService.getList(params),
    staleTime: 30_000,
  });
}

export function useBookingDetail(id: string | undefined) {
  return useQuery({
    queryKey: [QUERY_KEY, id],
    queryFn: () => bookingConfirmationService.getById(id!),
    enabled: !!id,
    staleTime: 60_000,
  });
}

export function useProcessBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: { status: "CONFIRMED" | "REJECTED"; statusReason?: string } }) =>
      bookingConfirmationService.processRequest(id, payload),
    onSuccess: (_res, { id }) => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY, id] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}
