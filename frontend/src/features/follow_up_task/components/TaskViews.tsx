"use client";

/**
 * Follow-up Task views — Website UI Blueprint §10.14.1.
 *
 * The seven non-calendar arrangements live together because they share the same
 * card/row vocabulary and the same drag semantics; splitting them across seven
 * files would mostly duplicate imports. The calendar view reuses the existing
 * `/calendar` components rather than reimplementing a second calendar.
 *
 * Drag rules, and why they are conservative:
 * - **Board** drag changes *status*. Dropping on COMPLETED opens the mandatory
 *   completion-note dialog rather than writing the status directly, because
 *   `PATCH /tasks/{id}/resolve` requires a note.
 * - **Priority lanes** drag changes *priority* via `PUT /tasks/{id}`.
 * - Nothing drags into a state the API would refuse.
 */

import * as React from "react";
import { ChevronDown, ChevronRight, Inbox } from "lucide-react";

import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/ui/states";
import { StatusPill } from "@/components/ui/status-pill";
import { PriorityChip } from "@/components/ui/priority-flag";
import { TaskCard, TaskRow } from "@/features/follow_up_task/components/TaskCard";
import {
  AGENDA_BUCKET_LABELS,
  BOARD_COLUMNS,
  BOARD_COLUMN_LABELS,
  PRIORITY_LANES,
  groupByAgendaBucket,
  groupByOwner,
  groupByPriority,
  groupByStatus,
  sortByDue,
  sortForPersonal,
  splitOverdue,
  type BoardColumnId,
  type PriorityLaneId,
} from "@/features/follow_up_task/lib/task-views";
import type { Task } from "@/services/follow_up_task_service";

type ViewHandlers = {
  onOpen: (task: Task) => void;
  onComplete: (task: Task) => void;
  /** Board drop — status change. `COMPLETED` routes through the note dialog. */
  onStatusChange?: (task: Task, status: BoardColumnId) => void;
  /** Priority-lane drop. */
  onPriorityChange?: (task: Task, priority: PriorityLaneId) => void;
};

/* ------------------------------------------------------------------ *
 * Board view — three columns, exactly three statuses
 * ------------------------------------------------------------------ */

export function TaskBoardView({
  tasks,
  onOpen,
  onComplete,
  onStatusChange,
}: { tasks: Task[] } & ViewHandlers) {
  const grouped = React.useMemo(() => groupByStatus(tasks), [tasks]);
  const dragged = React.useRef<Task | null>(null);
  const [dragOver, setDragOver] = React.useState<BoardColumnId | null>(null);

  return (
    <div className="grid min-h-0 flex-1 gap-3 md:grid-cols-3">
      {BOARD_COLUMNS.map((columnId) => {
        const columnTasks = grouped[columnId];
        // §10.14.1.2 — OVERDUE is a sub-band inside OPEN, not a fourth column.
        const { overdue, upcoming } =
          columnId === "OPEN"
            ? splitOverdue(columnTasks)
            : { overdue: [] as Task[], upcoming: columnTasks };

        return (
          <section
            key={columnId}
            onDragOver={(e) => {
              if (!onStatusChange) return;
              e.preventDefault();
              setDragOver(columnId);
            }}
            onDragLeave={() => setDragOver((c) => (c === columnId ? null : c))}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(null);
              const task = dragged.current;
              dragged.current = null;
              if (task && onStatusChange && task.status?.toUpperCase() !== columnId) {
                onStatusChange(task, columnId);
              }
            }}
            className={cn(
              "flex min-h-[240px] flex-col rounded-lg border bg-surface-2/40 transition-colors",
              dragOver === columnId
                ? "border-brand-500 bg-brand-500/5"
                : "border-border",
            )}
          >
            <header className="flex shrink-0 items-center justify-between gap-2 border-b border-border px-3 py-2">
              <span className="flex items-center gap-2">
                <StatusPill size="sm" domain="task" value={columnId} />
                <span className="numeric text-[11.5px] font-semibold text-muted-foreground">
                  {columnTasks.length}
                </span>
              </span>
              <span className="sr-only">{BOARD_COLUMN_LABELS[columnId]} column</span>
            </header>

            <div className="min-h-0 flex-1 space-y-2 overflow-y-auto p-2">
              {columnTasks.length === 0 && (
                <p className="px-2 py-8 text-center text-[12px] text-muted-foreground">
                  Nothing here
                </p>
              )}

              {overdue.length > 0 && (
                <div className="rounded-md border-l-[3px] border-danger bg-danger/[0.06] p-1.5">
                  <p className="mb-1.5 px-1 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-danger">
                    Overdue · {overdue.length}
                  </p>
                  <div className="space-y-2">
                    {sortByDue(overdue).map((task) => (
                      <TaskCard
                        key={task.taskId}
                        task={task}
                        onOpen={onOpen}
                        onComplete={onComplete}
                        draggable={!!onStatusChange}
                        onDragStart={(_, t) => {
                          dragged.current = t;
                        }}
                      />
                    ))}
                  </div>
                </div>
              )}

              {sortByDue(upcoming).map((task) => (
                <TaskCard
                  key={task.taskId}
                  task={task}
                  onOpen={onOpen}
                  onComplete={onComplete}
                  draggable={!!onStatusChange}
                  onDragStart={(_, t) => {
                    dragged.current = t;
                  }}
                />
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Agenda view — Today · Tomorrow · This week · Later · No date
 * ------------------------------------------------------------------ */

export function TaskAgendaView({
  tasks,
  onOpen,
  onComplete,
}: { tasks: Task[] } & ViewHandlers) {
  const groups = React.useMemo(() => groupByAgendaBucket(tasks), [tasks]);

  if (groups.size === 0) {
    return (
      <EmptyState
        icon={Inbox}
        title="Nothing scheduled"
        message="Tasks you create or are assigned will appear here."
      />
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface">
      {[...groups.entries()].map(([bucket, bucketTasks]) => (
        <section key={bucket}>
          <h3
            className={cn(
              "sticky top-0 z-10 flex items-baseline gap-2 border-b border-border px-3 py-2 backdrop-blur",
              bucket === "overdue" ? "bg-danger/[0.08]" : "bg-surface/95",
            )}
          >
            <span
              className={cn(
                "text-[12.5px] font-semibold",
                bucket === "overdue" ? "text-danger" : "text-foreground",
              )}
            >
              {AGENDA_BUCKET_LABELS[bucket]}
            </span>
            <span className="numeric text-[11.5px] text-muted-foreground">
              {bucketTasks.length}
            </span>
          </h3>
          {sortByDue(bucketTasks).map((task) => (
            <TaskRow
              key={task.taskId}
              task={task}
              onOpen={onOpen}
              onComplete={onComplete}
            />
          ))}
        </section>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Compact view — 32px rows, keyboard-first triage
 * ------------------------------------------------------------------ */

export function TaskCompactView({
  tasks,
  onOpen,
  onComplete,
}: { tasks: Task[] } & ViewHandlers) {
  if (tasks.length === 0) {
    return <EmptyState icon={Inbox} title="No tasks match these filters" variant="filter" />;
  }
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface">
      {sortByDue(tasks).map((task) => (
        <TaskRow
          key={task.taskId}
          task={task}
          variant="compact"
          onOpen={onOpen}
          onComplete={onComplete}
        />
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Personal view — "My day"
 * ------------------------------------------------------------------ */

export function TaskPersonalView({
  tasks,
  onOpen,
  onComplete,
}: { tasks: Task[] } & ViewHandlers) {
  const ordered = React.useMemo(() => sortForPersonal(tasks), [tasks]);

  if (ordered.length === 0) {
    return (
      <EmptyState
        title="You're all clear"
        message="Nothing assigned to you needs attention right now."
      />
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-2">
      {ordered.map((task) => (
        <TaskCard
          key={task.taskId}
          task={task}
          onOpen={onOpen}
          onComplete={onComplete}
          // Larger check-off targets are the point of this view (§10.14.1.7).
          className="p-4"
        />
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Priority view — lanes, drag to re-prioritise
 * ------------------------------------------------------------------ */

export function TaskPriorityView({
  tasks,
  onOpen,
  onComplete,
  onPriorityChange,
}: { tasks: Task[] } & ViewHandlers) {
  const grouped = React.useMemo(() => groupByPriority(tasks), [tasks]);
  const dragged = React.useRef<Task | null>(null);
  const [dragOver, setDragOver] = React.useState<PriorityLaneId | null>(null);
  // Low is collapsed by default per §10.14.1.8 — it is the lane you check last.
  const [collapsed, setCollapsed] = React.useState<Set<PriorityLaneId>>(
    () => new Set<PriorityLaneId>(["LOW"]),
  );

  const toggle = (lane: PriorityLaneId) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(lane)) next.delete(lane);
      else next.add(lane);
      return next;
    });

  return (
    <div className="space-y-3">
      {PRIORITY_LANES.map((lane) => {
        const laneTasks = grouped[lane];
        const isCollapsed = collapsed.has(lane);

        return (
          <section
            key={lane}
            onDragOver={(e) => {
              if (!onPriorityChange) return;
              e.preventDefault();
              setDragOver(lane);
            }}
            onDragLeave={() => setDragOver((l) => (l === lane ? null : l))}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(null);
              const task = dragged.current;
              dragged.current = null;
              if (task && onPriorityChange && task.priority?.toUpperCase() !== lane) {
                onPriorityChange(task, lane);
              }
            }}
            className={cn(
              "rounded-lg border bg-surface transition-colors",
              dragOver === lane ? "border-brand-500 bg-brand-500/5" : "border-border",
            )}
          >
            <button
              type="button"
              onClick={() => toggle(lane)}
              aria-expanded={!isCollapsed}
              className="flex w-full items-center gap-2 px-3 py-2 text-left transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500"
            >
              {isCollapsed ? (
                <ChevronRight className="size-4 text-muted-foreground" />
              ) : (
                <ChevronDown className="size-4 text-muted-foreground" />
              )}
              <PriorityChip size="sm" value={lane} />
              <span className="numeric text-[11.5px] text-muted-foreground">
                {laneTasks.length}
              </span>
            </button>

            {!isCollapsed && (
              <div className="grid gap-2 border-t border-border p-2 sm:grid-cols-2 xl:grid-cols-3">
                {laneTasks.length === 0 ? (
                  <p className="col-span-full px-2 py-4 text-center text-[12px] text-muted-foreground">
                    Nothing at this priority
                  </p>
                ) : (
                  sortByDue(laneTasks).map((task) => (
                    <TaskCard
                      key={task.taskId}
                      task={task}
                      onOpen={onOpen}
                      onComplete={onComplete}
                      draggable={!!onPriorityChange}
                      onDragStart={(_, t) => {
                        dragged.current = t;
                      }}
                    />
                  ))
                )}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Manager view — grouped by owner with per-owner counters
 * ------------------------------------------------------------------ */

export function TaskManagerView({
  tasks,
  onOpen,
  onComplete,
}: { tasks: Task[] } & ViewHandlers) {
  const groups = React.useMemo(() => groupByOwner(tasks), [tasks]);
  const [collapsed, setCollapsed] = React.useState<Set<string>>(() => new Set());

  if (groups.length === 0) {
    return <EmptyState icon={Inbox} title="No tasks to show" variant="filter" />;
  }

  return (
    <div className="space-y-3">
      {groups.map((group) => {
        const isCollapsed = collapsed.has(group.ownerId);
        return (
          <section
            key={group.ownerId}
            className="overflow-hidden rounded-lg border border-border bg-surface"
          >
            <button
              type="button"
              aria-expanded={!isCollapsed}
              onClick={() =>
                setCollapsed((prev) => {
                  const next = new Set(prev);
                  if (next.has(group.ownerId)) next.delete(group.ownerId);
                  else next.add(group.ownerId);
                  return next;
                })
              }
              className="flex w-full items-center gap-2 border-b border-border px-3 py-2.5 text-left transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500"
            >
              {isCollapsed ? (
                <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
              ) : (
                <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
              )}

              <span className="grid size-7 shrink-0 place-items-center rounded-full bg-brand-500 text-[11px] font-bold text-brand-foreground">
                {group.ownerName
                  .split(" ")
                  .map((p) => p[0])
                  .slice(0, 2)
                  .join("")
                  .toUpperCase()}
              </span>

              <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-foreground">
                {group.ownerName}
              </span>

              {/* Per-owner counters — §10.14.1.6. */}
              <span className="flex shrink-0 items-center gap-1.5">
                <Counter label="Open" value={group.open} tone="brand" />
                {group.overdue > 0 && (
                  <Counter label="Overdue" value={group.overdue} tone="danger" />
                )}
                <Counter label="Done" value={group.completed} tone="success" />
              </span>
            </button>

            {!isCollapsed &&
              sortByDue(group.tasks).map((task) => (
                <TaskRow
                  key={task.taskId}
                  task={task}
                  onOpen={onOpen}
                  onComplete={onComplete}
                />
              ))}
          </section>
        );
      })}
    </div>
  );
}

function Counter({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: "brand" | "danger" | "success";
}) {
  const toneClass = {
    brand: "bg-brand-500/10 text-brand-600 dark:text-brand-500",
    danger: "bg-danger/10 text-danger",
    success: "bg-success/10 text-success",
  }[tone];

  return (
    <span
      title={`${label}: ${value}`}
      className={cn(
        "numeric inline-flex h-5 min-w-5 items-center justify-center rounded px-1.5 text-[11px] font-semibold",
        toneClass,
      )}
    >
      {value}
    </span>
  );
}
