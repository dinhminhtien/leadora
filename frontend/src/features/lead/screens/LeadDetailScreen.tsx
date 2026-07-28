"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  ChevronLeft, Mail, Phone, Building2, User, Calendar,
  FileText, Clock, MessageSquare, Sparkles, CheckCircle2,
  Circle, Edit3, X, Loader2, AlertCircle, UserPlus,
  ArrowRight, ShieldCheck, ShieldAlert, Lock,
  BadgeCheck, Building, ServerCrash, Briefcase,
  Users, Link2,
} from "lucide-react";
import { isAxiosError } from "axios";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ApiErrorResponse } from "@/services/api_client";
import {
  useLeadDetail, useUpdateLead, useConvertLead, useLinkLeadToCustomer,
} from "@/features/lead/hooks/use_leads";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { dealService } from "@/services/deal_service";
import type { UserSummary } from "@/services/follow_up_task_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import type { Lead, LeadStatus, UpdateLeadPayload, CustomerType } from "@/services/lead_service";
import {
  LeadEditDrawer, validateLeadForm, seedLeadForm,
} from "@/features/lead/components/LeadEditDrawer";

// ── Status pipeline config ────────────────────────────────────────────────────

const PIPELINE: { status: LeadStatus; label: string }[] = [
  { status: "NEW",       label: "New" },
  { status: "CONTACTED", label: "Contacted" },
  { status: "QUALIFIED", label: "Qualified" },
  { status: "CONVERTED", label: "Converted" },
];

const STATUS_BADGE: Record<LeadStatus, string> = {
  NEW:       "bg-sky-50 text-sky-700 ring-sky-200",
  CONTACTED: "bg-amber-50 text-amber-700 ring-amber-200",
  QUALIFIED: "bg-teal-50 text-teal-700 ring-teal-200",
  CONVERTED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  LOST:      "bg-rose-50 text-rose-700 ring-rose-200",
};

const STATUS_LABEL: Record<LeadStatus, string> = {
  NEW: "New", CONTACTED: "Contacted", QUALIFIED: "Qualified", CONVERTED: "Converted", LOST: "Lost",
};

// One-directional flow: New → Contacted → Qualified. No skipping, no going back.
// "Converted" is reached only through the conversion flow, never via the status dropdown.
const NEXT_STATUS: Record<LeadStatus, LeadStatus | null> = {
  NEW:       "CONTACTED",
  CONTACTED: "QUALIFIED",
  QUALIFIED: null,
  CONVERTED: null,
  LOST:      null,
};

// Allowed status choices when editing: the current stage, the single next stage,
// and Lost (an active lead can always be marked Lost). Converted is locked.
function allowedStatusOptions(current: LeadStatus): LeadStatus[] {
  if (current === "CONVERTED") return ["CONVERTED"];
  const opts: LeadStatus[] = [current];
  const next = NEXT_STATUS[current];
  if (next) opts.push(next);
  if (current !== "LOST") opts.push("LOST");
  return opts;
}

// ── Mock interaction data ─────────────────────────────────────────────────────

const MOCK_LOGS = [
  { id: "i1", type: "call",    date: "2026-06-14 10:30", agent: "John Doe",  notes: "Discussed room-block requirements. Guest is interested in 30 rooms for 3 nights." },
  { id: "i2", type: "email",   date: "2026-06-12 14:15", agent: "Jane Smith",notes: "Sent initial proposal with catering packages. Awaiting response." },
  { id: "i3", type: "meeting", date: "2026-06-10 09:00", agent: "John Doe",  notes: "Site visit conducted. Guest was impressed with the Grand Ballroom." },
];

type LogEntry = { id: string; type: string; date: string; agent: string; notes: string };

// ── Sub-components ────────────────────────────────────────────────────────────

function InfoRow({ icon: Icon, label, children }: { icon: React.ElementType; label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-3 py-3 border-b border-slate-50 last:border-0">
      <span className="mt-0.5 p-1.5 bg-slate-50 rounded-lg">
        <Icon className="size-3.5 text-slate-400" />
      </span>
      <div>
        <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">{label}</p>
        <div className="text-sm text-slate-700 font-medium mt-0.5">{children}</div>
      </div>
    </div>
  );
}

function LogIcon({ type }: { type: string }) {
  const map: Record<string, { icon: React.ElementType; color: string }> = {
    call:    { icon: Phone,    color: "text-blue-500 bg-blue-50 border-blue-100" },
    email:   { icon: Mail,    color: "text-emerald-500 bg-emerald-50 border-emerald-100" },
    meeting: { icon: Calendar, color: "text-violet-500 bg-violet-50 border-violet-100" },
    note:    { icon: FileText, color: "text-amber-500 bg-amber-50 border-amber-100" },
  };
  const { icon: Icon, color } = map[type] ?? map.note;
  return (
    <span className={`flex items-center justify-center size-8 rounded-full border ${color} shrink-0`}>
      <Icon className="size-3.5" />
    </span>
  );
}

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

function ConvertModal({
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

// ── Main component ────────────────────────────────────────────────────────────

export function LeadDetailScreen({ leadId, editMode = false }: { leadId: string; editMode?: boolean }) {
  const router = useRouter();
  const { data: resp, isLoading, isError, error } = useLeadDetail(leadId);
  const updateMutation = useUpdateLead(leadId);

  // Managers may (re)assign a lead to a sales staff member; staff cannot.
  const role = getUserRole(useAuthStore(s => s.user));
  const canAssign = role === "MANAGER";
  const { data: usersResp } = useUsers();
  const salesUsers: UserSummary[] = (usersResp?.data ?? []).filter(
    u => (u.roleName ?? "").toUpperCase() === "SALES",
  );

  const [editOpen, setEditOpen]       = useState(false);
  const [editForm, setEditForm]       = useState<UpdateLeadPayload>({});
  const [editErrors, setEditErrors]   = useState<{ fullName?: string; email?: string; phone?: string; companyName?: string }>({});
  const [editServerErr, setEditServerErr] = useState("");
  const [convertOpen, setConvertOpen] = useState(false);
  const [logs, setLogs]               = useState<LogEntry[]>(MOCK_LOGS);
  const [logType, setLogType]         = useState<"call" | "email" | "meeting" | "note">("call");
  const [logText, setLogText]         = useState("");

  const lead = resp?.data;

  const isStaff = role === "SALES";
  // A lead with no assignee is a "draft" — used below to hide Convert in the full detail.
  const isUnassigned = !lead?.assignedUserId;
  // EDIT-ONLY mode is driven by the navigation context, NOT the assignment field:
  // the list links "Created by me" rows to ?mode=edit, while "Assigned to me" rows go
  // to the plain detail URL. This is necessary because a staff-created lead can also be
  // assigned to that same staff (assigned_user_id == created_by), so the assignment
  // field alone can't tell the two views apart. Restricted = staff arriving via that link.
  const restricted = isStaff && editMode;
  const seededRef = useRef(false);

  // Seed the edit form from the lead. Declared before the early returns so both the
  // restricted-mode effect and the normal "Edit" button can use it.
  const seedEditForm = () => {
    if (!lead) return;
    setEditForm(seedLeadForm(lead));
    setEditErrors({});
    setEditServerErr("");
  };
  const openEdit = () => { seedEditForm(); setEditOpen(true); };

  // In restricted mode the edit form IS the whole page, so seed it once the lead loads.
  useEffect(() => {
    if (restricted && lead && !seededRef.current) {
      seededRef.current = true;
      seedEditForm();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restricted, lead]);

  if (isLoading) return (
    <div className="flex items-center justify-center h-64 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Loading lead…
    </div>
  );

  // Distinguish "no permission" (403) from "doesn't exist" (404 / malformed id) so the
  // user gets a clear Access Denied screen instead of an indefinite loading state.
  if (isError || !lead) {
    const status = (error as any)?.response?.status;
    if (status === 403) {
      return (
        <div className="flex flex-col items-center justify-center h-[60vh] text-center px-6">
          <div className="flex items-center justify-center size-16 rounded-full bg-rose-50 mb-5">
            <Lock className="size-7 text-rose-500" />
          </div>
          <h2 className="text-lg font-bold text-slate-800">Access Denied</h2>
          <p className="text-sm text-slate-500 mt-1.5 max-w-sm">
            You don’t have permission to view this lead. It may belong to another sales
            representative. Contact your Sales Manager if you believe this is a mistake.
          </p>
          <Link href="/leads"
            className="mt-6 inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-primary rounded-xl hover:bg-primary/90 transition">
            <ChevronLeft className="size-4" /> Back to Leads
          </Link>
        </div>
      );
    }
    return (
      <div className="flex flex-col items-center justify-center h-[60vh] text-center px-6">
        <div className="flex items-center justify-center size-16 rounded-full bg-slate-100 mb-5">
          <AlertCircle className="size-7 text-slate-400" />
        </div>
        <h2 className="text-lg font-bold text-slate-800">Lead not found</h2>
        <p className="text-sm text-slate-500 mt-1.5 max-w-sm">
          This lead doesn’t exist or may have been removed.
        </p>
        <Link href="/leads"
          className="mt-6 inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-primary rounded-xl hover:bg-primary/90 transition">
          <ChevronLeft className="size-4" /> Back to Leads
        </Link>
      </div>
    );
  }

  const validateEdit = (): boolean => {
    const errs = validateLeadForm(editForm);
    setEditErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateEdit()) return;
    setEditServerErr("");
    // Empty assignee ("") → undefined: the backend field is a UUID (an empty string
    // would fail to deserialize), and a null/absent value leaves the assignment unchanged.
    const payload: UpdateLeadPayload = { ...editForm, assignedUserId: editForm.assignedUserId || undefined };
    updateMutation.mutate(payload, {
      onSuccess: () => {
        // Restricted mode has no detail page to return to — go back to the list.
        if (restricted) router.push("/leads");
        else setEditOpen(false);
      },
      onError: (err: any) => {
        if (err?.response?.status >= 500) {
          setEditServerErr("Server error — please contact your Admin.");
        } else {
          setEditServerErr(err?.response?.data?.message || "Update failed. Please try again.");
        }
      },
    });
  };

  const handleLog = (e: React.FormEvent) => {
    e.preventDefault();
    if (!logText.trim()) return;
    setLogs(prev => [{
      id: `log-${Date.now()}`, type: logType, notes: logText,
      date: new Date().toISOString().slice(0, 16).replace("T", " "), agent: "You",
    }, ...prev]);
    setLogText("");
  };

  // ── Restricted view: a lead this staff member created but does not own ────────
  // Rendered as the same slide-over the rest of the app edits leads in, minus Status: an
  // unassigned lead cannot change status at all (BR-06, enforced server-side), so a locked
  // dropdown here would only offer a click the server then refuses. Reached by direct URL —
  // the Leads list opens this drawer in place rather than navigating.
  if (restricted) {
    return (
      <LeadEditDrawer
        form={editForm}
        errors={editErrors}
        serverError={editServerErr}
        saving={updateMutation.isPending}
        subtitle="Update contact info"
        notice={
          <div className="flex items-start gap-2.5 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800">
            <AlertCircle className="size-4 shrink-0 mt-0.5 text-amber-500" />
            <p>This lead hasn’t been assigned yet. You can edit its details; a Manager must
              assign it to a sales rep before it can change status or be converted.</p>
          </div>
        }
        onChange={patch => {
          setEditForm(f => ({ ...f, ...patch }));
          setEditErrors(prev => {
            const next = { ...prev };
            for (const key of Object.keys(patch)) delete next[key as keyof typeof next];
            return next;
          });
        }}
        onSubmit={handleSave}
        onClose={() => router.push("/leads")}
      />
    );
  }

  const currentStepIdx = PIPELINE.findIndex(p => p.status === lead.status);
  const isLost      = lead.status === "LOST";
  const isConverted = lead.status === "CONVERTED";

  // BR-05 — what a lead still needs before it can sit in active follow-up.
  // The status control already refuses two impossible moves up front (a converted lead, an
  // unassigned one) and explains why. This was the third precondition and the only one left to
  // fail on the server: the user changed the status, saved, and got a 422 that pointed at no
  // field. Read from editForm so it clears the moment they type into the boxes above.
  //
  // Names kept short because they are printed inside the <option> itself. The hint below the
  // select is not enough on its own: opening the dropdown covers it with the option list, so at
  // the exact moment the user is asking "why can't I pick this?" the answer is behind the menu.
  const missingForFollowUp = [
    !editForm.phone?.trim() && !editForm.email?.trim() ? "phone or email" : null,
    !editForm.source?.trim() ? "source" : null,
    !editForm.interestedService?.trim() ? "interested service" : null,
  ].filter(Boolean) as string[];

  const statusLocked = isConverted || !editForm.assignedUserId;
  // Convert is unavailable until the lead is assigned (a Manager must assign it first)
  // and only for active, non-terminal leads.
  const canConvert  = !isLost && !isConverted && !isUnassigned;

  return (
    <div className="space-y-6">

      {/* ── Convert Modal ── */}
      {convertOpen && <ConvertModal lead={lead} onClose={() => setConvertOpen(false)} />}

      {/* ── Top bar ── */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div className="flex items-center gap-3">
          <Link href="/leads"
            className="p-2 rounded-xl hover:bg-slate-100 text-slate-500 hover:text-slate-800 transition border border-slate-200">
            <ChevronLeft className="size-4" />
          </Link>
          <div>
            <div className="flex items-center gap-2 mb-0.5">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Lead Detail</span>
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ring-1 ring-inset ${STATUS_BADGE[lead.status]}`}>
                {lead.status.charAt(0) + lead.status.slice(1).toLowerCase()}
              </span>
            </div>
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">{lead.fullName}</h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {canConvert && (
            <button onClick={() => setConvertOpen(true)}
              className="flex items-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-emerald-600 rounded-xl hover:bg-emerald-700 transition active:scale-95 shadow-sm shadow-emerald-200">
              <UserPlus className="size-4" /> Convert to Customer
            </button>
          )}
          {isConverted && (
            <div className="flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl">
              <BadgeCheck className="size-4 text-emerald-500" /> Converted
            </div>
          )}
          <button onClick={openEdit}
            className="flex items-center gap-2 px-4 py-2.5 text-sm font-bold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition active:scale-95">
            <Edit3 className="size-4" /> Edit
          </button>
        </div>
      </div>

      {/* ── Status pipeline stepper ── */}
      {!isLost ? (
        <div className="bg-white rounded-xl border border-slate-100 shadow-sm px-6 py-4">
          <div className="flex items-center">
            {PIPELINE.map((step, idx) => {
              const done    = idx < currentStepIdx;
              const current = idx === currentStepIdx;
              return (
                <React.Fragment key={step.status}>
                  <div className="flex flex-col items-center gap-1.5 flex-1 first:items-start last:items-end">
                    <div className={`flex items-center justify-center size-7 rounded-full border-2 transition-all
                      ${done    ? "bg-emerald-500 border-emerald-500" :
                        current ? "border-blue-500 bg-blue-50" : "bg-slate-50 border-slate-200"}`}>
                      {done
                        ? <CheckCircle2 className="size-4 text-white" />
                        : current
                          ? <Circle className="size-3 fill-blue-500 text-blue-500" />
                          : <Circle className="size-3 text-slate-300" />}
                    </div>
                    <span className={`text-[10px] font-semibold hidden sm:block
                      ${done ? "text-emerald-600" : current ? "text-blue-600" : "text-slate-400"}`}>
                      {step.label}
                    </span>
                  </div>
                  {idx < PIPELINE.length - 1 && (
                    <div className={`h-0.5 flex-1 mx-1 rounded-full transition-all
                      ${idx < currentStepIdx ? "bg-emerald-400" : "bg-slate-100"}`} />
                  )}
                </React.Fragment>
              );
            })}
          </div>
        </div>
      ) : (
        <div className="bg-rose-50 border border-rose-200 rounded-xl px-4 py-3 flex items-center gap-3">
          <AlertCircle className="size-5 text-rose-500 shrink-0" />
          <p className="text-sm font-semibold text-rose-700">This lead has been marked as <strong>Lost</strong>.</p>
        </div>
      )}

      {/* ── Body grid ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Left: info panel */}
        <div className="space-y-5">

          {/* Profile card */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
            <div className="bg-linear-to-br from-slate-700 to-slate-800 px-4 py-5 flex items-center gap-3">
              <span className="flex items-center justify-center size-12 rounded-full bg-white/10 text-white font-extrabold text-lg">
                {lead.fullName.split(" ").map(p => p[0]).slice(0, 2).join("")}
              </span>
              <div>
                <p className="text-white font-bold text-base leading-tight">{lead.fullName}</p>
                {lead.companyName && <p className="text-slate-300 text-xs mt-0.5">{lead.companyName}</p>}
              </div>
            </div>
            <div className="px-4">
              {lead.email && (
                <InfoRow icon={Mail} label="Email">
                  <a href={`mailto:${lead.email}`} className="text-blue-600 hover:underline">{lead.email}</a>
                </InfoRow>
              )}
              {lead.phone && (
                <InfoRow icon={Phone} label="Phone">
                  <a href={`tel:${lead.phone}`} className="hover:text-blue-600">{lead.phone}</a>
                </InfoRow>
              )}
              {lead.companyName && (
                <InfoRow icon={Building2} label="Company"><span>{lead.companyName}</span></InfoRow>
              )}
              <InfoRow icon={lead.isCorporate ? Building : User} label="Customer Type">
                {lead.isCorporate ? "Organization" : "Individual"}
              </InfoRow>
              <InfoRow icon={User} label="Assigned To">
                {lead.assignedUserName ?? <span className="text-slate-400 italic text-xs">Unassigned</span>}
              </InfoRow>
            </div>
          </div>

          {/* Metadata */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-1">
            <InfoRow icon={Sparkles} label="Source Channel">
              {lead.source ?? <span className="text-slate-400 italic text-xs">—</span>}
            </InfoRow>
            <InfoRow icon={Sparkles} label="Interested Service">
              {lead.interestedService ?? <span className="text-slate-400 italic text-xs">—</span>}
            </InfoRow>
            <InfoRow icon={Calendar} label="Created">
              {lead.createdAt
                ? new Date(lead.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })
                : "—"}
            </InfoRow>
            {lead.convertedAt && (
              <InfoRow icon={CheckCircle2} label="Converted">
                {new Date(lead.convertedAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })}
              </InfoRow>
            )}
          </div>

          {/* Notes */}
          {lead.notes && (
            <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-4">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide mb-2">Inquiry Notes</p>
              <p className="text-sm text-slate-600 leading-relaxed bg-slate-50 rounded-lg p-3 border border-slate-100">
                {lead.notes}
              </p>
            </div>
          )}
        </div>

        {/* Right: activity */}
        <div className="lg:col-span-2 space-y-5">

          {/* Quick activity log */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-5">
            <div className="flex items-center gap-2 mb-4">
              <MessageSquare className="size-4 text-blue-500" />
              <h3 className="text-sm font-bold text-slate-800">Log Activity</h3>
            </div>

            <form onSubmit={handleLog} className="space-y-3">
              <div className="flex gap-2 flex-wrap">
                {(["call", "email", "meeting", "note"] as const).map(t => (
                  <button key={t} type="button" onClick={() => setLogType(t)}
                    className={`px-3.5 py-1.5 rounded-full text-xs font-semibold border transition
                      ${logType === t
                        ? "bg-primary text-white border-primary shadow-sm"
                        : "bg-white text-slate-600 border-slate-200 hover:border-slate-300 hover:bg-slate-50"}`}>
                    {t.charAt(0).toUpperCase() + t.slice(1)}
                  </button>
                ))}
              </div>
              <textarea rows={3} required value={logText} onChange={e => setLogText(e.target.value)}
                placeholder={`Summarise this ${logType}…`}
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition resize-none" />
              <div className="flex justify-end">
                <button type="submit"
                  className="flex items-center gap-2 px-5 py-2 text-sm font-bold text-white bg-primary rounded-lg hover:bg-primary/90 transition active:scale-95">
                  <ArrowRight className="size-4" /> Post
                </button>
              </div>
            </form>
          </div>

          {/* Timeline */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm p-5">
            <h3 className="text-sm font-bold text-slate-800 mb-5">Interaction Timeline</h3>
            {logs.length === 0 ? (
              <p className="text-center text-slate-400 text-sm py-8">No interactions yet.</p>
            ) : (
              <div className="relative space-y-5">
                <div className="absolute left-3.5 top-0 bottom-0 w-px bg-slate-100" />
                {logs.map(entry => (
                  <div key={entry.id} className="flex items-start gap-4 relative pl-1">
                    <LogIcon type={entry.type} />
                    <div className="flex-1 min-w-0 pt-0.5">
                      <div className="flex items-baseline justify-between gap-2 mb-1">
                        <span className="text-xs font-bold text-slate-700 capitalize">{entry.type} logged</span>
                        <span className="text-[10px] text-slate-400 flex items-center gap-1 shrink-0">
                          <Clock className="size-3" />{entry.date}
                        </span>
                      </div>
                      <p className="text-sm text-slate-600 leading-relaxed">{entry.notes}</p>
                      <p className="text-[10px] text-slate-400 mt-1.5 flex items-center gap-1">
                        <User className="size-3" />{entry.agent}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── Edit Lead Drawer ── */}
      {editOpen && (
        <LeadEditDrawer
          form={editForm}
          errors={editErrors}
          serverError={editServerErr}
          saving={updateMutation.isPending}
          onChange={patch => {
            setEditForm(f => ({ ...f, ...patch }));
            setEditErrors(prev => {
              const next = { ...prev };
              for (const key of Object.keys(patch)) delete next[key as keyof typeof next];
              return next;
            });
          }}
          onSubmit={handleSave}
          onClose={() => setEditOpen(false)}
          status={{
            options: allowedStatusOptions(lead.status),
            labels: STATUS_LABEL,
            locked: statusLocked,
            missingForFollowUp,
            hint: isConverted
              ? "This lead has been converted to a customer — its status is locked."
              : !editForm.assignedUserId
                ? "This lead must be assigned to a sales rep before its status can change."
                : missingForFollowUp.length
                  ? `To move this lead to Contacted or Qualified, fill in ${missingForFollowUp.join(", ")} in this form — the option unlocks as soon as you type. You can still mark it Lost.`
                  : "Status only moves forward (New → Contacted → Qualified). “Converted” is set automatically during conversion.",
          }}
          assign={canAssign ? { users: salesUsers } : undefined}
        />
      )}
    </div>
  );
}
