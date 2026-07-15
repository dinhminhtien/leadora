"use client";

import React, { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  Gauge, Plus, AlertTriangle, ShieldOff, ShieldAlert,
  Pencil, Trash2, X, Check, ExternalLink, Activity, CheckCircle,
  BarChart2, Download, TrendingUp, TrendingDown,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { useAuthStore } from "@/stores/auth_store";
import {
  useSlaRules,
  useCreateSlaRule,
  useUpdateSlaRule,
  useDeleteSlaRule,
  useSlaMonitoring,
  useResolveSlaTracking,
  useSlaReport,
} from "@/features/sla/hooks/use_sla";
import { useResolveTask } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import type { SlaRule, SlaRulePayload, SlaActivityType, SlaTracking, SlaDisplayStatus, SlaActivityBreakdown } from "@/services/sla_service";
import { quotationService, type QuotationStatus } from "@/services/quotation_service";
import { leadService } from "@/services/lead_service";
import { taskService } from "@/services/follow_up_task_service";
import { operationalHandoverService } from "@/services/operational_handover_service";
import { depositPaymentService } from "@/services/deposit_payment_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";

// Quotation has no standalone detail page — "Go to" only makes sense while it's
// still actionable from the list. Mirrors QuotationListScreen's DONE_STATUSES.
const QUOTATION_DONE_STATUSES: QuotationStatus[] = ["converted", "closed", "expired", "rejected"];

// Before navigating from an SLA card, confirm the underlying record is still
// there — SLA tracking rows can outlive the entity they point at (deleted,
// or the entity moved past the point where the destination screen is useful).
// Each entry fetches the record by id; `isDone` + `doneMessage` are only set
// where landing on the destination is genuinely a dead end (see quotation:
// no detail page, only an active/done list).
type EntityCheck = {
  getById: (id: string) => Promise<{ data: unknown }>;
  isDone?: (item: unknown) => boolean;
  doneMessage?: (item: unknown) => string;
  notFoundMessage: string;
};

const ENTITY_CHECKS: Record<string, EntityCheck> = {
  QUOTATION: {
    getById: (id) => quotationService.getById(id),
    isDone: (item) => QUOTATION_DONE_STATUSES.includes((item as { status: QuotationStatus }).status),
    doneMessage: (item) =>
      `This quotation has already been processed (status: ${(item as { status: QuotationStatus }).status.replace("_", " ")}).`,
    notFoundMessage: "This quotation no longer exists.",
  },
  LEAD: {
    getById: (id) => leadService.getById(id),
    notFoundMessage: "This lead no longer exists.",
  },
  TASK: {
    getById: (id) => taskService.getById(id),
    notFoundMessage: "This task no longer exists.",
  },
  HANDOVER: {
    getById: (id) => operationalHandoverService.getById(id),
    notFoundMessage: "This handover record no longer exists.",
  },
  PAYMENT: {
    getById: (id) => depositPaymentService.getById(id),
    notFoundMessage: "This payment record no longer exists.",
  },
};

// BR-02: only ADMIN and MANAGER can configure rules
const CONFIGURE_ROLES = ["ADMIN", "MANAGER"];

// ─── Shared constants ────────────────────────────────────────────────────────

const ACTIVITY_LABELS: Record<string, string> = {
  LEAD_RESPONSE:              "Lead Response",
  QUOTATION_SENT:             "Quotation Dispatch",
  FOLLOW_UP_TASK:             "Follow-up Task",
  PAYMENT_DEPOSIT:            "Payment Deposit",
  HANDOVER_SUBMISSION:        "Handover Submission",
  QUOTATION_APPROVAL:         "Quotation Approval",
  CUSTOMER_FEEDBACK_RESPONSE: "Customer Feedback Response",
};

const ENTITY_TYPE_LABELS: Record<string, string> = {
  LEAD:      "Lead",
  QUOTATION: "Quotation",
  TASK:      "Task",
  PAYMENT:   "Payment",
  HANDOVER:  "Handover",
};

// ─── Monitor tab ─────────────────────────────────────────────────────────────

const DISPLAY_STATUS_VARIANT: Record<SlaDisplayStatus, "danger" | "warning" | "success"> = {
  BREACHED:   "danger",
  WARNING:    "warning",
  WITHIN_SLA: "success",
};

const DISPLAY_STATUS_LABEL: Record<SlaDisplayStatus, string> = {
  BREACHED:   "Breached",
  WARNING:    "Warning",
  WITHIN_SLA: "Within SLA",
};

function getEntityRoute(entityType: string, entityId: string): string | null {
  const highlight = `highlight=${encodeURIComponent(entityId)}`;
  switch (entityType.toUpperCase()) {
    case "LEAD":      return ROUTE_PATHS.leadDetail(entityId);
    // No standalone quotation detail page exists (only /quotations,
    // /quotations/[id]/revise) — route to the list (with highlight), not
    // ROUTE_PATHS.quotationDetail, which 404s since that page was never built.
    case "QUOTATION": return `${ROUTE_PATHS.quotations}?${highlight}`;
    case "TASK":      return `${ROUTE_PATHS.followUpTasks}?${highlight}`;
    case "PAYMENT":   return ROUTE_PATHS.depositPayment;
    case "HANDOVER":  return `${ROUTE_PATHS.operationalHandover}?${highlight}`;
    default:          return null;
  }
}

function formatHoursRemaining(hours: number): string {
  const abs = Math.abs(hours);
  const label = abs === 1 ? "1h" : `${abs}h`;
  return hours >= 0 ? `${label} left` : `${label} overdue`;
}

function MonitorTab() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { highlightedId, setRowRef } = useHighlightRow();
  const [entityTypeFilter, setEntityTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<SlaDisplayStatus | "">("");
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolveTarget, setResolveTarget] = useState<{ trackingId: string; entityId?: string; isTask: boolean } | null>(null);
  const [resolveError, setResolveError] = useState<string | null>(null);
  const [checkingId, setCheckingId] = useState<string | null>(null);
  const [infoDialog, setInfoDialog] = useState<{ title: string; message: string } | null>(null);
  const { show: showToast, ToastContainer } = useToast();

  const { data: records = [], isLoading } = useSlaMonitoring(
    entityTypeFilter || undefined,
    statusFilter || undefined,
  );
  const resolveMutation     = useResolveSlaTracking();
  const resolveTaskMutation = useResolveTask();

  const breachedCount = records.filter((r) => r.displayStatus === "BREACHED").length;
  const warningCount  = records.filter((r) => r.displayStatus === "WARNING").length;
  const withinCount   = records.filter((r) => r.displayStatus === "WITHIN_SLA").length;

  const handleConfirmResolve = async () => {
    if (!resolveTarget) return;
    const { trackingId, entityId, isTask } = resolveTarget;
    setResolvingId(trackingId);
    try {
      if (isTask && entityId) {
        try {
          await resolveTaskMutation.mutateAsync(entityId);
        } catch (e: unknown) {
          const status = (e as { response?: { status?: number } })?.response?.status;
          if (status === 404) {
            await resolveMutation.mutateAsync(trackingId);
          } else throw e;
        }
        showToast("Task marked as completed. SLA tracking and reminders resolved.");
      } else {
        await resolveMutation.mutateAsync(trackingId);
        showToast("SLA breach marked as resolved.");
      }
      setResolveTarget(null);
      setResolveError(null);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setResolveError(msg ?? "Failed to resolve. Please try again.");
    } finally {
      setResolvingId(null);
    }
  };

  // Confirm the underlying record is still there (and, for quotations, still
  // actionable) before navigating — SLA tracking rows can outlive the entity
  // they point at, and several destinations (quotation) have no way to signal
  // "not found" themselves. Avoids the dead-end 404/blank list users hit
  // when the record was deleted or already moved past the point of interest.
  const handleNavigate = async (r: SlaTracking) => {
    const route = getEntityRoute(r.entityType, r.entityId);
    if (!route) return;

    const check = ENTITY_CHECKS[r.entityType.toUpperCase()];
    if (!check) {
      router.push(route);
      return;
    }

    setCheckingId(r.trackingId);
    try {
      const res = await check.getById(r.entityId);
      if (check.isDone?.(res.data)) {
        setInfoDialog({ title: "Can't open this record", message: check.doneMessage!(res.data) });
        return;
      }
      router.push(route);
    } catch {
      setInfoDialog({ title: "Can't open this record", message: check.notFoundMessage });
    } finally {
      setCheckingId(null);
    }
  };

  return (
    <>
      <ToastContainer />
      {infoDialog && (
        <InfoDialog title={infoDialog.title} message={infoDialog.message} onClose={() => setInfoDialog(null)} />
      )}
      {resolveTarget && (
        <ResolveDialog
          title={resolveTarget.isTask ? "Resolve Task" : "Resolve SLA Breach"}
          message={resolveTarget.isTask
            ? "Mark this task as completed? SLA tracking and pending reminders will also be resolved."
            : "Mark this SLA breach as resolved? This action cannot be undone."}
          error={resolveError}
          onConfirm={handleConfirmResolve}
          onCancel={() => { setResolveTarget(null); setResolveError(null); }}
          isLoading={resolveMutation.isPending || resolveTaskMutation.isPending}
        />
      )}
    <div className="space-y-5">
      {/* Summary stats */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Breached", count: breachedCount, color: "text-red-600",   bg: "bg-red-50 border-red-100"   },
          { label: "Warning",  count: warningCount,  color: "text-amber-600", bg: "bg-amber-50 border-amber-100" },
          { label: "Within SLA", count: withinCount, color: "text-emerald-600", bg: "bg-emerald-50 border-emerald-100" },
        ].map(({ label, count, color, bg }) => (
          <div key={label} className={`rounded-xl border px-4 py-3 ${bg}`}>
            <p className={`text-xl font-bold ${color}`}>{count}</p>
            <p className="text-[10px] font-semibold text-slate-500 mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={entityTypeFilter}
          onChange={(e) => setEntityTypeFilter(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
        >
          <option value="">All entity types</option>
          {Object.entries(ENTITY_TYPE_LABELS).map(([v, l]) => (
            <option key={v} value={v}>{l}</option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as SlaDisplayStatus | "")}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
        >
          <option value="">All statuses</option>
          <option value="BREACHED">Breached</option>
          <option value="WARNING">Warning</option>
          <option value="WITHIN_SLA">Within SLA</option>
        </select>
      </div>

      {/* Records list */}
      {isLoading ? (
        <div className="py-12 text-center text-xs text-slate-400">Loading SLA data…</div>
      ) : records.length === 0 ? (
        <div className="py-14 text-center text-xs text-slate-400">
          <Activity className="mx-auto size-8 text-slate-300 mb-2" />
          <p className="font-bold text-slate-600 text-sm">No active SLA records</p>
          <p className="mt-1">
            {entityTypeFilter || statusFilter
              ? "Try clearing the filters."
              : "SLA tracking will appear here once activities are created."}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {records.map((r) => (
            <SlaTrackingRow
              key={r.trackingId}
              record={r}
              onNavigate={() => handleNavigate(r)}
              isNavigating={checkingId === r.trackingId}
              onResolve={
                r.entityType === "TASK"
                  ? () => setResolveTarget({ trackingId: r.trackingId, entityId: r.entityId, isTask: true })
                  : r.displayStatus === "BREACHED"
                    ? () => setResolveTarget({ trackingId: r.trackingId, isTask: false })
                    : undefined
              }
              resolveLabel={r.entityType === "TASK" ? "Resolve Task" : "Resolve"}
              isResolving={resolvingId === r.trackingId}
              rowRef={setRowRef(r.trackingId)}
              isHighlighted={highlightedId === r.trackingId}
            />
          ))}
        </div>
      )}
    </div>
    </>
  );
}

function SlaTrackingRow({
  record: r,
  onNavigate,
  isNavigating,
  onResolve,
  resolveLabel = "Resolve",
  isResolving,
  rowRef,
  isHighlighted,
}: {
  record: SlaTracking;
  onNavigate: () => void;
  isNavigating?: boolean;
  onResolve?: () => void;
  resolveLabel?: string;
  isResolving?: boolean;
  rowRef?: (el: HTMLDivElement | null) => void;
  isHighlighted?: boolean;
}) {
  const route = getEntityRoute(r.entityType, r.entityId);
  const isUrgent = r.displayStatus === "BREACHED" || r.displayStatus === "WARNING";

  return (
    <Card
      ref={rowRef}
      onClick={route && !isNavigating ? onNavigate : undefined}
      className={`border shadow-xs transition ${route ? "cursor-pointer hover:border-blue-200 hover:shadow-sm" : ""} ${
        isHighlighted ? "ring-2 ring-inset ring-amber-400 bg-amber-50" :
        r.displayStatus === "BREACHED"   ? "border-red-200 bg-red-50/30"   :
        r.displayStatus === "WARNING"    ? "border-amber-200 bg-amber-50/20" :
        "border-slate-100 bg-white"
      }`}
    >
      <CardContent className="p-3 flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-start gap-3 flex-1 min-w-0">
          {isUrgent && (
            <AlertTriangle className={`size-4 shrink-0 mt-0.5 ${
              r.displayStatus === "BREACHED" ? "text-red-500" : "text-amber-500"
            }`} />
          )}
          <div className="min-w-0 space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant="default" size="sm" className="text-[9px] font-bold uppercase">
                {ENTITY_TYPE_LABELS[r.entityType] ?? r.entityType}
              </Badge>
              <span className="text-xs font-semibold text-slate-700">
                {ACTIVITY_LABELS[r.activityType] ?? r.activityType}
              </span>
              <Badge
                variant={DISPLAY_STATUS_VARIANT[r.displayStatus as SlaDisplayStatus] ?? "default"}
                size="sm"
                className="text-[9px] font-bold uppercase"
              >
                {DISPLAY_STATUS_LABEL[r.displayStatus as SlaDisplayStatus] ?? r.displayStatus}
              </Badge>
            </div>
            <div className="flex flex-wrap gap-4 text-[10px] text-slate-500 font-semibold">
              <span>
                Deadline:{" "}
                <span className="text-slate-700">
                  {new Date(r.deadlineAt).toLocaleString("en-GB", {
                    day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit",
                  })}
                </span>
              </span>
              <span className={
                r.hoursRemaining < 0 ? "text-red-600" :
                r.hoursRemaining <= 2 ? "text-amber-600" : "text-slate-500"
              }>
                {formatHoursRemaining(r.hoursRemaining)}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {/* UC-17.4 / UC-17.5: Resolve button — label and behavior differ by entity type */}
          {onResolve && (
            <Button
              onClick={(e) => { e.stopPropagation(); onResolve(); }}
              isLoading={isResolving}
              variant="outline"
              size="sm"
              className="gap-1 text-[10px] border-red-200 text-red-600 font-semibold hover:bg-red-50"
            >
              <CheckCircle className="size-3" /> {resolveLabel}
            </Button>
          )}
          {route && (
            <Button
              onClick={(e) => { e.stopPropagation(); onNavigate(); }}
              isLoading={isNavigating}
              variant="outline"
              size="sm"
              className="gap-1 text-[10px] border-slate-200 font-semibold text-slate-600"
            >
              <ExternalLink className="size-3" /> Go to
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// ─── Toast ────────────────────────────────────────────────────────────────────

type ToastItem = { id: number; message: string; type: "success" | "error" };

function useToast() {
  const [toasts, setToasts] = React.useState<ToastItem[]>([]);
  const counter = React.useRef(0);

  const show = React.useCallback((message: string, type: "success" | "error" = "success") => {
    const id = ++counter.current;
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3500);
  }, []);

  const ToastContainer = React.useCallback(() => (
    <div className="fixed top-4 right-4 z-100 flex flex-col gap-2 pointer-events-none">
      {toasts.map((t) => (
        <div key={t.id} className={`flex items-center gap-2.5 px-4 py-3 rounded-xl shadow-lg border text-xs font-semibold pointer-events-auto transition-all
          ${t.type === "success"
            ? "bg-emerald-50 border-emerald-200 text-emerald-700"
            : "bg-red-50 border-red-200 text-red-700"}`}>
          {t.type === "success"
            ? <CheckCircle className="size-4 shrink-0" />
            : <AlertTriangle className="size-4 shrink-0" />}
          {t.message}
        </div>
      ))}
    </div>
  ), [toasts]);

  return { show, ToastContainer };
}

// ─── Info Dialog ────────────────────────────────────────────────────────────────

function InfoDialog({ title, message, onClose }: { title: string; message: string; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-sm mx-4 p-6 space-y-4">
        <div className="flex items-start gap-3">
          <div className="shrink-0 flex items-center justify-center size-10 rounded-full bg-amber-50">
            <AlertTriangle className="size-5 text-amber-500" />
          </div>
          <div className="space-y-1">
            <h3 className="text-sm font-bold text-slate-800">{title}</h3>
            <p className="text-xs text-slate-500 leading-relaxed">{message}</p>
          </div>
        </div>
        <div className="flex items-center justify-end pt-1">
          <Button onClick={onClose} size="sm" className="text-xs font-semibold">
            OK
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Confirm Dialog ───────────────────────────────────────────────────────────

function ConfirmDialog({
  title,
  message,
  confirmLabel = "Delete",
  error,
  onConfirm,
  onCancel,
  isLoading,
}: {
  title: string;
  message: string;
  confirmLabel?: string;
  error?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
  isLoading?: boolean;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancel} />
      <div className="relative z-10 bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-sm mx-4 p-6 space-y-4">
        <div className="flex items-start gap-3">
          <div className="shrink-0 flex items-center justify-center size-10 rounded-full bg-red-50">
            <Trash2 className="size-5 text-red-500" />
          </div>
          <div className="space-y-1">
            <h3 className="text-sm font-bold text-slate-800">{title}</h3>
            <p className="text-xs text-slate-500 leading-relaxed">{message}</p>
          </div>
        </div>
        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
            <AlertTriangle className="size-3.5 shrink-0" /> {error}
          </div>
        )}
        <div className="flex items-center justify-end gap-2 pt-1">
          <Button onClick={onCancel} variant="outline" size="sm" className="text-xs border-slate-200 text-slate-600 font-semibold">
            Cancel
          </Button>
          <Button onClick={onConfirm} isLoading={isLoading} size="sm" className="text-xs bg-red-600 hover:bg-red-700 text-white font-semibold gap-1.5">
            <Trash2 className="size-3" /> {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Resolve Dialog ───────────────────────────────────────────────────────────

function ResolveDialog({
  title,
  message,
  error,
  onConfirm,
  onCancel,
  isLoading,
}: {
  title: string;
  message: string;
  error?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
  isLoading?: boolean;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancel} />
      <div className="relative z-10 bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-sm mx-4 p-6 space-y-4">
        <div className="flex items-start gap-3">
          <div className="shrink-0 flex items-center justify-center size-10 rounded-full bg-emerald-50">
            <CheckCircle className="size-5 text-emerald-500" />
          </div>
          <div className="space-y-1">
            <h3 className="text-sm font-bold text-slate-800">{title}</h3>
            <p className="text-xs text-slate-500 leading-relaxed">{message}</p>
          </div>
        </div>
        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
            <AlertTriangle className="size-3.5 shrink-0" /> {error}
          </div>
        )}
        <div className="flex items-center justify-end gap-2 pt-1">
          <Button onClick={onCancel} variant="outline" size="sm" className="text-xs border-slate-200 text-slate-600 font-semibold">
            Cancel
          </Button>
          <Button onClick={onConfirm} isLoading={isLoading} size="sm" className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white font-semibold gap-1.5">
            <CheckCircle className="size-3" /> Confirm
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Configure tab ────────────────────────────────────────────────────────────

const ACTIVITY_OPTIONS: SlaActivityType[] = [
  "LEAD_RESPONSE", "QUOTATION_SENT", "FOLLOW_UP_TASK",
  "PAYMENT_DEPOSIT", "HANDOVER_SUBMISSION", "QUOTATION_APPROVAL", "CUSTOMER_FEEDBACK_RESPONSE",
];

type FormState = {
  activityType: SlaActivityType;
  name: string;
  deadlineHours: number;
  warningThreshold: number;
  escalationThreshold: number;
  active: boolean;
};

const EMPTY_FORM: FormState = {
  activityType: "LEAD_RESPONSE",
  name: "",
  deadlineHours: 2,
  warningThreshold: 1,
  escalationThreshold: 1,
  active: true,
};

function validate(form: FormState): string | null {
  if (!form.name.trim()) return "Rule name is required.";
  if (form.deadlineHours <= 0) return "Deadline must be greater than 0 hours.";
  if (form.warningThreshold <= 0) return "Warning threshold must be greater than 0 hours.";
  if (form.warningThreshold >= form.deadlineHours)
    return `Warning threshold (${form.warningThreshold}h) must be less than the deadline (${form.deadlineHours}h).`;
  if (form.escalationThreshold <= 0) return "Escalation threshold must be greater than 0 hours.";
  return null;
}

const inputCls =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition";

function RuleForm({
  value, onChange, onSave, onCancel, isSaving, error,
}: {
  value: FormState;
  onChange: (f: FormState) => void;
  onSave: () => void;
  onCancel: () => void;
  isSaving: boolean;
  error: string | null;
}) {
  const set = <K extends keyof FormState>(key: K, val: FormState[K]) =>
    onChange({ ...value, [key]: val });

  return (
    <div className="space-y-3 p-4 bg-slate-50 rounded-xl border border-slate-200">
      {error && (
        <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
          <AlertTriangle className="size-3.5 shrink-0" /> {error}
        </div>
      )}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Activity Type</label>
          <select value={value.activityType} onChange={(e) => set("activityType", e.target.value as SlaActivityType)} className={inputCls}>
            {ACTIVITY_OPTIONS.map((t) => <option key={t} value={t}>{ACTIVITY_LABELS[t]}</option>)}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Rule Name</label>
          <input type="text" value={value.name} onChange={(e) => set("name", e.target.value)} placeholder="e.g. Lead Response - High Priority" className={inputCls} />
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Deadline (hours)</label>
          <input type="number" min={1} value={value.deadlineHours} onChange={(e) => set("deadlineHours", Number(e.target.value))} className={inputCls} />
          <p className="text-[10px] text-slate-400">Maximum time before SLA violation is recorded.</p>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Warning Threshold (hours before deadline)</label>
          <input type="number" min={1} value={value.warningThreshold} onChange={(e) => set("warningThreshold", Number(e.target.value))} className={inputCls} />
          <p className="text-[10px] text-slate-400">Must be less than the deadline.</p>
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Escalation Threshold (hours after deadline)</label>
          <input type="number" min={1} value={value.escalationThreshold} onChange={(e) => set("escalationThreshold", Number(e.target.value))} className={inputCls} />
        </div>
        <div className="space-y-1 flex flex-col justify-end">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Status</label>
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <button type="button" onClick={() => set("active", !value.active)}
              className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${value.active ? "bg-blue-500" : "bg-slate-200"}`}>
              <span className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${value.active ? "translate-x-4" : "translate-x-1"}`} />
            </button>
            <span className="text-xs text-slate-600 font-semibold">{value.active ? "Active" : "Inactive"}</span>
          </label>
        </div>
      </div>
      <div className="flex items-center gap-2 pt-1">
        <Button onClick={onSave} isLoading={isSaving} variant="primary" size="sm" className="gap-1.5 text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white">
          <Check className="size-3.5" /> Save Rule
        </Button>
        <Button onClick={onCancel} variant="outline" size="sm" className="gap-1.5 text-xs font-semibold border-slate-200 text-slate-600">
          <X className="size-3.5" /> Cancel
        </Button>
      </div>
    </div>
  );
}

function ConfigureTab() {
  const { user } = useAuthStore();
  // BR-02: everyone may view SLA rules; only Admin/Sales Manager may create, edit, or delete them.
  const canEdit = user?.roles?.some((r) => CONFIGURE_ROLES.includes(r)) ?? false;

  const { data: rules = [], isLoading } = useSlaRules();
  const createRule = useCreateSlaRule();
  const updateRule = useUpdateSlaRule();
  const deleteRule = useDeleteSlaRule();

  const [isCreating, setIsCreating] = useState(false);
  const [newForm, setNewForm] = useState<FormState>(EMPTY_FORM);
  const [newError, setNewError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<FormState>(EMPTY_FORM);
  const [editError, setEditError] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleCreate = async () => {
    const err = validate(newForm);
    if (err) { setNewError(err); return; }
    setNewError(null);
    try {
      await createRule.mutateAsync(newForm as SlaRulePayload);
      setIsCreating(false);
      setNewForm(EMPTY_FORM);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setNewError(msg ?? "Failed to save rule. Please try again.");
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteRule.mutateAsync(deleteTarget.id);
      setDeleteTarget(null);
      setDeleteError(null);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setDeleteError(msg ?? "Failed to delete rule. Please try again.");
    }
  };

  const startEdit = (rule: SlaRule) => {
    setEditingId(rule.id);
    setEditForm({ activityType: rule.activityType, name: rule.name, deadlineHours: rule.deadlineHours, warningThreshold: rule.warningThreshold, escalationThreshold: rule.escalationThreshold, active: rule.active });
    setEditError(null);
  };

  const handleUpdate = async (id: string) => {
    const err = validate(editForm);
    if (err) { setEditError(err); return; }
    setEditError(null);
    try {
      await updateRule.mutateAsync({ id, payload: editForm as SlaRulePayload });
      setEditingId(null);
    } catch {
      setEditError("Failed to save rule. Please try again.");
    }
  };

  const activeCount = rules.filter((r) => r.active).length;

  return (
    <>
      {deleteTarget && (
        <ConfirmDialog
          title="Delete SLA Rule"
          message={`Are you sure you want to delete "${deleteTarget.name}"? This action cannot be undone.`}
          error={deleteError}
          onConfirm={handleDelete}
          onCancel={() => { setDeleteTarget(null); setDeleteError(null); }}
          isLoading={deleteRule.isPending}
        />
      )}
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <p className="text-xs text-slate-400">
          {rules.length} rules configured — <span className="font-semibold text-slate-600">{activeCount} active</span>
        </p>
        {canEdit && !isCreating && (
          <Button onClick={() => { setIsCreating(true); setNewForm(EMPTY_FORM); setNewError(null); setEditingId(null); }}
            variant="primary" size="sm" className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white">
            <Plus className="size-3.5" /> New Rule
          </Button>
        )}
      </div>

      {canEdit && isCreating && (
        <RuleForm value={newForm} onChange={setNewForm} onSave={handleCreate}
          onCancel={() => { setIsCreating(false); setNewError(null); }}
          isSaving={createRule.isPending} error={newError} />
      )}

      {isLoading ? (
        <div className="py-10 text-center text-xs text-slate-400">Loading rules…</div>
      ) : rules.length === 0 && !isCreating ? (
        <div className="py-14 text-center text-xs text-slate-400">
          <Gauge className="mx-auto size-8 text-slate-300 mb-2" />
          <p className="font-bold text-slate-600 text-sm">No SLA rules configured</p>
        </div>
      ) : (
        <div className="space-y-3">
          {rules.map((rule) => (
            <Card key={rule.id} className={`border-slate-100 shadow-xs bg-white ${!rule.active ? "opacity-60" : ""}`}>
              {canEdit && editingId === rule.id ? (
                <CardContent className="p-4">
                  <RuleForm value={editForm} onChange={setEditForm} onSave={() => handleUpdate(rule.id)}
                    onCancel={() => setEditingId(null)} isSaving={updateRule.isPending} error={editError} />
                </CardContent>
              ) : (
                <CardContent className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
                  <div className="flex-1 space-y-2">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs font-bold text-slate-800">{rule.name}</span>
                      <Badge variant={rule.active ? "success" : "default"} size="sm" className="text-[9px] font-bold uppercase">
                        {rule.active ? "Active" : "Inactive"}
                      </Badge>
                      <Badge variant="default" size="sm" className="text-[9px] font-semibold">
                        {ACTIVITY_LABELS[rule.activityType] ?? rule.activityType}
                      </Badge>
                    </div>
                    <div className="flex flex-wrap gap-5 text-[10px] font-semibold text-slate-500">
                      <span className="flex items-center gap-1"><Gauge className="size-3 text-blue-400" />Deadline: <span className="text-slate-700 ml-0.5">{rule.deadlineHours}h</span></span>
                      <span className="flex items-center gap-1"><AlertTriangle className="size-3 text-amber-400" />Warning: <span className="text-slate-700 ml-0.5">{rule.warningThreshold}h before</span></span>
                      <span className="flex items-center gap-1"><ShieldAlert className="size-3 text-red-400" />Escalation: <span className="text-slate-700 ml-0.5">{rule.escalationThreshold}h after</span></span>
                    </div>
                  </div>
                  {canEdit && (
                    <div className="flex items-center gap-2 shrink-0">
                      <Button onClick={() => startEdit(rule)} variant="outline" size="sm" className="gap-1.5 text-xs border-slate-200 text-slate-600 font-semibold">
                        <Pencil className="size-3" /> Edit
                      </Button>
                      <Button onClick={() => setDeleteTarget({ id: rule.id, name: rule.name })} variant="outline" size="sm" className="gap-1.5 text-xs border-red-200 text-red-500 hover:bg-red-50 font-semibold">
                        <Trash2 className="size-3" /> Delete
                      </Button>
                    </div>
                  )}
                </CardContent>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
    </>
  );
}

// ─── Report tab ───────────────────────────────────────────────────────────────

const REPORT_ROLES = ["ADMIN", "MANAGER", "RESERVATION_STAFF", "FRONT_OFFICE"];

function toIsoDate(d: Date): string {
  return d.toISOString().split("T")[0];
}

function exportCsv(rows: SlaActivityBreakdown[], from: string, to: string) {
  const header = "Activity Type,Activity Label,Total,Resolved,Breached,Warning,Within SLA,Breach Rate %,Avg Processing Hours";
  const lines = rows.map((r) =>
    [r.activityType, r.activityLabel, r.total, r.resolved, r.breached, r.warning, r.withinSla,
     r.breachRatePct.toFixed(1), r.avgProcessingHours.toFixed(1)].join(",")
  );
  const csv = [header, ...lines].join("\n");
  const blob = new Blob([csv], { type: "text/csv" });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement("a");
  a.href = url;
  a.download = `sla-report-${from}-to-${to}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function KpiCard({ label, value, sub, trend, color }: {
  label: string; value: string; sub?: string;
  trend?: "up" | "down" | "neutral"; color: string;
}) {
  return (
    <div className={`rounded-xl border px-4 py-3 space-y-1 ${color}`}>
      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">{label}</p>
      <p className="text-xl font-bold text-slate-800 flex items-center gap-1.5">
        {value}
        {trend === "up"   && <TrendingUp   className="size-4 text-emerald-500" />}
        {trend === "down" && <TrendingDown className="size-4 text-red-500" />}
      </p>
      {sub && <p className="text-[10px] text-slate-400">{sub}</p>}
    </div>
  );
}

function ReportTab() {
  const { user } = useAuthStore();
  const hasAccess = user?.roles?.some((r) => REPORT_ROLES.includes(r)) ?? false;

  const defaultFrom = toIsoDate(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000));
  const defaultTo   = toIsoDate(new Date());

  const [fromDate, setFromDate]             = useState(defaultFrom);
  const [toDate, setToDate]                 = useState(defaultTo);
  const [activityFilter, setActivityFilter] = useState("");

  const { data: report, isLoading, isFetching } = useSlaReport({
    from: fromDate,
    to:   toDate,
    activityType: activityFilter || undefined,
  });

  // E4: Unauthorized Access (BR-02)
  if (!hasAccess) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <ShieldOff className="size-10 text-red-400" />
        <h2 className="text-sm font-bold text-slate-700">Access Denied</h2>
        <p className="text-xs text-slate-400 text-center max-w-xs">
          Only Admin, Sales Manager, Reservation Staff, or Front Office can view SLA performance reports.
        </p>
      </div>
    );
  }

  const fmt1 = (n: number) => n.toFixed(1);
  const noData = !isLoading && report?.totalTracked === 0;

  return (
    <div className="space-y-5">
      {/* Filters row */}
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">From</label>
          <input
            type="date"
            value={fromDate}
            max={toDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
          />
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">To</label>
          <input
            type="date"
            value={toDate}
            min={fromDate}
            max={toIsoDate(new Date())}
            onChange={(e) => setToDate(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
          />
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Activity Type</label>
          <select
            value={activityFilter}
            onChange={(e) => setActivityFilter(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
          >
            <option value="">All activities</option>
            {ACTIVITY_OPTIONS.map((t) => (
              <option key={t} value={t}>{ACTIVITY_LABELS[t]}</option>
            ))}
          </select>
        </div>
        {report && report.byActivityType.length > 0 && (
          <Button
            onClick={() => exportCsv(report.byActivityType, fromDate, toDate)}
            variant="outline"
            size="sm"
            className="gap-1.5 text-xs border-slate-200 text-slate-600 font-semibold self-end"
          >
            <Download className="size-3.5" /> Export CSV
          </Button>
        )}
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="py-10 text-center text-xs text-slate-400">Loading report…</div>
      )}
      {!isLoading && isFetching && (
        <p className="text-[10px] text-slate-400 text-right">Updating…</p>
      )}

      {/* E3: No SLA data found */}
      {noData && (
        <div className="py-14 text-center text-xs text-slate-400">
          <BarChart2 className="mx-auto size-8 text-slate-300 mb-2" />
          <p className="font-bold text-slate-600 text-sm">No data available</p>
          <p className="mt-1">No SLA tracking records found for the selected filters.</p>
        </div>
      )}

      {/* Summary KPI cards */}
      {report && report.totalTracked > 0 && !isLoading && !isFetching && (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <KpiCard
              label="Total Tracked"
              value={String(report.totalTracked)}
              sub={`${fromDate} → ${toDate}`}
              color="bg-blue-50 border-blue-100"
            />
            <KpiCard
              label="Compliance Rate"
              value={`${fmt1(report.complianceRatePct)}%`}
              sub={`${report.withinSlaCount + report.resolvedCount} within / resolved`}
              trend={report.complianceRatePct >= 80 ? "up" : "down"}
              color="bg-emerald-50 border-emerald-100"
            />
            <KpiCard
              label="Breach Rate"
              value={`${fmt1(report.breachRatePct)}%`}
              sub={`${report.breachedCount} breached`}
              trend={report.breachRatePct > 20 ? "down" : "neutral"}
              color="bg-red-50 border-red-100"
            />
            <KpiCard
              label="Avg Processing"
              value={`${fmt1(report.avgProcessingHours)}h`}
              sub={`${report.resolvedCount} resolved tasks`}
              color="bg-violet-50 border-violet-100"
            />
          </div>

          {/* Secondary metrics row */}
          <div className="grid grid-cols-3 gap-3">
            {[
              { label: "Resolved",    count: report.resolvedCount,  color: "text-emerald-600" },
              { label: "Breached",    count: report.breachedCount,  color: "text-red-600" },
              { label: "Warning",     count: report.warningCount,   color: "text-amber-600" },
            ].map(({ label, count, color }) => (
              <div key={label} className="rounded-xl border border-slate-100 bg-white px-4 py-2.5 flex items-center justify-between">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">{label}</span>
                <span className={`text-sm font-bold ${color}`}>{count}</span>
              </div>
            ))}
          </div>

          {/* Breakdown table */}
          {report.byActivityType.length > 0 && (
            <Card className="border-slate-100 shadow-xs">
              <CardContent className="p-0">
                <div className="px-4 pt-3 pb-2 border-b border-slate-100">
                  <p className="text-xs font-bold text-slate-700">Breakdown by Activity Type</p>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-slate-600">
                    <thead>
                      <tr className="border-b border-slate-100 bg-slate-50/60 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                        <th className="px-4 py-2.5 text-left">Activity</th>
                        <th className="px-3 py-2.5 text-right">Total</th>
                        <th className="px-3 py-2.5 text-right">Resolved</th>
                        <th className="px-3 py-2.5 text-right">Breached</th>
                        <th className="px-3 py-2.5 text-right">Warning</th>
                        <th className="px-3 py-2.5 text-right">Within SLA</th>
                        <th className="px-3 py-2.5 text-right">Breach %</th>
                        <th className="px-3 py-2.5 text-right">Avg Hours</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.byActivityType.map((row) => (
                        <tr key={row.activityType} className="border-b border-slate-50 hover:bg-slate-50/50 transition">
                          <td className="px-4 py-2.5 font-semibold text-slate-700">{row.activityLabel}</td>
                          <td className="px-3 py-2.5 text-right font-bold text-slate-800">{row.total}</td>
                          <td className="px-3 py-2.5 text-right text-emerald-600 font-semibold">{row.resolved}</td>
                          <td className="px-3 py-2.5 text-right text-red-600 font-semibold">{row.breached}</td>
                          <td className="px-3 py-2.5 text-right text-amber-600 font-semibold">{row.warning}</td>
                          <td className="px-3 py-2.5 text-right text-slate-500">{row.withinSla}</td>
                          <td className="px-3 py-2.5 text-right">
                            <span className={`font-bold ${row.breachRatePct > 20 ? "text-red-600" : row.breachRatePct > 10 ? "text-amber-600" : "text-emerald-600"}`}>
                              {fmt1(row.breachRatePct)}%
                            </span>
                          </td>
                          <td className="px-3 py-2.5 text-right text-slate-500">{fmt1(row.avgProcessingHours)}h</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

// ─── Main screen with tabs ────────────────────────────────────────────────────

type Tab = "monitor" | "configure" | "report";

export function SlaManagementScreen() {
  const { user } = useAuthStore();
  const canReport = user?.roles?.some((r) => REPORT_ROLES.includes(r)) ?? false;
  const [activeTab, setActiveTab] = useState<Tab>("monitor");

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold text-slate-800">SLA Management</h1>
        <p className="text-xs text-slate-400 mt-0.5">
          Monitor deadlines, configure rules, and review performance reports
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {([
          { key: "monitor"   as Tab, label: "Monitor Status",  icon: Activity,   show: true },
          { key: "report"    as Tab, label: "Performance",     icon: BarChart2,  show: canReport },
          { key: "configure" as Tab, label: "SLA Rules",       icon: Gauge,      show: true },
        ]).filter((t) => t.show).map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-semibold border-b-2 transition -mb-px ${
              activeTab === key
                ? "border-blue-600 text-blue-600"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="size-3.5" />
            {label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === "monitor"   && <MonitorTab />}
      {activeTab === "report"    && <ReportTab />}
      {activeTab === "configure" && <ConfigureTab />}
    </div>
  );
}
