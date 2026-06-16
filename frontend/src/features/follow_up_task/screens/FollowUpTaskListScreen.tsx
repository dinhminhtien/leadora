"use client";

import React, { useState, useMemo } from "react";
import {
  CalendarCheck,
  Plus,
  Search,
  CheckCircle2,
  Clock,
  AlertCircle,
  X,
  Calendar,
  Briefcase,
  User,
  RefreshCw,
  Loader2,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  useTasks,
  useCreateTask,
  useUpdateTask,
  useUsers,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import {
  taskService,
  type Task,
  type TaskPriority,
  type TaskStatus,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type TaskListParams,
} from "@/services/follow_up_task_service";

// ── Helpers ───────────────────────────────────────────────────────────────────

function isOverdue(task: Task): boolean {
  if (!task.dueDate) return false;
  if (task.status === "DONE" || task.status === "CANCELLED") return false;
  return new Date(task.dueDate) < new Date();
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function linkedEntityLabel(task: Task): string {
  if (task.dealName) return task.dealName;
  if (task.customerName) return task.customerName;
  if (task.leadName) return task.leadName;
  return "None";
}

function linkedEntityType(task: Task): string {
  if (task.dealId) return "Deal";
  if (task.customerId) return "Customer";
  if (task.leadId) return "Lead";
  return "General";
}

const PRIORITY_BADGE: Record<TaskPriority, "danger" | "warning" | "default"> = {
  HIGH: "danger",
  MEDIUM: "warning",
  LOW: "default",
};

const STATUS_LABEL: Record<TaskStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  DONE: "Done",
  CANCELLED: "Cancelled",
};

type UserOption = { userId: string; fullName: string };

// ── Info row component ────────────────────────────────────────────────────────

function InfoRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-start gap-1.5">
      <span className="mt-0.5">{icon}</span>
      <div>
        <p className="text-[10px] text-slate-400 font-medium">{label}</p>
        <p className="text-xs text-slate-700 font-semibold">{value}</p>
      </div>
    </div>
  );
}

// ── Detail / Edit Drawer ──────────────────────────────────────────────────────

function TaskDetailDrawer({
  task,
  onClose,
  users,
}: {
  task: Task;
  onClose: () => void;
  users: UserOption[];
}) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<UpdateTaskPayload>({
    title: task.title,
    description: task.description ?? "",
    assignedUserId: task.assignedUserId ?? "",
    priority: task.priority,
    dueDate: task.dueDate ?? "",
    status: task.status,
    resultNote: task.resultNote ?? "",
  });

  const updateMutation = useUpdateTask(task.taskId);
  const taskOverdue = isOverdue(task);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    updateMutation.mutate(form, {
      onSuccess: () => {
        setEditing(false);
        onClose();
      },
    });
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-lg bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <CalendarCheck className="size-4.5 text-blue-600" />
              {editing ? "Edit Task" : "Task Detail"}
            </h3>
            <p className="text-[10px] text-slate-400 mt-0.5">{linkedEntityType(task)} follow-up</p>
          </div>
          <div className="flex items-center gap-2">
            {!editing && (
              <button
                onClick={() => setEditing(true)}
                className="px-2.5 py-1 text-[10px] font-semibold text-blue-600 border border-blue-200 rounded hover:bg-blue-50 transition"
              >
                Edit
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
            >
              <X className="size-4.5" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {editing ? (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Title *</label>
                <Input
                  required
                  value={form.title ?? ""}
                  onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Description</label>
                <textarea
                  rows={3}
                  value={form.description ?? ""}
                  onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Priority</label>
                  <Select
                    value={form.priority ?? "MEDIUM"}
                    onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))}
                    className="py-1.5"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Status</label>
                  <Select
                    value={form.status ?? "OPEN"}
                    onChange={e => setForm(f => ({ ...f, status: e.target.value as TaskStatus }))}
                    className="py-1.5"
                  >
                    <option value="OPEN">Open</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="DONE">Done</option>
                    <option value="CANCELLED">Cancelled</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Due Date *</label>
                  <Input
                    required
                    type="date"
                    value={form.dueDate ?? ""}
                    onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Assigned Staff *</label>
                  <Select
                    value={form.assignedUserId ?? ""}
                    onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
                    className="py-1.5"
                  >
                    <option value="">Select staff…</option>
                    {users.map(u => (
                      <option key={u.userId} value={u.userId}>{u.fullName}</option>
                    ))}
                  </Select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Completion Notes / Result</label>
                <textarea
                  rows={2}
                  value={form.resultNote ?? ""}
                  onChange={e => setForm(f => ({ ...f, resultNote: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                  placeholder="Outcome after completing this task…"
                />
              </div>

              {updateMutation.error && (
                <p className="text-xs text-red-500">Failed to update task. Please try again.</p>
              )}

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  disabled={updateMutation.isPending}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
                >
                  {updateMutation.isPending ? (
                    <span className="flex items-center gap-1.5">
                      <Loader2 className="size-3 animate-spin" /> Saving…
                    </span>
                  ) : (
                    "Save Changes"
                  )}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditing(false)}
                  className="w-full border-slate-200 text-xs text-slate-600"
                >
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <div className="space-y-4">
              <div>
                <h2 className="text-sm font-bold text-slate-800">{task.title}</h2>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  <Badge
                    variant={PRIORITY_BADGE[task.priority]}
                    size="sm"
                    className="text-[9px] uppercase font-bold px-1.5"
                  >
                    {task.priority}
                  </Badge>
                  <Badge
                    variant="default"
                    size="sm"
                    className="text-[9px] font-bold px-1.5 bg-slate-100 text-slate-600"
                  >
                    {STATUS_LABEL[task.status]}
                  </Badge>
                  {taskOverdue && (
                    <Badge
                      variant="danger"
                      size="sm"
                      className="text-[9px] font-bold px-1.5 bg-red-100 text-red-700"
                    >
                      OVERDUE
                    </Badge>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <InfoRow icon={<Calendar className="size-3.5 text-slate-400" />} label="Due Date" value={formatDate(task.dueDate)} />
                <InfoRow icon={<User className="size-3.5 text-slate-400" />} label="Assigned To" value={task.assignedUserName ?? "—"} />
                <InfoRow icon={<Briefcase className="size-3.5 text-slate-400" />} label="Related To" value={linkedEntityLabel(task)} />
                <InfoRow icon={<Clock className="size-3.5 text-slate-400" />} label="Type" value={linkedEntityType(task)} />
              </div>

              {task.description && (
                <div className="space-y-1">
                  <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">
                    Description
                  </p>
                  <p className="text-xs text-slate-700 leading-relaxed">{task.description}</p>
                </div>
              )}

              {task.resultNote && (
                <div className="space-y-1 p-3 bg-emerald-50 rounded-lg border border-emerald-100">
                  <p className="text-[10px] font-semibold text-emerald-700 uppercase tracking-wider">
                    Result / Notes
                  </p>
                  <p className="text-xs text-emerald-800">{task.resultNote}</p>
                </div>
              )}

              <div className="pt-2 border-t border-slate-100 text-[10px] text-slate-400 space-y-0.5">
                <p>Created by: <span className="text-slate-600">{task.createdByName ?? "—"}</span></p>
                <p>Created at: <span className="text-slate-600">{formatDate(task.createdAt)}</span></p>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// ── Create Drawer ─────────────────────────────────────────────────────────────

function CreateTaskDrawer({
  onClose,
  users,
}: {
  onClose: () => void;
  users: UserOption[];
}) {
  const [form, setForm] = useState<CreateTaskPayload>({
    title: "",
    description: "",
    assignedUserId: users[0]?.userId ?? "",
    priority: "MEDIUM",
    dueDate: "",
  });

  const createMutation = useCreateTask();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.title.trim() || !form.dueDate || !form.assignedUserId) return;

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (new Date(form.dueDate) < today) {
      alert("Invalid due date: must be today or a future date.");
      return;
    }

    createMutation.mutate(form, { onSuccess: () => onClose() });
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <CalendarCheck className="size-4.5 text-blue-600" />
              Create Follow-up Task
            </h3>
            <p className="text-[10px] text-slate-400 mt-0.5">
              Assign follow-up actions to sales staff
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <X className="size-4.5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Activity Title *</label>
            <Input
              required
              placeholder="e.g. Call client to verify headcount, Send invoice…"
              value={form.title}
              onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
              className="py-1.5 text-xs"
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">
              Description / Expected Follow-up Purpose
            </label>
            <textarea
              rows={3}
              placeholder="Provide details, checklist items, or follow-up goals…"
              value={form.description ?? ""}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Priority *</label>
              <Select
                value={form.priority}
                onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))}
                className="py-1.5"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Due Date *</label>
              <Input
                required
                type="date"
                value={form.dueDate}
                onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))}
                className="py-1.5 text-xs"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Assigned Sales Staff *</label>
            <Select
              value={form.assignedUserId}
              onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
              className="py-1.5"
              required
            >
              <option value="">Select staff…</option>
              {users.map(u => (
                <option key={u.userId} value={u.userId}>
                  {u.fullName}
                </option>
              ))}
            </Select>
          </div>

          <div className="pt-2 border-t border-slate-100">
            <p className="text-[10px] font-semibold text-slate-400 uppercase mb-2">
              Link to Lead / Customer / Deal (optional)
            </p>
            <div className="grid grid-cols-3 gap-2">
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">Lead ID</label>
                <Input
                  placeholder="UUID"
                  value={form.leadId ?? ""}
                  onChange={e => setForm(f => ({ ...f, leadId: e.target.value || undefined }))}
                  className="py-1 text-[10px]"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">Customer ID</label>
                <Input
                  placeholder="UUID"
                  value={form.customerId ?? ""}
                  onChange={e => setForm(f => ({ ...f, customerId: e.target.value || undefined }))}
                  className="py-1 text-[10px]"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">Deal ID</label>
                <Input
                  placeholder="UUID"
                  value={form.dealId ?? ""}
                  onChange={e => setForm(f => ({ ...f, dealId: e.target.value || undefined }))}
                  className="py-1 text-[10px]"
                />
              </div>
            </div>
          </div>

          {createMutation.error && (
            <p className="text-xs text-red-500">
              Failed to create task. Please check required fields.
            </p>
          )}

          <div className="pt-4 flex gap-3 border-t border-slate-100">
            <Button
              type="submit"
              variant="primary"
              disabled={createMutation.isPending}
              className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
            >
              {createMutation.isPending ? (
                <span className="flex items-center gap-1.5">
                  <Loader2 className="size-3 animate-spin" /> Creating…
                </span>
              ) : (
                "Create Task"
              )}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              className="w-full border-slate-200 text-xs text-slate-600"
            >
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────

type TabId = "all" | "today" | "upcoming" | "overdue" | "completed";

export function FollowUpTaskListScreen() {
  const [activeTab, setActiveTab] = useState<TabId>("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);

  const queryClient = useQueryClient();

  const { data: usersData } = useUsers();
  const users = useMemo(() => usersData?.data ?? [], [usersData]);

  // Quick toggle-done mutation that works per task ID
  const toggleMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateTaskPayload }) =>
      taskService.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });

  const queryParams = useMemo<TaskListParams>(() => {
    const params: TaskListParams = { page, size: 20 };
    if (searchTerm.trim()) params.search = searchTerm.trim();
    if (priorityFilter) params.priority = priorityFilter;
    if (assigneeFilter) params.assignedUserId = assigneeFilter;
    if (activeTab === "overdue") params.overdue = true;
    if (activeTab === "completed") params.status = "DONE";
    // today / upcoming are filtered client-side from the "all open" set
    return params;
  }, [activeTab, searchTerm, priorityFilter, assigneeFilter, page]);

  const { data, isLoading, isError, refetch } = useTasks(queryParams);
  const allTasks: Task[] = data?.data?.content ?? [];
  const totalPages = data?.data?.totalPages ?? 1;
  const totalElements = data?.data?.totalElements ?? 0;

  // Client-side post-filter for today / upcoming tabs
  const filteredTasks = useMemo(() => {
    if (activeTab === "today") {
      const todayStr = new Date().toISOString().split("T")[0];
      return allTasks.filter(t => {
        const d = t.dueDate?.split("T")[0];
        return d === todayStr && t.status !== "DONE" && t.status !== "CANCELLED";
      });
    }
    if (activeTab === "upcoming") {
      const todayStr = new Date().toISOString().split("T")[0];
      return allTasks.filter(t => {
        const d = t.dueDate?.split("T")[0] ?? "";
        return d > todayStr && t.status !== "DONE" && t.status !== "CANCELLED" && !isOverdue(t);
      });
    }
    return allTasks;
  }, [allTasks, activeTab]);

  const tabs: { id: TabId; label: string }[] = [
    { id: "all", label: "All Activities" },
    { id: "today", label: "Due Today" },
    { id: "upcoming", label: "Upcoming" },
    { id: "overdue", label: "Overdue Alert" },
    { id: "completed", label: "Completed Log" },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Follow-up Activities</h1>
          <p className="text-xs text-slate-400">
            Manage pre-deal follow-up tasks: lead contact, customer clarification, deal confirmation
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            className="gap-1 border-slate-200 text-xs text-slate-600"
          >
            <RefreshCw className="size-3.5" />
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsCreateOpen(true)}
            className="gap-1 bg-blue-600 hover:bg-blue-700 font-semibold text-xs text-white"
          >
            <Plus className="size-3.5" />
            <span>Create Task</span>
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 gap-6 text-xs font-bold text-slate-400">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveTab(tab.id);
              setPage(0);
            }}
            className={`pb-3 border-b-2 px-1 capitalize transition ${
              activeTab === tab.id
                ? "border-blue-600 text-blue-600"
                : "border-transparent hover:text-slate-700"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Filter toolbar */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="flex flex-col md:flex-row items-center gap-3">
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search tasks, descriptions…"
                value={searchTerm}
                onChange={e => {
                  setSearchTerm(e.target.value);
                  setPage(0);
                }}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>

            <div className="w-full md:w-40 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Priority:</span>
              <Select
                value={priorityFilter}
                onChange={e => {
                  setPriorityFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full py-1.5"
              >
                <option value="">All</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </Select>
            </div>

            <div className="w-full md:w-52 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Assignee:</span>
              <Select
                value={assigneeFilter}
                onChange={e => {
                  setAssigneeFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full py-1.5"
              >
                <option value="">All Staff</option>
                {users.map(u => (
                  <option key={u.userId} value={u.userId}>
                    {u.fullName}
                  </option>
                ))}
              </Select>
            </div>

            <div className="md:ml-auto text-xs text-slate-400">
              Showing <strong className="text-slate-700">{filteredTasks.length}</strong>
              {totalElements > 0 && (
                <> of <strong className="text-slate-700">{totalElements}</strong></>
              )}{" "}
              tasks
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Task list */}
      <div className="space-y-3">
        {isLoading ? (
          <div className="py-12 flex justify-center text-slate-400">
            <Loader2 className="size-6 animate-spin" />
          </div>
        ) : isError ? (
          <div className="py-12 text-center text-red-500 text-xs border border-dashed border-red-200 bg-red-50 rounded-xl">
            <AlertCircle className="size-5 mx-auto mb-2" />
            Failed to load tasks.{" "}
            <button onClick={() => refetch()} className="underline">
              Retry
            </button>
          </div>
        ) : filteredTasks.length === 0 ? (
          <div className="py-12 text-center text-slate-400 text-xs border border-dashed border-slate-200 bg-white rounded-xl">
            <CalendarCheck className="size-8 mx-auto mb-2 text-slate-300" />
            No tasks found for this filter.
          </div>
        ) : (
          filteredTasks.map(task => {
            const taskOverdue = isOverdue(task);
            const isDone = task.status === "DONE";

            return (
              <Card
                key={task.taskId}
                className={`border-slate-100 hover:border-blue-300 shadow-xs transition duration-200 cursor-pointer ${
                  isDone ? "bg-slate-50/50 opacity-70" : "bg-white"
                }`}
                onClick={() => setSelectedTask(task)}
              >
                <CardContent className="p-4 flex items-start gap-4">
                  {/* Toggle done */}
                  <button
                    onClick={e => {
                      e.stopPropagation();
                      const newStatus: TaskStatus = isDone ? "OPEN" : "DONE";
                      toggleMutation.mutate({ id: task.taskId, payload: { status: newStatus } });
                    }}
                    className="mt-0.5 shrink-0 focus:outline-none"
                    title="Toggle Complete"
                  >
                    <CheckCircle2
                      className={`size-5 transition ${
                        isDone
                          ? "text-emerald-500 fill-emerald-50"
                          : taskOverdue
                          ? "text-red-400"
                          : "text-slate-300 hover:text-slate-400"
                      }`}
                    />
                  </button>

                  {/* Body */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3
                        className={`text-xs font-bold text-slate-800 ${
                          isDone ? "line-through text-slate-400 font-normal" : ""
                        }`}
                      >
                        {task.title}
                      </h3>
                      <Badge
                        variant={PRIORITY_BADGE[task.priority]}
                        size="sm"
                        className="font-bold text-[9px] uppercase px-1.5"
                      >
                        {task.priority}
                      </Badge>
                      <Badge
                        variant="default"
                        size="sm"
                        className="font-bold text-[9px] px-1.5 bg-slate-100 text-slate-600"
                      >
                        {STATUS_LABEL[task.status]}
                      </Badge>
                      {taskOverdue && (
                        <Badge
                          variant="danger"
                          size="sm"
                          className="font-bold text-[9px] bg-red-100 text-red-700"
                        >
                          OVERDUE
                        </Badge>
                      )}
                    </div>

                    {task.description && (
                      <p className="text-xs text-slate-500 mt-1 line-clamp-1">{task.description}</p>
                    )}

                    <div className="flex flex-wrap items-center gap-3 mt-3 text-[10px] text-slate-400 font-medium">
                      <span className="flex items-center gap-1">
                        <Calendar className="size-3.5" />
                        Due: <strong className="text-slate-600">{formatDate(task.dueDate)}</strong>
                      </span>
                      <span className="flex items-center gap-1">
                        <Briefcase className="size-3.5" />
                        {linkedEntityType(task)}:{" "}
                        <strong className="text-slate-600">{linkedEntityLabel(task)}</strong>
                      </span>
                      <span className="flex items-center gap-1">
                        <User className="size-3.5" />
                        Assigned:{" "}
                        <strong className="text-slate-600">{task.assignedUserName ?? "—"}</strong>
                      </span>
                    </div>
                  </div>

                  {/* Edit button */}
                  <div
                    className="shrink-0 ml-4"
                    onClick={e => e.stopPropagation()}
                  >
                    <button
                      onClick={() => setSelectedTask(task)}
                      className="px-2 py-1 bg-slate-50 border border-slate-200 text-slate-600 hover:bg-slate-100 rounded text-[10px] font-bold transition"
                    >
                      View
                    </button>
                  </div>
                </CardContent>
              </Card>
            );
          })
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center items-center gap-3 pt-2">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
            className="p-1.5 rounded border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            <ChevronLeft className="size-4" />
          </button>
          <span className="text-xs text-slate-500">
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="p-1.5 rounded border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            <ChevronRight className="size-4" />
          </button>
        </div>
      )}

      {/* Create drawer */}
      {isCreateOpen && (
        <CreateTaskDrawer onClose={() => setIsCreateOpen(false)} users={users} />
      )}

      {/* Detail / Edit drawer */}
      {selectedTask && (
        <TaskDetailDrawer
          task={selectedTask}
          onClose={() => setSelectedTask(null)}
          users={users}
        />
      )}
    </div>
  );
}
