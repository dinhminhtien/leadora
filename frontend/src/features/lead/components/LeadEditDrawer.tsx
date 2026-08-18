"use client";

import React, { useState } from "react";
import {
  Edit3, X, Loader2, Save, AlertCircle, User, Building, ServerCrash, UserCog,
} from "lucide-react";
import { isAxiosError } from "axios";
import { Input } from "@/components/ui/Input";
import type { ApiErrorResponse } from "@/services/api_client";
import type { Lead, LeadStatus, UpdateLeadPayload } from "@/services/lead_service";
import type { UserSummary } from "@/services/follow_up_task_service";
import { InterestedServiceInput } from "@/features/lead/components/InterestedServiceInput";

/**
 * The Edit Lead form, in the slide-over the rest of the app uses for editing.
 *
 * <p><b>Why it is a component.</b> This markup existed twice — once in the detail screen's drawer
 * and once, re-typed, as a full-width page for the unassigned-lead case. They had already drifted:
 * the page laid Source Channel and Interested Service out as two stacked rows where the drawer
 * pairs them, cleared errors through a different helper, and used its own footer. Two copies of a
 * form that must agree do not stay in agreement, and the second copy is also why that screen
 * stopped matching the rest of the product.
 *
 * <p>The one real difference between the two callers is <b>Status</b>, so that is a prop rather
 * than a second file. An unassigned lead cannot change status at all — the server refuses it
 * (BR-06) — so showing a locked dropdown there would only invite the click it then rejects.
 */

export const SOURCE_OPTIONS = [
  "Website Inquiry", "Referral", "Social Media", "Cold Call", "Walk-in", "Event",
];

/**
 * Re-exported so this module's existing importers keep working, but the rules
 * themselves now live in one place.
 *
 * <p>This file used to carry its own `validateLeadForm`, a near-copy of
 * `lead-rules.ts`'s `validateLead`. The two agreed on names and phone shape and
 * differed on exactly one rule — a lead needs a phone or an email — which the
 * copy enforced and the original did not. Two validators that agree on the easy
 * rules and differ on the important one are worse than either alone: whichever
 * form you test, the other behaves differently.
 */
import { type LeadFieldErrors } from "@/features/lead/lib/lead-rules";

export { NAME_ALLOWED, validateLead as validateLeadForm } from "@/features/lead/lib/lead-rules";
export type LeadEditErrors = LeadFieldErrors;

/** Seeds the form from a lead. Kept here so both callers start from the same shape. */
export function seedLeadForm(lead: Lead): UpdateLeadPayload {
  return {
    fullName: lead.fullName,
    email: lead.email ?? "",
    phone: lead.phone ?? "",
    companyName: lead.companyName ?? "",
    address: lead.address ?? "",
    isCorporate: lead.isCorporate,
    source: lead.source ?? "",
    interestedService: lead.interestedService ?? "",
    notes: lead.notes ?? "",
    status: lead.status,
    assignedUserId: lead.assignedUserId ?? "",
  };
}

export function leadApiError(error: unknown): ApiErrorResponse | undefined {
  return isAxiosError<ApiErrorResponse>(error) ? error.response?.data : undefined;
}

export function leadApiStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}

interface StatusControl {
  /** Options the dropdown may offer, in pipeline order. */
  options: LeadStatus[];
  labels: Record<LeadStatus, string>;
  locked: boolean;
  /** BR-05 fields still missing; blocks CONTACTED/QUALIFIED while non-empty. */
  missingForFollowUp: string[];
  hint: string;
}

interface AssignControl {
  users: UserSummary[];
}

interface LeadEditDrawerProps {
  form: UpdateLeadPayload;
  errors: LeadEditErrors;
  serverError?: string;
  saving: boolean;
  onChange: (patch: Partial<UpdateLeadPayload>) => void;
  onSubmit: (e: React.FormEvent) => void;
  onClose: () => void;
  /** Omit to hide the Status control entirely — the unassigned-lead case. */
  status?: StatusControl;
  /** Manager-only reassignment. Omit to hide. */
  assign?: AssignControl;
  /** Shown above the fields; used for the "not assigned yet" explanation. */
  notice?: React.ReactNode;
  subtitle?: string;
}

export function LeadEditDrawer({
  form, errors, serverError, saving, onChange, onSubmit, onClose,
  status, assign, notice, subtitle,
}: LeadEditDrawerProps) {

  const inputClass = (invalid?: string) =>
    `w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition ${
      invalid
        ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100"
        : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"
    }`;

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40" onClick={onClose} />
      <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 flex flex-col
        animate-in slide-in-from-right duration-300 ease-out">

        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <Edit3 className="size-4.5 text-blue-600" />
              Edit Lead
            </h3>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {subtitle ?? "Update contact info and stage"}
            </p>
          </div>
          <button onClick={onClose} type="button"
            className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition">
            <X className="size-4.5" />
          </button>
        </div>

        <form onSubmit={onSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">

          {notice}

          {serverError && (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-4 shrink-0" />{serverError}
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700">Full Name <span className="text-rose-500">*</span></label>
            <input value={form.fullName ?? ""} maxLength={40}
              onChange={e => onChange({ fullName: e.target.value })}
              className={inputClass(errors.fullName)} />
            {errors.fullName && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{errors.fullName}</p>}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Email</label>
              <input type="text" value={form.email ?? ""} maxLength={40}
                onChange={e => onChange({ email: e.target.value })}
                className={inputClass(errors.email)} />
              {errors.email && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{errors.email}</p>}
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Phone Number</label>
              <Input phoneOnly value={form.phone ?? ""} placeholder="e.g. 09xxxxxxxx"
                onChange={e => onChange({ phone: e.target.value })}
                error={errors.phone} className="py-1.5 text-xs" />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700">Address</label>
            <input value={form.address ?? ""}
              onChange={e => onChange({ address: e.target.value })}
              placeholder="e.g. 12 Nguyen Hue, District 1, HCMC"
              className={inputClass()} />
          </div>

          {/* BR-05: the three fields a lead needs before it may enter active follow-up are
              phone/email, source and interested service — paired here in the order the
              server-side guard reads them. */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Source Channel</label>
              <select value={form.source ?? ""}
                onChange={e => onChange({ source: e.target.value })}
                className={`${inputClass()} cursor-pointer`}>
                <option value="">— Select —</option>
                {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Interested Service</label>
              <InterestedServiceInput
                value={form.interestedService ?? ""}
                onChange={v => onChange({ interestedService: v })}
                className={inputClass()} />
            </div>
          </div>

          {/* Full width, unlike the paired rows above: the hint carries the reason the control is
              blocked and which fields unblock it, which a half-column wraps into a stack. */}
          {status && (
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">Status</label>
              <select value={form.status ?? ""}
                disabled={status.locked}
                onChange={e => onChange({ status: e.target.value as LeadStatus })}
                className={`${inputClass()} cursor-pointer disabled:bg-slate-100 disabled:text-slate-400 disabled:cursor-not-allowed`}>
                {status.options.map(s => {
                  // Only the two active-follow-up states need BR-05's details. NEW and LOST stay
                  // selectable on purpose: a junk lead must be closable immediately rather than
                  // after filling in fields nobody will ever read.
                  const needsDetails = s === "CONTACTED" || s === "QUALIFIED";
                  const blocked = needsDetails && status.missingForFollowUp.length > 0;
                  return (
                    <option key={s} value={s} disabled={blocked}>
                      {status.labels[s]}
                      {/* Name the missing fields here, not just "unavailable": the hint under the
                          select is hidden by the open menu at the moment it is needed. */}
                      {blocked ? ` — add ${status.missingForFollowUp.join(" + ")} first` : ""}
                    </option>
                  );
                })}
              </select>
              <p className={`text-[10px] ${status.missingForFollowUp.length ? "text-amber-600" : "text-slate-400"}`}>
                {status.hint}
              </p>
            </div>
          )}

          {assign && (
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                <UserCog className="size-3.5 text-slate-400" /> Assign To
              </label>
              <select value={form.assignedUserId ?? ""}
                onChange={e => onChange({ assignedUserId: e.target.value })}
                className={`${inputClass()} cursor-pointer`}>
                <option value="">Unassigned</option>
                {assign.users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
              </select>
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700">Notes</label>
            <textarea rows={4} value={form.notes ?? ""}
              onChange={e => onChange({ notes: e.target.value })}
              className={`${inputClass()} resize-none`} />
          </div>

          {/* Customer Type at the bottom; Organization reveals a required company field. */}
          <div className="space-y-1.5 pt-1 border-t border-slate-100">
            <label className="text-xs font-semibold text-slate-700 pt-3 block">Customer Type</label>
            <div className="grid grid-cols-2 gap-2">
              {([[false, User], [true, Building]] as const).map(([corp, Icon]) => {
                const selected = !!form.isCorporate === corp;
                return (
                  <button key={String(corp)} type="button"
                    onClick={() => onChange({ isCorporate: corp, ...(corp ? {} : { companyName: "" }) })}
                    className={`flex items-center gap-2 px-3 py-2.5 rounded-xl border text-sm font-semibold transition
                      ${selected
                        ? "border-blue-500 bg-blue-50 text-blue-700 shadow-sm"
                        : "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:bg-slate-50"}`}>
                    <Icon className={`size-4 ${selected ? "text-blue-600" : "text-slate-400"}`} />
                    {corp ? "Organization" : "Individual"}
                  </button>
                );
              })}
            </div>
          </div>

          {form.isCorporate && (
            <div className="space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200">
              <label className="text-xs font-semibold text-slate-700">Company / Organization <span className="text-rose-500">*</span></label>
              <input value={form.companyName ?? ""} maxLength={40}
                onChange={e => onChange({ companyName: e.target.value })}
                placeholder="e.g. TechCorp Inc."
                className={inputClass(errors.companyName)} />
              {errors.companyName && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{errors.companyName}</p>}
            </div>
          )}
        </form>

        <div className="flex gap-3 px-6 py-4 border-t border-slate-100 bg-slate-50">
          <button type="button" onClick={onClose}
            className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
            Cancel
          </button>
          <button type="button" onClick={onSubmit} disabled={saving}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-primary rounded-xl hover:bg-primary/90 disabled:opacity-60 transition active:scale-95">
            {saving ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            Save Changes
          </button>
        </div>
      </aside>
    </>
  );
}

/**
 * The self-contained variant used from the Leads list: owns its own form state and mutation, so a
 * row click opens it without navigating away. `useState` is initialised from the lead once, which
 * is correct here because the drawer is mounted fresh per lead (keyed by the caller).
 */
export function useLeadEditState(lead: Lead) {
  const [form, setForm] = useState<UpdateLeadPayload>(() => seedLeadForm(lead));
  const [errors, setErrors] = useState<LeadEditErrors>({});
  const [serverError, setServerError] = useState("");

  const change = (patch: Partial<UpdateLeadPayload>) => {
    setForm(f => ({ ...f, ...patch }));
    setErrors(prev => {
      const next = { ...prev };
      for (const key of Object.keys(patch)) delete next[key as keyof LeadEditErrors];
      return next;
    });
  };

  return { form, setForm, errors, setErrors, serverError, setServerError, change };
}
