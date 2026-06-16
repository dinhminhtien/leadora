"use client";

import React, { useState } from "react";
import {
  ChevronLeft, Mail, Phone, Building2, User, Calendar,
  FileText, Clock, MessageSquare, Sparkles, CheckCircle2,
  Circle, Edit3, X, Loader2, Save, AlertCircle, TrendingUp,
  ArrowRight,
} from "lucide-react";
import Link from "next/link";
import { useLeadDetail, useUpdateLead } from "@/features/lead/hooks/use_leads";
import type { Lead, LeadStatus, UpdateLeadPayload } from "@/services/lead_service";
import { mockDb } from "@/shared/mock/mockData";

// ── Status pipeline config ────────────────────────────────────────────────────

const PIPELINE: { status: LeadStatus; label: string; color: string; bg: string }[] = [
  { status: "NEW",       label: "New",       color: "text-sky-600",     bg: "bg-sky-50 border-sky-200" },
  { status: "CONTACTED", label: "Contacted", color: "text-amber-600",   bg: "bg-amber-50 border-amber-200" },
  { status: "QUALIFIED", label: "Qualified", color: "text-emerald-600", bg: "bg-emerald-50 border-emerald-200" },
  { status: "CONVERTED", label: "Converted", color: "text-violet-600",  bg: "bg-violet-50 border-violet-200" },
];

const STATUS_BADGE: Record<LeadStatus, string> = {
  NEW:       "bg-sky-50 text-sky-700 ring-sky-200",
  CONTACTED: "bg-amber-50 text-amber-700 ring-amber-200",
  QUALIFIED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  CONVERTED: "bg-violet-50 text-violet-700 ring-violet-200",
  LOST:      "bg-rose-50 text-rose-700 ring-rose-200",
};

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

function InfoRow({ icon: Icon, label, children }: { icon: any; label: string; children: React.ReactNode }) {
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
  const map: Record<string, { icon: any; color: string }> = {
    call:    { icon: Phone,          color: "text-blue-500 bg-blue-50 border-blue-100" },
    email:   { icon: Mail,           color: "text-emerald-500 bg-emerald-50 border-emerald-100" },
    meeting: { icon: Calendar,       color: "text-violet-500 bg-violet-50 border-violet-100" },
    note:    { icon: FileText,       color: "text-amber-500 bg-amber-50 border-amber-100" },
  };
  const { icon: Icon, color } = map[type] ?? map.note;
  return (
    <span className={`flex items-center justify-center size-8 rounded-full border ${color} shrink-0`}>
      <Icon className="size-3.5" />
    </span>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function LeadDetailScreen({ leadId }: { leadId: string }) {
  const { data: resp, isLoading, isError } = useLeadDetail(leadId);
  const updateMutation = useUpdateLead(leadId);

  const [editOpen, setEditOpen]   = useState(false);
  const [editForm, setEditForm]   = useState<UpdateLeadPayload>({});
  const [logs, setLogs]           = useState<LogEntry[]>(MOCK_LOGS);
  const [logType, setLogType]     = useState<"call" | "email" | "meeting" | "note">("call");
  const [logText, setLogText]     = useState("");

  // Real data or mock fallback
  const apiLead = resp?.data;
  const mockRaw = mockDb.leads[0] as any;
  const lead: Lead = apiLead ?? {
    leadId, fullName: mockRaw.name, email: mockRaw.email, phone: mockRaw.phone,
    companyName: mockRaw.company, source: mockRaw.source, notes: mockRaw.notes,
    status: (mockRaw.status?.toUpperCase() ?? "NEW") as LeadStatus,
    convertedAt: null, assignedUserId: null, assignedUserName: mockRaw.owner ?? null,
    createdById: null, createdByName: null, createdAt: mockRaw.createdAt, updatedAt: mockRaw.createdAt,
  };

  const openEdit = () => {
    setEditForm({
      fullName: lead.fullName, email: lead.email ?? "", phone: lead.phone ?? "",
      companyName: lead.companyName ?? "", source: lead.source ?? "", notes: lead.notes ?? "",
      status: lead.status,
    });
    setEditOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate(editForm, { onSuccess: () => setEditOpen(false) });
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
  const isLost = lead.status === "LOST";

  if (isLoading) return (
    <div className="flex items-center justify-center h-64 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Loading lead…
    </div>
  );

  return (
    <div className="space-y-6">

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
              {isError && <span className="text-[10px] text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">Using mock data</span>}
            </div>
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">{lead.fullName}</h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {lead.status !== "CONVERTED" && lead.status !== "LOST" && (
            <button onClick={() => updateMutation.mutate({ status: "CONVERTED" })}
              className="flex items-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-emerald-600 rounded-xl hover:bg-emerald-700 transition active:scale-95 shadow-sm">
              <TrendingUp className="size-4" /> Convert to Deal
            </button>
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
                        current ? `border-blue-500 bg-blue-50` : "bg-slate-50 border-slate-200"}`}>
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
            <div className="bg-gradient-to-br from-slate-700 to-slate-800 px-4 py-5 flex items-center gap-3">
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
              <InfoRow icon={User} label="Assigned To">
                {lead.assignedUserName ?? <span className="text-slate-400 italic text-xs">Unassigned</span>}
              </InfoRow>
            </div>
          </div>

          {/* Deal metadata */}
          <div className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-1">
            <InfoRow icon={Sparkles} label="Source Channel">
              {lead.source ?? <span className="text-slate-400 italic text-xs">—</span>}
            </InfoRow>
            <InfoRow icon={Calendar} label="Created">
              {lead.createdAt ? new Date(lead.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" }) : "—"}
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
          <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl flex flex-col
            animate-in slide-in-from-right-4 duration-300 ease-out">

            <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100 bg-gradient-to-r from-slate-700 to-slate-800">
              <div>
                <h2 className="text-base font-bold text-white">Edit Lead</h2>
                <p className="text-slate-300 text-xs mt-0.5">Update contact info and stage</p>
              </div>
              <button onClick={() => setEditOpen(false)}
                className="p-1.5 rounded-full text-slate-300 hover:bg-white/20 transition">
                <X className="size-4" />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-6 space-y-5">

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Full Name <span className="text-rose-500">*</span></label>
                <input required value={editForm.fullName ?? ""}
                  onChange={e => setEditForm(f => ({ ...f, fullName: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Email</label>
                  <input type="email" value={editForm.email ?? ""}
                    onChange={e => setEditForm(f => ({ ...f, email: e.target.value }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Phone</label>
                  <input value={editForm.phone ?? ""}
                    onChange={e => setEditForm(f => ({ ...f, phone: e.target.value }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Company</label>
                <input value={editForm.companyName ?? ""}
                  onChange={e => setEditForm(f => ({ ...f, companyName: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Source</label>
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
                    onChange={e => setEditForm(f => ({ ...f, status: e.target.value as LeadStatus }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition cursor-pointer">
                    {(["NEW","CONTACTED","QUALIFIED","CONVERTED","LOST"] as LeadStatus[]).map(s => (
                      <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Notes</label>
                <textarea rows={4} value={editForm.notes ?? ""}
                  onChange={e => setEditForm(f => ({ ...f, notes: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition resize-none" />
              </div>
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
