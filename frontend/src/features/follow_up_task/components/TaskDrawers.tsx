"use client";
/**
 * Task create / detail / reassign drawers — Website UI Blueprint §10.14.3, §10.14.5.
 *
 * Extracted verbatim from the former `FollowUpTaskListScreen` so the task module
 * has exactly ONE screen. The list shell that wrapped these is replaced by
 * `TaskWorkspaceScreen` (eight views, bulk actions); the forms already carried
 * real validation, so they moved rather than being rewritten. No field, rule or
 * endpoint changed in the move.
 */
import React, { useState } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  CalendarCheck,
  Search,
  Clock,
  AlertCircle,
  X,
  Briefcase,
  User,
  RefreshCw,
  Loader2,
  UserCog,
  Phone,
  Mail,
  Users2,
  MapPin,
  CheckSquare2,
  Building2,
  ArrowRight,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import {
  useTaskDetail,
  useCreateTask,
  useUpdateTask,
  useResignTask,
} from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import {
  type Task,
  type TaskPriority,
  type TaskStatus,
  type ActivityType,
  type CreateTaskPayload,
  type UpdateTaskPayload,
  type ResignTaskPayload,
} from "@/services/follow_up_task_service";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useRouter } from "next/navigation";
import { leadService, type Lead } from "@/services/lead_service";
import { customerProfileService } from "@/services/customer_profile_service";
import { dealService } from "@/services/deal_service";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
// ── Activity Types (Pipedrive-style) ─────────────────────────────────────────
const ACTIVITY_TYPES = [
  { type: "CALL", label: "Call", Icon: Phone, activeClass: "border-green-400 bg-green-50 text-green-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-green-300 hover:text-green-600" },
  { type: "EMAIL", label: "Email", Icon: Mail, activeClass: "border-blue-400 bg-blue-50 text-blue-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-blue-300 hover:text-blue-600" },
  { type: "MEETING", label: "Meeting", Icon: Users2, activeClass: "border-purple-400 bg-purple-50 text-purple-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-purple-300 hover:text-purple-600" },
  { type: "SITE_VISIT", label: "Site Visit", Icon: MapPin, activeClass: "border-orange-400 bg-orange-50 text-orange-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-orange-300 hover:text-orange-600" },
  { type: "FOLLOW_UP", label: "Follow-up", Icon: RefreshCw, activeClass: "border-teal-400 bg-teal-50 text-teal-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-teal-300 hover:text-teal-600" },
  { type: "TASK", label: "Task", Icon: CheckSquare2, activeClass: "border-slate-500 bg-slate-100 text-slate-700", idleClass: "border-slate-200 bg-white text-slate-500 hover:border-slate-400 hover:text-slate-700" },
] as const satisfies readonly { type: ActivityType; label: string; [k: string]: unknown }[];
/**
 * The task's activity type, as told to us by the server.
 *
 * This is a *read*, not a guess: the title is never inspected. The `?? "TASK"` only
 * covers a row the activity-type backfill hasn't reached (or an older backend that
 * doesn't send the field), which is exactly the value the server itself defaults to.
 */
function activityTypeOf(task: Task): ActivityType {
  return task.activityType ?? "TASK";
}
/** UI metadata (label / icon / colours) for a type. Always resolves. */
function activityInfo(type: ActivityType) {
  return ACTIVITY_TYPES.find(a => a.type === type) ?? ACTIVITY_TYPES[5]; // TASK
}
// ── Helpers ───────────────────────────────────────────────────────────────────
function isOverdue(task: Task): boolean {
  if (task.status === "COMPLETED" || task.status === "CANCELLED") return false;
  if (task.endAt) return new Date(task.endAt) < new Date();
  return false;
}
/** Primary date for agenda/calendar grouping — uses startAt date. */
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
      // This object is built for all three kinds, so `leadId` is null whenever the task
      // relates to a deal or a customer instead. The href was a template literal before,
      // which happily produced the string "/leads/null"; falling back to the list means a
      // stray click lands somewhere real.
      href: task.leadId ? ROUTE_PATHS.leadDetail(task.leadId) : ROUTE_PATHS.leads,
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
export function ReassignFollowUpModal({
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
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent size="lg" className="gap-0">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-[16px] leading-6">
            <UserCog className="size-5 text-brand-600 dark:text-brand-500" />
            Reassign follow-up
          </DialogTitle>
          <DialogDescription className="truncate">{task.title}</DialogDescription>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-y-auto">
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
      </DialogContent>
    </Dialog>
  );
}
// ── Task Detail / Edit Drawer ─────────────────────────────────────────────────
export function TaskDetailDrawer({
  task,
  onClose,
  users,
  onReassign,
  initialEditing = false,
  canAssignOthers,
}: {
  task: Task;
  onClose: () => void;
  users: UserOption[];
  onReassign: () => void;
  initialEditing?: boolean;
  /** Manager/Admin. Staff may edit their own task but not hand it to anyone else. */
  canAssignOthers: boolean;
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
    // A pre-backfill task reports no type; it edits as TASK rather than blank.
    activityType: activityTypeOf(task),
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
  const actType = activityTypeOf(task);
  const typeInfo = activityInfo(actType);
  function handleSubmit(e: { preventDefault(): void }) {
    e.preventDefault();
    if (form.status === "COMPLETED" && !form.resultNote?.trim()) {
      toast.error("Completion note (Result / Notes) is required to complete the task.");
      return;
    }
    updateMutation.mutate({ ...form }, {
      onSuccess: () => { toast.success("Task updated successfully."); setEditing(false); onClose(); },
      onError: (error) => { toast.error(getApiErrorMessage(error, "Failed to update task.")); },
    });
  }
  return (
    <Drawer open onOpenChange={(next) => !next && onClose()}>
      <DrawerContent size="lg" className="gap-0" showClose={false}>
        <DrawerHeader className="flex-row items-center justify-between gap-3 pr-5">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[10px] font-bold shrink-0 ${ACTIVITY_CHIP[actType]}`}>
              <typeInfo.Icon className="size-3" />
              {typeInfo.label}
            </span>
            <div className="min-w-0">
              <DrawerTitle className="truncate text-[15px] leading-5">
                {editing ? "Edit Task" : task.title}
              </DrawerTitle>
              <DrawerDescription className="text-[11px]">
                {linkedEntityType(task)} · {task.assignedUserName ?? "Unassigned"}
              </DrawerDescription>
            </div>
          </div>
          <div className="ml-3 flex shrink-0 items-center gap-1.5">
            {!editing && task.status !== "CANCELLED" && canAssignOthers && (
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
            {/* §9.3 — the drawer peeks, the workspace is where the work happens.
                A real <Link> so ctrl/middle-click opens it in a new tab. */}
            {!editing && (
              <Link
                href={`${ROUTE_PATHS.manageFollowUpTasks}/${task.taskId}`}
                className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-100"
                title="Open the full task workspace"
              >
                Open workspace
                <ArrowUpRight className="size-3.5" />
              </Link>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
            >
              <X className="size-4" />
            </button>
          </div>
        </DrawerHeader>
        <DrawerBody className="px-6 py-5">
          {editing ? (
            /* ── Edit Form ── */
            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Activity type — a real field now. These buttons used to overwrite the
                  title with "Meeting: ", which threw away whatever the user had
                  written; they now set the type and leave the title alone. */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Activity Type *</label>
                <div className="flex flex-wrap gap-1.5">
                  {ACTIVITY_TYPES.map(({ type, label, Icon, activeClass, idleClass }) => {
                    const active = (form.activityType ?? "TASK") === type;
                    return (
                      <button
                        key={type}
                        type="button"
                        aria-pressed={active}
                        onClick={() => setForm(f => ({ ...f, activityType: type }))}
                        className={`inline-flex items-center gap-1.5 h-8 px-3 rounded-full border text-[11px] font-semibold transition-all duration-150 active:scale-95 ${active ? `${activeClass} shadow-sm` : idleClass}`}
                      >
                        <Icon className="size-3" />{label}
                      </button>
                    );
                  })}
                </div>
              </div>
              {/* Title — just the subject of the work. */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Title *</label>
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
              {/* Assigned Staff — staff see who owns the task, managers can move it. */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-600">Assigned Staff *</label>
                {canAssignOthers ? (
                  <Select value={form.assignedUserId ?? ""} onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))} className="py-2">
                    <option value="">Select staff member…</option>
                    {users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
                  </Select>
                ) : (
                  <>
                    <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                      <User className="size-3.5 text-slate-400" />
                      {task.assignedUserName ?? "You"}
                    </div>
                    <p className="text-[11px] text-slate-400">Only a manager can assign this to someone else.</p>
                  </>
                )}
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
                      min={toDateStr(new Date())}
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
                      min={extractLocalDate(form.startAt) || toDateStr(new Date())}
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
              {/* Link to Entity. Re-pointing a task at a different lead/customer/deal
                  rewrites its business context, so UpdateTaskUseCase restricts that to
                  a manager — staff see the link they're working against, read-only. */}
              {canAssignOthers ? (
                <EntitySearchPicker
                  selectedLead={selectedLead}
                  selectedCustomer={selectedCustomer}
                  selectedDeal={selectedDeal}
                  onSelectLead={handleSelectLead}
                  onSelectCustomer={handleSelectCustomer}
                  onSelectDeal={handleSelectDeal}
                />
              ) : (
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-600">Linked Record</label>
                  <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                    <Briefcase className="size-3.5 text-slate-400" />
                    {linkedEntityLabel(task) || "Not linked"}
                  </div>
                  <p className="text-[11px] text-slate-400">Only a manager can link this task to a different record.</p>
                </div>
              )}
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
        </DrawerBody>
      </DrawerContent>
    </Drawer>
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
export function EntitySearchPicker({
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
    const rec = d as unknown as Record<string, unknown>;
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
                      ? <p className="py-4 text-center text-xs text-slate-400">No leads found for &ldquo;{debouncedQuery}&rdquo;</p>
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
                      ? <p className="py-4 text-center text-xs text-slate-400">No customers found for &ldquo;{debouncedQuery}&rdquo;</p>
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
                      ? <p className="py-4 text-center text-xs text-slate-400">No deals found for &ldquo;{debouncedQuery}&rdquo;</p>
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
export function CreateTaskDrawer({
  onClose,
  users,
  initialDueDate,
  canAssignOthers,
  currentUserId,
  currentUserName,
}: {
  onClose: () => void;
  users: UserOption[];
  initialDueDate?: string;
  /** Manager/Admin. Staff may only raise tasks for themselves. */
  canAssignOthers: boolean;
  currentUserId: string;
  currentUserName: string;
}) {
  const [activityType, setActivityType] = useState<ActivityType>("FOLLOW_UP");
  const [form, setForm] = useState<CreateTaskPayload>({
    title: "",
    description: "",
    // Kept in sync from `activityType` on submit; a type is always selected, so the
    // required field can never go out empty.
    activityType: "FOLLOW_UP",
    // A new task starts out yours — the only value a staff member is allowed to
    // send, and the sane default for a manager. (It used to default to whoever
    // happened to be first in the user list.)
    assignedUserId: currentUserId,
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
  // Linking a record seeds an *empty* title with that record's name, and fills in
  // the contact. It no longer prepends "Call: " — the activity type is a field of
  // its own now, and the title is only ever what the task is about.
  function handleSelectLead(lead: Lead | null) {
    setSelectedLead(lead);
    setForm(f => ({
      ...f,
      leadId: lead?.leadId ?? undefined,
      customerId: undefined,
      dealId: undefined,
      primaryContactName: lead?.fullName ?? "",
      primaryContactPhone: lead?.phone ?? "",
      title: lead && !f.title.trim() ? lead.fullName : f.title,
    }));
    if (lead) { setSelectedCustomer(null); setSelectedDeal(null); }
  }
  function handleSelectCustomer(customer: CustomerResult | null) {
    setSelectedCustomer(customer);
    setForm(f => ({
      ...f,
      customerId: customer?.customerId ?? undefined,
      leadId: undefined,
      dealId: undefined,
      primaryContactName: customer?.fullName ?? "",
      primaryContactPhone: customer?.phone ?? "",
      title: customer && !f.title.trim() ? customer.fullName : f.title,
    }));
    if (customer) { setSelectedLead(null); setSelectedDeal(null); }
  }
  function handleSelectDeal(deal: DealResult | null) {
    setSelectedDeal(deal);
    setForm(f => ({
      ...f,
      dealId: deal?.dealId ?? undefined,
      leadId: undefined,
      customerId: undefined,
      title: deal && !f.title.trim() ? deal.title : f.title,
    }));
    if (deal) { setSelectedLead(null); setSelectedCustomer(null); }
  }
  /** Picking a type changes only the type. The title is the user's to write. */
  function handleActivityTypeChange(type: ActivityType) {
    setActivityType(type);
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
      // The selector is the single source of truth for this — it is required by the
      // backend, and there is always exactly one type selected.
      activityType,
      primaryContactName: form.primaryContactName?.trim() || undefined,
      primaryContactPhone: form.primaryContactPhone?.trim() || undefined,
    }, {
      onSuccess: () => { toast.success("Task created successfully."); onClose(); },
      onError: (error) => { toast.error(getApiErrorMessage(error, "Failed to create task.")); },
    });
  }
  return (
    <Drawer open onOpenChange={(next) => !next && onClose()}>
      <DrawerContent size="lg" className="gap-0">
        <DrawerHeader>
          <DrawerTitle className="flex items-center gap-2 text-[16px] leading-6">
            <CalendarCheck className="size-5 text-brand-600 dark:text-brand-500" />
            Create follow-up task
          </DrawerTitle>
          <DrawerDescription>Assign follow-up actions to the sales team</DrawerDescription>
        </DrawerHeader>
        <form onSubmit={handleSubmit} className="min-h-0 flex-1 space-y-5 overflow-y-auto px-7 py-5">
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
                  min={toDateStr(new Date())}
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
                  min={extractLocalDate(form.startAt) || toDateStr(new Date())}
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
              {canAssignOthers ? (
                <Select
                  required
                  value={form.assignedUserId}
                  onChange={e => setForm(f => ({ ...f, assignedUserId: e.target.value }))}
                  className="py-2"
                >
                  <option value="">Select staff member…</option>
                  {/* Yourself first — the common case is a manager noting their own follow-up. */}
                  {currentUserId && <option value={currentUserId}>{currentUserName} (Me)</option>}
                  {users
                    .filter(u => u.userId !== currentUserId)
                    .map(u => (
                      <option key={u.userId} value={u.userId}>{u.fullName}</option>
                    ))}
                </Select>
              ) : (
                <>
                  <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                    <User className="size-3.5 text-slate-400" />
                    {currentUserName} (Me)
                  </div>
                  <p className="text-[11px] text-slate-400">Only a manager can assign a task to someone else.</p>
                </>
              )}
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
      </DrawerContent>
    </Drawer>
  );
}
// Calendar helpers — Monday-first week
function toDateStr(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
/** Returns true when the task's scheduled window covers the given date string. */
// Calendar chip color per activity type
const ACTIVITY_CHIP: Record<ActivityType, string> = {
  CALL: "bg-green-50 border-green-200 text-green-700",
  EMAIL: "bg-blue-50 border-blue-200 text-blue-700",
  MEETING: "bg-purple-50 border-purple-200 text-purple-700",
  SITE_VISIT: "bg-orange-50 border-orange-200 text-orange-700",
  FOLLOW_UP: "bg-teal-50 border-teal-200 text-teal-700",
  TASK: "bg-slate-50 border-slate-200 text-slate-600",
};
