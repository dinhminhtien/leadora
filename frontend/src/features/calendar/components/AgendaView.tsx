"use client";

/**
 * Agenda view — Website UI Blueprint §10.15.2 and §10.14.1.4.
 *
 * A chronological, grouped list — "excellent for daily standup". Same component
 * backs the Tasks module's Agenda view, scoped to whatever range the caller
 * passes, which is why grouping and empty handling live here rather than in the
 * calendar screen.
 */

import * as React from "react";
import { format, isToday, isTomorrow } from "date-fns";

import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/ui/states";
import { StatusPill, OverdueBadge } from "@/components/ui/status-pill";
import { PriorityFlag } from "@/components/ui/priority-flag";
import {
  groupByDay,
  type CalendarEvent,
} from "@/features/calendar/lib/calendar-model";
import type { StatusDomain } from "@/shared/design/status-tokens";

/** Maps an event source onto its canonical status domain (§2.7). */
function domainFor(source: CalendarEvent["source"]): StatusDomain {
  if (source === "task") return "task";
  if (source === "reminder") return "reminder";
  return "booking";
}

function dayLabel(day: Date): string {
  if (isToday(day)) return "Today";
  if (isTomorrow(day)) return "Tomorrow";
  return format(day, "EEEE d MMMM");
}

type AgendaViewProps = {
  events: CalendarEvent[];
  from: Date;
  to: Date;
  onSelectEvent: (event: CalendarEvent) => void;
  onCreate?: () => void;
};

export function AgendaView({
  events,
  from,
  to,
  onSelectEvent,
  onCreate,
}: AgendaViewProps) {
  const groups = React.useMemo(
    () => groupByDay(events, from, to),
    [events, from, to],
  );

  if (groups.length === 0) {
    return (
      <div className="rounded-lg border border-border bg-surface">
        <EmptyState
          title="Nothing scheduled"
          message="No tasks, reminders or bookings fall in this range."
          actionLabel={onCreate ? "New task" : undefined}
          onAction={onCreate}
        />
      </div>
    );
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto rounded-lg border border-border bg-surface">
      {groups.map(({ day, events: dayEvents }) => (
        <section key={day.toISOString()}>
          <h3
            className={cn(
              "sticky top-0 z-10 flex items-baseline gap-2 border-b border-border px-4 py-2",
              "bg-surface/95 backdrop-blur",
            )}
          >
            <span
              className={cn(
                "text-[13px] font-semibold",
                isToday(day) ? "text-brand-600 dark:text-brand-500" : "text-foreground",
              )}
            >
              {dayLabel(day)}
            </span>
            <span className="text-[11.5px] text-muted-foreground">
              {format(day, "d MMM")} · {dayEvents.length}{" "}
              {dayEvents.length === 1 ? "item" : "items"}
            </span>
          </h3>

          <ul className="divide-y divide-border">
            {dayEvents.map((event) => (
              <li key={`${event.source}-${event.id}`}>
                <button
                  type="button"
                  onClick={() => onSelectEvent(event)}
                  className={cn(
                    "flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors",
                    "hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500",
                  )}
                >
                  {/* Time column — mono so rows align down the page. */}
                  <span className="numeric w-16 shrink-0 text-[12px] font-medium text-muted-foreground">
                    {event.allDay ? "All day" : format(event.start, "HH:mm")}
                  </span>

                  {event.source === "task" && <PriorityFlag value={event.priority} />}

                  <span className="min-w-0 flex-1">
                    <span
                      className={cn(
                        "block truncate text-[13.5px] font-medium text-foreground",
                        event.status?.toUpperCase() === "COMPLETED" &&
                          "text-muted-foreground line-through",
                        event.status?.toUpperCase() === "CANCELLED" &&
                          "text-muted-foreground italic",
                      )}
                    >
                      {event.title}
                    </span>
                    {(event.relatedLabel || event.ownerName) && (
                      <span className="mt-0.5 block truncate text-[11.5px] text-muted-foreground">
                        {[event.relatedLabel, event.ownerName]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    )}
                  </span>

                  <span className="flex shrink-0 items-center gap-1.5">
                    {event.overdue && <OverdueBadge size="sm" />}
                    <StatusPill
                      size="sm"
                      domain={domainFor(event.source)}
                      value={event.status}
                    />
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
