/**
 * Follow-up Task view model — Website UI Blueprint §10.14.1.
 *
 * The module offers **eight views** over the same task list. They are not eight
 * screens: they are eight arrangements of one dataset, so the filter state,
 * selection and mutations are shared and only the presentation swaps.
 *
 * | View     | Shape                                   | Primary use            |
 * |----------|-----------------------------------------|------------------------|
 * | table    | dense productivity table (SALES default)| triage + inline edit   |
 * | board    | Kanban OPEN · COMPLETED · CANCELLED     | status at a glance     |
 * | calendar | full calendar filtered to tasks         | scheduling             |
 * | agenda   | Today · Tomorrow · This week · …        | daily standup          |
 * | compact  | 32px rows, keyboard-first               | rapid triage           |
 * | manager  | grouped by owner + per-owner counters   | team oversight (MGR)   |
 * | personal | "My day", priority then due             | focused execution      |
 * | priority | 4 lanes, drag between to re-prioritise  | re-prioritising        |
 *
 * **The status rule, restated because this module is where it matters most.**
 * The board has exactly three columns because the system has exactly three
 * statuses. `OVERDUE` is surfaced as a *sub-band at the top of OPEN* with a
 * danger stripe — the underlying status stays `OPEN`. §10.14.1.2 calls this out
 * explicitly: the visual grouping must not become a fourth status.
 */

import {
  endOfWeek,
  isAfter,
  isBefore,
  isToday,
  isTomorrow,
  startOfDay,
  startOfTomorrow,
} from "date-fns";

import { isTaskOverdue } from "@/shared/design/status-tokens";
import { priorityWeight } from "@/components/ui/priority-flag";
import type { Task } from "@/services/follow_up_task_service";

export type TaskViewId =
  | "table"
  | "board"
  | "calendar"
  | "agenda"
  | "compact"
  | "manager"
  | "personal"
  | "priority";

export type TaskViewMeta = {
  id: TaskViewId;
  label: string;
  hint: string;
  /** Manager View is MGR/ADMIN only (§10.14.8 permission state). */
  managerOnly?: boolean;
};

export const TASK_VIEWS: TaskViewMeta[] = [
  { id: "table", label: "Table", hint: "Dense productivity table" },
  { id: "board", label: "Board", hint: "Kanban by status" },
  { id: "calendar", label: "Calendar", hint: "Schedule view" },
  { id: "agenda", label: "Agenda", hint: "Grouped by when" },
  { id: "compact", label: "Compact", hint: "Keyboard-first triage" },
  { id: "personal", label: "My day", hint: "Your tasks, by priority" },
  { id: "priority", label: "Priority", hint: "Lanes by priority" },
  { id: "manager", label: "Team", hint: "Grouped by owner", managerOnly: true },
];

/* ------------------------------------------------------------------ *
 * Grouping
 * ------------------------------------------------------------------ */

export type AgendaBucketId =
  | "overdue"
  | "today"
  | "tomorrow"
  | "thisWeek"
  | "later"
  | "noDate";

export const AGENDA_BUCKET_LABELS: Record<AgendaBucketId, string> = {
  overdue: "Overdue",
  today: "Today",
  tomorrow: "Tomorrow",
  thisWeek: "This week",
  later: "Later",
  noDate: "No date",
};

/** Order matters — it is the order the agenda renders. */
export const AGENDA_BUCKET_ORDER: AgendaBucketId[] = [
  "overdue",
  "today",
  "tomorrow",
  "thisWeek",
  "later",
  "noDate",
];

/**
 * Which agenda bucket a task belongs to.
 *
 * Overdue wins over every date bucket: a task that was due yesterday is not
 * "yesterday's work", it is work you have now.
 */
export function agendaBucket(task: Task): AgendaBucketId {
  if (isTaskOverdue(task)) return "overdue";

  const raw = task.endAt ?? task.startAt;
  if (!raw) return "noDate";

  const due = new Date(raw);
  if (Number.isNaN(due.getTime())) return "noDate";

  if (isToday(due)) return "today";
  if (isTomorrow(due)) return "tomorrow";
  if (isBefore(due, startOfTomorrow())) return "today"; // earlier today
  if (isBefore(due, endOfWeek(new Date(), { weekStartsOn: 1 }))) return "thisWeek";
  if (isAfter(due, startOfDay(new Date()))) return "later";
  return "later";
}

export function groupByAgendaBucket(tasks: Task[]): Map<AgendaBucketId, Task[]> {
  const map = new Map<AgendaBucketId, Task[]>();
  for (const id of AGENDA_BUCKET_ORDER) map.set(id, []);
  for (const task of tasks) map.get(agendaBucket(task))!.push(task);
  // Drop empties so the agenda doesn't render six headers over nothing.
  for (const [id, list] of map) if (list.length === 0) map.delete(id);
  return map;
}

/** The three real statuses, in board order. */
export const BOARD_COLUMNS = ["OPEN", "COMPLETED", "CANCELLED"] as const;
export type BoardColumnId = (typeof BOARD_COLUMNS)[number];

export const BOARD_COLUMN_LABELS: Record<BoardColumnId, string> = {
  OPEN: "Open",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export function groupByStatus(tasks: Task[]): Record<BoardColumnId, Task[]> {
  const out: Record<BoardColumnId, Task[]> = {
    OPEN: [],
    COMPLETED: [],
    CANCELLED: [],
  };
  for (const task of tasks) {
    const key = (task.status ?? "OPEN").toUpperCase() as BoardColumnId;
    if (key in out) out[key].push(task);
  }
  return out;
}

/**
 * Splits the OPEN column into its overdue sub-band and the rest.
 * Returns two arrays rather than a fourth status — see the file header.
 */
export function splitOverdue(openTasks: Task[]): {
  overdue: Task[];
  upcoming: Task[];
} {
  const overdue: Task[] = [];
  const upcoming: Task[] = [];
  for (const task of openTasks) {
    (isTaskOverdue(task) ? overdue : upcoming).push(task);
  }
  return { overdue, upcoming };
}

export const PRIORITY_LANES = ["HIGH", "MEDIUM", "LOW"] as const;
export type PriorityLaneId = (typeof PRIORITY_LANES)[number];

export function groupByPriority(tasks: Task[]): Record<PriorityLaneId, Task[]> {
  const out: Record<PriorityLaneId, Task[]> = { HIGH: [], MEDIUM: [], LOW: [] };
  for (const task of tasks) {
    const key = (task.priority ?? "MEDIUM").toUpperCase();
    // Anything the client doesn't recognise lands in MEDIUM, matching the
    // server's own lenient enum parse.
    (out[key as PriorityLaneId] ?? out.MEDIUM).push(task);
  }
  return out;
}

export function groupByOwner(tasks: Task[]): {
  ownerId: string;
  ownerName: string;
  tasks: Task[];
  open: number;
  overdue: number;
  completed: number;
}[] {
  const map = new Map<string, Task[]>();
  for (const task of tasks) {
    const key = task.assignedUserId ?? "__unassigned";
    const list = map.get(key);
    if (list) list.push(task);
    else map.set(key, [task]);
  }

  return [...map.entries()]
    .map(([ownerId, list]) => ({
      ownerId,
      ownerName: list[0]?.assignedUserName ?? "Unassigned",
      tasks: list,
      open: list.filter((t) => t.status?.toUpperCase() === "OPEN").length,
      overdue: list.filter(isTaskOverdue).length,
      completed: list.filter((t) => t.status?.toUpperCase() === "COMPLETED").length,
    }))
    // Busiest owners first — that is where a manager's attention goes.
    .sort((a, b) => b.overdue - a.overdue || b.open - a.open);
}

/* ------------------------------------------------------------------ *
 * Sorting
 * ------------------------------------------------------------------ */

/** "My day" order: overdue first, then priority, then soonest due. */
export function sortForPersonal(tasks: Task[]): Task[] {
  return [...tasks].sort((a, b) => {
    const overdueDelta = Number(isTaskOverdue(b)) - Number(isTaskOverdue(a));
    if (overdueDelta !== 0) return overdueDelta;

    const priorityDelta = priorityWeight(b.priority) - priorityWeight(a.priority);
    if (priorityDelta !== 0) return priorityDelta;

    const aDue = a.endAt ? new Date(a.endAt).getTime() : Infinity;
    const bDue = b.endAt ? new Date(b.endAt).getTime() : Infinity;
    return aDue - bDue;
  });
}

/** Chronological, undated last — the agenda's within-bucket order. */
export function sortByDue(tasks: Task[]): Task[] {
  return [...tasks].sort((a, b) => {
    const aDue = a.endAt ?? a.startAt;
    const bDue = b.endAt ?? b.startAt;
    if (!aDue && !bDue) return 0;
    if (!aDue) return 1;
    if (!bDue) return -1;
    return new Date(aDue).getTime() - new Date(bDue).getTime();
  });
}
