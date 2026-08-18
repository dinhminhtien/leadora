"use client";

/**
 * Mini calendar — Website UI Blueprint §10.15.1 (left rail) and §10.15.4
 * ("Mini Calendar navigation with keyboard arrows").
 *
 * Compact month picker with per-day density dots so a user can see where the
 * work is before navigating there. Arrow keys move the focused day, Enter picks
 * it — the roving-tabindex pattern, so the grid is one tab stop rather than 42.
 */

import * as React from "react";
import {
  addDays,
  addMonths,
  format,
  isSameDay,
  isSameMonth,
  isToday,
  subMonths,
} from "date-fns";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";
import {
  eventsForDay,
  monthMatrix,
  type CalendarEvent,
} from "@/features/calendar/lib/calendar-model";

const WEEKDAY_INITIALS = ["M", "T", "W", "T", "F", "S", "S"];

type MiniCalendarProps = {
  /** The month being displayed in the main surface. */
  anchor: Date;
  selected: Date;
  events: CalendarEvent[];
  onSelect: (day: Date) => void;
};

export function MiniCalendar({
  anchor,
  selected,
  events,
  onSelect,
}: MiniCalendarProps) {
  /**
   * The mini-calendar can be browsed independently of the main surface, but it
   * must follow when the main surface navigates. Rather than syncing in an
   * effect (which costs a second render on every parent navigation), the local
   * override is stored as a delta and cleared whenever `anchor` changes — the
   * "derive state during render" pattern.
   */
  const [override, setOverride] = React.useState<Date | null>(null);
  const [lastAnchor, setLastAnchor] = React.useState(anchor);
  if (anchor.getTime() !== lastAnchor.getTime()) {
    setLastAnchor(anchor);
    setOverride(null);
  }
  const viewMonth = override ?? anchor;
  const setViewMonth = (updater: Date | ((current: Date) => Date)) =>
    setOverride((current) =>
      typeof updater === "function" ? updater(current ?? anchor) : updater,
    );

  const [focusDay, setFocusDay] = React.useState(selected);

  const weeks = React.useMemo(() => monthMatrix(viewMonth), [viewMonth]);

  const onKeyDown = (e: React.KeyboardEvent) => {
    const delta =
      e.key === "ArrowLeft" ? -1
      : e.key === "ArrowRight" ? 1
      : e.key === "ArrowUp" ? -7
      : e.key === "ArrowDown" ? 7
      : 0;

    if (delta !== 0) {
      e.preventDefault();
      const next = addDays(focusDay, delta);
      setFocusDay(next);
      if (!isSameMonth(next, viewMonth)) setViewMonth(next);
      return;
    }
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onSelect(focusDay);
    }
  };

  return (
    <div className="rounded-lg border border-border bg-surface p-3">
      <div className="mb-2 flex items-center justify-between">
        <button
          type="button"
          onClick={() => setViewMonth((m) => subMonths(m, 1))}
          aria-label="Previous month"
          className="grid size-6 place-items-center rounded text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          <ChevronLeft className="size-3.5" />
        </button>
        <span className="text-[12.5px] font-semibold text-foreground">
          {format(viewMonth, "MMMM yyyy")}
        </span>
        <button
          type="button"
          onClick={() => setViewMonth((m) => addMonths(m, 1))}
          aria-label="Next month"
          className="grid size-6 place-items-center rounded text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          <ChevronRight className="size-3.5" />
        </button>
      </div>

      <div className="mb-1 grid grid-cols-7 gap-0.5">
        {WEEKDAY_INITIALS.map((d, i) => (
          <span
            key={i}
            className="grid h-5 place-items-center text-[10px] font-semibold text-muted-foreground"
          >
            {d}
          </span>
        ))}
      </div>

      {/* Roving tabindex: one tab stop for the whole grid. */}
      <div
        role="grid"
        aria-label="Mini calendar"
        tabIndex={0}
        onKeyDown={onKeyDown}
        className="grid grid-cols-7 gap-0.5 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      >
        {weeks.flat().map((day) => {
          const outside = !isSameMonth(day, viewMonth);
          const isSelected = isSameDay(day, selected);
          const isFocused = isSameDay(day, focusDay);
          const count = eventsForDay(events, day).length;
          const hasOverdue = eventsForDay(events, day).some((e) => e.overdue);

          return (
            <button
              key={day.toISOString()}
              type="button"
              role="gridcell"
              tabIndex={-1}
              aria-selected={isSelected}
              onClick={() => {
                setFocusDay(day);
                onSelect(day);
              }}
              className={cn(
                "relative grid h-7 place-items-center rounded text-[11.5px] font-medium transition-colors",
                outside && "text-muted-foreground/40",
                !outside && !isSelected && "text-foreground hover:bg-surface-2",
                isSelected && "bg-brand-500 text-brand-foreground",
                !isSelected && isToday(day) && "ring-1 ring-inset ring-brand-500",
                isFocused && !isSelected && "bg-surface-3",
              )}
            >
              {format(day, "d")}
              {count > 0 && (
                <span
                  aria-hidden
                  className={cn(
                    "absolute bottom-0.5 size-1 rounded-full",
                    isSelected
                      ? "bg-brand-foreground/70"
                      : hasOverdue
                        ? "bg-danger"
                        : "bg-brand-500",
                  )}
                />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
