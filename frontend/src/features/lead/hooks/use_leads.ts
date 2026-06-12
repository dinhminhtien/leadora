"use client";

import { useQuery } from "@tanstack/react-query";

import { leadService } from "@/services/lead_service";
import type { ListQuery } from "@/shared/types/api";

export function useLeads(params?: ListQuery) {
  return useQuery({
    queryKey: ["leads", params],
    queryFn: () => leadService.getList(params),
  });
}
