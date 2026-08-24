"use client";

/**
 * Task table view — Website UI Blueprint §10.14.1.1 and §10.14.4.
 *
 * Columns per the blueprint: `[ ] · Priority flag · Title · Status pill · Due ·
 * Related entity chip · Owner · Updated · ⋯`.
 *
 * Built on the shared `DataTable` (§2.6) so density, sticky headers, selection,
 * the bulk bar and the `j/k/Enter/x` keyboard model all come from one place —
 * this file only declares what a *task* column contains.
 */

import * as React from "react";
import { format } from "date-fns";
import { CheckCircle2, UserPlus } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import {
  DataTable,
  TablePagination,
  type ColumnDef,
  type TableDensity,
} from "@/components/ui/data-table";
import { DensityMenu } from "@/components/ui/list-toolbar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { StatusPill, OverdueBadge } from "@/components/ui/status-pill";
import { PriorityFlag } from "@/components/ui/priority-flag";
import { isTaskOverdue } from "@/shared/design/status-tokens";
import {
  activityLabel,
  relatedLabel,
} from "@/features/follow_up_task/components/TaskCard";
import type { Task, UserSummary } from "@/services/follow_up_task_service";

type TaskTableViewProps = {
  tasks: Task[];
  selectedIds: Set<string>;
  onSelectionChange: (next: Set<string>) => void;
  highlightId?: string | null;
  rowRef?: (id: string) => (el: HTMLElement | null) => void;
  isManager: boolean;
  users: UserSummary[];
  onOpen: (task: Task) => void;
  onComplete: (task: Task) => void;
  onPriorityChange?: (task: Task, priority: string) => void;
  onBulkPriority: (priority: string) => void;
  onBulkAssign: (userId: string) => void;
  page?: number;
  pageSize?: number;
  totalElements?: number;
  totalPages?: number;
  onPageChange?: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
};

export function TaskTableView({
  tasks,
  selectedIds,
  onSelectionChange,
  highlightId,
  rowRef,
  isManager,
  users,
  onOpen,
  onComplete,
  onBulkPriority,
  onBulkAssign,
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: TaskTableViewProps) {
  const [density, setDensity] = React.useState<TableDensity>("comfortable");

  const columns = React.useMemo<ColumnDef<Task>[]>(
    () => [
      {
        id: "priority",
        header: "",
        width: "w-8",
        cell: (t) => <PriorityFlag value={t.priority} />,
      },
      {
        id: "title",
        header: "Task",
        sticky: "left",
        cell: (t) => {
          const status = (t.status ?? "OPEN").toUpperCase();
          return (
            <div className="min-w-0">
              <p
                className={cn(
                  "truncate font-medium text-foreground",
                  status === "COMPLETED" && "text-muted-foreground line-through",
                  status === "CANCELLED" && "text-muted-foreground italic",
                )}
              >
                {t.title}
              </p>
              <p className="truncate text-[11.5px] text-muted-foreground">
                {activityLabel(t.activityType)}
                {t.primaryContactName ? ` · ${t.primaryContactName}` : ""}
              </p>
            </div>
          );
        },
      },
      {
        id: "status",
        header: "Status",
        width: "w-[170px]",
        cell: (t) => (
          <span className="flex flex-wrap items-center gap-1">
            <StatusPill size="sm" domain="task" value={t.status} />
            {isTaskOverdue(t) && <OverdueBadge size="sm" />}
          </span>
        ),
      },
      {
        id: "due",
        header: "Due",
        numeric: true,
        width: "w-[130px]",
        minWidth: "sm",
        cell: (t) => {
          const raw = t.endAt ?? t.startAt;
          if (!raw) return <span className="text-muted-foreground">—</span>;
          const d = new Date(raw);
          if (Number.isNaN(d.getTime()))
            return <span className="text-muted-foreground">—</span>;
          return (
            <span className={cn(isTaskOverdue(t) && "font-medium text-danger")}>
              {format(d, "d MMM, HH:mm")}
            </span>
          );
        },
      },
      {
        id: "related",
        header: "Related",
        minWidth: "lg",
        cell: (t) => {
          const rel = relatedLabel(t);
          if (!rel) return <span className="text-muted-foreground">—</span>;
          return (
            <span className="inline-flex max-w-[220px] items-center gap-1 truncate rounded-md border border-border bg-muted px-1.5 py-0.5 text-[11.5px] text-muted-foreground">
              <span className="font-medium text-foreground">{rel.kind}:</span>
              <span className="truncate">{rel.name}</span>
            </span>
          );
        },
      },
      {
        id: "owner",
        header: "Owner",
        minWidth: "md",
        cell: (t) =>
          t.assignedUserName ? (
            <span className="inline-flex min-w-0 items-center gap-1.5">
              <span className="grid size-6 shrink-0 place-items-center rounded-full bg-brand-500/10 text-[10px] font-bold text-brand-600 dark:text-brand-500">
                {t.assignedUserName
                  .split(" ")
                  .map((p) => p[0])
                  .slice(0, 2)
                  .join("")
                  .toUpperCase()}
              </span>
              <span className="truncate">{t.assignedUserName}</span>
            </span>
          ) : (
            <span className="text-muted-foreground">Unassigned</span>
          ),
      },
      {
        id: "actions",
        header: "",
        width: "w-12",
        sticky: "right",
        cell: (t) => {
          const status = (t.status ?? "OPEN").toUpperCase();
          if (status !== "OPEN") return null;
          return (
            <Button
              size="icon-sm"
              variant="ghost"
              title="Mark completed"
              aria-label={`Complete ${t.title}`}
              onClick={(e) => {
                e.stopPropagation();
                onComplete(t);
              }}
            >
              <CheckCircle2 className="size-4" />
            </Button>
          );
        },
      },
    ],
    [onComplete],
  );

  return (
    <div className="space-y-2">
      <div className="flex justify-end">
        <DensityMenu value={density} onChange={setDensity} />
      </div>

      <DataTable
        label="Follow-up tasks"
        rows={tasks}
        columns={columns}
        rowId={(t) => t.taskId}
        density={density}
        onRowClick={onOpen}
        highlightId={highlightId}
        rowRef={rowRef}
        selectedIds={selectedIds}
        onSelectionChange={onSelectionChange}
        emptyTitle="You're all clear"
        emptyMessage="Follow-ups you create or are assigned will appear here."
        bulkActions={
          <>
            <BulkPriorityMenu onSelect={onBulkPriority} />
            {/* Reassign is Manager/Admin only — BR-18. */}
            {isManager && <BulkAssignMenu users={users} onSelect={onBulkAssign} />}
          </>
        }
        footer={
          typeof page === "number" && totalElements !== undefined && totalPages !== undefined && onPageChange ? (
            <TablePagination
              page={page}
              pageSize={pageSize ?? 20}
              totalElements={totalElements}
              totalPages={totalPages}
              onPageChange={onPageChange}
              onPageSizeChange={onPageSizeChange}
              pageSizeOptions={[10, 20, 50, 100]}
            />
          ) : (
            <span className="numeric">
              {tasks.length} {tasks.length === 1 ? "task" : "tasks"} loaded
            </span>
          )
        }
      />
    </div>
  );
}

function BulkPriorityMenu({ onSelect }: { onSelect: (p: string) => void }) {
  const [open, setOpen] = React.useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button size="xs" variant="secondary">
          Set priority
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-40">
        {["HIGH", "MEDIUM", "LOW"].map((p) => (
          <button
            key={p}
            onClick={() => {
              setOpen(false);
              onSelect(p);
            }}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] capitalize text-foreground transition-colors hover:bg-surface-2"
          >
            <PriorityFlag value={p} />
            {p.toLowerCase()}
          </button>
        ))}
      </PopoverContent>
    </Popover>
  );
}

function BulkAssignMenu({
  users,
  onSelect,
}: {
  users: UserSummary[];
  onSelect: (userId: string) => void;
}) {
  const [open, setOpen] = React.useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button size="xs" variant="secondary" leftIcon={<UserPlus className="size-3.5" />}>
          Reassign
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="max-h-72 w-56 overflow-y-auto">
        {users.length === 0 && (
          <p className="px-2 py-3 text-[12.5px] text-muted-foreground">
            No assignable users.
          </p>
        )}
        {users.map((u) => (
          <button
            key={u.userId}
            onClick={() => {
              setOpen(false);
              onSelect(u.userId);
            }}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[13px] text-foreground transition-colors hover:bg-surface-2"
          >
            <span className="grid size-6 shrink-0 place-items-center rounded-full bg-brand-500/10 text-[10px] font-bold text-brand-600 dark:text-brand-500">
              {u.fullName
                .split(" ")
                .map((p) => p[0])
                .slice(0, 2)
                .join("")
                .toUpperCase()}
            </span>
            <span className="min-w-0 truncate">{u.fullName}</span>
          </button>
        ))}
      </PopoverContent>
    </Popover>
  );
}
