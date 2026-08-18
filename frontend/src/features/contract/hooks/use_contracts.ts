"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { contractService, type BillingMethod } from "@/services/contract_service";

export function useContracts() {
  return useQuery({
    queryKey: ["contracts"],
    queryFn: () => contractService.getList(),
    select: (res) => res.data ?? [],
  });
}

export function useContractById(id: string) {
  return useQuery({
    queryKey: ["contract", id],
    queryFn: () => contractService.getById(id),
    select: (res) => res.data,
    enabled: !!id,
  });
}

export function useUpdateContractBillingMethod() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, billingMethod }: { id: string; billingMethod: BillingMethod }) =>
      contractService.updateBillingMethod(id, billingMethod),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
      queryClient.invalidateQueries({ queryKey: ["contract", variables.id] });
    },
  });
}

export function useSendContract() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => contractService.send(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
      queryClient.invalidateQueries({ queryKey: ["contract", id] });
    },
  });
}

export function useCancelContract() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => contractService.cancel(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
      queryClient.invalidateQueries({ queryKey: ["contract", id] });
    },
  });
}

export function useResendContract() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => contractService.resend(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
      queryClient.invalidateQueries({ queryKey: ["contract", id] });
    },
  });
}

export function useRegenerateContract() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => contractService.regenerate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
    },
  });
}

// Public client portal hooks
export function usePublicContract(id: string, token: string) {
  return useQuery({
    queryKey: ["public-contract", id, token],
    queryFn: () => contractService.publicGetById(id, token),
    select: (res) => res.data,
    enabled: !!id && !!token,
  });
}

export function usePublicRequestOtp() {
  return useMutation({
    mutationFn: ({ id, token }: { id: string; token: string }) =>
      contractService.publicRequestOtp(id, token),
  });
}

export function usePublicConfirmOtp() {
  return useMutation({
    mutationFn: ({ id, token, otpCode }: { id: string; token: string; otpCode: string }) =>
      contractService.publicConfirmOtp(id, token, otpCode),
  });
}
