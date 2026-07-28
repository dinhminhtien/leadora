"use client";

/**
 * Lead → Customer conversion (UC-8.5, BR-07 / BR-09 / BR-10), plus the follow-up
 * actions that make sense the moment a customer exists.
 *
 * <p>It lives in its own file because it outlived its host. This markup used to
 * sit inside `LeadDetailScreen`, and `LeadDetailDrawer` imported it back out of
 * that screen — so the drawer depended on a full page it never rendered. The
 * page is gone (a lead now opens only in the drawer); the conversion flow is
 * not, and there is still exactly one copy of it.
 */

import React, { useState } from "react";
import {
  User, CheckCircle2, X, Loader2, AlertCircle, UserPlus,
  ShieldCheck, ShieldAlert, BadgeCheck, Building, ServerCrash,
  Briefcase, Users, Link2,
} from "lucide-react";
import { isAxiosError } from "axios";
import type { ApiErrorResponse } from "@/services/api_client";
import { useConvertLead, useLinkLeadToCustomer } from "@/features/lead/hooks/use_leads";
import { dealService } from "@/services/deal_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import type { Lead, CustomerType } from "@/services/lead_service";

// ── Quick Create Deal Form & Success Action Panel ─────────────────────────────

interface QuickCreateDealFormProps {
  customerId: string;
  lead: Lead;
  onCancel: () => void;
  onSuccess: () => void;
}

function QuickCreateDealForm({ customerId, lead, onCancel, onSuccess }: QuickCreateDealFormProps) {
  const [title, setTitle] = useState(`${lead.fullName} - Deal`);
  const [value, setValue] = useState("");
  const [stage, setStage] = useState<"Inquiry" | "Qualification" | "Proposal" | "Negotiation" | "Contract" | "Confirmed">("Inquiry");
  const [expectedClose, setExpectedClose] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split("T")[0];
  });
  const [notes, setNotes] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setErrorMsg("Deal Title is required");
      return;
    }
    setIsSubmitting(true);
    setErrorMsg("");

    // Absent contact details are sent as undefined, not "". An empty string is a value the
    // server has to validate — and the phone pattern rejected it — where undefined simply means
    // the customer has no number on file, which is the truth for a walk-in lead.
    const payload = {
      customerId,
      title: title.trim(),
      contactName: lead.fullName,
      email: lead.email || undefined,
      phone: lead.phone || undefined,
      stage,
      value: Number(value) || 0,
      expectedClose,
      status: "active",
      notes: notes.trim() || undefined,
    };

    try {
      const res = await dealService.create(payload);
      if (res && res.success) {
        onSuccess();
      } else {
        setErrorMsg(res?.message || "Failed to create deal");
      }
    } catch (err: any) {
      console.error("Failed to quick-create deal", err);
      setErrorMsg(err.response?.data?.message || err.message || "An error occurred");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4 text-left">
      <h3 className="text-sm font-bold text-slate-800 border-b border-slate-100 pb-2 flex items-center gap-1.5">
        <Briefcase className="size-4 text-[#185FA5]" /> Create Deal
      </h3>

      {errorMsg && (
        <div className="flex items-center gap-2 px-3 py-2 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-600">
          <AlertCircle className="size-4 shrink-0" />
          {errorMsg}
        </div>
      )}

      <div className="space-y-1">
        <label className="text-xs font-semibold text-slate-600">Customer</label>
        <input
          type="text"
          disabled
          value={lead.fullName}
          className="w-full px-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-500 cursor-not-allowed"
        />
      </div>

      <div className="space-y-1">
        <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
        <input maxLength={50}
          required
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="e.g. Wedding catering banquet"
          className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-[#185FA5] focus:outline-none focus:ring-1 focus:ring-[#185FA5]/20"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-600">Value (VND)</label>
          <input
            type="number"
            value={value}
            onChange={e => setValue(e.target.value)}
            placeholder="e.g. 15000"
            className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-[#185FA5] focus:outline-none focus:ring-1 focus:ring-[#185FA5]/20"
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-semibold text-slate-600">Stage</label>
          <select
            value={stage}
            onChange={e => setStage(e.target.value as any)}
            className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-[#185FA5] focus:outline-none focus:ring-1 focus:ring-[#185FA5]/20 cursor-pointer"
          >
            <option value="Inquiry">Inquiry</option>
            <option value="Qualification">Qualification</option>
            <option value="Proposal">Proposal</option>
            <option value="Negotiation">Negotiation</option>
            <option value="Contract">Contract</option>
            <option value="Confirmed">Confirmed</option>
          </select>
        </div>
      </div>

      <div className="space-y-1">
        <label className="text-xs font-semibold text-slate-600">Expected Close Date</label>
        <input
          type="date"
          value={expectedClose}
          onChange={e => setExpectedClose(e.target.value)}
          className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-[#185FA5] focus:outline-none focus:ring-1 focus:ring-[#185FA5]/20"
        />
      </div>

      <div className="space-y-1">
        <label className="text-xs font-semibold text-slate-600">Notes</label>
        <textarea
          rows={2}
          value={notes}
          onChange={e => setNotes(e.target.value)}
          className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-[#185FA5] focus:outline-none focus:ring-1 focus:ring-[#185FA5]/20 resize-none"
        />
      </div>

      <div className="flex gap-2 pt-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          className="flex-1 py-2 text-xs font-semibold text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="flex-1 flex items-center justify-center gap-1.5 py-2 text-xs font-bold text-white bg-[#185FA5] rounded-lg hover:bg-[#0C447C] transition disabled:opacity-60"
        >
          {isSubmitting ? (
            <Loader2 className="size-3 animate-spin" />
          ) : (
            <CheckCircle2 className="size-3" />
          )}
          Create
        </button>
      </div>
    </form>
  );
}

interface QuickActionPanelProps {
  lead: Lead;
  customerId: string;
  /** E6 — the lead was attached to a profile that already existed, rather than creating one. */
  linked?: boolean;
  onClose: () => void;
}

function QuickActionPanel({ lead, customerId, linked = false, onClose }: QuickActionPanelProps) {
  const [mode, setMode] = useState<"actions" | "create_deal" | "deal_success">("actions");

  if (mode === "deal_success") {
    return (
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-sm text-center p-10 animate-in zoom-in-95 duration-300">
        <div className="mx-auto mb-6 flex items-center justify-center size-20 rounded-full bg-emerald-100">
          <BadgeCheck className="size-10 text-emerald-500" />
        </div>
        <h2 className="text-xl font-extrabold text-slate-800 mb-2">Deal Created Successfully!</h2>
        <p className="text-sm text-slate-500 mb-8">
          The new deal opportunity is now tracked in your pipeline.
        </p>
        <button
          onClick={onClose}
          className="w-full py-3 text-sm font-bold text-white bg-[#185FA5] rounded-xl hover:bg-[#0C447C] transition active:scale-95 shadow-sm"
        >
          Done
        </button>
      </div>
    );
  }

  if (mode === "create_deal") {
    return (
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6 animate-in zoom-in-95 duration-300">
        <QuickCreateDealForm
          customerId={customerId}
          lead={lead}
          onCancel={() => setMode("actions")}
          onSuccess={() => setMode("deal_success")}
        />
      </div>
    );
  }

  return (
    <div className="bg-white rounded-3xl shadow-2xl w-full max-w-sm text-center p-10 animate-in zoom-in-95 duration-300">
      <div className="mx-auto mb-6 flex items-center justify-center size-20 rounded-full bg-emerald-100">
        <BadgeCheck className="size-10 text-emerald-500" />
      </div>
      <h2 className="text-xl font-extrabold text-slate-800 mb-2">Conversion successful!</h2>
      <p className="text-sm text-slate-500 mb-2">
        {linked ? (
          <>This lead has been linked to <strong className="text-slate-700">{lead.fullName}</strong>’s existing customer profile.</>
        ) : (
          <><strong className="text-slate-700">{lead.fullName}</strong> has been created as an official customer profile.</>
        )}
      </p>
      <p className="text-xs text-slate-400 mb-8">The original lead record is retained for historical lookup.</p>
      
      <div className="flex flex-col gap-3">
        <button
          onClick={() => setMode("create_deal")}
          className="w-full py-3 text-sm font-bold text-[#185FA5] bg-[#E6F1FB] border border-[#85B7EB] rounded-xl hover:bg-[#D0E6F9] transition active:scale-95 flex items-center justify-center gap-2"
        >
          <Briefcase className="size-4" /> Create Deal Opportunity
        </button>
        <button
          onClick={onClose}
          className="w-full py-3 text-sm font-semibold text-slate-600 bg-slate-50 border border-slate-200 rounded-xl hover:bg-slate-100 transition active:scale-95"
        >
          Done
        </button>
      </div>
    </div>
  );
}

// ── Convert Modal ─────────────────────────────────────────────────────────────

/** The backend's error envelope, when the failure actually came from the backend. */
function apiError(error: unknown): ApiErrorResponse | undefined {
  return isAxiosError<ApiErrorResponse>(error) ? error.response?.data : undefined;
}

function apiStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}

/**
 * UC-8.5 E6 — reads a duplicate-customer refusal out of a failed conversion.
 *
 * Returns `null` for every other error, which is what keeps the "link instead" affordance from
 * appearing next to failures it cannot fix. `details` carries the existing customer's id; a 409
 * without one is still shown as a plain error rather than a button that would post `undefined`.
 */
function duplicateCustomerFrom(error: unknown): { customerId: string; field: string } | null {
  const body = apiError(error);
  if (body?.errorCode !== "DUPLICATE_CUSTOMER_EMAIL" && body?.errorCode !== "DUPLICATE_CUSTOMER_PHONE") {
    return null;
  }
  if (!body.details) return null;
  return {
    customerId: body.details,
    field: body.errorCode === "DUPLICATE_CUSTOMER_EMAIL" ? "email address" : "phone number",
  };
}

/** Exported so `LeadDetailDrawer` converts through this exact flow — one
 *  conversion dialog, not a second copy that could drift from BR-09/BR-10. */
export function ConvertModal({
  lead, onClose,
}: {
  lead: Lead; onClose: () => void;
}) {
  const convertMutation = useConvertLead(lead.leadId);
  const linkMutation    = useLinkLeadToCustomer(lead.leadId);
  const [done, setDone] = useState(false);
  const [reason, setReason] = useState("");

  const isQualified = lead.status === "QUALIFIED";
  const isCorporate = lead.isCorporate;
  const customerType: CustomerType = isCorporate ? "CORPORATE" : "INDIVIDUAL";

  // BR-07: a non-qualified lead can still be converted, but only by a Sales
  // Manager/Admin who documents an approval reason. Sales staff never see this.
  const role = getUserRole(useAuthStore(s => s.user));
  const canOverride = role === "MANAGER" || role === "ADMIN";
  const canConfirm = isQualified || (canOverride && reason.trim().length > 0);
  const approvalReason = isQualified ? undefined : reason.trim();

  // E6 — the server refused because this person is already a customer. The refusal carries that
  // customer's id in `details`, which is what turns a dead end into the choice UC-8.5 describes:
  // link the lead to the existing profile, or cancel.
  const duplicate = duplicateCustomerFrom(convertMutation.error);

  const busy = convertMutation.isPending || linkMutation.isPending;

  // Confirmation only — every detail already lives on the lead (captured at create/edit time),
  // and the server now builds the customer from the lead rather than from anything sent here.
  const handleConfirm = () => {
    if (!canConfirm || busy) return;
    convertMutation.mutate(
      { customerType, reason: approvalReason },
      { onSuccess: () => setDone(true) },
    );
  };

  const handleLinkExisting = () => {
    if (!duplicate || busy) return;
    linkMutation.mutate(
      { customerId: duplicate.customerId, reason: approvalReason },
      { onSuccess: () => setDone(true) },
    );
  };

  // ── Success state ──────────────────────────────────────────────────────────
  // Either route ends here, and both return the same { customerId, lead } shape — so the
  // follow-up actions (create a deal for this customer) work identically after a link.
  if (done) {
    const result = (linkMutation.data ?? convertMutation.data)?.data;
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
        <QuickActionPanel
          lead={lead}
          customerId={result?.customerId ?? ""}
          linked={Boolean(linkMutation.data)}
          onClose={onClose}
        />
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-95 slide-in-from-bottom-4 duration-300">

        {/* Header */}
        <div className="relative shrink-0 bg-linear-to-br from-emerald-500 via-emerald-600 to-teal-700 px-5 py-4">
          <div className="absolute inset-0 opacity-10"
            style={{ backgroundImage: "radial-gradient(circle at 80% 50%, white 1px, transparent 1px)", backgroundSize: "20px 20px" }} />
          <div className="relative flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <UserPlus className="size-4 text-emerald-200" />
                <span className="text-emerald-200 text-xs font-semibold uppercase tracking-widest">Convert to Customer</span>
              </div>
              <h2 className="text-lg font-extrabold text-white">{lead.fullName}</h2>
              <p className="text-emerald-200 text-xs mt-0.5">Create an official customer profile from this lead</p>
            </div>
            <button onClick={onClose}
              className="p-1.5 rounded-full text-emerald-200 hover:bg-white/20 transition shrink-0">
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* Body */}
        <div className="p-5 flex-1 overflow-y-auto">

          {/* Conversion eligibility */}
          {isQualified ? (
            <div className="flex items-center gap-3 px-4 py-2.5 mb-4 bg-emerald-50 border border-emerald-200 rounded-xl">
              <ShieldCheck className="size-4 text-emerald-500 shrink-0" />
              <p className="text-xs text-emerald-700 font-medium">
                This lead is qualified — you can convert it into a customer right now.
              </p>
            </div>
          ) : canOverride ? (
            <div className="mb-5">
              <div className="flex items-start gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl">
                <ShieldAlert className="size-4 text-amber-500 shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-semibold text-amber-700">Manager override</p>
                  <p className="text-xs text-amber-600 mt-0.5">
                    This lead is <strong>{lead.status}</strong>, not yet Qualified. As a manager you may approve
                    an exception — record the reason below to enable conversion.
                  </p>
                </div>
              </div>
              <label className="block mt-3 text-xs font-semibold text-slate-700">Approval reason <span className="text-rose-500">*</span></label>
              <textarea rows={2} value={reason} onChange={e => setReason(e.target.value)}
                placeholder="e.g. Walk-in guest with a confirmed booking — converting ahead of qualification."
                className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-amber-400 focus:outline-none focus:ring-2 focus:ring-amber-100 transition resize-none" />
            </div>
          ) : (
            <div className="flex items-start gap-3 px-4 py-3 mb-5 bg-amber-50 border border-amber-200 rounded-xl">
              <ShieldAlert className="size-4 text-amber-500 shrink-0 mt-0.5" />
              <div>
                <p className="text-xs font-semibold text-amber-700">Not yet eligible for conversion</p>
                <p className="text-xs text-amber-600 mt-0.5">
                  This lead is currently <strong>{lead.status}</strong>. It must reach the{" "}
                  <strong>Qualified</strong> status before conversion, or contact a Sales Manager for an exception approval.
                </p>
              </div>
            </div>
          )}

          {/* Read-only summary — nothing to fill in, just confirm the details already on the lead */}
          <div className="rounded-xl border border-slate-200 bg-slate-50/70 overflow-hidden">
            <div className="flex items-center gap-2 px-4 py-2.5 border-b border-slate-200 bg-white">
              <span className={`flex items-center justify-center size-8 rounded-lg ${isCorporate ? "bg-violet-100 text-violet-600" : "bg-blue-100 text-blue-600"}`}>
                {isCorporate ? <Building className="size-4" /> : <User className="size-4" />}
              </span>
              <div>
                <p className="text-sm font-bold text-slate-800">{isCorporate ? "Organization" : "Individual"}</p>
                <p className="text-[11px] text-slate-400">Customer type inherited from this lead</p>
              </div>
            </div>
            <dl className="divide-y divide-slate-100">
              {isCorporate && (
                <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                  <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Company</dt>
                  <dd className="text-sm text-slate-700 font-medium text-right">
                    {lead.companyName || <span className="text-slate-300">—</span>}
                  </dd>
                </div>
              )}
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Email</dt>
                <dd className="text-sm text-slate-700 font-medium text-right">
                  {lead.email || <span className="text-slate-300">—</span>}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Phone</dt>
                <dd className="text-sm text-slate-700 font-medium text-right">
                  {lead.phone || <span className="text-slate-300">—</span>}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Address</dt>
                <dd className="text-sm text-slate-700 font-medium text-right max-w-[60%]">
                  {lead.address || <span className="text-slate-300">—</span>}
                </dd>
              </div>
            </dl>
          </div>

          {/* E6 — already a customer. Shown instead of the plain error, because this refusal has
              a next step and the others do not. */}
          {duplicate && (
            <div className="mt-5 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl">
              <div className="flex items-start gap-3">
                <Users className="size-4 text-amber-500 shrink-0 mt-0.5" />
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-amber-800">This person is already a customer</p>
                  <p className="text-xs text-amber-700 mt-1">
                    A customer profile with the same {duplicate.field} already exists. Link this lead to
                    that profile to keep their history in one place, or cancel and check the lead’s details.
                  </p>
                </div>
              </div>
              {linkMutation.isError && (
                <p className="mt-2 text-xs text-rose-600">
                  {apiError(linkMutation.error)?.message || "Linking failed. Please try again."}
                </p>
              )}
            </div>
          )}

          {convertMutation.isError && !duplicate && (
            <div className="flex items-center gap-2 px-3 py-2.5 mt-5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-3.5 shrink-0" />
              {(apiStatus(convertMutation.error) ?? 0) >= 500
                ? "Server error — please contact your Admin."
                : apiError(convertMutation.error)?.message || "Conversion failed. Please try again."}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex gap-3 px-5 py-3.5 border-t border-slate-100 bg-slate-50/80 shrink-0">
          <button type="button" onClick={onClose}
            className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
            Cancel
          </button>
          {duplicate ? (
            <button type="button" onClick={handleLinkExisting} disabled={busy}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-amber-600 rounded-xl hover:bg-amber-700 disabled:opacity-60 disabled:cursor-not-allowed transition active:scale-95">
              {busy ? <Loader2 className="size-4 animate-spin" /> : <Link2 className="size-4" />}
              {busy ? "Linking…" : "Link to existing customer"}
            </button>
          ) : (
            <button type="button" onClick={handleConfirm} disabled={busy || !canConfirm}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-emerald-600 rounded-xl hover:bg-emerald-700 disabled:opacity-60 disabled:cursor-not-allowed transition active:scale-95">
              {busy ? <Loader2 className="size-4 animate-spin" /> : <BadgeCheck className="size-4" />}
              {busy ? "Processing…" : isQualified ? "Confirm Conversion" : "Approve & Convert"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
