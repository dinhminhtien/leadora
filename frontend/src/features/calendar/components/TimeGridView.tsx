"use client";

/**
 * Week and Day time grids — Website UI Blueprint §10.15.2.
 *
 * One component serves both because they differ only in column count: Week is
 * 7 columns × 24h, Day is 1 column × 24h with denser event detail. Duplicating
 * the hour ruler, the working-hour band, the current-time line and the overlap
 * layout across two files would guarantee they drift.
 *
 * Implements from §10.15.2 / §10.15.4:
 * - Hour rows 48px (`HOUR_HEIGHT`).
 * - Working-hour band 08–18 tinted `brand-50`; weekend columns `surface-2`.
 * - Current-time line: 2px `danger` with a dot at the left edge, live-updating.
 * - Drag to another day/hour → reschedule. Resize the bottom edge → duration.
 * - Overlapping events lay out side-by-side with a conflict ribbon.
 */

import * as React from "react";
import { format, isToday, isSameDay } from "date-fns";

import { cn } from "@/lib/utils";
import { EventPill } from "@/features/calendar/components/EventPill";
import {
  DAY_END_HOUR,
  DAY_START_HOUR,
  HOUR_HEIGHT,
  WORK_END_HOUR,
  WORK_START_HOUR,
  eventsForDay,
  isWeekend,
  layoutDayColumn,
  type CalendarEvent,
} from "@/features/calendar/lib/calendar-model";

const HOURS = Array.from(
  { length: DAY_END_HOUR - DAY_START_HOUR },
  (_, i) => DAY_START_HOUR + i,
);

const GUTTER_WIDTH = 56;

type TimeGridViewProps = {
  days: Date[];
  events: CalendarEvent[];
  onSelectEvent: (event: CalendarEvent) => void;
  onQuickCreate?: (slot: Date) => void;
  /** Reschedule to a new start instant, preserving duration. */
  onMoveEvent?: (event: CalendarEvent, newStart: Date) => void;
  /** Change duration by dragging the bottom edge. */
  onResizeEvent?: (event: CalendarEvent, newEnd: Date) => void;
  onHoverEvent?: (e: React.MouseEvent, event: CalendarEvent) => void;
  onHoverEnd?: () => void;
};

export function TimeGridView({
  days,
  events,
  onSelectEvent,
  onQuickCreate,
  onMoveEvent,
  onResizeEvent,
  onHoverEvent,
  onHoverEnd,
}: TimeGridViewProps) {
  const [now, setNow] = React.useState(() => new Date());
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const draggedRef = React.useRef<CalendarEvent | null>(null);
  /**
   * Resize lives in state, not a ref, because the render reads it to draw the
   * live preview height. A ref read during render is not safe under concurrent
   * rendering — the preview would be able to lag the pointer by a frame.
   */
  const [resize, setResize] = React.useState<{
    event: CalendarEvent;
    startY: number;
    startHeight: number;
  } | null>(null);
  const [resizePreview, setResizePreview] = React.useState<number | null>(null);

  // Current-time line ticks each minute; a stale line is worse than none.
  React.useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(id);
  }, []);

  // Open scrolled to the working day rather than to 00:00, which is empty.
  React.useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = (WORK_START_HOUR - 1) * HOUR_HEIGHT;
    }
  }, []);

  const allDayByDay = React.useMemo(
    () => days.map((d) => eventsForDay(events, d).filter((e) => e.allDay)),
    [days, events],
  );
  const hasAllDay = allDayByDay.some((list) => list.length > 0);

  /** Converts a pointer offset inside a column into a 15-minute-snapped time. */
  const slotFromOffset = (day: Date, offsetY: number): Date => {
    const totalMinutes = (offsetY / HOUR_HEIGHT) * 60;
    const snapped = Math.round(totalMinutes / 15) * 15;
    const clamped = Math.max(0, Math.min(snapped, 24 * 60 - 15));
    const d = new Date(day);
    d.setHours(0, 0, 0, 0);
    d.setMinutes(clamped);
    return d;
  };

  // Resize is pointer-driven rather than HTML5 drag: HTML5 drag has no
  // continuous position feedback, so the user couldn't see the new duration.
  React.useEffect(() => {
    if (!resize) return;

    // The latest preview height is tracked locally so the pointerup handler
    // reads the final value without this effect re-subscribing on every move.
    let latest = resize.startHeight;

    const onMove = (e: PointerEvent) => {
      latest = Math.max(20, resize.startHeight + (e.clientY - resize.startY));
      setResizePreview(latest);
    };

    const onUp = () => {
      const { event } = resize;
      setResize(null);
      setResizePreview(null);
      if (!onResizeEvent) return;
      const minutes = Math.max(
        15,
        Math.round(((latest / HOUR_HEIGHT) * 60) / 15) * 15,
      );
      onResizeEvent(event, new Date(event.start.getTime() + minutes * 60_000));
    };

    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp, { once: true });
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
  }, [resize, onResizeEvent]);

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border bg-surface">
      {/* ── Column header ─────────────────────────────────────────────── */}
      <div className="flex shrink-0 border-b border-border bg-muted">
        <div style={{ width: GUTTER_WIDTH }} className="shrink-0" />
        {days.map((day) => (
          <div
            key={day.toISOString()}
            className={cn(
              "flex flex-1 flex-col items-center gap-0.5 border-l border-border py-2",
              isWeekend(day) && "bg-surface-2/60",
            )}
          >
            <span className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              {format(day, "EEE")}
            </span>
            <span
              className={cn(
                "grid size-7 place-items-center rounded-full text-[13px] font-semibold",
                isToday(day)
                  ? "bg-brand-500 text-brand-foreground"
                  : "text-foreground",
              )}
            >
              {format(day, "d")}
            </span>
          </div>
        ))}
      </div>

      {/* ── All-day band (bookings live here) ─────────────────────────── */}
      {hasAllDay && (
        <div className="flex shrink-0 border-b border-border bg-surface-2/40">
          <div
            style={{ width: GUTTER_WIDTH }}
            className="shrink-0 py-1.5 pr-2 text-right text-[10px] font-semibold uppercase tracking-wide text-muted-foreground"
          >
            All day
          </div>
          {days.map((day, i) => (
            <div
              key={day.toISOString()}
              className="flex min-w-0 flex-1 flex-col gap-0.5 border-l border-border p-1"
            >
              {allDayByDay[i].map((event) => (
                <EventPill
                  key={`${event.source}-${event.id}`}
                  event={event}
                  variant="chip"
                  onClick={onSelectEvent}
                  onMouseEnter={onHoverEvent}
                  onMouseLeave={onHoverEnd}
                />
              ))}
            </div>
          ))}
        </div>
      )}

      {/* ── Scrollable hour grid ──────────────────────────────────────── */}
      <div ref={scrollRef} className="relative min-h-0 flex-1 overflow-y-auto">
        <div className="flex" style={{ height: HOURS.length * HOUR_HEIGHT }}>
          {/* Hour gutter */}
          <div style={{ width: GUTTER_WIDTH }} className="relative shrink-0">
            {HOURS.map((h) => (
              <div
                key={h}
                style={{ height: HOUR_HEIGHT }}
                className="relative border-b border-transparent"
              >
                <span className="absolute -top-1.5 right-2 numeric text-[10.5px] text-muted-foreground">
                  {h === 0 ? "" : format(new Date(2000, 0, 1, h), "HH:mm")}
                </span>
              </div>
            ))}
          </div>

          {/* Day columns */}
          {days.map((day) => {
            const positioned = layoutDayColumn(eventsForDay(events, day));
            const showNowLine = isSameDay(day, now);

            return (
              <div
                key={day.toISOString()}
                className={cn(
                  "relative min-w-0 flex-1 border-l border-border",
                  isWeekend(day) && "bg-surface-2/40",
                )}
                onDragOver={(e) => {
                  if (onMoveEvent) e.preventDefault();
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  const dragged = draggedRef.current;
                  if (!dragged || !onMoveEvent) return;
                  const rect = e.currentTarget.getBoundingClientRect();
                  onMoveEvent(dragged, slotFromOffset(day, e.clientY - rect.top));
                  draggedRef.current = null;
                }}
                onDoubleClick={(e) => {
                  if (!onQuickCreate) return;
                  const rect = e.currentTarget.getBoundingClientRect();
                  onQuickCreate(slotFromOffset(day, e.clientY - rect.top));
                }}
              >
                {/* Working-hour band (§10.15.2) */}
                <div
                  aria-hidden
                  className="pointer-events-none absolute inset-x-0 bg-brand-500/[0.06]"
                  style={{
                    top: WORK_START_HOUR * HOUR_HEIGHT,
                    height: (WORK_END_HOUR - WORK_START_HOUR) * HOUR_HEIGHT,
                  }}
                />

                {/* Hour rules */}
                {HOURS.map((h) => (
                  <div
                    key={h}
                    style={{ height: HOUR_HEIGHT }}
                    className="border-b border-border/60"
                  />
                ))}

                {/* Positioned events */}
                {positioned.map(({ event, top, height, column, columns, conflicts }) => {
                  const isResizing = resize?.event.id === event.id;
                  return (
                    <div
                      key={`${event.source}-${event.id}`}
                      className="absolute px-0.5"
                      style={{
                        top,
                        height: isResizing && resizePreview ? resizePreview : height,
                        left: `${(column / columns) * 100}%`,
                        width: `${(1 / columns) * 100}%`,
                      }}
                    >
                      <div className="relative h-full">
                        <EventPill
                          event={event}
                          variant="block"
                          conflicts={conflicts}
                          draggable={!!onMoveEvent}
                          onDragStart={(_, ev) => {
                            draggedRef.current = ev;
                          }}
                          onClick={onSelectEvent}
                          onMouseEnter={onHoverEvent}
                          onMouseLeave={onHoverEnd}
                          className="h-full items-start"
                        />
                        {/* Resize handle — bottom edge (§10.15.4). */}
                        {onResizeEvent && !event.allDay && (
                          <span
                            role="separator"
                            aria-label="Resize event"
                            onPointerDown={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              setResize({
                                event,
                                startY: e.clientY,
                                startHeight: height,
                              });
                              setResizePreview(height);
                            }}
                            className="absolute inset-x-0 bottom-0 h-1.5 cursor-ns-resize rounded-b-md opacity-0 transition-opacity hover:bg-foreground/20 hover:opacity-100"
                          />
                        )}
                      </div>
                    </div>
                  );
                })}

                {/* Current-time indicator (§10.15.4). */}
                {showNowLine && (
                  <div
                    aria-hidden
                    className="pointer-events-none absolute inset-x-0 z-20 flex items-center"
                    style={{
                      top: (now.getHours() + now.getMinutes() / 60) * HOUR_HEIGHT,
                    }}
                  >
                    <span className="-ml-1 size-2 shrink-0 rounded-full bg-danger" />
                    <span className="h-0.5 flex-1 bg-danger" />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
