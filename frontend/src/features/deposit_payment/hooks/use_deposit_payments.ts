"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  depositPaymentService,
  type PaymentQuery,
  type GeneratePaymentPayload,
  type UpdatePaymentStatusPayload,
} from "@/services/deposit_payment_service";

const QUERY_KEY = "payments";

export function usePayments(params?: PaymentQuery) {
  return useQuery({
    queryKey: [QUERY_KEY, params],
    queryFn: () => depositPaymentService.getList(params),
    staleTime: 30_000,
  });
}

export function usePaymentDetail(id: string | undefined) {
  return useQuery({
    queryKey: [QUERY_KEY, id],
    queryFn: () => depositPaymentService.getById(id!),
    enabled: !!id,
    staleTime: 60_000,
  });
}

export function useGeneratePayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: GeneratePaymentPayload) => depositPaymentService.generate(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}

export function useUpdatePaymentStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdatePaymentStatusPayload) => depositPaymentService.updateStatus(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY, id] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}

export function useCancelPayment(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => depositPaymentService.cancel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY, id] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}
