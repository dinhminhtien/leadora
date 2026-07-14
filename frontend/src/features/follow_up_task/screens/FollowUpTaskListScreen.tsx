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
  UserCog,
  Phone,
  Mail,
  Users2,
  MapPin,
  CheckSquare2,
  Building2,
  ArrowRight,
} from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  useTasks,
  useTaskDetail,
  useCreateTask,
  useUpdateTask,
  useResignTask,
  useUsers,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import {
  taskService,
  type Task,
  type TaskPriority,
  type TaskStatus,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type ResignTaskPayload,
  type TaskListParams,
} from "@/services/follow_up_task_service";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";
import { useRouter } from "next/navigation";
import { leadService, type Lead } from "@/services/lead_service";
import { customerProfileService } from "@/services/customer_profile_service";
import { dealService } from "@/services/deal_service";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";

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
  if (task.status === "COMPLETED" || task.status === "CANCELLED") return false;
  if (task.endAt) return new Date(task.endAt) < new Date();
  return false;
}

/** Primary date for agenda/calendar grouping — uses startAt date. */
function taskDateKey(task: Task): string | null {
  if (task.startAt) return extractLocalDate(task.startAt);
  return null;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function formatTime(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

/** Build a local ISO datetime string from a date string "YYYY-MM-DD" and time "HH:mm". */
function buildISODateTime(date: string, time: string): string {
  if (!date || !time) return "";
  const offset = new Date().getTimezoneOffset();
  const sign = offset <= 0 ? "+" : "-";
  const absH = String(Math.floor(Math.abs(offset) / 60)).padStart(2, "0");
  const absM = String(Math.abs(offset) % 60).padStart(2, "0");
  return `${date}T${time}:00${sign}${absH}:${absM}`;
}

function addDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatShortDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleString("en-GB", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }).replace(",", "");
}

/** Extract local date "YYYY-MM-DD" from any ISO datetime string (handles UTC and offset). */
function extractLocalDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Extract local time "HH:mm" from any ISO datetime string (handles UTC and offset). */
function extractLocalTime(iso: string | null | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

function linkedEntityLabel(task: Task): string {
  if (task.dealName) return task.dealName;
  if (task.customerName) return task.customerName;
  if (task.leadName) return task.leadName;
  if (task.primaryContactName) return task.primaryContactName;
  return "—";
}

function linkedEntityType(task: Task): string {
  if (task.dealId) return "Deal";
  if (task.customerId) return "Customer";
  if (task.leadId) return "Lead";
  return "General";
}

function humanizeEnum(raw?: string | null): string {
  if (!raw) return "—";
  return raw
    .toLowerCase()
    .split(/[_\s]+/)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/**
 * Business-context card at the top of Task Detail — tells the user at a glance
 * which Deal / Customer / Lead this task serves, shows that record's key fields,
 * and navigates straight to it. Task FKs only cover deal/customer/lead, so those
 * are the three kinds handled here.
 */
function RelatedRecordCard({ task }: { task: Task }) {
  const router = useRouter();

  const kind: "deal" | "customer" | "lead" | null = task.dealId
    ? "deal"
    : task.customerId
      ? "customer"
      : task.leadId
        ? "lead"
        : null;

  if (!kind) {
    return (
      <div className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-400">
        This task isn’t linked to a deal, customer, or lead.
      </div>
    );
  }

  const money = (v?: number | null) =>
    v == null ? null : v.toLocaleString(undefined, { maximumFractionDigits: 0 });

  const config = {
    deal: {
      Icon: Briefcase,
      badgeClass: "bg-emerald-100 text-emerald-700",
      ringClass: "border-emerald-200 bg-emerald-50/40",
      name: task.dealName ?? "Deal",
      typeLabel: "Deal",
      rows: [
        ["Stage", humanizeEnum(task.dealStage)],
        ["Value", money(task.dealValue) ?? "—"],
        ["Customer", task.dealCustomerName ?? "—"],
        ["Owner", task.dealOwnerName ?? "—"],
      ] as const,
      openLabel: "Open deals board",
      href: "/deals",
    },
    customer: {
      Icon: Building2,
      badgeClass: "bg-blue-100 text-blue-700",
      ringClass: "border-blue-200 bg-blue-50/40",
      name: task.customerName ?? "Customer",
      typeLabel: "Customer",
      rows: [
        ["Company", task.customerCompanyName ?? "—"],
        ["Phone", task.customerPhone ?? "—"],
        ["Email", task.customerEmail ?? "—"],
      ] as const,
      openLabel: "Open customer",
      href: `/customer-profiles/${task.customerId}`,
    },
    lead: {
      Icon: User,
      badgeClass: "bg-purple-100 text-purple-700",
      ringClass: "border-purple-200 bg-purple-50/40",
      name: task.leadName ?? "Lead",
      typeLabel: "Lead",
      rows: [
        ["Company", task.leadCompanyName ?? "—"],
        ["Status", humanizeEnum(task.leadStatus)],
        ["Source", humanizeEnum(task.leadSource)],
        ["Owner", task.leadOwnerName ?? "—"],
      ] as const,
      openLabel: "Open lead",
      href: `/leads/${task.leadId}`,
    },
  }[kind];

  const { Icon } = config;

  return (
    <div className={`rounded-xl border ${config.ringClass} p-4`}>
      <div className="flex items-center justify-between gap-3">
        <span className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Related to</span>
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${config.badgeClass}`}>
          <Icon className="size-3" />
          {config.typeLabel}
        </span>
      </div>

      <div className="mt-2 flex items-start gap-3">
        <div className={`flex size-10 shrink-0 items-center justify-center rounded-lg ${config.badgeClass}`}>
          <Icon className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold text-slate-800">{config.name}</p>
          <div className="mt-1.5 grid grid-cols-2 gap-x-3 gap-y-1">
            {config.rows.map(([label, value]) => (
              <div key={label} className="flex items-baseline gap-1.5 min-w-0">
                <span className="text-[10px] uppercase tracking-wide text-slate-400 shrink-0">{label}</span>
                <span className="truncate text-xs font-medium text-slate-700">{value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={() => router.push(config.href)}
        className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white py-2 text-xs font-semibold text-slate-700 transition hover:border-[#185FA5]/40 hover:text-[#185FA5]"
      >
        {config.openLabel}
        <ArrowRight className="size-3.5" />
      </button>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────

const PRIORITY_BADGE: Record<TaskPriority, "danger" | "warning" | "default"> = {
  CRITICAL: "danger",
  HIGH: "danger",
  MEDIUM: "warning",
  LOW: "default",
};

const PRIORITY_DOT: Record<TaskPriority, string> = {
  CRITICAL: "bg-red-600",
  HIGH: "bg-red-400",
  MEDIUM: "bg-amber-400",
  LOW: "bg-slate-300",
};

const STATUS_BADGE: Record<TaskStatus, "primary" | "warning" | "success" | "default"> = {
  OPEN: "primary",
  COMPLETED: "success",
  CANCELLED: "default",
};

const STATUS_LABEL: Record<TaskStatus, string> = {
  OPEN: "Open",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

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

// ── Reassign Follow-up Modal ──────────────────────────────────────────────────

function ReassignFollowUpModal({
  task,
  onClose,
  users,
}: {
  task: Task;
  onClose: () => void;
  users: UserOption[];
}) {
  const [form, setForm] = useState({
    title: task.title,
    description: task.description ?? "",
    priority: task.priority as TaskPriority,
    assignedUserId: "",
    startDate: task.startAt ? extractLocalDate(task.startAt) : addDays(1),
    startTime: task.startAt ? extractLocalTime(task.startAt) : "09:00",
    endDate: task.endAt ? extractLocalDate(task.endAt) : (task.startAt ? extractLocalDate(task.startAt) : addDays(1)),
    endTime: task.endAt ? extractLocalTime(task.endAt) : "10:00",
    note: "",
  });
  const [validationError, setValidationError] = useState<string | null>(null);

  const resignMutation = useResignTask(task.taskId);

  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    setValidationError(null);

    if (!form.assignedUserId) {
      setValidationError("Please select a staff member to reassign to.");
      return;
    }
    if (!form.startDate || !form.startTime || !form.endDate || !form.endTime) {
      setValidationError("Start and end date/time are all required.");
      return;
    }
    const today = toDateStr(new Date());
    if (form.startDate < today) {
      setValidationError("Cannot schedule in the past.");
      return;
    }
    const newStartAt = buildISODateTime(form.startDate, form.startTime);
    const newEndAt = buildISODateTime(form.endDate, form.endTime);
    if (new Date(newStartAt) >= new Date(newEndAt)) {
      setValidationError("End time must be after start time.");
      return;
    }

    const toUser = users.find(u => u.userId === form.assignedUserId);
    const toName = toUser?.fullName ?? "Unknown";
    const fromName = task.assignedUserName ?? "Unassigned";
    const oldSchedule = task.startAt
      ? `${formatShortDateTime(task.startAt)} → ${formatShortDateTime(task.endAt)}`
      : "not set";
    const newSchedule = `${formatShortDateTime(newStartAt)} → ${formatShortDateTime(newEndAt)}`;

    const lines = [
      "[Reassigned]",
      `${fromName} → ${toName}`,
      `Old: ${oldSchedule}`,
      `New: ${newSchedule}`,
    ];
    if (form.note.trim()) lines.push(`Note: ${form.note.trim()}`);
    const resignNote = lines.join("\n");

    resignMutation.mutate(
      { title: form.title, description: form.description || undefined, priority: form.priority, assignedUserId: form.assignedUserId, resignNote, startAt: newStartAt, endAt: newEndAt } satisfies ResignTaskPayload,
      {
        onSuccess: () => { toast.success("Task reassigned successfully."); onClose(); },
        onError: (error) => { toast.error(getApiErrorMessage(error, "Failed to reassign. Please try again.")); },
      }
    );
  }

  const apiError = resignMutation.error
    ? getApiErrorMessage(resignMutation.error, "Failed to reassign. Please try again.")
    : null;

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 flex flex-col max-h-[90vh] overflow-y-auto">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 shrink-0">
            <div>
              <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
                <UserCog className="size-5 text-blue-600" />
                Reassign Follow-up
              </h3>
              <p className="text-xs text-slate-400 mt-0.5 truncate max-w-[320px]">{task.title}</p>
            </div>
            <button onClick={onClose} className="p-2 rounded-full text-slate-400 hover:bg-slate-100 transition">
              <X className="size-5" />
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-6 space-y-4">
            {/* Current assignment */}
            {task.assignedUserName && (
              <div className="p-3 rounded-xl bg-amber-50 border border-amber-100 text-xs text-amber-700">
                <span className="font-semibold">Current assignee:</span> {task.assignedUserName}
                {task.startAt && (
                  <span className="ml-2 text-amber-500">· {formatShortDateTime(task.startAt)} → {formatShortDateTime(task.endAt)}</span>
                )}
              </div>
            )}

            {/* Title */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Title *</label>
              <input
                required
                value={form.title}
                onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
                placeholder="Task title…"
              />
            </div>

            {/* Description */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Description</label>
              <textarea
                rows={2}
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                placeholder="Description…"
              />
            </div>

            {/* Priority */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Priority</label>
              <Select value={form.priority} onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))} className="py-2">
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            </div>

            {/* New assignee */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Reassign To *</label>
              <Select
                required
                value={form.assignedUserId}
                onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
                className="py-2"
              >
                <option value="">Select staff member…</option>
                {users.filter(u => u.userId !== task.assignedUserId).map(u => (
                  <option key={u.userId} value={u.userId}>{u.fullName}</option>
                ))}
              </Select>
            </div>

            {/* New schedule — start and end each with date + time */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-600 flex items-center gap-1.5">
                <Clock className="size-3.5 text-slate-400" />
                New Schedule *
              </label>
              <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide">Start at *</p>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="date"
                    required
                    value={form.startDate}
                    min={toDateStr(new Date())}
                    onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))}
                    className="py-2 text-sm"
                  />
                  <Input
                    type="time"
                    required
                    value={form.startTime}
                    onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))}
                    className="py-2 text-sm"
                  />
                </div>
              </div>
              <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide">End at *</p>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="date"
                    required
                    value={form.endDate}
                    min={form.startDate || toDateStr(new Date())}
                    onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))}
                    className="py-2 text-sm"
                  />
                  <Input
                    type="time"
                    required
                    value={form.endTime}
                    onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))}
                    className="py-2 text-sm"
                  />
                </div>
              </div>
            </div>

            {/* Note */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Reason / Note</label>
              <textarea
                rows={2}
                value={form.note}
                onChange={e => setForm(f => ({ ...f, note: e.target.value }))}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                placeholder="Reason for reassignment…"
              />
            </div>

            {(validationError || apiError) && (
              <p className="text-sm text-red-500 flex items-start gap-2">
                <AlertCircle className="size-4 shrink-0 mt-0.5" />
                {validationError ?? apiError}
              </p>
            )}

            <div className="flex gap-3 pt-2">
              <Button
                type="submit"
                variant="primary"
                disabled={resignMutation.isPending || !form.assignedUserId}
                className="flex-1 text-sm font-semibold py-2.5"
              >
                {resignMutation.isPending ? (
                  <span className="flex items-center justify-center gap-2">
                    <Loader2 className="size-4 animate-spin" />Reassigning…
                  </span>
                ) : (
                  <span className="flex items-center justify-center gap-2">
                    <UserCog className="size-4" />Reassign Follow-up
                  </span>
                )}
              </Button>
              <Button type="button" variant="ghost" onClick={onClose} className="flex-1 text-sm py-2.5">
                Cancel
              </Button>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}

// ── Task Detail / Edit Drawer ─────────────────────────────────────────────────

function TaskDetailDrawer({
  task,
  onClose,
  users,
  onReassign,
  initialEditing = false,
}: {
  task: Task;
  onClose: () => void;
  users: UserOption[];
  onReassign: () => void;
  initialEditing?: boolean;
}) {
  // The list task carries only dealId/dealName (lean list mapper); the deal
  // stage/value/customer/owner and lead status/source/owner come only from the
  // detail endpoint (fromDetail). Fetch it so the Related Record card is
  // populated instead of showing dashes.
  const { data: detailResp } = useTaskDetail(task.taskId);
  const relatedTask = detailResp?.data ?? task;

  const [editing, setEditing] = useState(initialEditing);
  const [form, setForm] = useState<UpdateTaskPayload>({
    title: task.title,
    description: task.description ?? "",
    assignedUserId: task.assignedUserId ?? "",
    priority: task.priority,
    status: task.status,
    resultNote: task.resultNote ?? "",
    leadId: task.leadId ?? undefined,
    customerId: task.customerId ?? undefined,
    dealId: task.dealId ?? undefined,
    startAt: task.startAt ?? undefined,
    endAt: task.endAt ?? undefined,
    primaryContactName: task.primaryContactName ?? "",
    primaryContactPhone: task.primaryContactPhone ?? "",
  });
  const [selectedLead, setSelectedLead] = useState<Lead | null>(
    task.leadId ? {
      leadId: task.leadId,
      fullName: task.leadName ?? "",
      email: task.leadEmail ?? null,
      phone: task.leadPhone ?? null,
      companyName: task.leadCompanyName ?? null,
      address: null, isCorporate: false, source: null, interestedService: null, status: "NEW",
      notes: null, convertedAt: null, customerId: null,
      assignedUserId: null, assignedUserName: null,
      createdById: null, createdByName: null,
      createdAt: task.createdAt, updatedAt: task.updatedAt ?? task.createdAt,
    } : null
  );
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerResult | null>(
    !task.leadId && task.customerId ? {
      customerId: task.customerId,
      fullName: task.customerName ?? "",
      email: task.customerEmail ?? null,
      phone: task.customerPhone ?? null,
      companyName: task.customerCompanyName ?? null,
    } : null
  );
  const [selectedDeal, setSelectedDeal] = useState<DealResult | null>(
    !task.leadId && !task.customerId && task.dealId ? {
      dealId: task.dealId,
      title: task.dealName ?? "Deal",
      detail: null,
    } : null
  );

  function handleSelectLead(lead: Lead | null) {
    setSelectedLead(lead);
    setSelectedCustomer(null);
    setSelectedDeal(null);
    setForm(f => ({
      ...f,
      leadId: lead?.leadId ?? undefined,
      customerId: undefined,
      dealId: undefined,
      primaryContactName: lead?.fullName ?? f.primaryContactName,
      primaryContactPhone: lead?.phone ?? f.primaryContactPhone,
    }));
  }

  function handleSelectCustomer(customer: CustomerResult | null) {
    setSelectedCustomer(customer);
    setSelectedLead(null);
    setSelectedDeal(null);
    setForm(f => ({
      ...f,
      customerId: customer?.customerId ?? undefined,
      leadId: undefined,
      dealId: undefined,
      primaryContactName: customer?.fullName ?? f.primaryContactName,
      primaryContactPhone: customer?.phone ?? f.primaryContactPhone,
    }));
  }

  function handleSelectDeal(deal: DealResult | null) {
    setSelectedDeal(deal);
    setSelectedLead(null);
    setSelectedCustomer(null);
    setForm(f => ({
      ...f,
      dealId: deal?.dealId ?? undefined,
      leadId: undefined,
      customerId: undefined,
    }));
  }

  const updateMutation = useUpdateTask(task.taskId);
  const taskOverdue = isOverdue(task);
  const actType = detectActivityType(task.title);
  const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;

  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    updateMutation.mutate({ ...form }, {
      onSuccess: () => { toast.success("Task updated successfully."); setEditing(false); onClose(); },
      onError: (error) => { toast.error(getApiErrorMessage(error, "Failed to update task.")); },
    });
  }

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-full max-w-xl bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">

        {/* ── Header ── */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 shrink-0">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[10px] font-bold shrink-0 ${ACTIVITY_CHIP[actType]}`}>
              <typeInfo.Icon className="size-3" />
              {typeInfo.label}
            </span>
            <div className="min-w-0">
              <h3 className="text-sm font-bold text-slate-800 truncate">
                {editing ? "Edit Task" : task.title}
              </h3>
              <p className="text-[10px] text-slate-400">
                {linkedEntityType(task)} · {task.assignedUserName ?? "Unassigned"}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0 ml-3">
            {!editing && task.status !== "CANCELLED" && (
              <button
                onClick={() => { onClose(); onReassign(); }}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 text-[11px] font-semibold text-slate-600 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100 transition"
                title="Reassign to another staff member"
              >
                <UserCog className="size-3.5" />
                Reassign
              </button>
            )}
            {!editing && (
              <button
                onClick={() => setEditing(true)}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 text-[11px] font-semibold text-slate-600 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100 transition"
              >
                Edit
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {editing ? (
            /* ── Edit Form ── */
            <form onSubmit={handleSubmit} className="space-y-5">

              {/* Title */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Title *</label>
                <div className="flex flex-wrap gap-1.5 mb-2">
                  {ACTIVITY_TYPES.map(({ type, label, Icon }) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => setForm(f => ({ ...f, title: `${label}: ` }))}
                      className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-[10px] font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-100"
                    >
                      <Icon className="size-3" />{label}
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

              {/* Description */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Description</label>
                <textarea
                  rows={2}
                  value={form.description ?? ""}
                  onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition resize-none"
                />
              </div>

              {/* Priority + Status */}
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Priority</label>
                  <Select value={form.priority ?? "MEDIUM"} onChange={e => setForm(f => ({ ...f, priority: e.target.value as TaskPriority }))} className="py-2">
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Status</label>
                  <Select value={form.status ?? task.status} onChange={e => setForm(f => ({ ...f, status: e.target.value as TaskStatus }))} className="py-2">
                    <option value="OPEN">Open</option>
                    <option value="COMPLETED">Completed</option>
                    <option value="CANCELLED" disabled={task.status === "COMPLETED"}>
                      {task.status === "COMPLETED" ? "Cancelled (reopen first)" : "Cancelled"}
                    </option>
                  </Select>
                </div>
              </div>

              {/* Assigned Staff */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Assigned Staff *</label>
                <Select value={form.assignedUserId ?? ""} onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))} className="py-2">
                  <option value="">Select staff member…</option>
                  {users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
                </Select>
              </div>

              {/* Schedule — Start at / End at */}
              <div className="space-y-3">
                <label className="text-xs font-semibold text-slate-600 flex items-center gap-1.5">
                  <Clock className="size-3.5 text-blue-500" />
                  Schedule
                </label>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide">Start at</p>
                  <div className="grid grid-cols-2 gap-2">
                    <Input
                      type="date"
                      value={extractLocalDate(form.startAt)}
                      onChange={e => {
                        const date = e.target.value;
                        const time = extractLocalTime(form.startAt) || "09:00";
                        setForm(f => ({ ...f, startAt: date ? buildISODateTime(date, time) : undefined }));
                      }}
                      className="py-2 text-sm"
                    />
                    <Input
                      type="time"
                      value={extractLocalTime(form.startAt)}
                      onChange={e => {
                        const time = e.target.value;
                        const date = extractLocalDate(form.startAt) || toDateStr(new Date());
                        setForm(f => ({ ...f, startAt: time ? buildISODateTime(date, time) : undefined }));
                      }}
                      className="py-2 text-sm"
                    />
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide">End at</p>
                  <div className="grid grid-cols-2 gap-2">
                    <Input
                      type="date"
                      value={extractLocalDate(form.endAt) || extractLocalDate(form.startAt)}
                      onChange={e => {
                        const date = e.target.value;
                        const time = extractLocalTime(form.endAt) || "10:00";
                        setForm(f => ({ ...f, endAt: date ? buildISODateTime(date, time) : undefined }));
                      }}
                      className="py-2 text-sm"
                    />
                    <Input
                      type="time"
                      value={extractLocalTime(form.endAt)}
                      onChange={e => {
                        const time = e.target.value;
                        const date = extractLocalDate(form.endAt) || extractLocalDate(form.startAt) || toDateStr(new Date());
                        setForm(f => ({ ...f, endAt: time ? buildISODateTime(date, time) : undefined }));
                      }}
                      className="py-2 text-sm"
                    />
                  </div>
                </div>
              </div>

              {/* Link to Entity */}
              <EntitySearchPicker
                selectedLead={selectedLead}
                selectedCustomer={selectedCustomer}
                selectedDeal={selectedDeal}
                onSelectLead={handleSelectLead}
                onSelectCustomer={handleSelectCustomer}
                onSelectDeal={handleSelectDeal}
              />

              {/* Primary Contact */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Primary Contact</label>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    value={form.primaryContactName ?? ""}
                    onChange={e => setForm(f => ({ ...f, primaryContactName: e.target.value }))}
                    placeholder="Contact name"
                    className="py-2 text-sm"
                  />
                  <Input
                    type="tel"
                    value={form.primaryContactPhone ?? ""}
                    onChange={e => setForm(f => ({ ...f, primaryContactPhone: e.target.value }))}
                    placeholder="Phone number"
                    className="py-2 text-sm"
                  />
                </div>
              </div>

              {/* Result Note */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Result / Notes</label>
                <textarea
                  rows={3}
                  value={form.resultNote ?? ""}
                  onChange={e => setForm(f => ({ ...f, resultNote: e.target.value }))}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition resize-none"
                  placeholder="Outcome or notes after completing this task…"
                />
              </div>


              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button type="submit" variant="primary" disabled={updateMutation.isPending} className="flex-1 py-2.5 text-sm font-semibold">
                  {updateMutation.isPending
                    ? <span className="flex items-center justify-center gap-2"><Loader2 className="size-4 animate-spin" />Saving…</span>
                    : "Save Changes"}
                </Button>
                <Button type="button" variant="ghost" onClick={() => setEditing(false)} className="flex-1 py-2.5 text-sm">Cancel</Button>
              </div>
            </form>
          ) : (
            /* ── View Mode ── */
            <div className="space-y-5">

              {/* Badges row */}
              <div className="flex flex-wrap gap-2">
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
                <SlaStatusBadge entityId={task.taskId} entityType="TASK" />
              </div>

              {/* Overdue warning banner */}
              {taskOverdue && (
                <div className="flex items-start gap-2.5 p-3.5 rounded-xl bg-red-50 border border-red-200">
                  <AlertCircle className="size-4 text-red-500 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold text-red-700">This task is overdue</p>
                    <p className="text-[11px] text-red-600 mt-0.5">
                      Ended {task.endAt ? formatDate(task.endAt) : "—"}. Use <strong>Reassign</strong> to reschedule and reassign.
                    </p>
                  </div>
                </div>
              )}

              {/* Schedule card */}
              <div className="rounded-xl border border-slate-200 overflow-hidden">
                <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center gap-1.5">
                  <Clock className="size-3.5 text-slate-400" />
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Schedule</p>
                </div>
                <div className="p-4">
                  {task.startAt || task.endAt ? (
                    <>
                      <div className="flex items-start gap-4">
                        {/* Start at */}
                        <div className="flex-1">
                          <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide mb-1">Start at</p>
                          {task.startAt ? (
                            <>
                              <p className="text-sm font-bold text-slate-800">{formatDate(task.startAt)}</p>
                              <p className="text-xs text-slate-500 mt-0.5">{formatTime(task.startAt)}</p>
                            </>
                          ) : (
                            <p className="text-sm text-slate-400">—</p>
                          )}
                        </div>
                        <div className="pt-5 text-slate-300 font-bold text-base select-none">→</div>
                        {/* End at */}
                        <div className="flex-1">
                          <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide mb-1">End at</p>
                          {task.endAt ? (
                            <>
                              <p className={`text-sm font-bold ${taskOverdue ? "text-red-700" : "text-slate-800"}`}>
                                {formatDate(task.endAt)}
                              </p>
                              <p className={`text-xs mt-0.5 ${taskOverdue ? "text-red-500" : "text-slate-500"}`}>
                                {formatTime(task.endAt)}
                              </p>
                            </>
                          ) : (
                            <p className="text-sm text-slate-400">—</p>
                          )}
                        </div>
                      </div>

                      {/* Duration + active indicator */}
                      {task.startAt && task.endAt && (() => {
                        const start = new Date(task.startAt);
                        const end = new Date(task.endAt);
                        const now = new Date();
                        const diffMs = end.getTime() - start.getTime();
                        const hours = Math.floor(diffMs / 3600000);
                        const mins = Math.floor((diffMs % 3600000) / 60000);
                        const dur = hours > 0 ? `${hours}h${mins > 0 ? ` ${mins}m` : ""}` : `${mins}m`;
                        const isActive = start <= now && now <= end && !taskOverdue;
                        return (
                          <div className="flex items-center gap-2 mt-3 pt-3 border-t border-slate-100">
                            <span className="text-[11px] text-slate-400">Duration: {dur}</span>
                            {isActive && (
                              <span className="inline-flex items-center gap-1 text-[10px] font-bold text-green-700 bg-green-50 border border-green-200 rounded-full px-2 py-0.5">
                                <span className="size-1.5 rounded-full bg-green-500 animate-pulse inline-block" />
                                ACTIVE NOW
                              </span>
                            )}
                          </div>
                        );
                      })()}
                    </>
                  ) : (
                    <p className="text-sm text-slate-400 italic">No schedule set</p>
                  )}
                </div>
              </div>

              {/* Business context — which record this task serves + navigation */}
              <RelatedRecordCard task={relatedTask} />

              {/* Staff grid */}
              <div className="grid grid-cols-2 gap-3 p-4 bg-slate-50 rounded-xl border border-slate-100">
                <InfoRow icon={<User className="size-4 text-slate-400" />} label="Assigned To" value={task.assignedUserName ?? "—"} />
                <InfoRow icon={<User className="size-4 text-slate-400" />} label="Created By" value={task.createdByName ?? "—"} />
              </div>

              {/* Lead / Customer contact card */}
              {(task.leadId || task.customerId) && (() => {
                const isLead = !!task.leadId;
                const name    = isLead ? task.leadName        : task.customerName;
                const phone   = isLead ? task.leadPhone       : task.customerPhone;
                const email   = isLead ? task.leadEmail       : task.customerEmail;
                const company = isLead ? task.leadCompanyName : task.customerCompanyName;
                const hasAny  = name || phone || email || company;
                if (!hasAny) return null;
                return (
                  <div className="rounded-xl border border-slate-200 overflow-hidden">
                    <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center gap-1.5">
                      <Phone className="size-3.5 text-slate-400" />
                      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Contact Information</p>
                      <span className={`ml-auto text-[10px] px-2 py-0.5 rounded-full font-bold ${isLead ? "bg-blue-100 text-blue-700" : "bg-green-100 text-green-700"}`}>
                        {isLead ? "Lead" : "Customer"}
                      </span>
                    </div>
                    <div className="px-4 py-3 space-y-2">
                      {name && (
                        <div className="flex items-center gap-2">
                          <User className="size-3.5 text-slate-400 shrink-0" />
                          <span className="text-sm font-semibold text-slate-800">{name}</span>
                        </div>
                      )}
                      {phone && (
                        <div className="flex items-center gap-2">
                          <Phone className="size-3.5 text-slate-400 shrink-0" />
                          <a href={`tel:${phone}`} className="text-sm text-blue-600 hover:underline">{phone}</a>
                        </div>
                      )}
                      {email && (
                        <div className="flex items-center gap-2">
                          <Mail className="size-3.5 text-slate-400 shrink-0" />
                          <a href={`mailto:${email}`} className="text-sm text-blue-600 hover:underline truncate">{email}</a>
                        </div>
                      )}
                      {company && (
                        <div className="flex items-center gap-2">
                          <Building2 className="size-3.5 text-slate-400 shrink-0" />
                          <span className="text-sm text-slate-600">{company}</span>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })()}

              {/* Primary Contact (override / manual entry) */}
              {(task.primaryContactName || task.primaryContactPhone) && (
                <div className="p-3.5 bg-amber-50 rounded-xl border border-amber-100">
                  <p className="text-[10px] font-semibold text-amber-700 uppercase tracking-wider mb-2">Primary Contact</p>
                  <div className="flex flex-wrap items-center gap-4">
                    {task.primaryContactName && (
                      <div className="flex items-center gap-1.5">
                        <User className="size-3.5 text-amber-600" />
                        <span className="text-xs font-semibold text-amber-900">{task.primaryContactName}</span>
                      </div>
                    )}
                    {task.primaryContactPhone && (
                      <div className="flex items-center gap-1.5">
                        <Phone className="size-3.5 text-amber-600" />
                        <span className="text-xs font-medium text-amber-800">{task.primaryContactPhone}</span>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Description */}
              {task.description && (
                <div className="space-y-1.5">
                  <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Description</p>
                  <p className="text-sm text-slate-700 leading-relaxed">{task.description}</p>
                </div>
              )}

              {/* Result Notes */}
              {task.resultNote && (
                <div className="p-4 bg-emerald-50 rounded-xl border border-emerald-100 space-y-1.5">
                  <p className="text-[10px] font-semibold text-emerald-700 uppercase tracking-wider">Result / Notes</p>
                  <p className="text-sm text-emerald-800 leading-relaxed whitespace-pre-line">{task.resultNote}</p>
                </div>
              )}

              {/* Timestamps */}
              <div className="pt-3 border-t border-slate-100 text-xs text-slate-400 space-y-1">
                <p>Created: <span className="text-slate-600 font-medium">{formatDate(task.createdAt)}</span></p>
                {task.updatedAt && (
                  <p>Updated: <span className="text-slate-600 font-medium">{formatDate(task.updatedAt)}</span></p>
                )}
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

type DealResult = {
  dealId: string;
  title: string;
  detail?: string | null;
};

function EntitySearchPicker({
  selectedLead,
  selectedCustomer,
  selectedDeal = null,
  onSelectLead,
  onSelectCustomer,
  onSelectDeal,
}: {
  selectedLead: Lead | null;
  selectedCustomer: CustomerResult | null;
  selectedDeal?: DealResult | null;
  onSelectLead: (lead: Lead | null) => void;
  onSelectCustomer: (customer: CustomerResult | null) => void;
  onSelectDeal?: (deal: DealResult | null) => void;
}) {
  const [tab, setTab] = useState<"lead" | "customer" | "deal">("lead");
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

  const dealSearch = useQuery({
    queryKey: ["entity-search-deal", debouncedQuery],
    queryFn: () => dealService.getList({ search: debouncedQuery, size: 8 }),
    enabled: tab === "deal" && !!onSelectDeal && debouncedQuery.length >= 1,
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
  const dealResults: DealResult[] = (dealSearch.data?.data ?? []).map((d) => {
    const rec = d as Record<string, unknown>;
    const contact = typeof rec.contactName === "string" ? rec.contactName : null;
    const stage = typeof rec.stage === "string" ? rec.stage : null;
    return {
      dealId: String(rec.id),
      title: typeof rec.title === "string" && rec.title ? rec.title : "Untitled deal",
      detail: [contact, stage].filter(Boolean).join(" · ") || null,
    };
  });

  const getEntityDetail = (item: { email?: string | null; phone?: string | null; companyName?: string | null }) => {
    return [item.email, item.phone, item.companyName].filter(Boolean).join(" · ");
  };

  const hasSelection =
    tab === "lead" ? !!selectedLead : tab === "customer" ? !!selectedCustomer : !!selectedDeal;

  return (
    <div className="border border-slate-200 rounded-xl">
      {/* Header row with tab toggle */}
      <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 rounded-t-xl">
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
            {onSelectDeal && (
              <button
                type="button"
                onClick={() => { setTab("deal"); setQuery(""); setOpen(false); }}
                className={`px-3 py-1 transition ${tab === "deal" ? "bg-[#185FA5] text-white" : "bg-white text-slate-500 hover:bg-slate-100"}`}
              >
                Deal
              </button>
            )}
          </div>
        </div>
        <p className="mt-2 text-[10px] text-slate-500">Search by name, email, phone, or company to link the correct lead, customer, or deal for this activity.</p>
      </div>

      <div className="px-4 py-3 space-y-2">
        {/* Selected chip */}
        {hasSelection && (
          <div className="flex items-center gap-2 px-3 py-2 bg-[#E6F1FB] rounded-lg border border-[#85B7EB]">
            {tab === "deal"
              ? <Briefcase className="size-3.5 text-[#0C447C] shrink-0" />
              : <User className="size-3.5 text-[#0C447C] shrink-0" />}
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-[#0C447C] truncate">
                {tab === "lead"
                  ? selectedLead!.fullName
                  : tab === "customer"
                    ? selectedCustomer!.fullName
                    : selectedDeal!.title}
              </p>
              <p className="text-[10px] text-[#185FA5] truncate">
                {tab === "lead"
                  ? (selectedLead!.email ?? selectedLead!.companyName ?? selectedLead!.status)
                  : tab === "customer"
                    ? (selectedCustomer!.email ?? selectedCustomer!.companyName ?? "")
                    : (selectedDeal!.detail ?? "Deal")}
              </p>
            </div>
            <button
              type="button"
              onClick={() =>
                tab === "lead"
                  ? onSelectLead(null)
                  : tab === "customer"
                    ? onSelectCustomer(null)
                    : onSelectDeal?.(null)
              }
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
              placeholder={tab === "lead" ? "Search lead by name, email, company…" : tab === "customer" ? "Search customer by name, email, company…" : "Search deal by title or contact…"}
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

                {tab === "deal" && (
                  dealSearch.isFetching
                    ? <p className="py-4 text-center text-xs text-slate-400">Searching deals…</p>
                    : dealResults.length === 0
                      ? <p className="py-4 text-center text-xs text-slate-400">No deals found for "{debouncedQuery}"</p>
                      : dealResults.map(d => (
                        <button
                          key={d.dealId}
                          type="button"
                          onMouseDown={() => { onSelectDeal?.(d); setQuery(""); setOpen(false); }}
                          className="w-full flex items-center gap-2.5 px-3 py-2.5 text-left hover:bg-[#E6F1FB] transition border-b border-slate-50 last:border-0"
                        >
                          <div className="size-7 rounded-full bg-[#EAE6FB] flex items-center justify-center shrink-0 text-[10px] font-bold text-[#5B3BC4]">
                            D
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs font-semibold text-slate-800 truncate">{d.title}</p>
                            <p className="text-[10px] text-slate-400 truncate">{d.detail ?? "Deal"}</p>
                            <p className="text-[9px] text-slate-500 uppercase tracking-[0.16em] mt-1">Deal</p>
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
    startAt: initialDueDate ? buildISODateTime(initialDueDate, "09:00") : undefined,
    endAt: initialDueDate ? buildISODateTime(initialDueDate, "10:00") : undefined,
    primaryContactName: "",
    primaryContactPhone: "",
  });
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerResult | null>(null);
  const [selectedDeal, setSelectedDeal] = useState<DealResult | null>(null);
  const createMutation = useCreateTask();

  function handleSelectLead(lead: Lead | null) {
    setSelectedLead(lead);
    setForm(f => {
      const titleIsAuto = !f.title.trim() || ACTIVITY_TYPES.some(a => f.title === `${a.label}: `);
      return {
        ...f,
        leadId: lead?.leadId ?? undefined,
        customerId: undefined,
        dealId: undefined,
        primaryContactName: lead?.fullName ?? "",
        primaryContactPhone: lead?.phone ?? "",
        title: lead && titleIsAuto ? `${ACTIVITY_TYPES.find(a => a.type === activityType)?.label ?? "Call"}: ${lead.fullName}` : f.title,
      };
    });
    if (lead) { setSelectedCustomer(null); setSelectedDeal(null); }
  }

  function handleSelectCustomer(customer: CustomerResult | null) {
    setSelectedCustomer(customer);
    setForm(f => {
      const titleIsAuto = !f.title.trim() || ACTIVITY_TYPES.some(a => f.title === `${a.label}: `);
      return {
        ...f,
        customerId: customer?.customerId ?? undefined,
        leadId: undefined,
        dealId: undefined,
        primaryContactName: customer?.fullName ?? "",
        primaryContactPhone: customer?.phone ?? "",
        title: customer && titleIsAuto ? `${ACTIVITY_TYPES.find(a => a.type === activityType)?.label ?? "Call"}: ${customer.fullName}` : f.title,
      };
    });
    if (customer) { setSelectedLead(null); setSelectedDeal(null); }
  }

  function handleSelectDeal(deal: DealResult | null) {
    setSelectedDeal(deal);
    setForm(f => {
      const titleIsAuto = !f.title.trim() || ACTIVITY_TYPES.some(a => f.title === `${a.label}: `);
      return {
        ...f,
        dealId: deal?.dealId ?? undefined,
        leadId: undefined,
        customerId: undefined,
        title: deal && titleIsAuto ? `${ACTIVITY_TYPES.find(a => a.type === activityType)?.label ?? "Call"}: ${deal.title}` : f.title,
      };
    });
    if (deal) { setSelectedLead(null); setSelectedCustomer(null); }
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
    const date = addDays(days);
    const startTime = extractLocalTime(form.startAt) || "09:00";
    const endTime = extractLocalTime(form.endAt) || "10:00";
    setForm(f => ({
      ...f,
      startAt: buildISODateTime(date, startTime),
      endAt: buildISODateTime(date, endTime),
    }));
  }

  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    if (!form.title.trim() || !form.assignedUserId || !form.startAt) return;
    createMutation.mutate({
      ...form,
      primaryContactName: form.primaryContactName?.trim() || undefined,
      primaryContactPhone: form.primaryContactPhone?.trim() || undefined,
    }, {
      onSuccess: () => { toast.success("Task created successfully."); onClose(); },
      onError: (error) => { toast.error(getApiErrorMessage(error, "Failed to create task.")); },
    });
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

          {/* Activity Type — compact quick-select chips (one tap, no dialog) */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-600 uppercase tracking-wide">Activity Type</label>
            <div className="flex flex-wrap gap-2">
              {ACTIVITY_TYPES.map(({ type, label, Icon, activeClass, idleClass }) => (
                <button
                  key={type}
                  type="button"
                  aria-pressed={activityType === type}
                  onClick={() => handleActivityTypeChange(type)}
                  className={`inline-flex items-center gap-1.5 h-9 px-3.5 rounded-full border text-xs font-semibold transition-all duration-150 active:scale-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-[#185FA5]/40 ${activityType === type ? `${activeClass} shadow-sm` : idleClass}`}
                >
                  <Icon className="size-3.5 shrink-0" />
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

          {/* Timeline Schedule — required */}
          <div className="space-y-2.5">
            <label className="text-xs font-semibold text-slate-600 flex items-center gap-1.5">
              <Clock className="size-3.5 text-blue-500" />
              Schedule *
            </label>
            {/* Quick date presets */}
            <div className="flex gap-2 flex-wrap">
              {([
                { label: "Today", days: 0 },
                { label: "+1 Day", days: 1 },
                { label: "+3 Days", days: 3 },
                { label: "+1 Week", days: 7 },
              ] as const).map(preset => (
                <button
                  key={preset.label}
                  type="button"
                  onClick={() => applyDatePreset(preset.days)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${extractLocalDate(form.startAt) === addDays(preset.days)
                      ? "bg-[#185FA5] text-white border-[#185FA5] shadow-sm"
                      : "bg-white text-slate-600 border-slate-200 hover:border-[#185FA5]/40 hover:text-[#185FA5]"
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>
            {/* Start row */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">Start Date *</label>
                <Input
                  required
                  type="date"
                  value={extractLocalDate(form.startAt)}
                  onChange={e => {
                    const date = e.target.value;
                    const time = extractLocalTime(form.startAt) || "09:00";
                    setForm(f => ({
                      ...f,
                      startAt: date ? buildISODateTime(date, time) : undefined,
                    }));
                  }}
                  className="py-2 text-sm"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">Start Time</label>
                <Input
                  type="time"
                  value={extractLocalTime(form.startAt)}
                  onChange={e => {
                    const time = e.target.value;
                    const date = extractLocalDate(form.startAt) || addDays(0);
                    setForm(f => ({ ...f, startAt: time ? buildISODateTime(date, time) : f.startAt }));
                  }}
                  className="py-2 text-sm"
                />
              </div>
            </div>
            {/* End row */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">End Date</label>
                <Input
                  type="date"
                  value={extractLocalDate(form.endAt) || extractLocalDate(form.startAt)}
                  onChange={e => {
                    const date = e.target.value;
                    const time = extractLocalTime(form.endAt) || "10:00";
                    setForm(f => ({ ...f, endAt: date ? buildISODateTime(date, time) : undefined }));
                  }}
                  className="py-2 text-sm"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] text-slate-500 font-medium">End Time</label>
                <Input
                  type="time"
                  value={extractLocalTime(form.endAt)}
                  onChange={e => {
                    const time = e.target.value;
                    const date = extractLocalDate(form.endAt) || extractLocalDate(form.startAt) || addDays(0);
                    setForm(f => ({ ...f, endAt: time ? buildISODateTime(date, time) : undefined }));
                  }}
                  className="py-2 text-sm"
                />
              </div>
            </div>
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
            <div className="grid grid-cols-2 gap-3">
              <Input
                value={form.primaryContactName ?? ""}
                onChange={e => setForm(f => ({ ...f, primaryContactName: e.target.value }))}
                placeholder="Contact name"
                className="py-2 text-sm"
              />
              <Input
                type="tel"
                value={form.primaryContactPhone ?? ""}
                onChange={e => setForm(f => ({ ...f, primaryContactPhone: e.target.value }))}
                placeholder="Phone number"
                className="py-2 text-sm"
              />
            </div>
          </div>

          {/* Entity Link — searchable picker */}
          <EntitySearchPicker
            selectedLead={selectedLead}
            selectedCustomer={selectedCustomer}
            selectedDeal={selectedDeal}
            onSelectLead={handleSelectLead}
            onSelectCustomer={handleSelectCustomer}
            onSelectDeal={handleSelectDeal}
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
                      setForm(f => ({ ...f, title: `${action.label}: ${targetName}`, primaryContactName: targetName }));
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
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// Detect activity type from title prefix ("Call: …")
function detectActivityType(title: string): ActivityType {
  const found = ACTIVITY_TYPES.find(a =>
    title.toLowerCase().startsWith(a.label.toLowerCase() + ":")
  );
  return found?.type ?? "TASK";
}

/** Returns true when the task's scheduled window covers the given date string. */
function taskCoversDay(task: Task, ds: string): boolean {
  const start = task.startAt ? extractLocalDate(task.startAt) : null;
  const end   = task.endAt   ? extractLocalDate(task.endAt)   : start;
  if (!start) return false;
  return start <= ds && (end ?? start) >= ds;
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
  const { highlightedId, setRowRef } = useHighlightRow();
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
  const [openTaskInEdit, setOpenTaskInEdit] = useState(false);
  const [reassignTask, setReassignTask] = useState<Task | null>(null);
  const [weekStart, setWeekStart] = useState(() => getWeekStart(new Date()));

  const queryClient = useQueryClient();
  const { data: usersData } = useUsers();
  const users = useMemo(() => usersData?.data ?? [], [usersData]);

  const toggleMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: TaskStatus }) =>
      taskService.update(id, { status }),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks"] });
      const snapshot = queryClient.getQueriesData({ queryKey: ["tasks"] });
      queryClient.setQueriesData({ queryKey: ["tasks"] }, (old: unknown) => {
        if (!old || typeof old !== "object") return old;
        const root = old as { data?: { content?: { taskId: string; status: TaskStatus }[] } };
        if (!root.data?.content) return old;
        return { ...root, data: { ...root.data, content: root.data.content.map(t => t.taskId === id ? { ...t, status } : t) } };
      });
      return { snapshot };
    },
    onError: (_e, _v, ctx) => ctx?.snapshot?.forEach(([k, d]) => queryClient.setQueryData(k, d)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["tasks"] }),
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
  const totalPages = (data?.data?.page && typeof data.data.page === "object") ? data.data.page.totalPages : (data?.data?.totalPages ?? 1);
  const totalElements = (data?.data?.page && typeof data.data.page === "object") ? data.data.page.totalElements : (data?.data?.totalElements ?? 0);
  const calTasks: Task[] = calData?.data?.content ?? [];

  const filteredTasks = useMemo(() => {
    let filtered = allTasks;
    const today = toDateStr(new Date());

    if (activeTab === "today") {
      filtered = filtered.filter(t => taskDateKey(t) === today && t.status !== "COMPLETED" && t.status !== "CANCELLED");
    } else if (activeTab === "upcoming") {
      filtered = filtered.filter(t => {
        const d = taskDateKey(t) ?? "";
        return d > today && t.status !== "COMPLETED" && t.status !== "CANCELLED" && !isOverdue(t);
      });
    }

    if (activityTypeFilter) {
      filtered = filtered.filter(t => detectActivityType(t.title) === activityTypeFilter);
    }

    return filtered;
  }, [allTasks, activeTab, activityTypeFilter]);

  const weekDays = useMemo(() => getWeekDays(weekStart), [weekStart]);
  const todayStr = toDateStr(new Date());
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
    const actType = detectActivityType(task.title);
    const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;
    // Customer / Lead / Deal name — prominently shown
    const contactName = task.customerName ?? task.leadName ?? task.primaryContactName ?? null;
    const entityName = linkedEntityLabel(task);
    const entityType = linkedEntityType(task);

    return (
      <tr
        ref={setRowRef(task.taskId)}
        className={`group border-b border-slate-200 dark:border-slate-700 transition-colors cursor-pointer ${done ? "opacity-60" : task.status === "CANCELLED" ? "opacity-40" : ""} ${overdue ? "hover:bg-[#4F1B1C]/40 dark:hover:bg-[#4F1B1C]/40" : "hover:bg-slate-100/60 dark:hover:bg-slate-800/80"} ${highlightedId === task.taskId ? "bg-amber-50! dark:bg-amber-500/10! ring-2 ring-inset ring-amber-400" : ""}`}
        onClick={() => setSelectedTask(task)}
      >
        {/* Done toggle */}
        <td className="w-10 px-3 py-3" onClick={e => e.stopPropagation()}>
          <button
            onClick={() => {
              if (!done) {
                toggleMutation.mutate({ id: task.taskId, status: "COMPLETED" });
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
        <td className="px-3 py-3 w-[140px] max-w-[140px]">
          {entityName !== "—" ? (
            <div className="flex items-start gap-1.5 w-full overflow-hidden">
              <Briefcase className="size-3 text-slate-400 shrink-0 mt-0.5" />
              <div className="min-w-0 flex-1 overflow-hidden">
                <p className="text-[9px] uppercase tracking-[0.12em] text-slate-400 font-semibold mb-0.5 dark:text-slate-500">
                  {entityType !== "General" ? entityType : "Entity"}
                </p>
                <p className="text-[11px] font-semibold text-slate-800 truncate dark:text-slate-200 leading-tight">
                  {entityName}
                </p>
              </div>
            </div>
          ) : (
            <span className="text-slate-300 text-xs">—</span>
          )}
        </td>

        {/* Schedule — start at / end at */}
        <td className="w-[110px] px-3 py-3 whitespace-nowrap">
          <p className={`text-xs font-semibold ${overdue ? "text-[#A32D2D]" : "text-slate-600 dark:text-slate-300"}`}>
            {formatDate(task.startAt)}
          </p>
          {task.startAt && (
            <p className="text-[9px] text-slate-400 font-medium mt-0.5 flex items-center gap-0.5">
              <Clock className="size-2.5" />
              {formatTime(task.startAt)}
              {task.endAt && <> – {formatTime(task.endAt)}</>}
            </p>
          )}
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

        {/* SLA */}
        <td className="px-3 py-3 whitespace-nowrap">
          <SlaStatusBadge entityId={task.taskId} entityType="TASK" />
        </td>

        {/* Row actions — appear on hover */}
        <td className="px-3 py-3 w-[120px]" onClick={e => e.stopPropagation()}>
          <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition">
            <Button variant="secondary" size="sm" onClick={() => { setSelectedTask(task); setOpenTaskInEdit(true); }}>Edit</Button>
            {task.status !== "CANCELLED" && task.status !== "COMPLETED" && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setReassignTask(task)}
                leftIcon={<UserCog className="size-3" />}
              >
                Reassign
              </Button>
            )}
          </div>
        </td>
      </tr>
    );
  }

  // ── Week Timeline View (Google Calendar / Pipedrive style) ─────────────────
  function WeekCalendarView() {
    const nowMs = Date.now();
    const DAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
    const weekStartStr = toDateStr(weekDays[0]);
    const weekEndStr   = toDateStr(weekDays[6]);

    // Separate multi-day tasks (span ≥2 days) from single-day tasks
    const filteredCalTasks = calTasks.filter(
      t => !activityTypeFilter || detectActivityType(t.title) === activityTypeFilter
    );

    const multiDayTasks = filteredCalTasks.filter(t => {
      if (!t.startAt || !t.endAt) return false;
      return extractLocalDate(t.startAt) !== extractLocalDate(t.endAt);
    });

    const singleDayTasks = filteredCalTasks.filter(t => !multiDayTasks.includes(t));

    // Compute grid column spans for multi-day tasks (clipped to current week)
    type MultiDayEvent = { task: Task; colStart: number; colEnd: number; clippedLeft: boolean; clippedRight: boolean };
    const multiDayEvents: MultiDayEvent[] = multiDayTasks.flatMap(task => {
      const spanStart = extractLocalDate(task.startAt);
      const spanEnd   = extractLocalDate(task.endAt);
      if (spanEnd < weekStartStr || spanStart > weekEndStr) return [];
      const effectiveStart = spanStart < weekStartStr ? weekStartStr : spanStart;
      const effectiveEnd   = spanEnd   > weekEndStr   ? weekEndStr   : spanEnd;
      const colStart = weekDays.findIndex(d => toDateStr(d) === effectiveStart);
      const colEnd   = weekDays.findIndex(d => toDateStr(d) === effectiveEnd);
      if (colStart === -1 || colEnd === -1) return [];
      return [{ task, colStart, colEnd, clippedLeft: spanStart < weekStartStr, clippedRight: spanEnd > weekEndStr }];
    });

    // Simple track assignment: greedy row packing for multi-day events
    const tracks: MultiDayEvent[][] = [];
    multiDayEvents.forEach(evt => {
      const trackIdx = tracks.findIndex(track =>
        !track.some(e => e.colStart <= evt.colEnd && e.colEnd >= evt.colStart)
      );
      if (trackIdx === -1) tracks.push([evt]);
      else tracks[trackIdx].push(evt);
    });

    return (
      <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
        {/* Day header row */}
        <div className="grid grid-cols-7 border-b border-slate-200">
          {weekDays.map((day) => {
            const ds = toDateStr(day);
            const isToday = ds === todayStr;
            const dayIdx = (day.getDay() + 6) % 7;
            const totalCount = filteredCalTasks.filter(t => taskCoversDay(t, ds)).length;
            return (
              <div key={ds} className={`px-2 py-3 text-center border-r border-slate-100 last:border-r-0 ${isToday ? "bg-[#185FA5]" : "bg-slate-50/80"}`}>
                <p className={`text-[9px] font-bold uppercase tracking-widest ${isToday ? "text-blue-100" : "text-slate-400"}`}>
                  {DAY_LABELS[dayIdx]}
                </p>
                <p className={`text-base font-bold leading-tight mt-0.5 ${isToday ? "text-white" : "text-slate-700"}`}>
                  {day.getDate()}
                </p>
                {totalCount > 0 && (
                  <span className={`inline-block mt-1 text-[8px] font-bold px-1.5 py-0.5 rounded-full ${isToday ? "bg-white/20 text-white" : "bg-slate-200 text-slate-500"}`}>
                    {totalCount}
                  </span>
                )}
              </div>
            );
          })}
        </div>

        {/* Multi-day spanning event strip */}
        {tracks.length > 0 && (
          <div
            className="grid grid-cols-7 border-b border-slate-100 bg-white px-0.5 gap-y-0.5 py-1"
            style={{ minHeight: `${tracks.length * 30 + 8}px` }}
          >
            {tracks.map((track, rowIdx) =>
              track.map(({ task, colStart, colEnd, clippedLeft, clippedRight }) => {
                const done = task.status === "COMPLETED";
                const overdue = isOverdue(task);
                const isActive = !done && task.startAt && task.endAt
                  && new Date(task.startAt).getTime() <= nowMs
                  && new Date(task.endAt).getTime() >= nowMs;
                const actType = detectActivityType(task.title);
                const chipCls = done
                  ? "bg-slate-100 border-slate-200 text-slate-400"
                  : overdue
                    ? "bg-[#FCEBEB] border-[#F7C1C1] text-[#791F1F]"
                    : ACTIVITY_CHIP[actType];
                const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;
                return (
                  <button
                    key={task.taskId}
                    style={{
                      gridColumn: `${colStart + 1} / ${colEnd + 2}`,
                      gridRow: rowIdx + 1,
                      marginTop: rowIdx === 0 ? 0 : undefined,
                    }}
                    onClick={() => setSelectedTask(task)}
                    className={`flex items-center gap-1.5 px-2.5 py-1.5 text-[10px] font-semibold border transition hover:brightness-95 active:scale-[0.99] ${done ? "line-through opacity-60" : ""} ${clippedLeft ? "rounded-l-none border-l-2 border-l-dashed" : "rounded-l-md"} ${clippedRight ? "rounded-r-none border-r-2 border-r-dashed" : "rounded-r-md"} ${chipCls}`}
                  >
                    {isActive && (
                      <span className="size-1.5 rounded-full bg-green-500 animate-pulse shrink-0" />
                    )}
                    <typeInfo.Icon className="size-3 shrink-0" />
                    {!done && (
                      <span className={`size-1.5 rounded-full shrink-0 ${PRIORITY_DOT[task.priority]}`} title={task.priority} />
                    )}
                    <span className="truncate leading-tight">
                      {task.title.replace(/^[^:]+:\s*/, "") || task.title}
                    </span>
                    {task.startAt && (
                      <span className="ml-auto shrink-0 text-[9px] opacity-60 font-medium">
                        {formatTime(task.startAt)}
                        {task.endAt && <> – {formatTime(task.endAt)}</>}
                      </span>
                    )}
                  </button>
                );
              })
            )}
          </div>
        )}

        {/* Single-day event columns */}
        <div className="grid grid-cols-7">
          {weekDays.map(day => {
            const ds = toDateStr(day);
            const isToday = ds === todayStr;
            const dayTasks = singleDayTasks
              .filter(t => taskCoversDay(t, ds))
              .sort((a, b) => {
                if (a.startAt && b.startAt) return a.startAt.localeCompare(b.startAt);
                if (a.startAt) return -1;
                if (b.startAt) return 1;
                return (isOverdue(b) ? 1 : 0) - (isOverdue(a) ? 1 : 0);
              });

            return (
              <div key={ds} className={`flex flex-col border-r border-slate-100 last:border-r-0 ${isToday ? "bg-[#E6F1FB]/20" : ""}`}>
                <div className="flex-1 px-1.5 py-2 space-y-1.5 overflow-y-auto min-h-[160px] max-h-[360px]">
                  {dayTasks.map(task => {
                    const actType = detectActivityType(task.title);
                    const chipCls = ACTIVITY_CHIP[actType];
                    const typeInfo = ACTIVITY_TYPES.find(a => a.type === actType)!;
                    const done = task.status === "COMPLETED";
                    const overdue = isOverdue(task);
                    const contact = task.customerName ?? task.leadName ?? task.primaryContactName;
                    const isActive = !done && task.startAt && task.endAt
                      && new Date(task.startAt).getTime() <= nowMs
                      && new Date(task.endAt).getTime() >= nowMs;

                    return (
                      <button
                        key={task.taskId}
                        onClick={() => setSelectedTask(task)}
                        className={`w-full text-left px-2 py-2 rounded-lg border text-[10px] font-semibold transition hover:shadow-sm active:scale-[0.98] ${done ? "opacity-50 line-through bg-slate-50 border-slate-100 text-slate-400" : overdue ? "bg-[#FCEBEB] border-[#F7C1C1] text-[#791F1F]" : chipCls}`}
                      >
                        <span className="flex items-center gap-1.5 min-w-0">
                          {isActive && <span className="size-1.5 rounded-full bg-green-500 animate-pulse shrink-0" />}
                          <typeInfo.Icon className="size-3 shrink-0" />
                          {!done && (
                            <span className={`size-1.5 rounded-full shrink-0 ${PRIORITY_DOT[task.priority]}`} title={task.priority} />
                          )}
                          <span className="truncate leading-tight">
                            {task.title.replace(/^[^:]+:\s*/, "") || task.title}
                          </span>
                        </span>
                        {task.startAt && (
                          <span className="flex items-center gap-0.5 mt-0.5 text-[9px] opacity-70 font-medium">
                            <Clock className="size-2.5 shrink-0" />
                            {formatTime(task.startAt)}
                            {task.endAt && <> – {formatTime(task.endAt)}</>}
                          </span>
                        )}
                        {contact && (
                          <span className="flex items-center gap-1 mt-0.5 text-[9px] opacity-70 font-medium truncate">
                            <User className="size-2.5 shrink-0" />{contact}
                          </span>
                        )}
                        {task.assignedUserName && (
                          <span className="block mt-0.5 text-[9px] opacity-60 truncate">{task.assignedUserName}</span>
                        )}
                      </button>
                    );
                  })}

                  <button
                    onClick={() => { setCreateDueDate(ds); setIsCreateOpen(true); }}
                    className="w-full text-center py-1.5 text-[9px] text-slate-300 hover:text-[#185FA5] hover:bg-[#E6F1FB] rounded-lg transition"
                  >
                    + Add
                  </button>
                </div>
              </div>
            );
          })}
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
                    <th className="w-[90px] px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Schedule</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Assigned To</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">Priority / Status</th>
                    <th className="px-3 py-2.5 text-[9px] font-bold text-slate-500 uppercase tracking-wide dark:text-slate-400">SLA</th>
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
              <WeekCalendarView />
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
          onClose={() => { setSelectedTask(null); setOpenTaskInEdit(false); }}
          users={users}
          onReassign={() => { setReassignTask(selectedTask); setSelectedTask(null); setOpenTaskInEdit(false); }}
          initialEditing={openTaskInEdit}
        />
      )}
      {reassignTask && <ReassignFollowUpModal task={reassignTask} onClose={() => setReassignTask(null)} users={users} />}
    </div>
  );
}
