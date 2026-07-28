"use client";

/**
 * Calendar data — Website UI Blueprint §10.15.1 (source filters).
 *
 * Merges the three sources the calendar renders into one `CalendarEvent[]`.
 * Every source is an **existing** endpoint used exactly as the rest of the app
 * uses it — no new API, no changed contract:
 *
 * - tasks     → `GET /tasks`
 * - reminders → `GET /reminders`
 * - bookings  → `GET /bookings`
 *
 * A source that is toggled off is not fetched at all, so unchecking "Bookings"
 * genuinely costs nothing rather than merely hiding rows.
 */

import * as React from "react";
import { useQueries } from "@tanstack/react-query";

import { taskService } from "@/services/follow_up_task_service";
import { reminderService } from "@/services/reminder_service";
import { bookingConfirmationService } from "@/services/booking_confirmation_service";
import {
  bookingToEvent,
  reminderToEvent,
  taskToEvent,
  type CalendarEvent,
  type CalendarSource,
} from "@/features/calendar/lib/calendar-model";

export type CalendarFilters = {
  sources: Record<CalendarSource, boolean>;
  /** Task priorities to include; empty means all. */
  priorities: string[];
  /** Owner id to restrict to; undefined means no restriction. */
  ownerId?: string;
};

export const DEFAULT_CALENDAR_FILTERS: CalendarFilters = {
  sources: { task: true, reminder: true, booking: false },
  priorities: [],
  ownerId: undefined,
};

/**
 * The list endpoints are paged; the calendar needs a window's worth at once.
 * 200 comfortably covers a month for a single rep or a small team, and the
 * server caps what a SALES user can see anyway via owner scoping.
 */
const PAGE_SIZE = 200;

export function useCalendarEvents(filters: CalendarFilters) {
  const results = useQueries({
    queries: [
      {
        queryKey: ["calendar", "tasks", filters.ownerId],
        queryFn: () =>
          taskService.getList({
            size: PAGE_SIZE,
            assignedUserId: filters.ownerId,
          }),
        enabled: filters.sources.task,
        staleTime: 30_000,
      },
      {
        queryKey: ["calendar", "reminders", filters.ownerId],
        queryFn: () => reminderService.getList({ userId: filters.ownerId }),
        enabled: filters.sources.reminder,
        staleTime: 30_000,
      },
      {
        queryKey: ["calendar", "bookings"],
        queryFn: () => bookingConfirmationService.getList({ size: PAGE_SIZE }),
        enabled: filters.sources.booking,
        staleTime: 60_000,
      },
    ],
  });

  const [tasksQuery, remindersQuery, bookingsQuery] = results;

  const events = React.useMemo(() => {
    const out: CalendarEvent[] = [];

    if (filters.sources.task) {
      const page = tasksQuery.data?.data;
      for (const task of page?.content ?? []) {
        const ev = taskToEvent(task);
        if (ev) out.push(ev);
      }
    }

    if (filters.sources.reminder) {
      for (const reminder of remindersQuery.data?.data ?? []) {
        const ev = reminderToEvent(reminder);
        if (ev) out.push(ev);
      }
    }

    if (filters.sources.booking) {
      const page = bookingsQuery.data?.data;
      for (const booking of page?.content ?? []) {
        const ev = bookingToEvent(booking);
        if (ev) out.push(ev);
      }
    }

    // Priority filter applies to tasks only — reminders and bookings have no
    // comparable channel, so filtering them by it would silently hide them.
    const wanted = filters.priorities.map((p) => p.toUpperCase());
    return wanted.length === 0
      ? out
      : out.filter(
          (e) =>
            e.source !== "task" ||
            wanted.includes((e.priority ?? "MEDIUM").toUpperCase()),
        );
  }, [
    filters.sources.task,
    filters.sources.reminder,
    filters.sources.booking,
    filters.priorities,
    tasksQuery.data,
    remindersQuery.data,
    bookingsQuery.data,
  ]);

  const activeQueries = results.filter((_, i) =>
    i === 0 ? filters.sources.task
    : i === 1 ? filters.sources.reminder
    : filters.sources.booking,
  );

  return {
    events,
    isLoading: activeQueries.some((q) => q.isLoading),
    isError: activeQueries.some((q) => q.isError),
    error: activeQueries.find((q) => q.isError)?.error,
    refetch: () => {
      for (const q of activeQueries) void q.refetch();
    },
  };
}
