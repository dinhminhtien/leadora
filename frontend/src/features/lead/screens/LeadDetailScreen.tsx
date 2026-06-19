"use client";

import React, { useState } from "react";
import {
  ChevronLeft, Mail, Phone, Building2, User, Calendar,
  FileText, Clock, MessageSquare, Sparkles, CheckCircle2,
  Circle, Edit3, X, Loader2, Save, AlertCircle, UserPlus,
  ArrowRight, ShieldCheck, ShieldAlert,
  BadgeCheck, Building, ServerCrash,
} from "lucide-react";
import Link from "next/link";
import { useLeadDetail, useUpdateLead, useConvertLead } from "@/features/lead/hooks/use_leads";
import type { Lead, LeadStatus, UpdateLeadPayload, CustomerType } from "@/services/lead_service";

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

const SOURCE_OPTIONS = [
  "Website Inquiry", "Referral", "Social Media", "Cold Call", "Walk-in", "Event",
];

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

// ── Convert Modal ─────────────────────────────────────────────────────────────

function ConvertModal({
  lead, onClose,
}: {
  lead: Lead; onClose: () => void;
}) {
  const convertMutation = useConvertLead(lead.leadId);
  const [done, setDone] = useState(false);

  const isQualified = lead.status === "QUALIFIED";
  const isCorporate = lead.isCorporate;
  const customerType: CustomerType = isCorporate ? "CORPORATE" : "INDIVIDUAL";

  // Confirmation only — every detail already lives on the lead (captured at create/edit time).
  const handleConfirm = () => {
    if (!isQualified) return;
    convertMutation.mutate(
      {
        customerType,
        fullName:    lead.fullName,
        email:       lead.email ?? "",
        phone:       lead.phone ?? "",
        companyName: lead.companyName ?? "",
        taxCode:     "",
        address:     lead.address ?? "",
      },
      {
        onSuccess: () => setDone(true),
        onError: () => {},
      },
    );
  };

  // ── Success state ──────────────────────────────────────────────────────────
  if (done) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
        <div className="bg-white rounded-3xl shadow-2xl w-full max-w-sm text-center p-10 animate-in zoom-in-95 duration-300">
          <div className="mx-auto mb-6 flex items-center justify-center size-20 rounded-full bg-emerald-100">
            <BadgeCheck className="size-10 text-emerald-500" />
          </div>
          <h2 className="text-xl font-extrabold text-slate-800 mb-2">Conversion successful!</h2>
          <p className="text-sm text-slate-500 mb-2">
            <strong className="text-slate-700">{lead.fullName}</strong> has been created as an official customer profile.
          </p>
          <p className="text-xs text-slate-400 mb-8">The original lead record is retained for historical lookup.</p>
          <button onClick={onClose}
            className="w-full py-3 text-sm font-bold text-white bg-emerald-600 rounded-xl hover:bg-emerald-700 transition active:scale-95">
            Done
          </button>
        </div>
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
                    {lead.companyName || <span className="text-rose-500 italic">Unknown</span>}
                  </dd>
                </div>
              )}
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Email</dt>
                <dd className="text-sm text-slate-700 font-medium text-right">
                  {lead.email || <span className="text-rose-500 italic">Unknown</span>}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Phone</dt>
                <dd className="text-sm text-slate-700 font-medium text-right">
                  {lead.phone || <span className="text-rose-500 italic">Unknown</span>}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">Address</dt>
                <dd className="text-sm text-slate-700 font-medium text-right max-w-[60%]">
                  {lead.address || <span className="text-rose-500 italic">Unknown</span>}
                </dd>
              </div>
            </dl>
          </div>

          {convertMutation.isError && (
            <div className="flex items-center gap-2 px-3 py-2.5 mt-5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-3.5 shrink-0" />
              {(convertMutation.error as any)?.response?.status >= 500
                ? "Server error — please contact your Admin."
                : (convertMutation.error as any)?.response?.data?.message || "Conversion failed. Please try again."}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex gap-3 px-5 py-3.5 border-t border-slate-100 bg-slate-50/80 shrink-0">
          <button type="button" onClick={onClose}
            className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
            Cancel
          </button>
          <button type="button" onClick={handleConfirm}
            disabled={convertMutation.isPending || !isQualified}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-emerald-600 rounded-xl hover:bg-emerald-700 disabled:opacity-60 disabled:cursor-not-allowed transition active:scale-95">
            {convertMutation.isPending
              ? <Loader2 className="size-4 animate-spin" />
              : <BadgeCheck className="size-4" />}
            {convertMutation.isPending ? "Processing…" : "Confirm Conversion"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function LeadDetailScreen({ leadId }: { leadId: string }) {
  const { data: resp, isLoading, isError } = useLeadDetail(leadId);
  const updateMutation = useUpdateLead(leadId);

  const [editOpen, setEditOpen]       = useState(false);
  const [editForm, setEditForm]       = useState<UpdateLeadPayload>({});
  const [editErrors, setEditErrors]   = useState<{ fullName?: string; email?: string; phone?: string; companyName?: string }>({});
  const [editServerErr, setEditServerErr] = useState("");
  const [convertOpen, setConvertOpen] = useState(false);
  const [logs, setLogs]               = useState<LogEntry[]>(MOCK_LOGS);
  const [logType, setLogType]         = useState<"call" | "email" | "meeting" | "note">("call");
  const [logText, setLogText]         = useState("");

  const lead = resp?.data;

  if (isLoading) return (
    <div className="flex items-center justify-center h-64 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Loading lead…
    </div>
  );

  if (isError || !lead) return (
    <div className="flex items-center justify-center h-64 gap-2 text-rose-500">
      <AlertCircle className="size-5" /> Lead not found.
    </div>
  );

  const openEdit = () => {
    setEditForm({
      fullName: lead.fullName, email: lead.email ?? "", phone: lead.phone ?? "",
      companyName: lead.companyName ?? "", address: lead.address ?? "", isCorporate: lead.isCorporate,
      source: lead.source ?? "", notes: lead.notes ?? "",
      status: lead.status,
    });
    setEditErrors({});
    setEditServerErr("");
    setEditOpen(true);
  };

  const validateEdit = (): boolean => {
    const errs: typeof editErrors = {};
    if (!editForm.fullName?.trim()) {
      errs.fullName = "Full name is required";
    } else if (/\d/.test(editForm.fullName)) {
      errs.fullName = "Full name cannot contain numbers";
    }
    if (editForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editForm.email)) {
      errs.email = "Invalid email format (must contain @)";
    }
    if (editForm.phone && !/^\d{10,11}$/.test((editForm.phone ?? "").replace(/\s/g, ""))) {
      errs.phone = "Phone number must be 10–11 digits";
    }
    if (editForm.isCorporate && !editForm.companyName?.trim()) {
      errs.companyName = "Company name is required for an organization";
    }
    setEditErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateEdit()) return;
    setEditServerErr("");
    updateMutation.mutate(editForm, {
      onSuccess: () => setEditOpen(false),
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

  const currentStepIdx = PIPELINE.findIndex(p => p.status === lead.status);
  const isLost      = lead.status === "LOST";
  const isConverted = lead.status === "CONVERTED";
  const canConvert  = !isLost && !isConverted;

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
                        ? "bg-blue-600 text-white border-blue-600 shadow-sm"
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
                  className="flex items-center gap-2 px-5 py-2 text-sm font-bold text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition active:scale-95">
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
        <>
          <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40" onClick={() => setEditOpen(false)} />
          <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 flex flex-col
            animate-in slide-in-from-right duration-300 ease-out">

            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Edit3 className="size-4.5 text-blue-600" />
                  Edit Lead
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">Update contact info and stage</p>
              </div>
              <button onClick={() => setEditOpen(false)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition">
                <X className="size-4.5" />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-6 space-y-5">

              {editServerErr && (
                <div className="flex items-center gap-2 px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
                  <ServerCrash className="size-4 shrink-0" />{editServerErr}
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Full Name <span className="text-rose-500">*</span></label>
                <input value={editForm.fullName ?? ""}
                  onChange={e => { setEditForm(f => ({ ...f, fullName: e.target.value })); setEditErrors(er => ({ ...er, fullName: undefined })); }}
                  className={`w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                    ${editErrors.fullName ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
                {editErrors.fullName && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{editErrors.fullName}</p>}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Email</label>
                  <input type="text" value={editForm.email ?? ""}
                    onChange={e => { setEditForm(f => ({ ...f, email: e.target.value })); setEditErrors(er => ({ ...er, email: undefined })); }}
                    className={`w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                      ${editErrors.email ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
                  {editErrors.email && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{editErrors.email}</p>}
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Phone Number</label>
                  <input value={editForm.phone ?? ""}
                    onChange={e => { setEditForm(f => ({ ...f, phone: e.target.value })); setEditErrors(er => ({ ...er, phone: undefined })); }}
                    className={`w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                      ${editErrors.phone ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
                  {editErrors.phone && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{editErrors.phone}</p>}
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Address</label>
                <input value={editForm.address ?? ""}
                  onChange={e => setEditForm(f => ({ ...f, address: e.target.value }))}
                  placeholder="e.g. 12 Nguyen Hue, District 1, HCMC"
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Source Channel</label>
                  <select value={editForm.source ?? ""}
                    onChange={e => setEditForm(f => ({ ...f, source: e.target.value }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition cursor-pointer">
                    <option value="">— Select —</option>
                    {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Status</label>
                  <select value={editForm.status ?? ""}
                    disabled={isConverted}
                    onChange={e => setEditForm(f => ({ ...f, status: e.target.value as LeadStatus }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition cursor-pointer disabled:bg-slate-100 disabled:text-slate-400 disabled:cursor-not-allowed">
                    {allowedStatusOptions(lead.status).map(s => (
                      <option key={s} value={s}>{STATUS_LABEL[s]}</option>
                    ))}
                  </select>
                  <p className="text-[10px] text-slate-400">
                    {isConverted
                      ? "This lead has been converted to a customer — its status is locked."
                      : "Status only moves forward (New → Contacted → Qualified). “Converted” is set automatically during conversion."}
                  </p>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Notes</label>
                <textarea rows={4} value={editForm.notes ?? ""}
                  onChange={e => setEditForm(f => ({ ...f, notes: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition resize-none" />
              </div>

              {/* Customer Type at the bottom; Organization reveals a required company field. */}
              <div className="space-y-1.5 pt-1 border-t border-slate-100">
                <label className="text-xs font-semibold text-slate-700 pt-3 block">Customer Type</label>
                <div className="grid grid-cols-2 gap-2">
                  {([["individual", false, User], ["corporate", true, Building]] as const).map(([val, corp, Icon]) => {
                    const selected = !!editForm.isCorporate === corp;
                    return (
                      <button key={val} type="button"
                        onClick={() => setEditForm(f => ({ ...f, isCorporate: corp, ...(corp ? {} : { companyName: "" }) }))}
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

              {editForm.isCorporate && (
                <div className="space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200">
                  <label className="text-xs font-semibold text-slate-700">Company / Organization <span className="text-rose-500">*</span></label>
                  <input value={editForm.companyName ?? ""}
                    onChange={e => { setEditForm(f => ({ ...f, companyName: e.target.value })); setEditErrors(er => ({ ...er, companyName: undefined })); }}
                    placeholder="e.g. TechCorp Inc."
                    className={`w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 transition
                      ${editErrors.companyName ? "border-rose-300 focus:border-rose-400 focus:ring-rose-100" : "border-slate-200 focus:border-blue-400 focus:ring-blue-100"}`} />
                  {editErrors.companyName && <p className="text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{editErrors.companyName}</p>}
                </div>
              )}
            </form>

            <div className="flex gap-3 px-6 py-4 border-t border-slate-100 bg-slate-50">
              <button type="button" onClick={() => setEditOpen(false)}
                className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
                Cancel
              </button>
              <button onClick={handleSave} disabled={updateMutation.isPending}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-blue-600 rounded-xl hover:bg-blue-700 disabled:opacity-60 transition active:scale-95">
                {updateMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                Save Changes
              </button>
            </div>
          </aside>
        </>
      )}
    </div>
  );
}
