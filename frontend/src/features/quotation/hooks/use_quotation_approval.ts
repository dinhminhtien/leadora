"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { quotationService, type ProcessApprovalPayload } from "@/services/quotation_service";

export function usePendingApprovals() {
  return useQuery({
    queryKey: ["quotations-pending-approvals"],
    queryFn: () => quotationService.getPendingApprovals(),
    select: (res) => res.data ?? [],
  });
}

export function useProcessApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ProcessApprovalPayload }) =>
      quotationService.processApproval(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quotations-pending-approvals"] });
      queryClient.invalidateQueries({ queryKey: ["quotations"] });
    },
  });
}
