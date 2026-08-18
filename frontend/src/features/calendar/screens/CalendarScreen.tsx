"use client";

/**
 * Calendar — Website UI Blueprint §10.15 (flagship sub-experience).
 *
 * Layout per §10.15.1: toolbar (nav · view switcher · today · filters · TZ chip)
 * over a left rail (mini-calendar + source/priority filters) and the main
 * surface.
 *
 * Views: **Month · Week · Day · Agenda**. Interactions: quick-create on an empty
 * slot, drag to reschedule, resize to change duration, hover preview with quick
 * actions, click to open the record, conflict indicators, working-hour band,
 * weekend tint and a live current-time line.
 *
 * **The status rule.** Only `OPEN / COMPLETED / CANCELLED` exist. `OVERDUE` is
 * computed and rendered as a visual flag. Nothing on this screen writes a
 * status the API doesn't already accept: rescheduling calls `PUT /tasks/{id}`
 * with new dates, and completing routes through the mandatory-note dialog.
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import {
  addDays,
  addMonths,
  addWeeks,
  format,
  startOfDay,
  subMonths,
  subWeeks,
} from "date-fns";
import { CalendarDays, ChevronLeft, ChevronRight, Globe, Plus } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/checkbox";
import { PageHeader } from "@/components/ui/page-header";
import { ErrorState } from "@/components/ui/states";
import { ListSkeleton } from "@/components/ui/skeletons";
import { toast } from "@/stores/toast_store";
import { apiErrorCopy } from "@/shared/design/error-messages";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { PAGE_META } from "@/app/routes/page_meta";
import {
  useResolveTask,
  useUpdateTaskById,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { MiniCalendar } from "@/features/calendar/components/MiniCalendar";
import { MonthView } from "@/features/calendar/components/MonthView";
import { TimeGridView } from "@/features/calendar/components/TimeGridView";
import { AgendaView } from "@/features/calendar/components/AgendaView";
import { EventPreviewCard } from "@/features/calendar/components/EventPreviewCard";
import { TaskCompleteDialog } from "@/features/follow_up_task/components/TaskCompleteDialog";
import {
  DEFAULT_CALENDAR_FILTERS,
  useCalendarEvents,
  type CalendarFilters,
} from "@/features/calendar/hooks/use_calendar_events";
import {
  visibleRange,
  weekDays,
  type CalendarEvent,
  type CalendarView,
} from "@/features/calendar/lib/calendar-model";
import type { Task } from "@/services/follow_up_task_service";

const VIEWS: { key: CalendarView; label: string; shortcut: string }[] = [
  { key: "month", label: "Month", shortcut: "m" },
  { key: "week", label: "Week", shortcut: "w" },
  { key: "day", label: "Day", shortcut: "d" },
  { key: "agenda", label: "Agenda", shortcut: "a" },
];

const PRIORITY_OPTIONS = ["HIGH", "MEDIUM", "LOW"];

const HOVER_DELAY_MS = 400; // §10.15.4

export function CalendarScreen() {
  const router = useRouter();
  const [view, setView] = React.useState<CalendarView>("month");
  const [anchor, setAnchor] = React.useState(() => startOfDay(new Date()));
  const [filters, setFilters] = React.useState<CalendarFilters>(
    DEFAULT_CALENDAR_FILTERS,
  );

  const [preview, setPreview] = React.useState<{
    event: CalendarEvent;
    x: number;
    y: number;
  } | null>(null);
  const hoverTimer = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const [completeTarget, setCompleteTarget] = React.useState<Task | null>(null);

  const { events, isLoading, isError, error, refetch } = useCalendarEvents(filters);
  // Id-per-call: the calendar mutates whichever event was dragged.
  const updateTask = useUpdateTaskById();
  const resolveTask = useResolveTask();

  const range = React.useMemo(() => visibleRange(view, anchor), [view, anchor]);

  /* ── Navigation ──────────────────────────────────────────────────────── */

  const goToday = React.useCallback(() => setAnchor(startOfDay(new Date())), []);

  const step = React.useCallback(
    (direction: 1 | -1) => {
      setAnchor((current) => {
        if (view === "month")
          return direction === 1 ? addMonths(current, 1) : subMonths(current, 1);
        if (view === "week")
          return direction === 1 ? addWeeks(current, 1) : subWeeks(current, 1);
        if (view === "day") return addDays(current, direction);
        return addDays(current, direction * 7);
      });
    },
    [view],
  );

  // §6.3 — `t` today, `w` week, `m` month. Scoped to this screen and ignored
  // while typing, matching the global shortcut contract.
  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      if (
        el &&
        (el.tagName === "INPUT" ||
          el.tagName === "TEXTAREA" ||
          el.isContentEditable)
      )
        return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      const key = e.key.toLowerCase();
      if (key === "t") { e.preventDefault(); goToday(); }
      else if (key === "m") { e.preventDefault(); setView("month"); }
      else if (key === "w") { e.preventDefault(); setView("week"); }
      else if (key === "a") { e.preventDefault(); setView("agenda"); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [goToday]);

  /* ── Hover preview ───────────────────────────────────────────────────── */

  const handleHover = (e: React.MouseEvent, event: CalendarEvent) => {
    const { clientX, clientY } = e;
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    hoverTimer.current = setTimeout(
      () => setPreview({ event, x: clientX, y: clientY }),
      HOVER_DELAY_MS,
    );
  };

  const handleHoverEnd = () => {
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    setPreview(null);
  };

  React.useEffect(
    () => () => {
      if (hoverTimer.current) clearTimeout(hoverTimer.current);
    },
    [],
  );

  /* ── Mutations ───────────────────────────────────────────────────────── */

  /** Reschedule preserves duration — moving an event never silently resizes it. */
  const moveEvent = async (event: CalendarEvent, newStart: Date) => {
    if (event.source !== "task") {
      toast.info("Only follow-up tasks can be rescheduled from the calendar.");
      return;
    }
    const durationMs = event.end.getTime() - event.start.getTime();
    const newEnd = new Date(newStart.getTime() + durationMs);
    try {
      await updateTask.mutateAsync({
        taskId: event.id,
        // Dates only — nothing else about the task changes.
        payload: {
          startAt: newStart.toISOString(),
          endAt: newEnd.toISOString(),
        },
      });
      toast.success("Task rescheduled");
    } catch (err) {
      toast.error(apiErrorCopy(err));
    }
  };

  const resizeEvent = async (event: CalendarEvent, newEnd: Date) => {
    if (event.source !== "task") return;
    try {
      await updateTask.mutateAsync({
        taskId: event.id,
        payload: {
          startAt: event.start.toISOString(),
          endAt: newEnd.toISOString(),
        },
      });
      toast.success("Duration updated");
    } catch (err) {
      toast.error(apiErrorCopy(err));
    }
  };

  const openEvent = (event: CalendarEvent) => {
    setPreview(null);
    if (event.source === "task") {
      router.push(`${ROUTE_PATHS.followUpTasks}?highlight=${event.id}`);
    } else if (event.source === "reminder") {
      router.push(`${ROUTE_PATHS.reminders}?highlight=${event.id}`);
    } else {
      router.push(`${ROUTE_PATHS.bookingConfirmation}?highlight=${event.id}`);
    }
  };

  /** Completing requires the mandatory result note — route through the dialog. */
  const requestComplete = (event: CalendarEvent) => {
    setPreview(null);
    if (event.source === "task") setCompleteTarget(event.raw as Task);
  };

  const quickCreate = (slot: Date) => {
    const iso = encodeURIComponent(slot.toISOString());
    router.push(`${ROUTE_PATHS.manageFollowUpTasks}?new=1&start=${iso}`);
  };

  /* ── Conflicts for the hovered event ─────────────────────────────────── */

  const previewConflicts = React.useMemo(() => {
    if (!preview) return [];
    const e = preview.event;
    return events.filter(
      (o) =>
        o.id !== e.id &&
        !o.allDay &&
        !e.allDay &&
        o.start.getTime() < e.end.getTime() &&
        o.end.getTime() > e.start.getTime(),
    );
  }, [preview, events]);

  const label =
    view === "month"
      ? format(anchor, "MMMM yyyy")
      : view === "week"
        ? `${format(range.from, "d MMM")} – ${format(range.to, "d MMM yyyy")}`
        : view === "day"
          ? format(anchor, "EEEE d MMMM yyyy")
          : `${format(range.from, "d MMM")} – ${format(range.to, "d MMM")}`;

  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <div className="flex min-h-[calc(100vh-8rem)] flex-col">
      <PageHeader
        {...PAGE_META.calendar}
        actions={
          <Button
            variant="primary"
            leftIcon={<Plus className="size-4" />}
            onClick={() => quickCreate(new Date())}
          >
            New task
          </Button>
        }
      />

      {/* ── Toolbar (§10.15.1) ──────────────────────────────────────────── */}
      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface p-2">
        <div className="flex items-center gap-1">
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label="Previous period"
            onClick={() => step(-1)}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button size="sm" variant="secondary" onClick={goToday} title="Today — T">
            Today
          </Button>
          <Button
            size="icon-sm"
            variant="ghost"
            aria-label="Next period"
            onClick={() => step(1)}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>

        <p className="ml-1 min-w-0 flex-1 truncate text-[15px] font-semibold text-foreground">
          {label}
        </p>

        {/* View switcher */}
        <div
          role="tablist"
          aria-label="Calendar view"
          className="flex items-center gap-0.5 rounded-md border border-border bg-muted p-0.5"
        >
          {VIEWS.map((v) => (
            <button
              key={v.key}
              role="tab"
              aria-selected={view === v.key}
              onClick={() => setView(v.key)}
              title={`${v.label} — ${v.shortcut.toUpperCase()}`}
              className={cn(
                "rounded px-2.5 py-1 text-[12.5px] font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                view === v.key
                  ? "bg-surface text-foreground shadow-elev-1"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {v.label}
            </button>
          ))}
        </div>

        {/* Timezone chip (§10.15.1) */}
        <span
          title={`All times shown in ${timezone}`}
          className="hidden items-center gap-1 rounded-md border border-border bg-muted px-2 py-1 text-[11px] font-medium text-muted-foreground lg:flex"
        >
          <Globe className="size-3" />
          {timezone}
        </span>
      </div>

      {/* ── Rail + surface ──────────────────────────────────────────────── */}
      <div className="flex min-h-0 flex-1 gap-4">
        <aside className="hidden w-[232px] shrink-0 flex-col gap-3 xl:flex">
          <MiniCalendar
            anchor={anchor}
            selected={anchor}
            events={events}
            onSelect={(day) => {
              setAnchor(day);
              if (view === "month") setView("day");
            }}
          />

          <div className="rounded-lg border border-border bg-surface p-3">
            <p className="mb-2 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              Show
            </p>
            <div className="space-y-2">
              {(
                [
                  ["task", "Tasks"],
                  ["reminder", "Reminders"],
                  ["booking", "Bookings"],
                ] as const
              ).map(([key, label]) => (
                <label
                  key={key}
                  className="flex cursor-pointer items-center gap-2 text-[13px] text-foreground"
                >
                  <Checkbox
                    checked={filters.sources[key]}
                    onCheckedChange={(checked) =>
                      setFilters((f) => ({
                        ...f,
                        sources: { ...f.sources, [key]: checked === true },
                      }))
                    }
                  />
                  {label}
                </label>
              ))}
            </div>

            <p className="mb-2 mt-4 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              Task priority
            </p>
            <div className="space-y-2">
              {PRIORITY_OPTIONS.map((p) => (
                <label
                  key={p}
                  className="flex cursor-pointer items-center gap-2 text-[13px] capitalize text-foreground"
                >
                  <Checkbox
                    checked={filters.priorities.includes(p)}
                    onCheckedChange={(checked) =>
                      setFilters((f) => ({
                        ...f,
                        priorities:
                          checked === true
                            ? [...f.priorities, p]
                            : f.priorities.filter((x) => x !== p),
                      }))
                    }
                  />
                  {p.toLowerCase()}
                </label>
              ))}
            </div>
          </div>
        </aside>

        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {isError ? (
            <ErrorState error={error} onRetry={refetch} />
          ) : isLoading ? (
            <ListSkeleton count={6} />
          ) : view === "month" ? (
            <MonthView
              anchor={anchor}
              events={events}
              onSelectDay={(day) => {
                setAnchor(day);
                setView("day");
              }}
              onSelectEvent={openEvent}
              onQuickCreate={quickCreate}
              onDropEvent={(event, day) => {
                // Keep the time-of-day, change only the date.
                const next = new Date(day);
                next.setHours(
                  event.start.getHours(),
                  event.start.getMinutes(),
                  0,
                  0,
                );
                void moveEvent(event, next);
              }}
              onHoverEvent={handleHover}
              onHoverEnd={handleHoverEnd}
            />
          ) : view === "agenda" ? (
            <AgendaView
              events={events}
              from={range.from}
              to={range.to}
              onSelectEvent={openEvent}
              onCreate={() => quickCreate(new Date())}
            />
          ) : (
            <TimeGridView
              days={view === "week" ? weekDays(anchor) : [anchor]}
              events={events}
              onSelectEvent={openEvent}
              onQuickCreate={quickCreate}
              onMoveEvent={(event, newStart) => void moveEvent(event, newStart)}
              onResizeEvent={(event, newEnd) => void resizeEvent(event, newEnd)}
              onHoverEvent={handleHover}
              onHoverEnd={handleHoverEnd}
            />
          )}
        </div>
      </div>

      {/* Hover preview, positioned at the pointer and clamped to the viewport. */}
      {preview && (
        <div
          className="pointer-events-none fixed z-50"
          style={{
            left: Math.min(preview.x + 12, (typeof window !== "undefined" ? window.innerWidth : 1200) - 320),
            top: Math.min(preview.y + 12, (typeof window !== "undefined" ? window.innerHeight : 800) - 280),
          }}
        >
          <div className="pointer-events-auto" onMouseLeave={handleHoverEnd}>
            <EventPreviewCard
              event={preview.event}
              conflicts={previewConflicts}
              onOpen={openEvent}
              onComplete={requestComplete}
            />
          </div>
        </div>
      )}

      <TaskCompleteDialog
        task={completeTarget}
        open={!!completeTarget}
        onOpenChange={(open) => !open && setCompleteTarget(null)}
        onCompleted={() => {
          setCompleteTarget(null);
          refetch();
        }}
        resolveTask={resolveTask}
      />

      {/* Empty-state hint on an otherwise blank calendar. */}
      {!isLoading && !isError && events.length === 0 && view !== "agenda" && (
        <p className="mt-3 flex items-center justify-center gap-2 text-[13px] text-muted-foreground">
          <CalendarDays className="size-4" />
          Nothing scheduled in this range.
        </p>
      )}
    </div>
  );
}
