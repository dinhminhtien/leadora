/**
 * Calendar domain model — Website UI Blueprint §10.15.
 *
 * The calendar renders three *sources* — tasks, reminders and bookings — through
 * one `CalendarEvent` shape so Month/Week/Day/Agenda each implement layout once
 * instead of branching per source.
 *
 * **The status rule this file exists to protect.** Tasks have exactly
 * `OPEN / COMPLETED / CANCELLED`. `OVERDUE` is *computed* (`OPEN && endAt < now`)
 * and travels as a boolean flag on the event, never as a status value. Nothing
 * here may add a fourth task status.
 */

import {
  addDays,
  differenceInMinutes,
  endOfDay,
  endOfMonth,
  endOfWeek,
  isSameDay,
  startOfDay,
  startOfMonth,
  startOfWeek,
} from "date-fns";

import { isTaskOverdue } from "@/shared/design/status-tokens";
import type { Task } from "@/services/follow_up_task_service";
import type { Reminder } from "@/services/reminder_service";
import type { Booking } from "@/services/booking_confirmation_service";

export type CalendarView = "month" | "week" | "day" | "agenda";

export type CalendarSource = "task" | "reminder" | "booking";

export type CalendarEvent = {
  id: string;
  source: CalendarSource;
  title: string;
  /** Inclusive start instant. */
  start: Date;
  /** Exclusive end instant. Equal to `start` for point-in-time events. */
  end: Date;
  /** Spans whole days — bookings, and any event without a time component. */
  allDay: boolean;
  /** Raw wire status (`OPEN`, `PENDING`, `CONFIRMED`, …). */
  status: string;
  /** Task priority only; reminders carry their own priority, bookings none. */
  priority?: string | null;
  /** Computed, never stored (§10.14.6). */
  overdue: boolean;
  ownerId?: string | null;
  ownerName?: string | null;
  /** Linked record label, e.g. "Deal: Q3 Retreat". */
  relatedLabel?: string | null;
  /** Route to open the underlying record. */
  href?: string;
  /** Original object, for the detail drawer. */
  raw: Task | Reminder | Booking;
};

/* ------------------------------------------------------------------ *
 * Source adapters
 * ------------------------------------------------------------------ */

/**
 * A task's calendar slot. `startAt`/`endAt` are both optional on the wire, so
 * we fall back the way the mobile client does: anchor on whichever exists.
 * A task with neither is *not* schedulable and is filtered out by the caller.
 */
export function taskToEvent(task: Task): CalendarEvent | null {
  const startRaw = task.startAt ?? task.endAt;
  if (!startRaw) return null;

  const start = new Date(startRaw);
  if (Number.isNaN(start.getTime())) return null;

  const endRaw = task.endAt ?? task.startAt;
  const end = endRaw ? new Date(endRaw) : start;

  // A task whose end is at or before its start still needs a visible slot.
  const safeEnd = end.getTime() > start.getTime() ? end : new Date(start.getTime() + 30 * 60_000);

  const related =
    task.dealName ?? task.customerName ?? task.leadName ?? null;

  return {
    id: task.taskId,
    source: "task",
    title: task.title,
    start,
    end: safeEnd,
    allDay: false,
    status: task.status,
    priority: task.priority,
    overdue: isTaskOverdue(task),
    ownerId: task.assignedUserId,
    ownerName: task.assignedUserName,
    relatedLabel: related ? `${task.dealName ? "Deal" : task.customerName ? "Customer" : "Lead"}: ${related}` : null,
    raw: task,
  };
}

export function reminderToEvent(reminder: Reminder): CalendarEvent | null {
  if (!reminder.remindAt) return null;
  const start = new Date(reminder.remindAt);
  if (Number.isNaN(start.getTime())) return null;

  return {
    id: reminder.reminderId,
    source: "reminder",
    title: reminder.title,
    start,
    // Reminders are instants; give them a 30-minute visual slot in time grids.
    end: new Date(start.getTime() + 30 * 60_000),
    allDay: false,
    status: reminder.status,
    priority: reminder.priority,
    overdue: reminder.status?.toUpperCase() === "OVERDUE",
    ownerId: reminder.assignedUserId ?? null,
    ownerName: reminder.assignedUserName ?? null,
    relatedLabel: reminder.relatedEntity ? `${reminder.relatedEntity}` : null,
    raw: reminder,
  };
}

export function bookingToEvent(booking: Booking): CalendarEvent | null {
  if (!booking.checkInDate) return null;
  const start = startOfDay(new Date(booking.checkInDate));
  if (Number.isNaN(start.getTime())) return null;

  const end = booking.checkOutDate
    ? endOfDay(new Date(booking.checkOutDate))
    : endOfDay(start);

  return {
    id: booking.bookingId,
    source: "booking",
    title: booking.customerName
      ? `${booking.customerName} · ${booking.bookingCode ?? "Booking"}`
      : (booking.bookingCode ?? "Booking"),
    start,
    end,
    // A stay occupies whole days — it belongs in the all-day band, not an hour row.
    allDay: true,
    status: booking.status,
    overdue: false,
    ownerId: booking.assignedUserId ?? null,
    ownerName: booking.assignedUserName ?? null,
    relatedLabel: booking.bookingCode ?? null,
    raw: booking,
  };
}

/* ------------------------------------------------------------------ *
 * Range helpers
 * ------------------------------------------------------------------ */

/** Monday-first, matching the SLA business calendar (`MON..FRI`). */
const WEEK_OPTS = { weekStartsOn: 1 as const };

/** The visible instant range for a view — used to fetch and to filter. */
export function visibleRange(view: CalendarView, anchor: Date): { from: Date; to: Date } {
  switch (view) {
    case "month": {
      // Month grids show trailing/leading days of adjacent months.
      const from = startOfWeek(startOfMonth(anchor), WEEK_OPTS);
      const to = endOfWeek(endOfMonth(anchor), WEEK_OPTS);
      return { from, to };
    }
    case "week":
      return { from: startOfWeek(anchor, WEEK_OPTS), to: endOfWeek(anchor, WEEK_OPTS) };
    case "day":
      return { from: startOfDay(anchor), to: endOfDay(anchor) };
    case "agenda":
      // Agenda looks forward a month from the anchor day.
      return { from: startOfDay(anchor), to: endOfDay(addDays(anchor, 30)) };
  }
}

/** The 6×7 (or 5×7) day matrix a month grid renders. */
export function monthMatrix(anchor: Date): Date[][] {
  const { from, to } = visibleRange("month", anchor);
  const weeks: Date[][] = [];
  let cursor = from;
  while (cursor <= to) {
    const week: Date[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(cursor);
      cursor = addDays(cursor, 1);
    }
    weeks.push(week);
  }
  return weeks;
}

export function weekDays(anchor: Date): Date[] {
  const start = startOfWeek(anchor, WEEK_OPTS);
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
}

/** Events that intersect a given day, ordered by start then duration. */
export function eventsForDay(events: CalendarEvent[], day: Date): CalendarEvent[] {
  const dayStart = startOfDay(day).getTime();
  const dayEnd = endOfDay(day).getTime();
  return events
    .filter((e) => e.start.getTime() <= dayEnd && e.end.getTime() >= dayStart)
    .sort((a, b) => {
      if (a.allDay !== b.allDay) return a.allDay ? -1 : 1;
      const byStart = a.start.getTime() - b.start.getTime();
      if (byStart !== 0) return byStart;
      // Longer events first so short ones stack on top of them.
      return b.end.getTime() - b.start.getTime() - (a.end.getTime() - a.start.getTime());
    });
}

/* ------------------------------------------------------------------ *
 * Time-grid geometry (Week / Day)
 * ------------------------------------------------------------------ */

export const HOUR_HEIGHT = 48; // §10.15.2 — "Hour rows 48px"
export const DAY_START_HOUR = 0;
export const DAY_END_HOUR = 24;

/** Working-hour band — mirrors the backend SLA calendar (08:00–18:00, Mon–Fri). */
export const WORK_START_HOUR = 8;
export const WORK_END_HOUR = 18;

export function isWeekend(day: Date): boolean {
  const d = day.getDay();
  return d === 0 || d === 6;
}

/** Pixel offset from the top of a 24-hour column. */
export function offsetForTime(date: Date): number {
  return (date.getHours() + date.getMinutes() / 60) * HOUR_HEIGHT;
}

/** Pixel height for a duration, floored so a 0-minute event stays visible. */
export function heightForRange(start: Date, end: Date): number {
  const minutes = Math.max(differenceInMinutes(end, start), 20);
  return (minutes / 60) * HOUR_HEIGHT;
}

/**
 * Overlap layout — §10.15.4 "Conflict Indicator".
 *
 * Events that share time are placed side-by-side in columns. The algorithm is
 * the standard sweep: walk events in start order, put each in the first column
 * whose last event has already ended, and remember the widest cluster so every
 * member of that cluster renders at the same width.
 *
 * Returns positioning plus a `conflicts` count, which drives the red corner
 * ribbon and the popover's conflict list.
 */
export type PositionedEvent = {
  event: CalendarEvent;
  top: number;
  height: number;
  /** 0-based column index within its overlap cluster. */
  column: number;
  /** Total columns in the cluster — width is `1 / columns`. */
  columns: number;
  /** How many other events this one overlaps. */
  conflicts: number;
};

export function layoutDayColumn(events: CalendarEvent[]): PositionedEvent[] {
  const timed = events
    .filter((e) => !e.allDay)
    .sort((a, b) => a.start.getTime() - b.start.getTime());

  if (timed.length === 0) return [];

  const positioned: PositionedEvent[] = [];
  // A cluster is a run of events connected by overlap.
  let cluster: CalendarEvent[] = [];
  let clusterEnd = 0;

  const flush = () => {
    if (cluster.length === 0) return;
    const columnEnds: number[] = [];
    const assignment = new Map<string, number>();

    for (const e of cluster) {
      let col = columnEnds.findIndex((end) => end <= e.start.getTime());
      if (col === -1) {
        col = columnEnds.length;
        columnEnds.push(0);
      }
      columnEnds[col] = e.end.getTime();
      assignment.set(e.id, col);
    }

    const columns = columnEnds.length;
    for (const e of cluster) {
      const conflicts = cluster.filter(
        (o) =>
          o.id !== e.id &&
          o.start.getTime() < e.end.getTime() &&
          o.end.getTime() > e.start.getTime(),
      ).length;

      positioned.push({
        event: e,
        top: offsetForTime(e.start),
        height: heightForRange(e.start, e.end),
        column: assignment.get(e.id) ?? 0,
        columns,
        conflicts,
      });
    }
    cluster = [];
  };

  for (const e of timed) {
    if (cluster.length > 0 && e.start.getTime() >= clusterEnd) {
      flush();
      clusterEnd = 0;
    }
    cluster.push(e);
    clusterEnd = Math.max(clusterEnd, e.end.getTime());
  }
  flush();

  return positioned;
}

/** Groups events into day buckets for the agenda view. */
export function groupByDay(
  events: CalendarEvent[],
  from: Date,
  to: Date,
): { day: Date; events: CalendarEvent[] }[] {
  const buckets: { day: Date; events: CalendarEvent[] }[] = [];
  let cursor = startOfDay(from);
  const end = endOfDay(to);

  while (cursor <= end) {
    const dayEvents = eventsForDay(events, cursor);
    if (dayEvents.length > 0) buckets.push({ day: cursor, events: dayEvents });
    cursor = addDays(cursor, 1);
  }
  return buckets;
}

export { isSameDay };
