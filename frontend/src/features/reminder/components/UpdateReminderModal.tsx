"use client";

import React, { useState } from "react";
import { X, Bell, AlertTriangle, CheckCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useUpdateReminder, useEscalateReminder } from "@/features/reminder/hooks/use_reminders";
import { useAuthStore } from "@/stores/auth_store";
import type { Reminder, ReminderPriority, UpdateReminderPayload } from "@/services/reminder_service";

const inputCls =
  "w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition";

function toDatetimeLocal(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

type Props = {
  reminder: Reminder;
  onClose: () => void;
};

export function UpdateReminderModal({ reminder, onClose }: Props) {
  const { user } = useAuthStore();
  const updateMutation = useUpdateReminder();
  const escalateMutation = useEscalateReminder();

  const isManager = user?.roles?.includes("MANAGER") ?? false;
  const isAssignee = user?.id === reminder.assignedUserId;
  const canEdit = isManager || isAssignee;

  const [form, setForm] = useState({
    title: reminder.title,
    description: reminder.description ?? "",
    remindAt: toDatetimeLocal(reminder.remindAt),
    priority: reminder.priority as ReminderPriority,
  });
  const [forceIfDone, setForceIfDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  // Time-overdue: PENDING but past due (scheduler may not have run yet)
  const isTimeOverdue = reminder.status === "PENDING" && new Date(reminder.remindAt) < new Date();
  // Server-confirmed overdue: scheduler already set status = OVERDUE
  const isOverdue = reminder.status === "OVERDUE" || isTimeOverdue;

  // Only allow escalation when server has confirmed OVERDUE — avoids 409 from backend
  const showEscalate = reminder.status === "OVERDUE";
  const showMarkDone = reminder.status !== "DONE";

  const handleSave = async () => {
    setError(null);
    setSuccessMsg(null);

    if (form.remindAt !== toDatetimeLocal(reminder.remindAt)) {
      if (new Date(form.remindAt) <= new Date()) {
        setError("Due date must be in the future.");
        return;
      }
    }

    const payload: UpdateReminderPayload = { forceIfDone };

    if (form.title !== reminder.title) payload.title = form.title;
    if (form.description !== (reminder.description ?? "")) payload.description = form.description;
    if (form.remindAt !== toDatetimeLocal(reminder.remindAt)) {
      payload.remindAt = new Date(form.remindAt).toISOString();
    }
    if (form.priority !== reminder.priority) payload.priority = form.priority;

    try {
      await updateMutation.mutateAsync({ reminderId: reminder.reminderId, payload });
      onClose();
    } catch {
      setError("Failed to update reminder. Please try again.");
    }
  };

  const handleMarkDone = async () => {
    setError(null);
    setSuccessMsg(null);

    try {
      await updateMutation.mutateAsync({
        reminderId: reminder.reminderId,
        payload: { markAsDone: true, forceIfDone },
      });
      onClose();
    } catch {
      setError("Failed to mark reminder as done. Please try again.");
    }
  };

  const handleEscalate = async () => {
    setError(null);
    setSuccessMsg(null);

    try {
      await escalateMutation.mutateAsync(reminder.reminderId);
      setSuccessMsg("Reminder escalated to manager successfully.");
    } catch {
      setError("Failed to escalate reminder. Please try again.");
    }
  };

  return (
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
              <h2 className="text-sm font-bold text-slate-800">Update Reminder</h2>
              <p className="text-[10px] text-slate-400 max-w-[240px] truncate">{reminder.title}</p>
            </div>
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-slate-100 text-slate-400 transition">
            <X className="size-4" />
          </button>
        </div>

        {/* Body */}
        <div className="px-5 py-4 space-y-3">
          {/* Status banners */}
          {reminder.status === "OVERDUE" && (
            <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
              <AlertTriangle className="size-3.5 shrink-0" /> This reminder is overdue
            </div>
          )}
          {isTimeOverdue && (
            <div className="flex items-center gap-2 rounded-lg bg-orange-50 border border-orange-200 px-3 py-2 text-xs text-orange-700 font-semibold">
              <AlertTriangle className="size-3.5 shrink-0" /> Past due — system will mark as overdue shortly (up to 5 min). Refresh to escalate.
            </div>
          )}
          {reminder.status === "DONE" && (
            <div className="space-y-2">
              <div className="flex items-center gap-2 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-700 font-semibold">
                <CheckCircle className="size-3.5 shrink-0" /> This reminder is already completed
              </div>
              <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={forceIfDone}
                  onChange={e => setForceIfDone(e.target.checked)}
                  className="rounded border-slate-300"
                />
                Update anyway
              </label>
            </div>
          )}

          {/* Access denied */}
          {!canEdit && (
            <div className="flex items-center gap-2 rounded-lg bg-slate-50 border border-slate-200 px-3 py-2 text-xs text-slate-500 font-semibold">
              <AlertTriangle className="size-3.5 shrink-0" /> Access Denied — only the assignee or a manager can edit this reminder.
            </div>
          )}

          {/* Error / success */}
          {error && (
            <div className="flex items-center gap-2 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-xs text-red-600 font-semibold">
              <AlertTriangle className="size-3.5 shrink-0" /> {error}
            </div>
          )}
          {successMsg && (
            <div className="flex items-center gap-2 rounded-lg bg-emerald-50 border border-emerald-200 px-3 py-2 text-xs text-emerald-700 font-semibold">
              <CheckCircle className="size-3.5 shrink-0" /> {successMsg}
            </div>
          )}

          {/* Form fields */}
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Title</label>
            <input
              type="text"
              value={form.title}
              onChange={e => set("title", e.target.value)}
              disabled={!canEdit}
              className={inputCls + (!canEdit ? " opacity-50 cursor-not-allowed" : "")}
              maxLength={255}
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Description</label>
            <textarea
              value={form.description}
              onChange={e => set("description", e.target.value)}
              disabled={!canEdit}
              rows={2}
              className={inputCls + " resize-none" + (!canEdit ? " opacity-50 cursor-not-allowed" : "")}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Due Date &amp; Time</label>
              <input
                type="datetime-local"
                value={form.remindAt}
                onChange={e => set("remindAt", e.target.value)}
                disabled={!canEdit}
                className={inputCls + (!canEdit ? " opacity-50 cursor-not-allowed" : "")}
              />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wide">Priority</label>
              <select
                value={form.priority}
                onChange={e => set("priority", e.target.value as ReminderPriority)}
                disabled={!canEdit}
                className={inputCls + (!canEdit ? " opacity-50 cursor-not-allowed" : "")}
              >
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 px-5 pb-5 pt-2 flex-wrap">
          <Button variant="outline" size="sm" onClick={onClose} className="text-xs border-slate-200 text-slate-600">
            Cancel
          </Button>

          {showEscalate && canEdit && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleEscalate}
              isLoading={escalateMutation.isPending}
              className="text-xs border-orange-300 text-orange-600 hover:bg-orange-50"
            >
              Escalate
            </Button>
          )}

          {showMarkDone && canEdit && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleMarkDone}
              isLoading={updateMutation.isPending}
              className="text-xs border-emerald-300 text-emerald-700 hover:bg-emerald-50"
            >
              Mark as Done
            </Button>
          )}

          <Button
            variant="primary"
            size="sm"
            onClick={handleSave}
            isLoading={updateMutation.isPending}
            disabled={!canEdit}
            className="text-xs font-bold"
          >
            Save Changes
          </Button>
        </div>
      </div>
    </div>
  );
}
