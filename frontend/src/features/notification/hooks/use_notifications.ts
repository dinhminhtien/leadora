"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationService, type NotificationListParams } from "@/services/notification_service";

// Matches the SLA scheduler cadence (30s) so the bell stays reasonably fresh
// without a full WebSocket/SSE push channel.
const POLL_INTERVAL_MS = 30_000;

export function useNotifications(params: NotificationListParams = {}, poll = false) {
  const { unreadOnly = false, page = 0, size = 20 } = params;
  return useQuery({
    queryKey: ["notifications", unreadOnly, page, size],
    queryFn: () => notificationService.getList({ unreadOnly, page, size }),
    select: (res) => res.data,
    refetchInterval: poll ? POLL_INTERVAL_MS : undefined,
  });
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => notificationService.getUnreadCount(),
    select: (res) => res.data?.count ?? 0,
    refetchInterval: POLL_INTERVAL_MS,
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
