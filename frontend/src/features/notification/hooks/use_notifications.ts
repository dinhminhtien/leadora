"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationService } from "@/services/notification_service";

export function useNotifications(unreadOnly = false) {
  return useQuery({
    queryKey: ["notifications", unreadOnly],
    queryFn: () => notificationService.getList(unreadOnly),
    select: (res) => res.data ?? [],
  });
}

export function useNotificationById(id: string | undefined) {
  return useQuery({
    queryKey: ["notification", id],
    queryFn: () => notificationService.getById(id!),
    select: (res) => res.data,
    enabled: !!id,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, read }: { id: string; read: boolean }) =>
      notificationService.markRead(id, read),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}
