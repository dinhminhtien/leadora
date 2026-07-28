"use client";

/**
 * Month grid — Website UI Blueprint §10.15.2.
 *
 * 7 columns × 5–6 rows. Each cell: date number top-left, up to 3 event chips,
 * `+N` overflow bottom-right. Weekend cells `surface-2`. Today gets a brand
 * ring. Clicking a date switches to Day view (§10.15.2).
 *
 * Drop target: dragging an event onto a day reschedules it to that date,
 * preserving its time-of-day — moving a 14:00 call to Friday should keep it at
 * 14:00, not reset it to midnight.
 */

import * as React from "react";
import { format, isSameMonth, isToday } from "date-fns";

import { cn } from "@/lib/utils";
import { EventPill } from "@/features/calendar/components/EventPill";
import {
  eventsForDay,
  isWeekend,
  monthMatrix,
  type CalendarEvent,
} from "@/features/calendar/lib/calendar-model";

const MAX_CHIPS = 3;
const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

type MonthViewProps = {
  anchor: Date;
  events: CalendarEvent[];
  onSelectDay: (day: Date) => void;
  onSelectEvent: (event: CalendarEvent) => void;
  onQuickCreate?: (day: Date) => void;
  onDropEvent?: (event: CalendarEvent, day: Date) => void;
  onHoverEvent?: (e: React.MouseEvent, event: CalendarEvent) => void;
  onHoverEnd?: () => void;
};

export function MonthView({
  anchor,
  events,
  onSelectDay,
  onSelectEvent,
  onQuickCreate,
  onDropEvent,
  onHoverEvent,
  onHoverEnd,
}: MonthViewProps) {
  const weeks = React.useMemo(() => monthMatrix(anchor), [anchor]);
  const [dragOver, setDragOver] = React.useState<string | null>(null);
  const draggedRef = React.useRef<CalendarEvent | null>(null);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border bg-surface">
      {/* Weekday header */}
      <div className="grid shrink-0 grid-cols-7 border-b border-border bg-muted">
        {WEEKDAY_LABELS.map((label, i) => (
          <div
            key={label}
            className={cn(
              "px-2 py-2 text-center text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground",
              i >= 5 && "text-muted-foreground/70",
            )}
          >
            {label}
          </div>
        ))}
      </div>

      {/* Day grid */}
      <div className="grid min-h-0 flex-1 auto-rows-fr">
        {weeks.map((week, wi) => (
          <div key={wi} className="grid grid-cols-7 border-b border-border last:border-b-0">
            {week.map((day) => {
              const key = day.toISOString();
              const dayEvents = eventsForDay(events, day);
              const visible = dayEvents.slice(0, MAX_CHIPS);
              const overflow = dayEvents.length - visible.length;
              const outside = !isSameMonth(day, anchor);
              const today = isToday(day);
              const hasOverdue = dayEvents.some((e) => e.overdue);

              return (
                <div
                  key={key}
                  onDragOver={(e) => {
                    if (!onDropEvent) return;
                    e.preventDefault();
                    setDragOver(key);
                  }}
                  onDragLeave={() => setDragOver((k) => (k === key ? null : k))}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragOver(null);
                    const dragged = draggedRef.current;
                    if (dragged && onDropEvent) onDropEvent(dragged, day);
                    draggedRef.current = null;
                  }}
                  onDoubleClick={() => onQuickCreate?.(day)}
                  className={cn(
                    "group relative flex min-h-[104px] flex-col gap-1 border-r border-border p-1.5 last:border-r-0",
                    "transition-colors",
                    isWeekend(day) && "bg-surface-2/60",
                    outside && "bg-muted/40",
                    dragOver === key && "bg-brand-500/10 ring-2 ring-inset ring-brand-500/40",
                  )}
                >
                  {/* Date number — today is ringed in brand (§10.15.2). */}
                  <div className="flex shrink-0 items-center justify-between">
                    <button
                      type="button"
                      onClick={() => onSelectDay(day)}
                      className={cn(
                        "grid size-6 place-items-center rounded-full text-[12px] font-semibold transition-colors",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                        today
                          ? "bg-brand-500 text-brand-foreground"
                          : outside
                            ? "text-muted-foreground/50 hover:bg-surface-3"
                            : "text-foreground hover:bg-surface-3",
                      )}
                      aria-label={format(day, "EEEE d MMMM yyyy")}
                    >
                      {format(day, "d")}
                    </button>

                    {/* Days containing an overdue task get a danger dot ring. */}
                    {hasOverdue && (
                      <span
                        aria-label="Has overdue items"
                        title="Has overdue items"
                        className="size-1.5 rounded-full bg-danger ring-2 ring-danger/25"
                      />
                    )}
                  </div>

                  <div className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-hidden">
                    {visible.map((event) => (
                      <EventPill
                        key={`${event.source}-${event.id}`}
                        event={event}
                        variant="chip"
                        draggable={!!onDropEvent}
                        onDragStart={(_, ev) => {
                          draggedRef.current = ev;
                        }}
                        onClick={onSelectEvent}
                        onMouseEnter={onHoverEvent}
                        onMouseLeave={onHoverEnd}
                      />
                    ))}
                  </div>

                  {overflow > 0 && (
                    <button
                      type="button"
                      onClick={() => onSelectDay(day)}
                      className="shrink-0 self-end rounded px-1 text-[10.5px] font-semibold text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                    >
                      +{overflow} more
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
