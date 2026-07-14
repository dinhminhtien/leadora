"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  reminderService,
  type CreateReminderPayload,
  type UpdateReminderPayload,
} from "@/services/reminder_service";

export function useReminders(userId?: string, status?: string, fetchAll?: boolean) {
  return useQuery({
    queryKey: ["reminders", userId, status, fetchAll],
    queryFn: () => reminderService.getList({ userId, status }),
    select: (res) => res.data ?? [],
    enabled: !!userId || !!fetchAll,
  });
}

export function useCreateReminder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateReminderPayload) => reminderService.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reminders"] }),
  });
}

export function useDismissReminder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reminderId: string) => reminderService.dismiss(reminderId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reminders"] }),
  });
}

export function useUpdateReminder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reminderId, payload }: { reminderId: string; payload: UpdateReminderPayload }) =>
      reminderService.update(reminderId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reminders"] }),
  });
}

export function useEscalateReminder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reminderId: string) => reminderService.escalate(reminderId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reminders"] }),
  });
}
