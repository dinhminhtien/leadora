"use client";

import { useQuery } from "@tanstack/react-query";
import {
  dealService,
  type DealListParams,
  type DealFilterParams,
} from "@/services/deal_service";

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
