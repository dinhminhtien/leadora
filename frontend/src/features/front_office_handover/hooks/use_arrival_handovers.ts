"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  arrivalHandoverService,
  type ArrivalHandoverQuery,
  type ReadinessStatus,
} from "@/services/arrival_handover_service";

const LIST_KEY = "arrival-handovers";

export function useArrivalHandovers(params?: ArrivalHandoverQuery) {
  return useQuery({
    queryKey: [LIST_KEY, params],
    queryFn: () => arrivalHandoverService.getList(params),
  });
}

export function useArrivalHandoverSummary() {
  return useQuery({
    queryKey: [LIST_KEY, "summary"],
    queryFn: () => arrivalHandoverService.getSummary(),
    select: (res) => res.data,
    staleTime: 15_000,
  });
}

export function useArrivalHandoverDetail(id: string | null) {
  return useQuery({
    queryKey: [LIST_KEY, "detail", id],
    queryFn: () => arrivalHandoverService.getDetail(id as string),
    enabled: !!id,
  });
}

export function useUpdateReadiness() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      readinessStatus,
      clarificationNote,
    }: {
      id: string;
      readinessStatus: ReadinessStatus;
      clarificationNote?: string;
    }) => arrivalHandoverService.updateReadiness(id, readinessStatus, clarificationNote),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [LIST_KEY] });
      queryClient.invalidateQueries({ queryKey: [LIST_KEY, "detail", variables.id] });
    },
  });
}
