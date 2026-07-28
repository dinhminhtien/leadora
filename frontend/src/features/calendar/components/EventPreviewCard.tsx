"use client";

/**
 * Event preview — Website UI Blueprint §10.15.4 ("Task Preview Popover").
 *
 * Shown after a 400ms hover. Carries title, status, priority, owner, related
 * chips and quick actions (Complete · Snooze · Open), plus the conflict list
 * when the event overlaps others.
 *
 * **Quick actions respect the server's rules.** "Complete" is offered only for
 * an OPEN task, because `PATCH /tasks/{id}/resolve` rejects an already-resolved
 * task with `TASK_ALREADY_RESOLVED` — and it routes through the completion-note
 * dialog rather than firing directly, because the note is mandatory
 * (`TASK_COMPLETION_NOTE_REQUIRED`).
 */

import * as React from "react";
import { format } from "date-fns";
import { ArrowUpRight, CheckCircle2, Clock } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { StatusPill, OverdueBadge } from "@/components/ui/status-pill";
import { PriorityChip } from "@/components/ui/priority-flag";
import type { CalendarEvent } from "@/features/calendar/lib/calendar-model";
import type { StatusDomain } from "@/shared/design/status-tokens";

function domainFor(source: CalendarEvent["source"]): StatusDomain {
  if (source === "task") return "task";
  if (source === "reminder") return "reminder";
  return "booking";
}

type EventPreviewCardProps = {
  event: CalendarEvent;
  conflicts?: CalendarEvent[];
  onOpen: (event: CalendarEvent) => void;
  onComplete?: (event: CalendarEvent) => void;
  onSnooze?: (event: CalendarEvent, until: "hour" | "tomorrow") => void;
  className?: string;
  style?: React.CSSProperties;
};

export function EventPreviewCard({
  event,
  conflicts = [],
  onOpen,
  onComplete,
  onSnooze,
  className,
  style,
}: EventPreviewCardProps) {
  const status = event.status?.toUpperCase() ?? "";
  const isOpenTask = event.source === "task" && status === "OPEN";

  return (
    <div
      role="dialog"
      aria-label={`Preview: ${event.title}`}
      style={style}
      className={cn(
        "w-[300px] rounded-lg border border-border bg-popover p-3 shadow-elev-3",
        className,
      )}
    >
      <div className="mb-2 flex items-start gap-2">
        <p className="min-w-0 flex-1 text-[13.5px] font-semibold leading-5 text-foreground">
          {event.title}
        </p>
      </div>

      <div className="mb-2.5 flex flex-wrap items-center gap-1.5">
        <StatusPill size="sm" domain={domainFor(event.source)} value={event.status} />
        {event.overdue && <OverdueBadge size="sm" />}
        {event.source === "task" && <PriorityChip size="sm" value={event.priority} />}
      </div>

      <dl className="space-y-1 text-[12px]">
        <Row
          label="When"
          value={
            event.allDay
              ? `${format(event.start, "d MMM")} – ${format(event.end, "d MMM")}`
              : `${format(event.start, "EEE d MMM, HH:mm")} – ${format(event.end, "HH:mm")}`
          }
        />
        {event.ownerName && <Row label="Owner" value={event.ownerName} />}
        {event.relatedLabel && <Row label="Related" value={event.relatedLabel} />}
      </dl>

      {conflicts.length > 0 && (
        <div className="mt-2.5 rounded-md border border-danger/30 bg-danger-bg/40 p-2">
          <p className="mb-1 text-[11px] font-semibold text-danger">
            Overlaps {conflicts.length} other{" "}
            {conflicts.length === 1 ? "event" : "events"}
          </p>
          <ul className="space-y-0.5">
            {conflicts.slice(0, 3).map((c) => (
              <li
                key={`${c.source}-${c.id}`}
                className="truncate text-[11px] text-muted-foreground"
              >
                {c.allDay ? "All day" : format(c.start, "HH:mm")} · {c.title}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-1.5">
        <Button
          size="xs"
          variant="secondary"
          onClick={() => onOpen(event)}
          rightIcon={<ArrowUpRight className="size-3" />}
        >
          Open
        </Button>
        {isOpenTask && onComplete && (
          <Button
            size="xs"
            variant="success"
            onClick={() => onComplete(event)}
            leftIcon={<CheckCircle2 className="size-3.5" />}
          >
            Complete
          </Button>
        )}
        {isOpenTask && onSnooze && (
          <Button
            size="xs"
            variant="ghost"
            onClick={() => onSnooze(event, "tomorrow")}
            leftIcon={<Clock className="size-3.5" />}
          >
            Tomorrow
          </Button>
        )}
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="w-14 shrink-0 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 flex-1 truncate text-foreground">{value}</dd>
    </div>
  );
}
