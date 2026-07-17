"use client";

import React, { useState } from "react";
import { X, Bell, AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/Button";
import { useCreateReminder } from "@/features/reminder/hooks/use_reminders";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { useAuthStore } from "@/stores/auth_store";
import { apiClient } from "@/services/api_client";
import type { ReminderPriority } from "@/services/reminder_service";
import { Portal } from "@/components/ui/Portal";

type RecordOption = { id: string; label: string };
type EntityTypeOption = { label: string; value: string };

const ENTITY_TYPES_WITH_LIST = ["QUOTATION", "LEAD", "BOOKING"];

function useEntityOptions(entityType: string, enabled: boolean) {
  return useQuery<RecordOption[]>({
    queryKey: ["reminder-entity-options", entityType],
    queryFn: async () => {
      if (entityType === "QUOTATION") {
        const res = await apiClient.get("/quotations");
        const list: { id: string; quoteNo: string; contactName: string }[] = res.data?.data ?? [];
        return list.map((q) => ({ id: q.id, label: `${q.quoteNo} — ${q.contactName}` }));
      }
      if (entityType === "LEAD") {
        const res = await apiClient.get("/leads");
        const list: { leadId: string; fullName: string }[] = res.data?.data?.content ?? res.data?.data ?? [];
        return list.map((l) => ({ id: l.leadId, label: l.fullName }));
      }
      if (entityType === "BOOKING") {
        const res = await apiClient.get("/bookings");
        const list: { bookingId: string; bookingCode: string; customerName: string }[] =
          res.data?.data?.content ?? res.data?.data ?? [];
        return list.map((b) => ({ id: b.bookingId, label: `${b.bookingCode} — ${b.customerName}` }));
      }
      return [];
    },
    enabled: enabled && ENTITY_TYPES_WITH_LIST.includes(entityType),
    staleTime: 60_000,
  });
}

const ENTITY_TYPE_OPTIONS: EntityTypeOption[] = [
  { label: "Quotation", value: "QUOTATION" },
  { label: "Lead", value: "LEAD" },
  { label: "Booking", value: "BOOKING" },
  { label: "Deposit / Payment", value: "DEPOSIT" },
];

const PRIORITY_OPTIONS: { label: string; value: ReminderPriority }[] = [
  { label: "High", value: "HIGH" },
  { label: "Medium", value: "MEDIUM" },
  { label: "Low", value: "LOW" },
];

type Props = {
  onClose: () => void;
  /** Pre-fill entity context when opening from a specific record */
  defaultRelatedEntity?: string;
  defaultRelatedId?: string;
};

const inputCls =
  "w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition";

export function CreateReminderModal({ onClose, defaultRelatedEntity, defaultRelatedId }: Props) {
  const { user } = useAuthStore();
  const createReminder = useCreateReminder();
  const { data: usersRes } = useUsers();
  const allUsers = usersRes?.data ?? [];

  const [form, setForm] = useState({
    title: "",
    description: "",
    remindAt: "",
    priority: "MEDIUM" as ReminderPriority,
    relatedEntity: defaultRelatedEntity ?? "QUOTATION",
    relatedId: defaultRelatedId ?? "",
    assignedUserId: "",
  });
  const [error, setError] = useState<string | null>(null);

  const isStandaloneMode = !defaultRelatedId;
  const hasListEndpoint = ENTITY_TYPES_WITH_LIST.includes(form.relatedEntity);
  const { data: entityOptions = [], isLoading: loadingOptions } = useEntityOptions(
    form.relatedEntity,
    isStandaloneMode && hasListEndpoint,
  );

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleEntityTypeChange = (type: string) => {
    setForm((prev) => ({ ...prev, relatedEntity: type, relatedId: "" }));
  };

  const handleSubmit = async () => {
    setError(null);

    const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

    // E3: validate required fields
    if (!form.title.trim()) { setError("Title is required."); return; }
    if (!form.remindAt)     { setError("Due date/time is required."); return; }
    if (!form.relatedId.trim()) { setError("Related entity ID is required."); return; }
    if (!UUID_RE.test(form.relatedId.trim())) {
      setError("Entity ID must be a valid UUID (e.g. xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
      return;
    }

    // E4: due date must be in the future
    if (new Date(form.remindAt) <= new Date()) {
      setError("Due date/time must be in the future.");
      return;
    }

    try {
      await createReminder.mutateAsync({
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        remindAt: new Date(form.remindAt).toISOString(),
        priority: form.priority,
        relatedEntity: form.relatedEntity,
        relatedId: form.relatedId.trim(),
        assignedUserId: form.assignedUserId || undefined,
        // createdByUserId is resolved server-side from the JWT token
      });
      onClose();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? "Failed to create reminder. Please try again.");
    }
  };

  return (
    <Portal>
      <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 bg-white rounded-2xl shadow-2xl border border-slate-100 w-full max-w-md mx-4">
        {/* Header */}
        <div className="flex items-center justify-between px-5 pt-5 pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center size-8 rounded-full bg-blue-50">
              <Bell className="size-4 text-blue-500" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Create Reminder</h2>
              <p className="text-[10px] text-slate-400">Schedule a manual reminder for follow-up</p>
            </div>
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-slate-100 text-slate-400 transition">
            <X className="size-4" />
          </button>
        </div>

        {/* Body */}
        <div className="px-5 py-4 space-y-3">
          {error && (
            <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
              <AlertTriangle className="size-3.5 shrink-0" /> {error}
            </div>
          )}

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
              Title <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => set("title", e.target.value)}
              placeholder="e.g. Follow up on quotation response"
              className={inputCls}
              maxLength={255}
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Description</label>
            <textarea
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
              placeholder="Additional context or notes..."
              rows={2}
              className={inputCls + " resize-none"}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                Due Date &amp; Time <span className="text-red-400">*</span>
              </label>
              <input
                type="datetime-local"
                value={form.remindAt}
                onChange={(e) => set("remindAt", e.target.value)}
                className={inputCls}
              />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Priority</label>
              <select value={form.priority} onChange={(e) => set("priority", e.target.value as ReminderPriority)} className={inputCls}>
                {PRIORITY_OPTIONS.map((p) => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="space-y-3">
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                Linked To <span className="text-red-400">*</span>
              </label>
              <select
                value={form.relatedEntity}
                onChange={(e) => handleEntityTypeChange(e.target.value)}
                disabled={!!defaultRelatedEntity}
                className={inputCls}
              >
                {ENTITY_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            {/* Standalone mode: dropdown search — Pre-filled mode: read-only display */}
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                Select Record <span className="text-red-400">*</span>
              </label>
              {defaultRelatedId ? (
                <div className={inputCls + " text-slate-500 cursor-not-allowed"}>
                  {defaultRelatedEntity} · ID pre-filled
                </div>
              ) : loadingOptions ? (
                <div className={inputCls + " text-slate-400"}>Loading…</div>
              ) : entityOptions.length > 0 ? (
                <select
                  value={form.relatedId}
                  onChange={(e) => set("relatedId", e.target.value)}
                  className={inputCls}
                >
                  <option value="">— Select a record —</option>
                  {entityOptions.map((o) => (
                    <option key={o.id} value={o.id}>{o.label}</option>
                  ))}
                </select>
              ) : hasListEndpoint ? (
                /* Has list endpoint but returned empty */
                <div className={inputCls + " text-slate-400"}>No records found</div>
              ) : (
                /* DEPOSIT: no list endpoint, fall back to text input */
                <input
                  type="text"
                  value={form.relatedId}
                  onChange={(e) => set("relatedId", e.target.value)}
                  placeholder="Paste UUID of the record"
                  className={inputCls}
                />
              )}
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">
              Assign To
            </label>
            <select
              value={form.assignedUserId}
              onChange={(e) => set("assignedUserId", e.target.value)}
              className={inputCls}
            >
              <option value="">Myself</option>
              {allUsers
                .filter((u) => u.userId !== user?.id)
                .map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.fullName}{u.roleName ? ` (${u.roleName})` : ""}
                  </option>
                ))}
            </select>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 px-5 pb-5 pt-2">
          <Button variant="outline" size="sm" onClick={onClose} className="text-xs border-slate-200 text-slate-600">
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleSubmit}
            isLoading={createReminder.isPending}
            leftIcon={<Bell className="size-3.5" />}
            className="text-xs font-bold"
          >
            Create Reminder
          </Button>
        </div>
      </div>
    </div>
    </Portal>
  );
}
