"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dealService,
  type Deal,
  type DealPayload,
  type DealListParams,
  type DealFilterParams,
} from "@/services/deal_service";

/** Patch a deal inside any cached page/list or single-deal response. */
export function patchDealInCache(old: unknown, dealId: string, patch: Partial<Deal>): unknown {
  if (!old || typeof old !== "object") return old;
  const data = (old as { data?: unknown }).data;
  if (!data) return old;

  // PageResponse — list query
  if (typeof data === "object" && data !== null && "content" in data) {
    const page = data as { content: Deal[] };
    return {
      ...old,
      data: {
        ...page,
        content: page.content.map((d: Deal) =>
          d.id === dealId ? { ...d, ...patch } : d
        ),
      },
    };
  }

  // Single deal detail query
  if (typeof data === "object" && data !== null && "id" in data) {
    const d = data as Deal;
    if (d.id === dealId) return { ...old, data: { ...d, ...patch } };
  }

  return old;
}

export function useDeals(params?: DealListParams) {
  return useQuery({
    queryKey: ["deals", params],
    queryFn: () => dealService.getList(params),
  });
}

/**
 * Summary counts for the tiles above the list.
 *
 * Deliberately a separate query from {@link useDeals}: the list is paged, these are not, and
 * deriving them from the loaded page is what made them describe "this page" instead of "these
 * filters". Kept under the same `["deals"]` key prefix so every mutation that invalidates the
 * list refreshes the tiles with it.
 */
export function useDealStats(params?: DealFilterParams) {
  return useQuery({
    queryKey: ["deals", "stats", params],
    queryFn: () => dealService.getStats(params),
  });
}

export function useDealDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["deals", id],
    queryFn: () => dealService.getById(id!),
    enabled: !!id,
  });
}

export function useCreateDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: DealPayload) => dealService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}

export function useUpdateDeal(dealId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: DealPayload) => dealService.update(dealId, payload),
    onSuccess: (updated) => {
      if (updated?.data) {
        queryClient.setQueryData(["deals", dealId], updated);
      }
      queryClient.setQueriesData({ queryKey: ["deals"], exact: false }, (old) =>
        patchDealInCache(old, dealId, updated?.data ?? {})
      );
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}

export function useUpdateDealStage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ dealId, stage, payload }: { dealId: string; stage: Deal["stage"]; payload?: DealPayload }) =>
      dealService.update(dealId, { ...payload, stage }),
    onMutate: async ({ dealId, stage }) => {
      await queryClient.cancelQueries({ queryKey: ["deals"] });
      const previousDeals = queryClient.getQueriesData({ queryKey: ["deals"] });

      queryClient.setQueriesData({ queryKey: ["deals"], exact: false }, (old) =>
        patchDealInCache(old, dealId, { stage })
      );

      return { previousDeals };
    },
    onError: (_err, _vars, context) => {
      if (context?.previousDeals) {
        context.previousDeals.forEach(([key, data]) => {
          queryClient.setQueryData(key, data);
        });
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["deals"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    },
  });
}
