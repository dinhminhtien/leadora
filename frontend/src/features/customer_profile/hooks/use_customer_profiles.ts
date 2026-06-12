"use client";

import { useQuery } from "@tanstack/react-query";

import { customerProfileService } from "@/services/customer_profile_service";
import type { ListQuery } from "@/shared/types/api";

export function useCustomerProfiles(params?: ListQuery) {
  return useQuery({
    queryKey: ["customer-profiles", params],
    queryFn: () => customerProfileService.getList(params),
  });
}
