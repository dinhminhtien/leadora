"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationService } from "@/services/notification_service";

export function useNotifications(userId: string | undefined, unreadOnly = false) {
  return useQuery({
    queryKey: ["notifications", userId, unreadOnly],
    queryFn: () => notificationService.getList(userId!, unreadOnly),
    select: (res) => res.data ?? [],
    enabled: !!userId,
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
    mutationFn: (userId: string) => notificationService.markAllRead(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}