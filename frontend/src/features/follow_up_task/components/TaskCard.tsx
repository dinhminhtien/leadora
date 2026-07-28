"use client";

/**
 * Task card + row — Website UI Blueprint §10.14.6 and §10.14.7.
 *
 * The single most reused surface in the module: the board, agenda, priority
 * lanes, manager groups and compact list all render tasks through this file, so
 * status and priority read identically wherever a task appears.
 *
 * Status visualisation (§10.14.6), all with a non-colour channel:
 * - **OPEN** — regular weight title, surface background.
 * - **COMPLETED** — muted title with **strikethrough**, filled checkbox, opacity .85.
 * - **CANCELLED** — muted **italic** title, **dashed** left border, no strikethrough
 *   (that is what distinguishes it from completed at a glance).
 * - **OVERDUE** — an `+ Overdue` badge beside the OPEN pill plus a 3px danger
 *   left stripe. Never a separate status.
 */

import * as React from "react";
import { format } from "date-fns";
import { Building2, CalendarClock, Check, User } from "lucide-react";

import { cn } from "@/lib/utils";
import { StatusPill, OverdueBadge } from "@/components/ui/status-pill";
import { PriorityFlag } from "@/components/ui/priority-flag";
import { isTaskOverdue } from "@/shared/design/status-tokens";
import type { Task } from "@/services/follow_up_task_service";

/** Activity types keep categorical colours that never imply a status. */
const ACTIVITY_META: Record<string, { label: string; className: string }> = {
  CALL: { label: "Call", className: "text-warning" },
  EMAIL: { label: "Email", className: "text-teal" },
  MEETING: { label: "Meeting", className: "text-info" },
  SITE_VISIT: { label: "Site visit", className: "text-warning" },
  FOLLOW_UP: { label: "Follow-up", className: "text-info" },
  TASK: { label: "Task", className: "text-muted-foreground" },
};

export function activityLabel(type?: string | null): string {
  return ACTIVITY_META[(type ?? "TASK").toUpperCase()]?.label ?? "Task";
}

/** The linked record, in the order the detail drawer prefers it. */
export function relatedLabel(task: Task): { kind: string; name: string } | null {
  if (task.dealName) return { kind: "Deal", name: task.dealName };
  if (task.customerName) return { kind: "Customer", name: task.customerName };
  if (task.leadName) return { kind: "Lead", name: task.leadName };
  return null;
}

export function dueLabel(task: Task): string | null {
  const raw = task.endAt ?? task.startAt;
  if (!raw) return null;
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return null;
  return format(d, "d MMM, HH:mm");
}

/* ------------------------------------------------------------------ *
 * Completion toggle
 * ------------------------------------------------------------------ */

function CompletionToggle({
  task,
  onComplete,
  disabled,
}: {
  task: Task;
  onComplete?: (task: Task) => void;
  disabled?: boolean;
}) {
  const status = (task.status ?? "OPEN").toUpperCase();
  const done = status === "COMPLETED";
  const cancelled = status === "CANCELLED";

  return (
    <button
      type="button"
      disabled={disabled || done || cancelled}
      onClick={(e) => {
        e.stopPropagation();
        onComplete?.(task);
      }}
      aria-label={done ? "Task completed" : "Mark task completed"}
      title={
        done
          ? "Completed"
          : cancelled
            ? "Cancelled"
            : "Mark completed"
      }
      className={cn(
        "grid size-5 shrink-0 place-items-center rounded-full border transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
        done
          ? "border-success bg-success text-white dark:text-success-bg"
          : cancelled
            ? "cursor-not-allowed border-dashed border-border text-transparent"
            : "border-border text-transparent hover:border-success hover:text-success",
      )}
    >
      <Check className="size-3" strokeWidth={3} />
    </button>
  );
}

/* ------------------------------------------------------------------ *
 * Card — used by board, priority lanes, manager groups
 * ------------------------------------------------------------------ */

type TaskCardProps = {
  task: Task;
  onOpen?: (task: Task) => void;
  onComplete?: (task: Task) => void;
  draggable?: boolean;
  onDragStart?: (e: React.DragEvent, task: Task) => void;
  className?: string;
};

export function TaskCard({
  task,
  onOpen,
  onComplete,
  draggable = false,
  onDragStart,
  className,
}: TaskCardProps) {
  const status = (task.status ?? "OPEN").toUpperCase();
  const completed = status === "COMPLETED";
  const cancelled = status === "CANCELLED";
  const overdue = isTaskOverdue(task);
  const related = relatedLabel(task);
  const due = dueLabel(task);

  return (
    <article
      draggable={draggable}
      onDragStart={draggable && onDragStart ? (e) => onDragStart(e, task) : undefined}
      onClick={() => onOpen?.(task)}
      className={cn(
        "group relative flex gap-2.5 rounded-lg border bg-surface p-3 text-left",
        "transition-[box-shadow,border-color] duration-[120ms]",
        onOpen && "cursor-pointer hover:border-brand-300 hover:shadow-elev-2",
        // CANCELLED gets a dashed border — the non-colour cue (§10.14.6).
        cancelled ? "border-dashed border-border opacity-70" : "border-border",
        completed && "opacity-85",
        draggable && "active:cursor-grabbing",
        className,
      )}
    >
      {/* Overdue stripe — 3px danger down the left edge. */}
      {overdue && (
        <span
          aria-hidden
          className="absolute inset-y-0 left-0 w-[3px] rounded-l-lg bg-danger"
        />
      )}

      <CompletionToggle task={task} onComplete={onComplete} />

      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex items-start gap-1.5">
          <PriorityFlag value={task.priority} className="mt-0.5" />
          <h4
            className={cn(
              "min-w-0 flex-1 text-[13px] font-medium leading-[18px] text-foreground",
              completed && "text-muted-foreground line-through",
              cancelled && "text-muted-foreground italic",
            )}
          >
            {task.title}
          </h4>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <StatusPill size="sm" domain="task" value={task.status} />
          {overdue && <OverdueBadge size="sm" />}
          <span
            className={cn(
              "text-[11px] font-medium",
              ACTIVITY_META[(task.activityType ?? "TASK").toUpperCase()]?.className,
            )}
          >
            {activityLabel(task.activityType)}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11.5px] text-muted-foreground">
          {due && (
            <span className={cn("inline-flex items-center gap-1", overdue && "text-danger")}>
              <CalendarClock aria-hidden className="size-3" />
              <span className="numeric">{due}</span>
            </span>
          )}
          {related && (
            <span className="inline-flex min-w-0 items-center gap-1">
              <Building2 aria-hidden className="size-3 shrink-0" />
              <span className="truncate">
                {related.kind}: {related.name}
              </span>
            </span>
          )}
          {task.assignedUserName && (
            <span className="inline-flex min-w-0 items-center gap-1">
              <User aria-hidden className="size-3 shrink-0" />
              <span className="truncate">{task.assignedUserName}</span>
            </span>
          )}
        </div>
      </div>
    </article>
  );
}

/* ------------------------------------------------------------------ *
 * Row — used by agenda and compact views
 * ------------------------------------------------------------------ */

export function TaskRow({
  task,
  onOpen,
  onComplete,
  /** `compact` is the 32px keyboard-triage row (§10.14.1.5). */
  variant = "default",
  className,
}: {
  task: Task;
  onOpen?: (task: Task) => void;
  onComplete?: (task: Task) => void;
  variant?: "default" | "compact";
  className?: string;
}) {
  const status = (task.status ?? "OPEN").toUpperCase();
  const completed = status === "COMPLETED";
  const cancelled = status === "CANCELLED";
  const overdue = isTaskOverdue(task);
  const related = relatedLabel(task);
  const due = dueLabel(task);
  const compact = variant === "compact";

  return (
    <div
      onClick={() => onOpen?.(task)}
      className={cn(
        "group relative flex items-center gap-2.5 border-b border-border px-3 transition-colors last:border-b-0",
        compact ? "h-8" : "h-12",
        onOpen && "cursor-pointer hover:bg-surface-2",
        className,
      )}
    >
      {overdue && (
        <span aria-hidden className="absolute inset-y-0 left-0 w-[3px] bg-danger" />
      )}

      <CompletionToggle task={task} onComplete={onComplete} />
      <PriorityFlag value={task.priority} />

      <span
        className={cn(
          "min-w-0 flex-1 truncate text-[13px] text-foreground",
          completed && "text-muted-foreground line-through",
          cancelled && "text-muted-foreground italic",
        )}
      >
        {task.title}
      </span>

      {!compact && related && (
        <span className="hidden min-w-0 max-w-[180px] truncate text-[11.5px] text-muted-foreground md:inline">
          {related.name}
        </span>
      )}

      {due && (
        <span
          className={cn(
            "numeric hidden shrink-0 text-[11.5px] sm:inline",
            overdue ? "font-medium text-danger" : "text-muted-foreground",
          )}
        >
          {due}
        </span>
      )}

      {!compact && (
        <StatusPill size="sm" domain="task" value={task.status} className="shrink-0" />
      )}
    </div>
  );
}
