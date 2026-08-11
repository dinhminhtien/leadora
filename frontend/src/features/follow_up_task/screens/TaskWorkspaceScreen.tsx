"use client";

/**
 * Follow-up Tasks workspace — Website UI Blueprint §10.14 (flagship module).
 *
 * One dataset, eight views (§10.14.1), one toolbar (§10.14.2), quick create
 * (§10.14.3) and bulk actions (§10.14.4).
 *
 * **Business rules mirrored, never re-implemented.** Every mutation goes through
 * the existing hooks and endpoints:
 * - completing → `PATCH /tasks/{id}/resolve` via the mandatory-note dialog
 * - status/priority/date changes → `PUT /tasks/{id}`
 * - assignment → `PUT /tasks/{id}` (Manager/Admin only, per `TaskPermissions`)
 *
 * **Permission gating mirrors the server (§12.13 of the mobile spec, §14 here).**
 * SALES users are hard-scoped to their own tasks server-side, so the assignee
 * filter and Manager View are hidden for them rather than shown and then 403'd.
 */

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  CalendarDays,
  Columns3,
  LayoutList,
  ListTodo,
  Plus,
  Rows3,
  User,
  Users,
  Flag,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { StatusPill } from "@/components/ui/status-pill";
import { PriorityChip } from "@/components/ui/priority-flag";
import { ListSkeleton } from "@/components/ui/skeletons";
import { ErrorState } from "@/components/ui/states";
import {
  ListToolbar,
  SegmentedControl,
  StatTiles,
  ToolbarSearch,
  type FilterChipSpec,
} from "@/components/ui/list-toolbar";
import { toast } from "@/stores/toast_store";
import { apiErrorCopy } from "@/shared/design/error-messages";
import { isTaskOverdue } from "@/shared/design/status-tokens";
import { useAuthStore } from "@/stores/auth_store";
import { hasFullAccess } from "@/shared/auth/access";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { PAGE_META } from "@/app/routes/page_meta";
import {
  useResolveTask,
  useTasks,
  useUpdateTaskById,
  useUsers,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { TaskCompleteDialog } from "@/features/follow_up_task/components/TaskCompleteDialog";
import {
  CreateTaskDrawer,
  ReassignFollowUpModal,
  TaskDetailDrawer,
} from "@/features/follow_up_task/components/TaskDrawers";
import {
  TaskAgendaView,
  TaskBoardView,
  TaskCompactView,
  TaskManagerView,
  TaskPersonalView,
  TaskPriorityView,
} from "@/features/follow_up_task/components/TaskViews";
import { TaskTableView } from "@/features/follow_up_task/components/TaskTableView";
import { TASK_VIEWS, type TaskViewId } from "@/features/follow_up_task/lib/task-views";
import type { Task } from "@/services/follow_up_task_service";

const VIEW_ICONS: Record<TaskViewId, React.ComponentType<{ className?: string }>> = {
  table: Rows3,
  board: Columns3,
  calendar: CalendarDays,
  agenda: LayoutList,
  compact: ListTodo,
  personal: User,
  priority: Flag,
  manager: Users,
};

const STATUS_FILTERS = ["OPEN", "COMPLETED", "CANCELLED"] as const;
const PRIORITY_FILTERS = ["HIGH", "MEDIUM", "LOW"] as const;

/** §9.1.6 — Tasks uses 50 per page "for productivity". */
const PAGE_SIZE = 50;

import { useHighlightRow } from "@/shared/hooks/use_highlight_row";

export function TaskWorkspaceScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { highlightedId, setRowRef } = useHighlightRow("highlight", "task");
  const user = useAuthStore((s) => s.user);
  const isManager = hasFullAccess(user);

  const [view, setView] = React.useState<TaskViewId>("table");
  const [search, setSearch] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<string | null>(null);
  const [priorityFilter, setPriorityFilter] = React.useState<string | null>(null);
  const [overdueOnly, setOverdueOnly] = React.useState(false);
  const [assigneeFilter, setAssigneeFilter] = React.useState<string | null>(null);
  const [selectedIds, setSelectedIds] = React.useState<Set<string>>(new Set());
  const [completeTarget, setCompleteTarget] = React.useState<Task | null>(null);

  // The create / detail / reassign drawers live in this one screen — there is
  // no second task page to hand off to.
  //
  // `?new=1` (Quick Add, dashboard) is read with a lazy initialiser rather than
  // synced in an effect: the param is only meaningful on first mount, and an
  // effect would re-open the drawer every time the user closed it.
  const [createOpen, setCreateOpen] = React.useState(
    () => searchParams.get("new") === "1",
  );
  const [detailTask, setDetailTask] = React.useState<Task | null>(null);
  const [detailEditing, setDetailEditing] = React.useState(false);
  const [reassignTask, setReassignTask] = React.useState<Task | null>(null);

  const highlightId = searchParams.get("highlight");

  const { data, isLoading, isError, error, refetch } = useTasks({
    page: 0,
    size: PAGE_SIZE,
    search: search || undefined,
    status: statusFilter ?? undefined,
    priority: priorityFilter ?? undefined,
    assignedUserId: isManager ? (assigneeFilter ?? undefined) : undefined,
    overdue: overdueOnly || undefined,
  });

  const { data: usersResponse } = useUsers();
  const users = usersResponse?.data ?? [];

  const updateTask = useUpdateTaskById();
  const resolveTask = useResolveTask();

  const tasks = React.useMemo(() => data?.data?.content ?? [], [data]);

  // §10.14.8 — Manager View is manager-only. Resolved during render rather than
  // corrected in an effect, so a non-manager never paints a frame of a view they
  // are not allowed to see.
  const activeView: TaskViewId =
    view === "manager" && !isManager ? "table" : view;

  /* ── Derived counters for the stat strip ──────────────────────────────── */

  const counters = React.useMemo(() => {
    let open = 0;
    let overdue = 0;
    let completed = 0;
    for (const t of tasks) {
      const s = (t.status ?? "").toUpperCase();
      if (s === "OPEN") open++;
      if (s === "COMPLETED") completed++;
      if (isTaskOverdue(t)) overdue++;
    }
    return { open, overdue, completed, total: tasks.length };
  }, [tasks]);

  /* ── Mutations ────────────────────────────────────────────────────────── */

  const openTask = (task: Task) => {
    setDetailEditing(false);
    setDetailTask(task);
  };

  /**
   * A notification deep-link (`?highlight={taskId}`) opens that task's detail
   * once the list containing it has loaded.
   *
   * Resolved during render, not in an effect: the task arrives asynchronously,
   * and an effect would paint one frame of the list before the drawer appeared.
   * `openedHighlight` records which id was already auto-opened so closing the
   * drawer does not immediately re-open it.
   */
  const [openedHighlight, setOpenedHighlight] = React.useState<string | null>(null);
  if (highlightId && openedHighlight !== highlightId && !detailTask) {
    const match = tasks.find((t) => t.taskId === highlightId);
    if (match) {
      setOpenedHighlight(highlightId);
      setDetailEditing(false);
      setDetailTask(match);
    }
  }

  const requestComplete = (task: Task) => setCompleteTarget(task);

  const changeStatus = async (task: Task, status: string) => {
    // COMPLETED needs the mandatory result note, so route it through the dialog
    // rather than writing the status directly.
    if (status === "COMPLETED") {
      setCompleteTarget(task);
      return;
    }
    try {
      await updateTask.mutateAsync({
        taskId: task.taskId,
        payload: { status: status as Task["status"] },
      });
      toast.success(`Task moved to ${status.toLowerCase()}`);
    } catch (err) {
      toast.error(apiErrorCopy(err));
    }
  };

  const changePriority = async (task: Task, priority: string) => {
    try {
      await updateTask.mutateAsync({
        taskId: task.taskId,
        payload: { priority: priority as Task["priority"] },
      });
      toast.success(`Priority set to ${priority.toLowerCase()}`);
    } catch (err) {
      toast.error(apiErrorCopy(err));
    }
  };

  /** §10.14.4 — bulk actions run sequentially so one failure doesn't hide others. */
  const bulkSetPriority = async (priority: string) => {
    const ids = [...selectedIds];
    let failed = 0;
    for (const taskId of ids) {
      try {
        await updateTask.mutateAsync({
          taskId,
          payload: { priority: priority as Task["priority"] },
        });
      } catch {
        failed++;
      }
    }
    setSelectedIds(new Set());
    if (failed === 0) toast.success(`${ids.length} tasks updated`);
    else toast.warning(`${ids.length - failed} updated, ${failed} failed`);
  };

  const bulkAssign = async (userId: string) => {
    const ids = [...selectedIds];
    let failed = 0;
    for (const taskId of ids) {
      try {
        await updateTask.mutateAsync({
          taskId,
          payload: { assignedUserId: userId },
        });
      } catch {
        failed++;
      }
    }
    setSelectedIds(new Set());
    if (failed === 0) toast.success(`${ids.length} tasks reassigned`);
    else toast.warning(`${ids.length - failed} reassigned, ${failed} failed`);
  };

  /* ── Filter chips ─────────────────────────────────────────────────────── */

  const chips: FilterChipSpec[] = [];
  if (statusFilter)
    chips.push({
      id: "status",
      label: `Status: ${statusFilter.toLowerCase()}`,
      onRemove: () => setStatusFilter(null),
    });
  if (priorityFilter)
    chips.push({
      id: "priority",
      label: `Priority: ${priorityFilter.toLowerCase()}`,
      onRemove: () => setPriorityFilter(null),
    });
  if (overdueOnly)
    chips.push({
      id: "overdue",
      label: "Overdue only",
      onRemove: () => setOverdueOnly(false),
    });
  if (assigneeFilter)
    chips.push({
      id: "assignee",
      label: `Owner: ${users.find((u) => u.userId === assigneeFilter)?.fullName ?? "selected"}`,
      onRemove: () => setAssigneeFilter(null),
    });

  const clearFilters = () => {
    setStatusFilter(null);
    setPriorityFilter(null);
    setOverdueOnly(false);
    setAssigneeFilter(null);
    setSearch("");
  };

  const viewOptions = TASK_VIEWS.filter((v) => !v.managerOnly || isManager).map(
    (v) => ({ value: v.id, label: v.label, icon: VIEW_ICONS[v.id] }),
  );

  const handlers = {
    onOpen: openTask,
    onComplete: requestComplete,
    onStatusChange: (task: Task, status: string) => void changeStatus(task, status),
    onPriorityChange: (task: Task, p: string) => void changePriority(task, p),
  };

  return (
    <div className="flex min-h-[calc(100vh-8rem)] flex-col">
      <PageHeader
        {...PAGE_META.followUpTasks}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<CalendarDays className="size-4" />}
              onClick={() => router.push(ROUTE_PATHS.calendar)}
            >
              Calendar
            </Button>
            <Button
              variant="primary"
              leftIcon={<Plus className="size-4" />}
              onClick={() => setCreateOpen(true)}
              title="New task — C"
            >
              New task
            </Button>
          </>
        }
      />

      <StatTiles
        tiles={[
          {
            label: "Open",
            value: counters.open,
            tone: "brand",
            active: statusFilter === "OPEN",
            onClick: () =>
              setStatusFilter((s) => (s === "OPEN" ? null : "OPEN")),
          },
          {
            label: "Overdue",
            value: counters.overdue,
            tone: "danger",
            active: overdueOnly,
            onClick: () => setOverdueOnly((v) => !v),
          },
          {
            label: "Completed",
            value: counters.completed,
            tone: "success",
            active: statusFilter === "COMPLETED",
            onClick: () =>
              setStatusFilter((s) => (s === "COMPLETED" ? null : "COMPLETED")),
          },
          { label: "Loaded", value: counters.total, tone: "muted" },
        ]}
      />

      <ListToolbar chips={chips} onClearFilters={clearFilters}>
        <ToolbarSearch
          value={search}
          onChange={setSearch}
          placeholder="Search title, customer, contact…"
        />

        {/* Status chips — exactly three, matching the three real statuses. */}
        <div className="flex flex-wrap items-center gap-1">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setStatusFilter((cur) => (cur === s ? null : s))}
              className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 rounded-pill"
              aria-pressed={statusFilter === s}
            >
              <StatusPill
                size="sm"
                domain="task"
                value={s}
                className={cn(
                  "transition-opacity",
                  statusFilter && statusFilter !== s && "opacity-45",
                )}
              />
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-1">
          {PRIORITY_FILTERS.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setPriorityFilter((cur) => (cur === p ? null : p))}
              aria-pressed={priorityFilter === p}
              className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            >
              <PriorityChip
                size="sm"
                value={p}
                className={cn(
                  "transition-opacity",
                  priorityFilter && priorityFilter !== p && "opacity-45",
                )}
              />
            </button>
          ))}
        </div>

        {/* Owner filter — hidden for SALES, who are hard-scoped server-side. */}
        {isManager && (
          <select
            value={assigneeFilter ?? ""}
            onChange={(e) => setAssigneeFilter(e.target.value || null)}
            aria-label="Filter by owner"
            className="h-9 rounded-md border border-input bg-surface px-2 text-[12.5px] text-foreground focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
          >
            <option value="">All owners</option>
            {users.map((u) => (
              <option key={u.userId} value={u.userId}>
                {u.fullName}
              </option>
            ))}
          </select>
        )}

        <SegmentedControl
          className="ml-auto"
          label="Task view"
          value={activeView}
          onChange={setView}
          options={viewOptions}
        />
      </ListToolbar>

      {/* ── View surface ──────────────────────────────────────────────── */}
      <div className="min-h-0 flex-1">
        {isError ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : isLoading ? (
          <ListSkeleton count={8} />
        ) : activeView === "table" ? (
          <TaskTableView
            tasks={tasks}
            selectedIds={selectedIds}
            onSelectionChange={setSelectedIds}
            highlightId={highlightedId}
            rowRef={setRowRef}
            isManager={isManager}
            users={users}
            onBulkPriority={bulkSetPriority}
            onBulkAssign={bulkAssign}
            {...handlers}
          />
        ) : activeView === "board" ? (
          <TaskBoardView tasks={tasks} {...handlers} />
        ) : activeView === "agenda" ? (
          <TaskAgendaView tasks={tasks} {...handlers} />
        ) : activeView === "compact" ? (
          <TaskCompactView tasks={tasks} {...handlers} />
        ) : activeView === "personal" ? (
          <TaskPersonalView
            tasks={tasks.filter((t) => t.assignedUserId === user?.id)}
            {...handlers}
          />
        ) : activeView === "priority" ? (
          <TaskPriorityView tasks={tasks} {...handlers} />
        ) : activeView === "manager" ? (
          <TaskManagerView tasks={tasks} {...handlers} />
        ) : (
          // Calendar view — the module's own calendar lives at /calendar so the
          // two never diverge. Redirecting is honest about that.
          <div className="rounded-lg border border-border bg-surface p-10 text-center">
            <p className="text-[13px] text-muted-foreground">
              The calendar view opens the full calendar surface.
            </p>
            <Button
              className="mt-3"
              variant="primary"
              size="sm"
              onClick={() => router.push(ROUTE_PATHS.calendar)}
              leftIcon={<CalendarDays className="size-4" />}
            >
              Open calendar
            </Button>
          </div>
        )}
      </div>

      <TaskCompleteDialog
        task={completeTarget}
        open={!!completeTarget}
        onOpenChange={(open) => !open && setCompleteTarget(null)}
        onCompleted={() => setCompleteTarget(null)}
        resolveTask={resolveTask}
      />

      {/* Create / detail / reassign — the forms carried over from the previous
          screen, now rendered by the single task workspace. */}
      {createOpen && (
        <CreateTaskDrawer
          onClose={() => setCreateOpen(false)}
          users={users}
          canAssignOthers={isManager}
          currentUserId={user?.id ?? ""}
          currentUserName={user?.name ?? ""}
        />
      )}

      {detailTask && (
        <TaskDetailDrawer
          task={detailTask}
          users={users}
          initialEditing={detailEditing}
          canAssignOthers={isManager}
          onClose={() => setDetailTask(null)}
          onReassign={() => {
            setReassignTask(detailTask);
            setDetailTask(null);
          }}
        />
      )}

      {reassignTask && (
        <ReassignFollowUpModal
          task={reassignTask}
          users={users}
          onClose={() => setReassignTask(null)}
        />
      )}
    </div>
  );
}
