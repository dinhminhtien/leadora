"use client";

import React, { useState, useMemo } from "react";
import {
  CalendarCheck,
  CalendarDays,
  LayoutList,
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
  ArrowUpRight,
  Phone,
  Mail,
  Users2,
  MapPin,
  CheckSquare2,
  Zap,
  Building2,
} from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { dealService, type Deal } from "@/services/deal_service";
import { leadService, type Lead } from "@/services/lead_service";
import { customerProfileService } from "@/services/customer_profile_service";

// ── Activity Types (Pipedrive-style) ─────────────────────────────────────────

const ACTIVITY_TYPES = [
  { type: "CALL", label: "Call", Icon: Phone, activeClass: "border-green-400 bg-green-50 text-green-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-green-300 hover:text-green-600" },
  { type: "EMAIL", label: "Email", Icon: Mail, activeClass: "border-blue-400 bg-blue-50 text-blue-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-blue-300 hover:text-blue-600" },
  { type: "MEETING", label: "Meeting", Icon: Users2, activeClass: "border-purple-400 bg-purple-50 text-purple-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-purple-300 hover:text-purple-600" },
  { type: "SITE_VISIT", label: "Site Visit", Icon: MapPin, activeClass: "border-orange-400 bg-orange-50 text-orange-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-orange-300 hover:text-orange-600" },
  { type: "FOLLOW_UP", label: "Follow-up", Icon: RefreshCw, activeClass: "border-teal-400 bg-teal-50 text-teal-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-teal-300 hover:text-teal-600" },
  { type: "TASK", label: "Task", Icon: CheckSquare2, activeClass: "border-slate-500 bg-slate-100 text-slate-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-slate-400 hover:text-slate-700" },
] as const;

type ActivityType = typeof ACTIVITY_TYPES[number]["type"];

// ── Helpers ───────────────────────────────────────────────────────────────────

function isOverdue(task: Task): boolean {
  if (!task.dueDate) return false;
  if (task.status === "COMPLETED" || task.status === "CANCELLED") return false;
  return new Date(task.dueDate) < new Date();
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function addDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().split("T")[0];
}

function linkedEntityLabel(task: Task): string {
  if (task.dealName) return task.dealName;
  if (task.customerName) return task.customerName;
  if (task.leadName) return task.leadName;
  if (task.contactName) return task.contactName;
  return "—";
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

const STATUS_BADGE: Record<TaskStatus, "primary" | "warning" | "success" | "default"> = {
  OPEN: "primary",
  IN_PROGRESS: "warning",
  WAITING_CUSTOMER: "warning",
  COMPLETED: "success",
  CANCELLED: "default",
};

const STATUS_LABEL: Record<TaskStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  WAITING_CUSTOMER: "Waiting Customer",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_OPTIONS: { value: TaskStatus; label: string }[] = [
  { value: "OPEN", label: "Open" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "WAITING_CUSTOMER", label: "Waiting Customer" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
];

type UserOption = { userId: string; fullName: string };

// ── Info Row ──────────────────────────────────────────────────────────────────

function InfoRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-start gap-2">
      <span className="mt-0.5">{icon}</span>
      <div>
        <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wide">{label}</p>
        <p className="text-xs text-slate-700 font-semibold mt-0.5">{value}</p>
      </div>
    </div>
  );
}

// ── Convert to Deal Drawer ────────────────────────────────────────────────────

function ConvertToDealDrawer({
  task,
  onClose,
}: {
  task: Task;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const updateTaskMutation = useUpdateTask(task.taskId);

  const initialNotes = [task.description, task.resultNote].filter(Boolean).join("\n\n---\n");

  const [form, setForm] = useState({
    title: task.title,
    contactName: task.customerName || task.leadName || "",
    email: "",
    phone: "",
    value: "",
    stage: "Inquiry",
    expectedClose: "",
    notes: initialNotes,
    owner: task.assignedUserName || "",
  });
  const [markCompleted, setMarkCompleted] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  async function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    if (!form.title.trim() || !form.contactName.trim()) return;

    setIsSubmitting(true);
    setError(null);

    try {
      const payload = {
        title: form.title.trim(),
        contactName: form.contactName.trim(),
        email: form.email || undefined,
        phone: form.phone || undefined,
        value: form.value ? Number(form.value) : 0,
        stage: form.stage,
        status: "active",
        expectedClose: form.expectedClose || undefined,
        notes: form.notes || undefined,
        owner: form.owner || undefined,
      };

      await dealService.create(payload);

      if (markCompleted && task.status !== "COMPLETED" && task.status !== "CANCELLED") {
        await updateTaskMutation.mutateAsync({ status: "COMPLETED" });
      }

      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setSuccess(true);
      setTimeout(() => onClose(), 1400);
    } catch {
      setError("Failed to create deal. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-2xl bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between px-8 py-5 border-b border-slate-100 bg-linear-to-r from-emerald-50 to-white shrink-0">
          <div>
            <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
              <ArrowUpRight className="size-5 text-emerald-600" />
              Convert Task → Deal
            </h3>
            <p className="text-xs text-slate-400 mt-1">
              Source: <span className="text-slate-600 font-semibold">{task.title}</span>
            </p>
          </div>
          <button onClick={onClose} className="p-2 rounded-full text-slate-400 hover:bg-slate-100 transition">
            <X className="size-5" />
          </button>
        </div>

        {/* Source task context */}
        <div className="mx-8 mt-5 p-4 rounded-xl bg-blue-50 border border-blue-100 shrink-0">
          <p className="text-[10px] font-bold text-blue-600 uppercase tracking-wide mb-2">Source Task Details</p>
          <div className="grid grid-cols-3 gap-x-4 gap-y-1 text-xs">
            <div><span className="text-slate-400">Status:</span> <span className="font-semibold text-slate-700">{STATUS_LABEL[task.status]}</span></div>
            <div><span className="text-slate-400">Assignee:</span> <span className="font-semibold text-slate-700">{task.assignedUserName || "—"}</span></div>
            <div><span className="text-slate-400">Due:</span> <span className="font-semibold text-slate-700">{formatDate(task.dueDate)}</span></div>
            {task.customerName && <div className="col-span-3"><span className="text-slate-400">Customer:</span> <span className="font-semibold text-slate-700">{task.customerName}</span></div>}
            {task.leadName && !task.customerName && <div className="col-span-3"><span className="text-slate-400">Lead:</span> <span className="font-semibold text-slate-700">{task.leadName}</span></div>}
          </div>
        </div>

        {/* Success banner */}
        {success && (
          <div className="mx-8 mt-4 p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center gap-3 shrink-0">
            <CheckCircle2 className="size-5 text-emerald-500 shrink-0" />
            <div>
              <p className="text-sm font-bold text-emerald-700">Deal created successfully!</p>
              <p className="text-xs text-emerald-600">Closing drawer...</p>
            </div>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-8 py-5 space-y-5">
          <div className="grid grid-cols-2 gap-5">
            <div className="col-span-2 space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
              <Input
                required
                value={form.title}
                onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>

            <div className="col-span-2 space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Primary Contact *</label>
              <Input
                required
                placeholder="Full name of primary contact"
                value={form.contactName}
                onChange={e => setForm(f => ({ ...f, contactName: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Email</label>
              <Input
                type="email"
                placeholder="contact@example.com"
                value={form.email}
                onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Phone</label>
              <Input
                placeholder="+1 555 0100"
                value={form.phone}
                onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Deal Value (USD)</label>
              <Input
                type="number"
                min="0"
                placeholder="e.g. 15000"
                value={form.value}
                onChange={e => setForm(f => ({ ...f, value: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Pipeline Stage</label>
              <Select
                value={form.stage}
                onChange={e => setForm(f => ({ ...f, stage: e.target.value }))}
                className="py-2"
              >
                <option value="Inquiry">Inquiry</option>
                <option value="Site Visit">Site Visit</option>
                <option value="Proposal">Proposal</option>
                <option value="Negotiation">Negotiation</option>
                <option value="Contract">Contract</option>
                <option value="Confirmed">Confirmed</option>
              </Select>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Expected Close Date</label>
              <Input
                type="date"
                value={form.expectedClose}
                onChange={e => setForm(f => ({ ...f, expectedClose: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Deal Owner</label>
              <Input
                placeholder="Owner full name"
                value={form.owner}
                onChange={e => setForm(f => ({ ...f, owner: e.target.value }))}
                className="py-2 text-sm"
              />
            </div>

            <div className="col-span-2 space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Notes</label>
              <textarea
                rows={4}
                value={form.notes}
                onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                placeholder="Additional context and deal notes..."
              />
            </div>
          </div>

          <label className="flex items-center gap-3 cursor-pointer py-3 px-4 rounded-xl bg-slate-50 border border-slate-100 hover:bg-slate-100 transition">
            <input
              type="checkbox"
              checked={markCompleted}
              onChange={e => setMarkCompleted(e.target.checked)}
              className="rounded accent-emerald-600 size-4"
            />
            <span className="text-sm text-slate-600">
              Mark this task as <strong>Completed</strong> after converting to a Deal
            </span>
          </label>

          {error && (
            <p className="text-sm text-red-500 flex items-center gap-2">
              <AlertCircle className="size-4 shrink-0" />{error}
            </p>
          )}

          <div className="pt-4 flex gap-3 border-t border-slate-100">
            <Button
              type="submit"
              variant="success"
              disabled={isSubmitting || success}
              className="flex-1 text-sm font-semibold py-2.5"
            >
              {isSubmitting ? (
                <span className="flex items-center justify-center gap-2">
                  <Loader2 className="size-4 animate-spin" />Converting…
                </span>
              ) : (
                <span className="flex items-center justify-center gap-2">
                  <ArrowUpRight className="size-4" />Convert to Deal
                </span>
              )}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={onClose}
              className="flex-1 text-sm py-2.5"
            >
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </>
  );
}

// ── Task Detail / Edit Drawer ─────────────────────────────────────────────────

function TaskDetailDrawer({
  task,
  onClose,
  users,
  onConvert,
}: {
  task: Task;
  onClose: () => void;
  users: UserOption[];
  onConvert: () => void;
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
  const canConvert = !task.dealId && task.status !== "CANCELLED";

  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    updateMutation.mutate(form, {
      onSuccess: () => { setEditing(false); onClose(); },
    });
  }

  function handleStatusChange(status: TaskStatus) {
    if (status === task.status) return;
    updateMutation.mutate({ status }, {
      onSuccess: () => onClose(),
    });
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-xl bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between px-7 py-5 border-b border-slate-100 shrink-0">
          <div>
            <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
              <CalendarCheck className="size-5 text-blue-600" />
              {editing ? "Edit Task" : "Task Details"}
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">{linkedEntityType(task)} follow-up activity</p>
          </div>
          <div className="flex items-center gap-2.5">
            {!editing && canConvert && (
              <Button
                variant="success"
                size="sm"
                onClick={() => { onClose(); onConvert(); }}
                leftIcon={<ArrowUpRight className="size-3.5" />}
              >
                Convert → Deal
              </Button>
            )}
            {!editing && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEditing(true)}
              >
                Edit Task
              </Button>
            )}
            <button
              onClick={onClose}
              className="p-2 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
            >
              <X className="size-5" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-7 py-5">
          {editing ? (
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Title *</label>
                <div className="flex flex-wrap gap-2 mb-2">
                  {ACTIVITY_TYPES.map(({ type, label, Icon }) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => setForm(f => ({ ...f, title: `${label}: ` }))}
                      className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-[11px] font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-100"
                    >
                      <Icon className="size-4" />
                      {label}
                    </button>
                  ))}
                </div>
                <Input
                  required
                  value={form.title ?? ""}
                  onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                  className="py-2 text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Description</label>
                <textarea
                  rows={3}
                  value={form.description ?? ""}
                  onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Priority</label>
                  <Select
                    value={form.priority ?? "MEDIUM"}
                    onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))}
                    className="py-2"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Status</label>
                  <Select
                    value={form.status ?? "OPEN"}
                    onChange={e => setForm(f => ({ ...f, status: e.target.value as TaskStatus }))}
                    className="py-2"
                  >
                    {STATUS_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Due Date *</label>
                  <Input
                    required
                    type="date"
                    value={form.dueDate ?? ""}
                    onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))}
                    className="py-2 text-sm"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Assigned Staff *</label>
                  <Select
                    value={form.assignedUserId ?? ""}
                    onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
                    className="py-2"
                  >
                    <option value="">Select staff member…</option>
                    {users.map(u => (
                      <option key={u.userId} value={u.userId}>{u.fullName}</option>
                    ))}
                  </Select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Result / Completion Notes</label>
                <textarea
                  rows={3}
                  value={form.resultNote ?? ""}
                  onChange={e => setForm(f => ({ ...f, resultNote: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                  placeholder="Outcome or notes after completing this task…"
                />
              </div>

              {updateMutation.error && (
                <p className="text-sm text-red-500 flex items-center gap-2">
                  <AlertCircle className="size-4 shrink-0" />Failed to update task. Please try again.
                </p>
              )}

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  disabled={updateMutation.isPending}
                  className="flex-1 text-sm font-semibold py-2.5"
                >
                  {updateMutation.isPending ? (
                    <span className="flex items-center justify-center gap-2">
                      <Loader2 className="size-4 animate-spin" />Saving…
                    </span>
                  ) : "Save Changes"}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setEditing(false)}
                  className="flex-1 text-sm py-2.5"
                >
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <div className="space-y-5">
              {/* Title & badges */}
              <div>
                <h2 className="text-base font-bold text-slate-800 leading-snug">{task.title}</h2>
                <div className="flex flex-wrap gap-2 mt-3">
                  <Badge variant={PRIORITY_BADGE[task.priority]} size="sm" className="text-[10px] uppercase font-bold px-2 py-0.5">
                    {task.priority}
                  </Badge>
                  <Badge variant={STATUS_BADGE[task.status]} size="sm" className="text-[10px] font-bold px-2 py-0.5">
                    {STATUS_LABEL[task.status]}
                  </Badge>
                  {taskOverdue && (
                    <Badge variant="danger" size="sm" className="text-[10px] font-bold px-2 py-0.5 bg-red-100 text-red-700">
                      OVERDUE
                    </Badge>
                  )}
                  {task.dealId && (
                    <Badge variant="default" size="sm" className="text-[10px] font-bold px-2 py-0.5 bg-emerald-100 text-emerald-700">
                      Linked Deal
                    </Badge>
                  )}
                </div>
              </div>

              {/* Info grid */}
              <div className="grid grid-cols-2 gap-4 p-4 bg-slate-50 rounded-xl border border-slate-100">
                <InfoRow icon={<Calendar className="size-4 text-slate-400" />} label="Due Date" value={formatDate(task.dueDate)} />
                <InfoRow icon={<User className="size-4 text-slate-400" />} label="Assigned To" value={task.assignedUserName ?? "—"} />
                <InfoRow icon={<Briefcase className="size-4 text-slate-400" />} label="Related To" value={linkedEntityLabel(task)} />
                <InfoRow icon={<Clock className="size-4 text-slate-400" />} label="Entity Type" value={linkedEntityType(task)} />
              </div>

              {task.description && (
                <div className="space-y-1.5">
                  <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Description</p>
                  <p className="text-sm text-slate-700 leading-relaxed">{task.description}</p>
                </div>
              )}

              {task.resultNote && (
                <div className="space-y-1.5 p-4 bg-emerald-50 rounded-xl border border-emerald-100">
                  <p className="text-[10px] font-semibold text-emerald-700 uppercase tracking-wider">Result / Notes</p>
                  <p className="text-sm text-emerald-800 leading-relaxed">{task.resultNote}</p>
                </div>
              )}

              {/* Convert CTA banner */}
              {canConvert && (
                <div className="p-4 rounded-xl border border-emerald-200 bg-linear-to-r from-emerald-50 to-teal-50 flex items-center justify-between gap-4">
                  <div>
                    <p className="text-sm font-bold text-emerald-800 flex items-center gap-1.5">
                      <Zap className="size-4" />Ready to close this task?
                    </p>
                    <p className="text-xs text-emerald-600 mt-0.5">Convert this follow-up into a Deal and continue the sales workflow.</p>
                  </div>
                  <Button
                    variant="success"
                    size="sm"
                    onClick={() => { onClose(); onConvert(); }}
                    leftIcon={<ArrowUpRight className="size-4" />}
                  >
                    Convert → Deal
                  </Button>
                </div>
              )}

              <div className="pt-3 border-t border-slate-100 text-xs text-slate-400 space-y-1">
                <p>Created by: <span className="text-slate-600 font-medium">{task.createdByName ?? "—"}</span></p>
                <p>Created at: <span className="text-slate-600 font-medium">{formatDate(task.createdAt)}</span></p>
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// ── Entity Search Picker ──────────────────────────────────────────────────────

type CustomerResult = {
  customerId: string;
  fullName: string;
  email?: string | null;
  phone?: string | null;
  companyName?: string | null;
};

function EntitySearchPicker({
  selectedLead,
  selectedCustomer,
  onSelectLead,
  onSelectCustomer,
}: {
  selectedLead: Lead | null;
  selectedCustomer: CustomerResult | null;
  onSelectLead: (lead: Lead | null) => void;
  onSelectCustomer: (customer: CustomerResult | null) => void;
}) {
  const [tab, setTab] = useState<"lead" | "customer">("lead");
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [open, setOpen] = useState(false);

  // Debounce input
  React.useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query.trim()), 320);
    return () => clearTimeout(t);
  }, [query]);

  const leadSearch = useQuery({
    queryKey: ["entity-search-lead", debouncedQuery],
    queryFn: () => leadService.getList({ search: debouncedQuery, size: 8 }),
    enabled: tab === "lead" && debouncedQuery.length >= 1,
    staleTime: 30_000,
  });

  const customerSearch = useQuery({
    queryKey: ["entity-search-customer", debouncedQuery],
    queryFn: () => customerProfileService.getList({ search: debouncedQuery, size: 8 }),
    enabled: tab === "customer" && debouncedQuery.length >= 1,
    staleTime: 30_000,
  });

  const leadResults: Lead[] = leadSearch.data?.data?.content ?? [];
  const customerResults: CustomerResult[] = (customerSearch.data?.data ?? []).map(c => ({
    customerId: c.id,
    fullName: c.name ?? "Unknown customer",
    email: c.email ?? null,
    phone: c.phone ?? null,
    companyName: c.company ?? null,
  }));

  const getEntityDetail = (item: { email?: string | null; phone?: string | null; companyName?: string | null }) => {
    return [item.email, item.phone, item.companyName].filter(Boolean).join(" · ");
  };

  const hasSelection = tab === "lead" ? !!selectedLead : !!selectedCustomer;

  return (
    <div className="border border-slate-200 rounded-xl overflow-hidden">
      {/* Header row with tab toggle */}
      <div className="px-4 py-3 bg-slate-50 border-b border-slate-100">
        <div className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
            <Building2 className="size-3.5" />
            Link to Entity <span className="font-normal text-slate-400">(optional)</span>
          </span>
          <div className="flex rounded-lg border border-slate-200 overflow-hidden text-[10px] font-semibold">
            <button
              type="button"
              onClick={() => { setTab("lead"); setQuery(""); setOpen(false); }}
              className={`px-3 py-1 transition ${tab === "lead" ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-100"}`}
            >
              Lead
            </button>
            <button
              type="button"
              onClick={() => { setTab("customer"); setQuery(""); setOpen(false); }}
              className={`px-3 py-1 transition ${tab === "customer" ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-100"}`}
            >
              Customer
            </button>
          </div>
        </div>
        <p className="mt-2 text-[10px] text-slate-500">Search by name, email, phone, or company to link the correct lead or customer for this activity.</p>
      </div>

      <div className="px-4 py-3 space-y-2">
        {/* Selected chip */}
        {hasSelection && (
          <div className="flex items-center gap-2 px-3 py-2 bg-[#E6F1FB] rounded-lg border border-[#85B7EB]">
            <User className="size-3.5 text-[#0C447C] shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-[#0C447C] truncate">
                {tab === "lead" ? selectedLead!.fullName : selectedCustomer!.fullName}
              </p>
              <p className="text-[10px] text-[#185FA5] truncate">
                {tab === "lead"
                  ? (selectedLead!.email ?? selectedLead!.companyName ?? selectedLead!.status)
                  : (selectedCustomer!.email ?? selectedCustomer!.companyName ?? "")}
              </p>
            </div>
            <button
              type="button"
              onClick={() => tab === "lead" ? onSelectLead(null) : onSelectCustomer(null)}
              className="shrink-0 p-0.5 rounded text-[#185FA5] hover:text-[#A32D2D] transition"
              title="Remove"
            >
              <X className="size-3.5" />
            </button>
          </div>
        )}

        {/* Search input — hidden once entity is selected */}
        {!hasSelection && (
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              type="text"
              value={query}
              onChange={e => { setQuery(e.target.value); setOpen(true); }}
              onFocus={() => setOpen(true)}
              onBlur={() => setTimeout(() => setOpen(false), 180)}
              placeholder={tab === "lead" ? "Search lead by name, email, company…" : "Search customer by name, email, company…"}
              className="w-full pl-8 pr-3 py-2 text-xs border border-slate-200 rounded-lg bg-white focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 transition placeholder:text-slate-400"
            />

            {/* Dropdown results */}
            {open && debouncedQuery.length >= 1 && (
              <div className="absolute top-full left-0 right-0 z-50 mt-1 max-h-52 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-xl">
                {tab === "lead" && (
                  leadSearch.isFetching
                    ? <p className="py-4 text-center text-xs text-slate-400">Searching leads…</p>
                    : leadResults.length === 0
                      ? <p className="py-4 text-center text-xs text-slate-400">No leads found for "{debouncedQuery}"</p>
                      : leadResults.map(lead => (
                        <button
                          key={lead.leadId}
                          type="button"
                          onMouseDown={() => { onSelectLead(lead); setQuery(""); setOpen(false); }}
                          className="w-full flex items-center gap-2.5 px-3 py-2.5 text-left hover:bg-[#E6F1FB] transition border-b border-slate-50 last:border-0"
                        >
                          <div className="size-7 rounded-full bg-[#E6F1FB] flex items-center justify-center shrink-0 text-[10px] font-bold text-[#185FA5]">
                            L
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs font-semibold text-slate-800 truncate">{lead.fullName}</p>
                            <p className="text-[10px] text-slate-400 truncate">{getEntityDetail({ email: lead.email, phone: lead.phone, companyName: lead.companyName }) || lead.status}</p>
                            <p className="text-[9px] text-slate-500 uppercase tracking-[0.16em] mt-1">Lead</p>
                          </div>
                        </button>
                      ))
                )}

                {tab === "customer" && (
                  customerSearch.isFetching
                    ? <p className="py-4 text-center text-xs text-slate-400">Searching customers…</p>
                    : customerResults.length === 0
                      ? <p className="py-4 text-center text-xs text-slate-400">No customers found for "{debouncedQuery}"</p>
                      : customerResults.map(c => (
                        <button
                          key={c.customerId}
                          type="button"
                          onMouseDown={() => { onSelectCustomer(c); setQuery(""); setOpen(false); }}
                          className="w-full flex items-center gap-2.5 px-3 py-2.5 text-left hover:bg-[#E6F1FB] transition border-b border-slate-50 last:border-0"
                        >
                          <div className="size-7 rounded-full bg-[#EAF3DE] flex items-center justify-center shrink-0 text-[10px] font-bold text-[#3B6D11]">
                            C
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs font-semibold text-slate-800 truncate">{c.fullName}</p>
                            <p className="text-[10px] text-slate-400 truncate">{getEntityDetail(c)}</p>
                            <p className="text-[9px] text-slate-500 uppercase tracking-[0.16em] mt-1">Customer</p>
                          </div>
                        </button>
                      ))
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Create Task Drawer (Pipedrive-style) ──────────────────────────────────────

function CreateTaskDrawer({
  onClose,
  users,
  initialDueDate,
}: {
  onClose: () => void;
  users: UserOption[];
  initialDueDate?: string;
}) {
  const [activityType, setActivityType] = useState<ActivityType>("FOLLOW_UP");
  const [form, setForm] = useState<CreateTaskPayload>({
    title: "",
    description: "",
    assignedUserId: users[0]?.userId ?? "",
    priority: "MEDIUM",
    dueDate: initialDueDate ?? "",
    contactName: "",
  });
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerResult | null>(null);
  const createMutation = useCreateTask();

  function handleSelectLead(lead: Lead | null) {
    setSelectedLead(lead);
    setForm(f => {
      const titleIsAuto = !f.title.trim() || ACTIVITY_TYPES.some(a => f.title === `${a.label}: `);
      return {
        ...f,
        leadId: lead?.leadId ?? undefined,
        customerId: undefined,
        contactName: lead?.fullName ?? "",
        title: lead && titleIsAuto ? `${ACTIVITY_TYPES.find(a => a.type === activityType)?.label ?? "Call"}: ${lead.fullName}` : f.title,
      };
    });
    if (lead) setSelectedCustomer(null);
  }

  function handleSelectCustomer(customer: CustomerResult | null) {
    setSelectedCustomer(customer);
    setForm(f => {
      const titleIsAuto = !f.title.trim() || ACTIVITY_TYPES.some(a => f.title === `${a.label}: `);
      return {
        ...f,
        customerId: customer?.customerId ?? undefined,
        leadId: undefined,
        contactName: customer?.fullName ?? "",
        title: customer && titleIsAuto ? `${ACTIVITY_TYPES.find(a => a.type === activityType)?.label ?? "Call"}: ${customer.fullName}` : f.title,
      };
    });
    if (customer) setSelectedLead(null);
  }

  // FIX: update title when switching types — replaces any previously auto-generated title
  function handleActivityTypeChange(type: ActivityType) {
    setActivityType(type);
    const newLabel = ACTIVITY_TYPES.find(a => a.type === type)?.label ?? "";
    const isAutoGenerated =
      form.title === "" ||
      ACTIVITY_TYPES.some(a => form.title === `${a.label}: `);
    if (isAutoGenerated) {
      setForm(f => ({ ...f, title: `${newLabel}: ` }));
    }
  }

  function applyDatePreset(days: number) {
    setForm(f => ({ ...f, dueDate: addDays(days) }));
  }

  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    if (!form.title.trim() || !form.dueDate || !form.assignedUserId) return;

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (new Date(form.dueDate) < today) {
      alert("Due date must be today or a future date.");
      return;
    }

    const payload: CreateTaskPayload = {
      ...form,
      contactName: form.contactName?.trim() || undefined,
    };

    createMutation.mutate(payload, { onSuccess: () => onClose() });
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-lg bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between px-7 py-5 border-b border-slate-100 shrink-0">
          <div>
            <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
              <CalendarCheck className="size-5 text-blue-600" />
              Create Follow-up Task
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">Assign follow-up actions to the sales team</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <X className="size-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-7 py-5 space-y-5">

          {/* Activity Type Selector */}
          <div className="space-y-2.5">
            <label className="text-xs font-semibold text-slate-600 uppercase tracking-wide">Activity Type</label>
            <div className="grid grid-cols-3 gap-2">
              {ACTIVITY_TYPES.map(({ type, label, Icon, activeClass, idleClass }) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => handleActivityTypeChange(type)}
                  className={`flex flex-col items-center gap-1.5 py-3 px-2 rounded-xl border text-xs font-bold transition-all ${activityType === type ? activeClass : idleClass
                    }`}
                >
                  <Icon className="size-4" />
                  {label}
                </button>
              ))}
            </div>
          </div>

          {/* Title */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-600">Activity Title *</label>
            <Input
              required
              placeholder="e.g. Call client to confirm headcount…"
              value={form.title}
              onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
              className="py-2 text-sm"
            />
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-600">Description / Goal</label>
            <textarea
              rows={3}
              placeholder="Describe the objective and steps to complete…"
              value={form.description ?? ""}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
            />
          </div>

          {/* Due Date with quick presets */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600">Due Date *</label>
            <div className="flex gap-2 flex-wrap">
              {[
                { label: "Today", days: 0 },
                { label: "+1 Day", days: 1 },
                { label: "+3 Days", days: 3 },
                { label: "+1 Week", days: 7 },
              ].map(preset => (
                <button
                  key={preset.label}
                  type="button"
                  onClick={() => applyDatePreset(preset.days)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${form.dueDate === addDays(preset.days)
                      ? "bg-blue-600 text-white border-blue-600 shadow-sm"
                      : "bg-white text-slate-600 border-slate-200 hover:border-blue-300 hover:text-blue-600"
                    }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>
            <Input
              required
              type="date"
              value={form.dueDate}
              onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))}
              className="py-2 text-sm"
            />
          </div>

          {/* Priority & Assignee */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Priority *</label>
              <Select
                value={form.priority}
                onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))}
                className="py-2"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Assigned Staff *</label>
              <Select
                required
                value={form.assignedUserId}
                onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
                className="py-2"
              >
                <option value="">Select staff member…</option>
                {users.map(u => (
                  <option key={u.userId} value={u.userId}>{u.fullName}</option>
                ))}
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-600">Primary Contact</label>
            <Input
              value={form.contactName ?? ""}
              onChange={e => setForm(f => ({ ...f, contactName: e.target.value }))}
              placeholder="Contact name or customer/lead name"
              className="py-2 text-sm"
            />
          </div>

          {/* Entity Link — searchable picker */}
          <EntitySearchPicker
            selectedLead={selectedLead}
            selectedCustomer={selectedCustomer}
            onSelectLead={handleSelectLead}
            onSelectCustomer={handleSelectCustomer}
          />

          {(selectedLead || selectedCustomer) && (
            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500 mb-3">Quick action</p>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { label: "Call", suffix: "now" },
                  { label: "Meeting", suffix: "set up" },
                  { label: "Email", suffix: "send" },
                ].map(action => (
                  <button
                    key={action.label}
                    type="button"
                    onClick={() => {
                      const targetName = selectedLead?.fullName ?? selectedCustomer?.fullName ?? "";
                      setForm(f => ({ ...f, title: `${action.label}: ${targetName}`, contactName: targetName }));
                    }}
                    className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-[11px] font-semibold text-slate-700 hover:bg-slate-100 transition"
                  >
                    <span className="block text-slate-900">{action.label}</span>
                    <span className="text-[10px] text-slate-400">{selectedLead ? "Lead" : "Customer"}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {createMutation.error && (
            <p className="text-sm text-red-500 flex items-center gap-2">
              <AlertCircle className="size-4 shrink-0" />Failed to create task. Please check required fields.
            </p>
          )}

          <div className="pt-4 flex gap-3 border-t border-slate-100">
            <Button
              type="submit"
              variant="primary"
              disabled={createMutation.isPending}
              className="flex-1 text-sm font-semibold py-2.5"
            >
              {createMutation.isPending ? (
                <span className="flex items-center justify-center gap-2">
                  <Loader2 className="size-4 animate-spin" />Creating…
                </span>
              ) : "Create Task"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={onClose}
              className="flex-1 text-sm py-2.5"
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

type ViewMode = "list" | "calendar";
type TabId = "all" | "today" | "upcoming" | "overdue" | "completed";

const HOURS = [
  "08:00", "09:00", "10:00", "11:00", "12:00",
  "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00",
];

// Calendar helpers — Monday-first week
function getWeekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  d.setDate(d.getDate() - (day === 0 ? 6 : day - 1));
  d.setHours(0, 0, 0, 0);
  return d;
}

function getWeekDays(ws: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(ws);
    d.setDate(d.getDate() + i);
    return d;
  });
}

function toDateStr(d: Date): string {
  return d.toISOString().split("T")[0];
}

// Detect activity type from title prefix ("Call: …")
function detectActivityType(title: string): ActivityType {
  const found = ACTIVITY_TYPES.find(a =>
    title.toLowerCase().startsWith(a.label.toLowerCase() + ":")
  );
  return found?.type ?? "TASK";
}

// Calendar chip color per activity type
const ACTIVITY_CHIP: Record<ActivityType, string> = {
  CALL: "bg-green-50 border-green-200 text-green-700",
  EMAIL: "bg-blue-50 border-blue-200 text-blue-700",
  MEETING: "bg-purple-50 border-purple-200 text-purple-700",
  SITE_VISIT: "bg-orange-50 border-orange-200 text-orange-700",
  FOLLOW_UP: "bg-teal-50 border-teal-200 text-teal-700",
  TASK: "bg-slate-50 border-slate-200 text-slate-600",
};

export function FollowUpTaskListScreen() {
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [activeTab, setActiveTab] = useState<TabId>("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [assigneeFilter, setAssigneeFilter] = useState("");
  const [activityTypeFilter, setActivityTypeFilter] = useState<ActivityType | "">("");
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createDueDate, setCreateDueDate] = useState<string | undefined>(undefined);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [convertTask, setConvertTask] = useState<Task | null>(null);
  const [weekStart, setWeekStart] = useState(() => getWeekStart(new Date()));

  const queryClient = useQueryClient();
  const { data: usersData } = useUsers();
  const users = useMemo(() => usersData?.data ?? [], [usersData]);

  const toggleMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateTaskPayload }) =>
      taskService.update(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["tasks"] }),
  });

  // List query
  const listParams = useMemo<TaskListParams>(() => {
    const params: TaskListParams = { page, size: 20 };
    if (searchTerm.trim()) params.search = searchTerm.trim();
    if (priorityFilter) params.priority = priorityFilter;
    if (assigneeFilter) params.assignedUserId = assigneeFilter;
    if (activeTab === "overdue") params.overdue = true;
    if (activeTab === "completed") params.status = "COMPLETED";
    return params;
  }, [activeTab, searchTerm, priorityFilter, assigneeFilter, page]);

  const { data, isLoading, isError, refetch } = useTasks(listParams);

  // Calendar query — large batch, filter client-side
  const calParams = useMemo<TaskListParams>(() => ({
    page: 0,
    size: 200,
    ...(assigneeFilter ? { assignedUserId: assigneeFilter } : {}),
  }), [assigneeFilter]);

  const { data: calData, isLoading: calLoading } = useTasks(
    viewMode === "calendar" ? calParams : { page: 0, size: 1 }
  );

  const allTasks: Task[] = data?.data?.content ?? [];
  const totalPages = data?.data?.totalPages ?? 1;
  const totalElements = data?.data?.totalElements ?? 0;
  const calTasks: Task[] = calData?.data?.content ?? [];

  const filteredTasks = useMemo(() => {
    let filtered = allTasks;
    const today = new Date().toISOString().split("T")[0];

    if (activeTab === "today") {
      filtered = filtered.filter(t => t.dueDate?.split("T")[0] === today && t.status !== "COMPLETED" && t.status !== "CANCELLED");
    } else if (activeTab === "upcoming") {
      filtered = filtered.filter(t => {
        const d = t.dueDate?.split("T")[0] ?? "";
        return d > today && t.status !== "COMPLETED" && t.status !== "CANCELLED" && !isOverdue(t);
      });
    }

    if (activityTypeFilter) {
      filtered = filtered.filter(t => detectActivityType(t.title) === activityTypeFilter);
    }

    return filtered;
  }, [allTasks, activeTab, activityTypeFilter]);

  const weekDays = useMemo(() => getWeekDays(weekStart), [weekStart]);
  const todayStr = new Date().toISOString().split("T")[0];

  const tabs: { id: TabId; label: string }[] = [
    { id: "all", label: "All" },
    { id: "today", label: "Today" },
    { id: "upcoming", label: "Upcoming" },
    { id: "overdue", label: "Overdue" },
    { id: "completed", label: "Completed" },
  ];

  // ── Pipedrive-style table row ────────────────────────────────────────────
  function TaskRow({ task }: { task: Task }) {
    const overdue = isOverdue(task);
    const done = task.status === "COMPLETED";
    const canConvert = !task.dealId && task.status !== "CANCELLED";
    const actType = detectActivityType(task.title);
    const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;
    // Customer / Lead / Deal name — prominently shown
    const contactName = task.customerName ?? task.leadName ?? task.contactName ?? null;
    const entityName = linkedEntityLabel(task);
    const entityType = linkedEntityType(task);

    return (
      <tr
        className={`group border-b border-slate-200 dark:border-slate-700 transition-colors cursor-pointer ${done ? "opacity-70 bg-slate-900/70" : overdue ? "hover:bg-[#4F1B1C]/40 dark:hover:bg-[#4F1B1C]/40" : "hover:bg-slate-100/60 dark:hover:bg-slate-800/80"
          }`}
        onClick={() => setSelectedTask(task)}
      >
        {/* Done toggle */}
        <td className="w-10 px-3 py-3" onClick={e => e.stopPropagation()}>
          <button
            onClick={() => {
              if (!done) {
                toggleMutation.mutate({ id: task.taskId, payload: { status: "COMPLETED" } });
              }
            }}
            title={done ? "Task completed" : "Mark complete"}
            className="block focus:outline-none"
          >
            <CheckCircle2 className={`size-[18px] transition ${done ? "text-[#3B6D11] fill-[#EAF3DE]" :
                overdue ? "text-[#E24B4A] hover:text-[#A32D2D]" :
                  "text-slate-200 hover:text-[#185FA5]"
              }`} />
          </button>
        </td>

        {/* Activity type icon */}
        <td className="w-10 px-1 py-3">
          <div className={`size-7 rounded-lg flex items-center justify-center border ${typeInfo.activeClass}`}>
            <typeInfo.Icon className="size-3.5" />
          </div>
        </td>

        {/* Subject + description */}
        <td className="px-3 py-3 min-w-[180px] max-w-xs">
          <p className={`text-xs font-bold truncate leading-snug ${done ? "line-through text-slate-400" : overdue ? "text-[#A32D2D]" : "text-slate-800"
            }`}>
            {task.title}
          </p>
          {task.description && (
            <p className="text-[10px] text-slate-400 truncate mt-0.5 leading-tight">{task.description}</p>
          )}
        </td>

        {/* Contact person — Lead or Customer name (key column) */}
        <td className="px-3 py-3 min-w-[120px] max-w-[160px]">
          {contactName ? (
            <div className="flex items-center gap-1.5">
              <div className="size-5 rounded-full bg-[#FAEEDA] flex items-center justify-center text-[9px] font-bold text-[#854F0B] shrink-0">
                {contactName.charAt(0).toUpperCase()}
              </div>
              <span className="text-xs font-semibold text-slate-700 truncate dark:text-slate-100">{contactName}</span>
            </div>
          ) : (
            <span className="text-slate-300 text-xs">—</span>
          )}
        </td>

        {/* Deal / Entity link */}
        <td className="px-3 py-3 min-w-[120px] max-w-[140px]">
          {entityName !== "—" ? (
            <div className="inline-flex items-center gap-2 min-w-0">
              <Briefcase className="size-3 text-slate-400 shrink-0" />
              <div className="min-w-0 max-w-full overflow-hidden">
                <p className="text-[10px] uppercase tracking-[0.12em] text-slate-500 mb-0.5 truncate dark:text-slate-400">
                  {entityType !== "General" ? entityType : "Entity"}
                </p>
                <p className="text-[11px] font-semibold text-slate-900 truncate dark:text-slate-100 overflow-hidden whitespace-nowrap">
                  {entityName}
                </p>
              </div>
            </div>
          ) : (
            <span className="text-slate-300 text-xs">—</span>
          )}
        </td>

        {/* Due date */}
        <td className="w-[90px] px-3 py-3 whitespace-nowrap">
          <p className={`text-xs font-semibold ${overdue ? "text-[#A32D2D]" : "text-slate-600 dark:text-slate-300"}`}>
            {formatDate(task.dueDate)}
          </p>
          {overdue && <p className="text-[9px] text-[#E24B4A] font-bold mt-0.5">OVERDUE</p>}
        </td>

        {/* Assigned to */}
        <td className="px-3 py-3 whitespace-nowrap">
          {task.assignedUserName ? (
            <div className="flex items-center gap-1.5">
              <div className="size-5 rounded-full bg-[#E6F1FB] flex items-center justify-center text-[9px] font-bold text-[#0C447C] shrink-0">
                {task.assignedUserName.charAt(0).toUpperCase()}
              </div>
              <span className="text-[10px] text-slate-600 font-medium max-w-[90px] truncate">{task.assignedUserName}</span>
            </div>
          ) : (
            <span className="text-slate-300 text-xs">—</span>
          )}
        </td>

        {/* Priority + Status */}
        <td className="px-3 py-3 whitespace-nowrap">
          <div className="flex flex-col gap-1">
            <Badge variant={PRIORITY_BADGE[task.priority]} size="sm" className="text-[9px] uppercase font-bold px-1.5 w-fit">
              {task.priority}
            </Badge>
            <Badge variant={STATUS_BADGE[task.status]} size="sm" className="text-[9px] font-bold px-1.5 w-fit">
              {STATUS_LABEL[task.status]}
            </Badge>
          </div>
        </td>

        {/* Row actions — appear on hover */}
        <td className="px-3 py-3 w-[120px]" onClick={e => e.stopPropagation()}>
          <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition">
            <Button variant="secondary" size="sm" onClick={() => setSelectedTask(task)}>Edit</Button>
            {canConvert && (
              <Button
                variant="success"
                size="sm"
                onClick={() => setConvertTask(task)}
                leftIcon={<ArrowUpRight className="size-3" />}
              >
                Deal
              </Button>
            )}
          </div>
        </td>
      </tr>
    );
  }

  // ── Weekly calendar column ───────────────────────────────────────────────
  function CalendarDay({ day }: { day: Date }) {
    const ds = toDateStr(day);
    const isToday = ds === todayStr;
    const dayLabels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    const dayIdx = (day.getDay() + 6) % 7;
    const dayTasks = calTasks
      .filter(t => t.dueDate?.split("T")[0] === ds)
      .filter(t => !activityTypeFilter || detectActivityType(t.title) === activityTypeFilter)
      .sort((a, b) => (isOverdue(b) ? 1 : 0) - (isOverdue(a) ? 1 : 0));

    return (
      <div className={`flex-1 min-w-0 flex flex-col border-r border-slate-100 last:border-r-0 ${isToday ? "bg-[#E6F1FB]/25" : ""}`}>
        {/* Day header */}
        <div className={`px-2 py-2.5 text-center border-b border-slate-100 shrink-0 ${isToday ? "bg-[#185FA5]" : "bg-slate-50/80"}`}>
          <p className={`text-[9px] font-bold uppercase tracking-widest ${isToday ? "text-blue-100" : "text-slate-400"}`}>
            {dayLabels[dayIdx]}
          </p>
          <p className={`text-base font-bold leading-tight mt-0.5 ${isToday ? "text-white" : "text-slate-700"}`}>
            {day.getDate()}
          </p>
          {dayTasks.length > 0 && (
            <span className={`inline-block mt-1 text-[8px] font-bold px-1.5 py-0.5 rounded-full ${isToday ? "bg-white/20 text-white" : "bg-slate-200 text-slate-500"
              }`}>
              {dayTasks.length}
            </span>
          )}
        </div>

        {/* Task chips */}
        <div className="flex-1 px-1.5 py-2 space-y-1.5 overflow-y-auto min-h-[160px] max-h-[380px]">
          {dayTasks.map(task => {
            const actType = detectActivityType(task.title);
            const chipCls = ACTIVITY_CHIP[actType];
            const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;
            const done = task.status === "COMPLETED";
            const overdue = isOverdue(task);
            const contact = task.customerName ?? task.leadName;

            return (
              <button
                key={task.taskId}
                onClick={() => setSelectedTask(task)}
                className={`w-full text-left px-2 py-2 rounded-lg border text-[10px] font-semibold transition hover:shadow-sm active:scale-[0.98] ${done ? "opacity-50 line-through bg-slate-50 border-slate-100 text-slate-400" :
                    overdue ? "bg-[#FCEBEB] border-[#F7C1C1] text-[#791F1F]" :
                      chipCls
                  }`}
              >
                <span className="flex items-center gap-1.5 min-w-0">
                  <typeInfo.Icon className="size-3 shrink-0" />
                  <span className="truncate leading-tight">
                    {task.title.replace(/^[^:]+:\s*/, "") || task.title}
                  </span>
                </span>
                {/* Customer / Lead name chip inside calendar card */}
                {contact && (
                  <span className="flex items-center gap-1 mt-1 text-[9px] opacity-70 font-medium truncate">
                    <User className="size-2.5 shrink-0" />
                    {contact}
                  </span>
                )}
                {task.assignedUserName && (
                  <span className="block mt-0.5 text-[9px] opacity-60 truncate">{task.assignedUserName}</span>
                )}
              </button>
            );
          })}

          {/* Add shortcut */}
          <button
            onClick={() => { setCreateDueDate(ds); setIsCreateOpen(true); }}
            className="w-full text-center py-1.5 text-[9px] text-slate-300 hover:text-[#185FA5] hover:bg-[#E6F1FB] rounded-lg transition"
          >
            + Add activity
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* ── Top bar ─────────────────────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-foreground flex items-center gap-2">
            <CalendarCheck className="size-5 text-[#185FA5]" />
            Manage Follow-up Tasks
          </h1>
          <p className="text-[10px] text-muted-foreground mt-0.5">
            Track lead contacts · customer clarifications · deal confirmations
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* List / Calendar toggle */}
          <div className="flex rounded-lg border border-slate-200 overflow-hidden">
            <button
              onClick={() => setViewMode("list")}
              title="Table view"
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold transition ${viewMode === "list" ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-50"
                }`}
            >
              <LayoutList className="size-3.5" /> List
            </button>
            <button
              onClick={() => setViewMode("calendar")}
              title="Calendar view"
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold transition ${viewMode === "calendar" ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-50"
                }`}
            >
              <CalendarDays className="size-3.5" /> Calendar
            </button>
          </div>


          <Button variant="primary" size="sm" onClick={() => { setCreateDueDate(undefined); setIsCreateOpen(true); }} leftIcon={<Plus className="size-4" />}>
            Tasks
          </Button>
        </div>
      </div>

      {/* ═══════════════════════════════════════════════════════════════════
          LIST VIEW — Pipedrive-style data table
      ═══════════════════════════════════════════════════════════════════ */}
      {viewMode === "list" && (
        <>
          {/* Combined filter + tab bar */}
          <div className="flex flex-wrap items-center gap-2 px-4 py-2.5 rounded-xl border border-slate-100 bg-white shadow-xs dark:border-slate-700/80 dark:bg-slate-900/80">
            {/* Status tabs as pill buttons */}
            <div className="flex rounded-lg border border-slate-200 overflow-hidden shrink-0 dark:border-slate-700">
              {tabs.map(tab => (
                <button
                  key={tab.id}
                  onClick={() => { setActiveTab(tab.id); setPage(0); }}
                  className={`px-3 py-1.5 text-[10px] font-bold transition ${activeTab === tab.id ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-50 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-800"
                    }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Activity type quick-filter chips */}
            <div className="hidden lg:flex items-center gap-1">
              {ACTIVITY_TYPES.map(a => {
                const active = activityTypeFilter === a.type;
                return (
                  <button
                    key={a.type}
                    type="button"
                    onClick={() => setActivityTypeFilter(active ? "" : a.type)}
                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded border text-[9px] font-semibold transition ${active ? "bg-slate-900 text-white border-slate-900" : a.idleClass}`}
                  >
                    <a.Icon className="size-2.5" />{a.label}
                  </button>
                );
              })}
            </div>

            {/* Search */}
            <div className="relative flex-1 min-w-40 max-w-56">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 pointer-events-none" />
              <input
                type="text"
                placeholder="Search tasks…"
                value={searchTerm}
                onChange={e => { setSearchTerm(e.target.value); setPage(0); }}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-900 focus:outline-none focus:border-[#185FA5] focus:bg-white transition dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:bg-slate-900"
              />
            </div>

            <Select value={priorityFilter} onChange={e => { setPriorityFilter(e.target.value); setPage(0); }} className="py-1.5 text-xs w-28 shrink-0">
              <option value="">All Priority</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </Select>

            <Select value={assigneeFilter} onChange={e => { setAssigneeFilter(e.target.value); setPage(0); }} className="py-1.5 text-xs w-36 shrink-0">
              <option value="">All Staff</option>
              {users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
            </Select>

            <p className="ml-auto text-[10px] text-slate-400 shrink-0">
              <strong className="text-slate-600">{filteredTasks.length}</strong>
              {totalElements > 0 ? <> / <strong className="text-slate-600">{totalElements}</strong></> : ""} tasks
            </p>
          </div>

          {/* Pipedrive-style table */}
          <div className="rounded-xl border border-slate-200 bg-white shadow-xs overflow-x-auto dark:border-slate-700/80 dark:bg-slate-950 dark:shadow-none">
            {isLoading ? (
              <div className="py-16 flex justify-center text-slate-300"><Loader2 className="size-7 animate-spin" /></div>
            ) : isError ? (
              <div className="py-10 text-center text-sm text-[#A32D2D]">
                <AlertCircle className="size-5 mx-auto mb-2" />
                Failed to load.{" "}
                <button onClick={() => refetch()} className="underline font-semibold">Retry</button>
              </div>
            ) : filteredTasks.length === 0 ? (
              <div className="py-16 text-center text-slate-400 text-sm">
                <CalendarCheck className="size-10 mx-auto mb-3 text-slate-200" />
                No activities match the current filters.
              </div>
            ) : (
              <table className="w-full text-left text-slate-900 dark:text-slate-100">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-700/80 dark:bg-slate-900/95">
                    <th className="w-10 px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Done</th>
                    <th className="w-10 px-1 py-2.5" />
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Subject</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Contact</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Deal / Entity</th>
                    <th className="w-[90px] px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Due Date</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Assigned To</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Priority / Status</th>
                    <th className="w-[120px] px-3 py-2.5" />
                  </tr>
                </thead>
                <tbody>
                  {filteredTasks.map(task => <TaskRow key={task.taskId} task={task} />)}
                </tbody>
              </table>
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-3">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                className="p-2 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 transition">
                <ChevronLeft className="size-4" />
              </button>
              <span className="text-xs text-slate-500">Page <strong>{page + 1}</strong> / <strong>{totalPages}</strong></span>
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                className="p-2 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 disabled:opacity-40 transition">
                <ChevronRight className="size-4" />
              </button>
            </div>
          )}
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════
          CALENDAR VIEW — Weekly grid
      ═══════════════════════════════════════════════════════════════════ */}
      {viewMode === "calendar" && (
        <>
          {/* Calendar toolbar */}
          <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5 rounded-xl border border-slate-100 bg-white shadow-xs">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setWeekStart(w => { const d = new Date(w); d.setDate(d.getDate() - 7); return d; })}
                className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 transition"
              >
                <ChevronLeft className="size-4" />
              </button>
              <button
                onClick={() => setWeekStart(getWeekStart(new Date()))}
                className="px-3 py-1 text-[10px] font-bold text-[#185FA5] border border-[#185FA5]/30 rounded-lg hover:bg-[#E6F1FB] transition"
              >
                Today
              </button>
              <button
                onClick={() => setWeekStart(w => { const d = new Date(w); d.setDate(d.getDate() + 7); return d; })}
                className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 transition"
              >
                <ChevronRight className="size-4" />
              </button>
              <span className="text-xs font-bold text-slate-700">
                {weekStart.toLocaleDateString("en-GB", { day: "2-digit", month: "short" })}
                {" – "}
                {weekDays[6].toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" })}
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Select value={assigneeFilter} onChange={e => setAssigneeFilter(e.target.value)} className="py-1.5 text-xs w-36">
                <option value="">All Staff</option>
                {users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
              </Select>
              {/* Legend / activity type filter */}
              <div className="hidden xl:flex flex-wrap items-center gap-1.5">
                {ACTIVITY_TYPES.map(a => {
                  const active = activityTypeFilter === a.type;
                  return (
                    <button
                      key={a.type}
                      type="button"
                      onClick={() => setActivityTypeFilter(active ? "" : a.type)}
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded border text-[9px] font-semibold transition ${active ? "bg-slate-900 text-white border-slate-900" : ACTIVITY_CHIP[a.type]}`}
                    >
                      <a.Icon className="size-2.5" />{a.label}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Weekly grid */}
          <div className="rounded-xl border border-slate-100 bg-white shadow-xs overflow-hidden">
            {calLoading ? (
              <div className="py-16 flex justify-center text-slate-300"><Loader2 className="size-7 animate-spin" /></div>
            ) : (
              <div className="flex min-h-[320px]">
                {weekDays.map(day => <CalendarDay key={toDateStr(day)} day={day} />)}
              </div>
            )}
          </div>

          {/* Overdue alert banner */}
          {calTasks.filter(isOverdue).length > 0 && (
            <div className="px-4 py-3 rounded-xl border border-[#F7C1C1] bg-[#FCEBEB] flex items-start gap-3">
              <AlertCircle className="size-4 text-[#A32D2D] shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-bold text-[#A32D2D]">
                  {calTasks.filter(isOverdue).length} overdue {calTasks.filter(isOverdue).length === 1 ? "task" : "tasks"} need attention
                </p>
                <button
                  onClick={() => { setViewMode("list"); setActiveTab("overdue"); }}
                  className="text-[10px] text-[#A32D2D] underline font-semibold"
                >
                  Switch to list view → Overdue
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* ── Shared drawers ───────────────────────────────────────────────── */}
      {isCreateOpen && <CreateTaskDrawer onClose={() => { setIsCreateOpen(false); setCreateDueDate(undefined); }} users={users} initialDueDate={createDueDate} />}
      {selectedTask && (
        <TaskDetailDrawer
          task={selectedTask}
          onClose={() => setSelectedTask(null)}
          users={users}
          onConvert={() => { setConvertTask(selectedTask); setSelectedTask(null); }}
        />
      )}
      {convertTask && <ConvertToDealDrawer task={convertTask} onClose={() => setConvertTask(null)} />}
    </div>
  );
}
