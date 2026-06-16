"use client";

import React, { useState, useMemo } from "react";
import {
  Search, Plus, X, Handshake, Users, TrendingUp,
  DollarSign, Percent, Mail, Phone, Building2, ArrowUpRight,
  ChevronLeft, ChevronRight, Loader2, AlertCircle,
} from "lucide-react";
import Link from "next/link";
import { useLeads, useCreateLead } from "@/features/lead/hooks/use_leads";
import type { LeadStatus, CreateLeadPayload } from "@/services/lead_service";
import { mockDb } from "@/shared/mock/mockData";

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<LeadStatus, { label: string; dot: string; badge: string }> = {
  NEW:       { label: "New",       dot: "bg-sky-400",     badge: "bg-sky-50 text-sky-700 ring-sky-200" },
  CONTACTED: { label: "Contacted", dot: "bg-amber-400",   badge: "bg-amber-50 text-amber-700 ring-amber-200" },
  QUALIFIED: { label: "Qualified", dot: "bg-emerald-400", badge: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  CONVERTED: { label: "Converted", dot: "bg-violet-400",  badge: "bg-violet-50 text-violet-700 ring-violet-200" },
  LOST:      { label: "Lost",      dot: "bg-rose-400",    badge: "bg-rose-50 text-rose-700 ring-rose-200" },
};

const SOURCE_OPTIONS = [
  "Website Inquiry", "Referral", "Social Media", "Cold Call", "Walk-in", "Event",
];

const EMPTY_FORM: CreateLeadPayload = {
  fullName: "", email: "", phone: "", companyName: "", source: "Website Inquiry", notes: "",
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function Avatar({ name, size = "sm" }: { name: string | null; size?: "sm" | "md" }) {
  const initials = (name ?? "?").split(" ").map(p => p[0]).slice(0, 2).join("").toUpperCase();
  const colors = ["bg-blue-100 text-blue-700", "bg-violet-100 text-violet-700",
    "bg-emerald-100 text-emerald-700", "bg-amber-100 text-amber-700"];
  const color = colors[(name?.charCodeAt(0) ?? 0) % colors.length];
  return (
    <span className={`inline-flex items-center justify-center rounded-full font-bold ${color}
      ${size === "sm" ? "size-6 text-[9px]" : "size-8 text-xs"}`}>
      {initials}
    </span>
  );
}

function StatusBadge({ status }: { status: LeadStatus }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.NEW;
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ring-1 ring-inset ${cfg.badge}`}>
      <span className={`size-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function LeadListScreen() {
  const [search, setSearch]         = useState("");
  const [statusFilter, setStatus]   = useState("");
  const [sourceFilter, setSource]   = useState("");
  const [page, setPage]             = useState(0);
  const [drawerOpen, setDrawer]     = useState(false);
  const [form, setForm]             = useState<CreateLeadPayload>(EMPTY_FORM);

  const { data: resp, isLoading, isError } = useLeads({
    search: search || undefined,
    status: statusFilter || undefined,
    source: sourceFilter || undefined,
    page,
    size: 15,
  });

  const createMutation = useCreateLead();

  // Use API data when available, otherwise fall back to mock (mapped to Lead shape)
  const pageData  = resp?.data;
  const apiLeads  = pageData?.content ?? [];

  // Mock fallback mapped to same shape
  const mockLeads = useMemo(() => mockDb.leads.map((l: any) => ({
    leadId: l.id, fullName: l.name, email: l.email, phone: l.phone,
    companyName: l.company, source: l.source, status: (l.status?.toUpperCase() ?? "NEW") as LeadStatus,
    notes: l.notes, convertedAt: null, assignedUserId: null,
    assignedUserName: l.owner ?? null, createdById: null, createdByName: null,
    createdAt: l.createdAt, updatedAt: l.createdAt,
  })), []);

  const leads = apiLeads.length > 0 ? apiLeads : mockLeads;
  const totalPages = pageData?.totalPages ?? 1;
  const totalElements = pageData?.totalElements ?? leads.length;

  // Stats
  const active    = leads.filter(l => l.status !== "LOST" && l.status !== "lost");
  const qualified = leads.filter(l => l.status === "QUALIFIED").length;

  // Submit
  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.fullName.trim()) return;
    createMutation.mutate(form, {
      onSuccess: () => { setDrawer(false); setForm(EMPTY_FORM); },
    });
  };

  return (
    <div className="min-h-full space-y-6">

      {/* ── Hero header ── */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-blue-600 via-blue-700 to-indigo-800 p-6 shadow-lg">
        <div className="absolute inset-0 opacity-10"
          style={{ backgroundImage: "radial-gradient(circle at 70% 50%, white 1px, transparent 1px)", backgroundSize: "24px 24px" }} />
        <div className="relative flex flex-col sm:flex-row justify-between sm:items-start gap-4">
          <div>
            <p className="text-blue-200 text-xs font-semibold uppercase tracking-widest mb-1">Leadora CRM</p>
            <h1 className="text-2xl font-extrabold text-white tracking-tight">Leads Workspace</h1>
            <p className="text-blue-200 text-sm mt-1">Qualify hotel inquiries from first contact to deal</p>
          </div>
          <button
            onClick={() => setDrawer(true)}
            className="flex items-center gap-2 bg-white text-blue-700 font-bold text-sm px-4 py-2.5 rounded-xl shadow-md hover:bg-blue-50 transition-all active:scale-95 shrink-0"
          >
            <Plus className="size-4" /> New Lead
          </button>
        </div>

        {/* KPI row */}
        <div className="relative mt-6 grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { icon: Handshake, label: "Total Leads",    value: totalElements,                   color: "text-blue-200" },
            { icon: Users,     label: "Active",         value: active.length,                   color: "text-emerald-300" },
            { icon: TrendingUp,label: "Qualified",      value: qualified,                       color: "text-amber-300" },
            { icon: Percent,   label: "Qualify Rate",
              value: `${((qualified / (leads.length || 1)) * 100).toFixed(0)}%`,                color: "text-violet-300" },
          ].map(({ icon: Icon, label, value, color }) => (
            <div key={label} className="bg-white/10 backdrop-blur-sm rounded-xl p-3 border border-white/20">
              <Icon className={`size-4 ${color} mb-2`} />
              <p className="text-white font-extrabold text-xl leading-none">{value}</p>
              <p className="text-blue-200 text-[11px] font-medium mt-0.5">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* ── Filters ── */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px] max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
            <input
              type="text" placeholder="Search name, company, email…"
              value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
              className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none transition"
            />
          </div>

          <select value={statusFilter} onChange={e => { setStatus(e.target.value); setPage(0); }}
            className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
            <option value="">All Statuses</option>
            {(Object.keys(STATUS_CONFIG) as LeadStatus[]).map(s => (
              <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>
            ))}
          </select>

          <select value={sourceFilter} onChange={e => { setSource(e.target.value); setPage(0); }}
            className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
            <option value="">All Sources</option>
            {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
          </select>

          <p className="ml-auto text-xs text-slate-400 hidden sm:block">
            {leads.length} result{leads.length !== 1 && "s"}
          </p>
        </div>
      </div>

      {/* ── Table ── */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20 gap-2 text-slate-400">
            <Loader2 className="size-5 animate-spin" /> Loading leads…
          </div>
        ) : isError ? (
          <div className="flex items-center justify-center py-20 gap-2 text-rose-500">
            <AlertCircle className="size-5" /> Failed to load. Showing mock data.
          </div>
        ) : leads.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400">
            <Handshake className="size-10 mb-3 opacity-30" />
            <p className="text-sm font-medium">No leads found</p>
            <p className="text-xs mt-1">Try adjusting filters or create a new lead</p>
          </div>
        ) : (
          <>
            <table className="w-full text-left">
              <thead className="bg-slate-50 border-b border-slate-100">
                <tr>
                  {["Lead / Contact", "Company", "Reach", "Source", "Owner", "Status", "Created", ""].map(h => (
                    <th key={h} className="px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {leads.map(lead => (
                  <tr key={lead.leadId} className="group hover:bg-slate-50/70 transition-colors">
                    {/* Name */}
                    <td className="px-4 py-3.5">
                      <Link href={`/leads/${lead.leadId}`}
                        className="font-semibold text-sm text-slate-800 hover:text-blue-600 transition-colors line-clamp-1 group-hover:underline decoration-blue-300 underline-offset-2">
                        {lead.fullName}
                      </Link>
                      {lead.email && <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-1">{lead.email}</p>}
                    </td>
                    {/* Company */}
                    <td className="px-4 py-3.5">
                      <span className="flex items-center gap-1.5 text-xs text-slate-600">
                        {lead.companyName
                          ? <><Building2 className="size-3.5 text-slate-300 shrink-0" />{lead.companyName}</>
                          : <span className="text-slate-300 italic">—</span>}
                      </span>
                    </td>
                    {/* Reach */}
                    <td className="px-4 py-3.5">
                      <div className="flex flex-col gap-1">
                        {lead.phone && (
                          <a href={`tel:${lead.phone}`} className="flex items-center gap-1 text-[11px] text-slate-500 hover:text-blue-600 transition-colors">
                            <Phone className="size-3 text-slate-300" /> {lead.phone}
                          </a>
                        )}
                        {lead.email && (
                          <a href={`mailto:${lead.email}`} className="flex items-center gap-1 text-[11px] text-slate-500 hover:text-blue-600 transition-colors">
                            <Mail className="size-3 text-slate-300" /> {lead.email}
                          </a>
                        )}
                      </div>
                    </td>
                    {/* Source */}
                    <td className="px-4 py-3.5 text-xs text-slate-500 whitespace-nowrap">
                      {lead.source ?? <span className="text-slate-300 italic">—</span>}
                    </td>
                    {/* Owner */}
                    <td className="px-4 py-3.5">
                      {lead.assignedUserName ? (
                        <div className="flex items-center gap-2">
                          <Avatar name={lead.assignedUserName} />
                          <span className="text-xs text-slate-600 truncate max-w-[80px]">{lead.assignedUserName}</span>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-300 italic">Unassigned</span>
                      )}
                    </td>
                    {/* Status */}
                    <td className="px-4 py-3.5"><StatusBadge status={lead.status} /></td>
                    {/* Created */}
                    <td className="px-4 py-3.5 text-xs text-slate-400 whitespace-nowrap">
                      {lead.createdAt ? new Date(lead.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "2-digit" }) : "—"}
                    </td>
                    {/* Action */}
                    <td className="px-4 py-3.5">
                      <Link href={`/leads/${lead.leadId}`}
                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity hover:text-blue-800">
                        View <ArrowUpRight className="size-3" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50">
                <p className="text-xs text-slate-500">
                  Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
                </p>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                    className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                    <ChevronLeft className="size-3.5" />
                  </button>
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                    className="p-1.5 rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                    <ChevronRight className="size-3.5" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* ── Create Lead Drawer ── */}
      {drawerOpen && (
        <>
          <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40" onClick={() => setDrawer(false)} />
          <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl flex flex-col
            animate-in slide-in-from-right-4 duration-300 ease-out">

            {/* Drawer header */}
            <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100 bg-gradient-to-r from-blue-600 to-indigo-600">
              <div>
                <h2 className="text-base font-bold text-white">New Lead</h2>
                <p className="text-blue-200 text-xs mt-0.5">Capture a new guest inquiry or group booking</p>
              </div>
              <button onClick={() => setDrawer(false)}
                className="p-1.5 rounded-full text-blue-200 hover:bg-white/20 transition">
                <X className="size-4" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreate} className="flex-1 overflow-y-auto p-6 space-y-5">

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Full Name <span className="text-rose-500">*</span></label>
                <input required placeholder="e.g. Emily Chen"
                  value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Email</label>
                  <input type="email" placeholder="guest@mail.com"
                    value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700">Phone</label>
                  <input placeholder="+84 901 234 567"
                    value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Company / Organisation</label>
                <input placeholder="e.g. TechCorp Inc."
                  value={form.companyName} onChange={e => setForm(f => ({ ...f, companyName: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Source Channel</label>
                <select value={form.source} onChange={e => setForm(f => ({ ...f, source: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition cursor-pointer">
                  {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Notes</label>
                <textarea rows={4} placeholder="Describe the event, room-block size, catering needs, target dates…"
                  value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition resize-none" />
              </div>
            </form>

            {/* Drawer footer */}
            <div className="flex gap-3 px-6 py-4 border-t border-slate-100 bg-slate-50">
              <button type="button" onClick={() => setDrawer(false)}
                className="flex-1 px-4 py-2.5 text-sm font-semibold text-slate-600 bg-white border border-slate-200 rounded-xl hover:bg-slate-100 transition">
                Cancel
              </button>
              <button
                onClick={handleCreate}
                disabled={createMutation.isPending || !form.fullName.trim()}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-bold text-white bg-blue-600 rounded-xl hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed transition active:scale-95">
                {createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
                Create Lead
              </button>
            </div>
          </aside>
        </>
      )}
    </div>
  );
}
