"use client";

/**
 * Calendar event pill — Website UI Blueprint §10.15.3.
 *
 * Encodes two orthogonal channels at once, which is the whole reason this is a
 * component rather than a div with a colour:
 *
 * - **Left 3px stripe = priority** (`danger` high · `warning` medium ·
 *   `muted-foreground` low).
 * - **Fill = status**:
 *   - `OPEN` — solid brand
 *   - `COMPLETED` — 20% tint + tick + strikethrough, opacity .7
 *   - `CANCELLED` — dashed border, transparent fill, italic, opacity .6
 *   - `OVERDUE` (computed, still `OPEN`) — solid danger, pulses once on mount
 *
 * Note what is *not* here: an OVERDUE status. The pill reads `event.overdue`,
 * a computed boolean, and the underlying status stays `OPEN`.
 *
 * Accessibility: status is never carried by colour alone — completed gets a
 * tick plus strikethrough, cancelled gets a dashed border plus italics.
 */

import * as React from "react";
import { AlertTriangle, BedDouble, Bell, Check, X } from "lucide-react";

import { cn } from "@/lib/utils";
import type { CalendarEvent } from "@/features/calendar/lib/calendar-model";

const sourceIcon = {
  task: Check,
  reminder: Bell,
  booking: BedDouble,
} as const;

/** Priority → stripe colour. Low uses the neutral token, never a status colour. */
function stripeClass(priority?: string | null): string {
  const p = (priority ?? "MEDIUM").toUpperCase();
  if (p === "HIGH" || p === "URGENT") return "bg-danger";
  if (p === "LOW") return "bg-muted-foreground";
  return "bg-warning";
}

type EventPillProps = {
  event: CalendarEvent;
  /** `chip` = month cell one-liner · `block` = positioned time-grid block. */
  variant?: "chip" | "block";
  conflicts?: number;
  onClick?: (event: CalendarEvent) => void;
  onMouseEnter?: (e: React.MouseEvent, event: CalendarEvent) => void;
  onMouseLeave?: () => void;
  className?: string;
  style?: React.CSSProperties;
  /** Enables HTML5 drag for reschedule (§10.15.4). */
  draggable?: boolean;
  onDragStart?: (e: React.DragEvent, event: CalendarEvent) => void;
};

export function EventPill({
  event,
  variant = "chip",
  conflicts = 0,
  onClick,
  onMouseEnter,
  onMouseLeave,
  className,
  style,
  draggable = false,
  onDragStart,
}: EventPillProps) {
  const status = event.status?.toUpperCase() ?? "";
  const completed = status === "COMPLETED" || status === "DONE";
  const cancelled = status === "CANCELLED";
  const overdue = event.overdue && !completed && !cancelled;

  const Icon = sourceIcon[event.source];

  // Fill encodes status. Bookings sit outside the task language, so they take
  // the teal reservation tone rather than borrowing a task status colour.
  const fill = cancelled
    ? "border border-dashed border-border bg-transparent text-muted-foreground"
    : completed
      ? "bg-brand-500/20 text-brand-700 dark:text-brand-500"
      : overdue
        ? "bg-danger text-white dark:text-danger-bg"
        : event.source === "booking"
          ? "bg-teal/15 text-teal"
          : event.source === "reminder"
            ? "bg-warning/15 text-warning"
            : "bg-brand-500 text-brand-foreground";

  const timeLabel = event.allDay
    ? "All day"
    : event.start.toLocaleTimeString(undefined, {
        hour: "2-digit",
        minute: "2-digit",
      });

  return (
    <button
      type="button"
      draggable={draggable}
      onDragStart={draggable && onDragStart ? (e) => onDragStart(e, event) : undefined}
      onClick={() => onClick?.(event)}
      onMouseEnter={(e) => onMouseEnter?.(e, event)}
      onMouseLeave={onMouseLeave}
      style={style}
      title={`${timeLabel} · ${event.title}`}
      aria-label={[
        event.title,
        timeLabel,
        status.toLowerCase(),
        overdue ? "overdue" : null,
        conflicts > 0 ? `${conflicts} overlapping` : null,
      ]
        .filter(Boolean)
        .join(", ")}
      className={cn(
        "group relative flex w-full items-center gap-1 overflow-hidden rounded-md text-left",
        "transition-[filter,box-shadow] duration-[120ms]",
        "hover:brightness-[1.08] active:brightness-[0.94]",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-1 focus-visible:ring-offset-background",
        variant === "chip" ? "h-[18px] px-1 text-[10.5px]" : "px-1.5 py-1 text-[11px]",
        fill,
        completed && "opacity-70",
        cancelled && "opacity-60 italic",
        overdue && "pulse-once",
        draggable && "cursor-grab active:cursor-grabbing",
        className,
      )}
    >
      {/* Priority stripe — always first, so the eye reads urgency before text. */}
      <span
        aria-hidden
        className={cn(
          "absolute inset-y-0 left-0 w-[3px] rounded-l-md",
          stripeClass(event.priority),
        )}
      />

      <span className="ml-1 flex min-w-0 flex-1 items-center gap-1">
        {completed ? (
          <Check aria-hidden className="size-3 shrink-0" strokeWidth={3} />
        ) : cancelled ? (
          <X aria-hidden className="size-3 shrink-0" strokeWidth={3} />
        ) : overdue ? (
          <AlertTriangle aria-hidden className="size-3 shrink-0" />
        ) : (
          <Icon aria-hidden className="size-3 shrink-0 opacity-80" />
        )}

        {variant === "block" && !event.allDay && (
          <span className="numeric shrink-0 text-[10px] opacity-80">{timeLabel}</span>
        )}

        <span
          className={cn(
            "min-w-0 flex-1 truncate font-medium",
            completed && "line-through",
          )}
        >
          {event.title}
        </span>
      </span>

      {/* §10.15.4 conflict indicator — red corner ribbon. */}
      {conflicts > 0 && (
        <span
          aria-hidden
          title={`${conflicts} overlapping event${conflicts > 1 ? "s" : ""}`}
          className="absolute right-0 top-0 size-0 border-l-[10px] border-t-[10px] border-l-transparent border-t-danger"
        />
      )}
    </button>
  );
}
