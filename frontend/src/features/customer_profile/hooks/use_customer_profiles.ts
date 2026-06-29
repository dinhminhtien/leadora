"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  customerProfileService,
  type CustomerListParams,
  type CreateCustomerPayload,
  type UpdateCustomerPayload,
} from "@/services/customer_profile_service";
import { taskService } from "@/services/follow_up_task_service";

/** Autocomplete — lightweight search for dropdowns. */
export function useCustomerProfiles(params?: { search?: string; size?: number }) {
  return useQuery({
    queryKey: ["customer-search", params],
    queryFn: () => customerProfileService.getList(params),
  });
}

/** Global counts — total, active, inactive, individual, corporate. */
export function useCustomerStats() {
  return useQuery({
    queryKey: ["customer-stats"],
    queryFn: () => customerProfileService.getStats(),
    staleTime: 30_000,
  });
}

/** Full paginated list for the Customer Profiles screen. */
export function useCustomers(params?: CustomerListParams) {
  return useQuery({
    queryKey: ["customers", params],
    queryFn: () => customerProfileService.getCustomers(params),
  });
}

export function useCustomerDetail(customerId: string | undefined) {
  return useQuery({
    queryKey: ["customers", customerId],
    queryFn: () => customerProfileService.getById(customerId!),
    enabled: !!customerId,
  });
}

export function useCreateCustomer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCustomerPayload) => customerProfileService.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
    },
  });
}

export function useUpdateCustomer(customerId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateCustomerPayload) =>
      customerProfileService.update(customerId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      queryClient.invalidateQueries({ queryKey: ["customers", customerId] });
    },
  });
}

/** Full activity history — deals, bookings, quotations — sorted newest first. */
export function useCustomerHistory(customerId: string | undefined) {
  return useQuery({
    queryKey: ["customer-history", customerId],
    queryFn: () => customerProfileService.getHistory(customerId!),
    enabled: !!customerId,
  });
}

/** Tasks linked to a specific customer — used in the service history tab. */
export function useCustomerTasks(customerId: string | undefined) {
  return useQuery({
    queryKey: ["tasks", { customerId }],
    queryFn: () => taskService.getList({ customerId, size: 100, page: 0 }),
    enabled: !!customerId,
  });
}
