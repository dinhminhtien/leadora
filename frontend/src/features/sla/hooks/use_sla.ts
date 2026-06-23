"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { slaService, type SlaRulePayload, type SlaReportParams } from "@/services/sla_service";
import type { SlaDisplayStatus } from "@/services/sla_service";


export function useSlaRules() {
  return useQuery({
    queryKey: ["sla-rules"],
    queryFn: () => slaService.getList(),
    select: (res) => res.data ?? [],
  });
}

export function useCreateSlaRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: SlaRulePayload) => slaService.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sla-rules"] }),
  });
}

export function useUpdateSlaRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SlaRulePayload }) =>
      slaService.update(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sla-rules"] }),
  });
}

export function useDeleteSlaRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => slaService.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sla-rules"] }),
  });
}

export function useSlaMonitoring(entityType?: string, displayStatus?: SlaDisplayStatus | "") {
  return useQuery({
    queryKey: ["sla-monitoring", entityType, displayStatus],
    queryFn: () => slaService.getMonitoring(entityType || undefined, displayStatus || undefined),
    select: (res) => res.data ?? [],
    refetchInterval: 5 * 60 * 1000,
  });
}

export function useResolveSlaTracking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (trackingId: string) => slaService.resolve(trackingId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sla-monitoring"] }),
  });
}

export function useSlaReport(params?: SlaReportParams) {
  return useQuery({
    queryKey: ["sla-report", params],
    queryFn: () => slaService.getReport(params),
    select: (res) => res.data,
    enabled: !!(params?.from && params?.to),
  });
}
