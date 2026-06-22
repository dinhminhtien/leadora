"use client";

import { useQuery } from "@tanstack/react-query";

import { customerProfileService } from "@/services/customer_profile_service";
import { taskService } from "@/services/follow_up_task_service";
import type { ListQuery } from "@/shared/types/api";

export function useCustomerProfiles(params?: ListQuery) {
  return useQuery({
    queryKey: ["customer-profiles", params],
    queryFn: () => customerProfileService.getList(params),
  });
}

export function useCustomerDetail(customerId: string | undefined) {
  return useQuery({
    queryKey: ["customer-profiles", customerId],
    queryFn: () => customerProfileService.getById(customerId!),
    enabled: !!customerId,
  });
}

/** Fetch all tasks linked to a specific customer (server-side filter). */
export function useCustomerTasks(customerId: string | undefined) {
  return useQuery({
    queryKey: ["tasks", { customerId }],
    queryFn: () => taskService.getList({ customerId, size: 100, page: 0 }),
    enabled: !!customerId,
  });
}
