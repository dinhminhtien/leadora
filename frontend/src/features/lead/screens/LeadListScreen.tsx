"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Search, Plus, X, Handshake, Users, TrendingUp, Percent,
  Phone, Building2, User, ArrowUpRight, ChevronLeft, ChevronRight,
  Loader2, AlertCircle, AlertTriangle, SlidersHorizontal, CalendarDays, ArrowUpDown,
  ArrowUp, ArrowDown, ArrowDownWideNarrow, ChevronDown, ServerCrash,
  UserCheck, PenLine, UserCog,
} from "lucide-react";
import Link from "next/link";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { useLeads, useCreateLead } from "@/features/lead/hooks/use_leads";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import type { UserSummary } from "@/services/follow_up_task_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import type { LeadStatus, CreateLeadPayload } from "@/services/lead_service";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";

// ── Constants ─────────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<LeadStatus, { label: string; dot: string; badge: string }> = {
  NEW: { label: "New", dot: "bg-sky-400", badge: "bg-sky-50 text-sky-700 ring-sky-200" },
  CONTACTED: { label: "Contacted", dot: "bg-amber-400", badge: "bg-amber-50 text-amber-700 ring-amber-200" },
  QUALIFIED: { label: "Qualified", dot: "bg-teal-400", badge: "bg-teal-50 text-teal-700 ring-teal-200" },
  CONVERTED: { label: "Converted", dot: "bg-emerald-500", badge: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  LOST: { label: "Lost", dot: "bg-rose-400", badge: "bg-rose-50 text-rose-700 ring-rose-200" },
};

const SOURCE_OPTIONS = ["Website Inquiry", "Referral", "Social Media", "Cold Call", "Walk-in", "Event"];

const SORT_OPTIONS = [
  { value: "status_desc", label: "Status", icon: ArrowDownWideNarrow },
  { value: "createdAt_desc", label: "Newest", icon: ArrowDown },
  { value: "createdAt_asc", label: "Oldest", icon: ArrowUp },
  { value: "fullName_asc", label: "Name A → Z", icon: ArrowUpDown },
  { value: "fullName_desc", label: "Name Z → A", icon: ArrowUpDown },
];

const EMPTY_FORM: CreateLeadPayload = {
  fullName: "", email: "", phone: "", companyName: "", address: "", isCorporate: false, source: "Website Inquiry", notes: "",
};

// Lead type (individual vs corporate/organization) — `isCorporate` boolean on the lead.
const TYPE_OPTIONS = [
  { value: "individual", label: "Individual", isCorporate: false },
  { value: "corporate", label: "Organization", isCorporate: true },
] as const;

// Segmented toggle shown at the top-right of the list (All / Individual / Organization).
const TYPE_SEGMENTS: { value: string; label: string; icon?: React.ElementType }[] = [
  { value: "", label: "All" },
  { value: "individual", label: "Individual", icon: User },
  { value: "corporate", label: "Organization", icon: Building2 },
];

// ── Validation ────────────────────────────────────────────────────────────────

type FormErrors = { fullName?: string; email?: string; phone?: string; companyName?: string };

// Name: letters (any language, incl. Vietnamese), spaces and the few punctuation
// marks real names use (hyphen, apostrophe, period). Digits and other symbols are rejected.
const NAME_ALLOWED = /^[\p{L}\s.'-]+$/u;

function validateForm(f: CreateLeadPayload): FormErrors {
  const err: FormErrors = {};
  const name = f.fullName.trim();
  if (!name) {
    err.fullName = "Full name is required";
  } else if (/\d/.test(name)) {
    err.fullName = "Full name cannot contain numbers";
  } else if (!NAME_ALLOWED.test(name)) {
    err.fullName = "Full name cannot contain special characters";
  }
  if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) {
    err.email = "Invalid email format (e.g. name@domain.com)";
  }
  if (f.phone) {
    const digits = f.phone.replace(/\s/g, "");
    if (/[^\d]/.test(digits)) {
      err.phone = "Phone number can only contain digits (no letters or symbols)";
    } else if (!/^\d{10,11}$/.test(digits)) {
      err.phone = "Phone number must be 10–11 digits";
    }
  }
  // Organization leads must name the company; individuals don't need one.
  if (f.isCorporate && !f.companyName?.trim()) {
    err.companyName = "Company name is required for an organization";
  }
  return err;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function Avatar({ name }: { name: string | null }) {
  const initials = (name ?? "?").split(" ").map(p => p[0]).slice(0, 2).join("").toUpperCase();
  const colors = ["bg-blue-100 text-blue-700", "bg-violet-100 text-violet-700", "bg-emerald-100 text-emerald-700", "bg-amber-100 text-amber-700"];
  const color = colors[(name?.charCodeAt(0) ?? 0) % colors.length];
  return (
    <span className={`inline-flex items-center justify-center rounded-full font-bold size-7 text-[10px] ${color}`}>
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

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{msg}</p>;
}

// Missing/null information in the list is surfaced in red as "Unknown".
function Unknown() {
  return <span className="text-rose-500 italic font-medium">Unknown</span>;
}

// Caps a value at a fixed pixel width and clips the overflow with a CSS ellipsis ("…"),
// so even a long unbroken string (e.g. "wwwwwww…") can never push the table columns out
// of alignment — character count is irrelevant, only rendered width. Full value in tooltip.
function Truncate({ text, width = 150, className = "" }: { text: string; width?: number; className?: string }) {
  return (
    <span title={text} className={`block truncate ${className}`} style={{ maxWidth: width }}>
      {text}
    </span>
  );
}

function FilterChip({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-700 ring-1 ring-inset ring-blue-200">
      {label}
      <button type="button" onClick={onRemove}
        className="flex items-center justify-center size-3.5 rounded-full hover:bg-blue-200 transition">
        <X className="size-2.5" />
      </button>
    </span>
  );
}

// ── Create Lead Drawer ────────────────────────────────────────────────────────

function CreateLeadDrawer({ onClose, canAssign, users }: { onClose: () => void; canAssign: boolean; users: UserSummary[] }) {
  const [form, setForm] = useState<CreateLeadPayload>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [serverError, setServerError] = useState("");
  // A duplicate (same email/phone) is shown as a warning with a link to the existing lead,
  // not as a generic server error.
  const [duplicate, setDuplicate] = useState<{ message: string; leadId?: string } | null>(null);
  const createMutation = useCreateLead();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validateForm(form);
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setErrors({});
    setServerError("");
    setDuplicate(null);
    // Empty assignee ("") must go out as undefined — the backend field is a UUID
    // and an empty string would fail to deserialize.
    const payload: CreateLeadPayload = { ...form, assignedUserId: form.assignedUserId || undefined };
    createMutation.mutate(payload, {
      onSuccess: onClose,
      onError: (err: any) => {
        const data = err?.response?.data;
        const status = err?.response?.status;
        if (status === 409 || data?.errorCode === "DUPLICATE_LEAD") {
          setDuplicate({
            message: data?.message || "A lead with these contact details already exists.",
            leadId: data?.details || undefined,
          });
        } else if (status >= 500) {
          setServerError("Server error — please contact your Admin.");
        } else {
          setServerError(data?.message || "Failed to create lead. Please try again.");
        }
      },
    });
  };

  const field = (key: keyof CreateLeadPayload, value: string) => {
    setForm(f => ({ ...f, [key]: value }));
    if (errors[key as keyof FormErrors]) setErrors(e => ({ ...e, [key]: undefined }));
    // Editing a contact field clears a stale duplicate warning.
    if (duplicate && (key === "email" || key === "phone")) setDuplicate(null);
  };

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 flex flex-col animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <Plus className="size-4.5 text-blue-600" />
              Add New Lead
            </h3>
            <p className="text-[10px] text-slate-400 mt-0.5">Capture a new potential customer</p>
          </div>
          <button onClick={onClose}
            className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition">
            <X className="size-4.5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {serverError && (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-4 shrink-0" />{serverError}
            </div>
          )}

          {duplicate && (
            <div className="flex items-start gap-2.5 px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800">
              <AlertTriangle className="size-4 shrink-0 mt-0.5 text-amber-500" />
              <div className="space-y-1">
                <p className="font-semibold">Possible duplicate lead</p>
                <p className="text-amber-700">{duplicate.message}</p>
                {duplicate.leadId && (
                  <Link href={`/leads/${duplicate.leadId}`}
                    className="inline-flex items-center gap-1 font-semibold text-amber-900 underline underline-offset-2 hover:text-amber-950">
                    View the existing lead <ArrowUpRight className="size-3" />
                  </Link>
                )}
              </div>
            </div>
          )}

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Full Name *</label>
            <Input placeholder="e.g. John Smith" value={form.fullName}
              onChange={e => field("fullName", e.target.value)}
              error={errors.fullName}
              className="py-1.5 text-xs" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Email</label>
              <Input type="text" placeholder="example@gmail.com" value={form.email}
                onChange={e => field("email", e.target.value)}
                error={errors.email}
                className="py-1.5 text-xs" />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Phone Number</label>
              <Input placeholder="e.g. 09xxxxxxxx" value={form.phone}
                onChange={e => field("phone", e.target.value)}
                error={errors.phone}
                className="py-1.5 text-xs" />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Source Channel</label>
            <Select value={form.source} onChange={e => field("source", e.target.value)} className="py-1.5">
              {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
            </Select>
          </div>

          {/* Manager only: assign the new lead to a sales staff member.
              Staff leave this blank — their lead is created unassigned and shows under "Created by me". */}
          {canAssign && (
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600 flex items-center gap-1.5">
                <UserCog className="size-3.5 text-slate-400" /> Assign To
              </label>
              <Select value={form.assignedUserId ?? ""} onChange={e => field("assignedUserId", e.target.value)} className="py-1.5">
                <option value="">Unassigned</option>
                {users.map(u => <option key={u.userId} value={u.userId}>{u.fullName}</option>)}
              </Select>
            </div>
          )}

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Address</label>
            <Input placeholder="e.g. 12 Nguyen Hue, District 1, HCMC" value={form.address}
              onChange={e => field("address", e.target.value)}
              className="py-1.5 text-xs" />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Notes</label>
            <textarea rows={3} placeholder="Describe the requirement, event, room count…" value={form.notes}
              onChange={e => field("notes", e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3.5 text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none" />
          </div>

          {/* Customer Type sits at the very bottom; choosing Organization reveals a required company field. */}
          <div className="space-y-1.5 pt-1 border-t border-slate-100">
            <label className="text-xs font-semibold text-slate-600 pt-2 block">Customer Type</label>
            <div className="grid grid-cols-2 gap-2">
              {TYPE_OPTIONS.map(t => {
                const selected = form.isCorporate === t.isCorporate;
                const Icon = t.isCorporate ? Building2 : User;
                return (
                  <button key={t.value} type="button"
                    onClick={() => setForm(f => ({ ...f, isCorporate: t.isCorporate, ...(t.isCorporate ? {} : { companyName: "" }) }))}
                    className={`flex items-center gap-2 px-3 py-2.5 rounded-xl border text-xs font-semibold transition
                      ${selected
                        ? "border-blue-500 bg-blue-50 text-blue-700 shadow-sm"
                        : "border-slate-200 bg-white text-slate-500 hover:border-slate-300 hover:bg-slate-50"}`}>
                    <Icon className={`size-4 ${selected ? "text-blue-600" : "text-slate-400"}`} />
                    {t.label}
                  </button>
                );
              })}
            </div>
          </div>

          {form.isCorporate && (
            <div className="space-y-1 animate-in fade-in slide-in-from-top-1 duration-200">
              <label className="text-xs font-semibold text-slate-600">Company / Organization *</label>
              <Input placeholder="e.g. TechCorp Inc." value={form.companyName}
                onChange={e => field("companyName", e.target.value)}
                error={errors.companyName}
                className="py-1.5 text-xs" />
            </div>
          )}

          <div className="pt-4 flex gap-3 border-t border-slate-100">
            <Button type="submit" variant="primary" isLoading={createMutation.isPending}
              className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold">
              Create Lead
            </Button>
            <Button type="button" variant="outline" onClick={onClose}
              className="w-full border-slate-200 text-xs text-slate-600">
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </>
  );
}

// Fixed column widths (sum = 100%). Combined with `table-fixed` these keep the
// header and body columns aligned regardless of content length or which status
// tab is active — the layout no longer reflows when switching tabs.
const COL_WIDTHS = ["4%", "12%", "9%", "10%", "9%", "8%", "10%", "10%", "8%", "7%", "9%", "4%"];

// Rows per page. The table always renders this many row slots (real rows + invisible
// fillers) so its height — and therefore the pagination bar pinned below it — stays
// fixed regardless of how many leads the current page actually holds.
const PAGE_SIZE = 10;

// ── Lead Table ────────────────────────────────────────────────────────────────

function LeadTable({
  isLoading, isError, leads, totalPages, totalElements, page, onPageChange, onClearFilters, hasFilters, editMode,
}: {
  isLoading: boolean; isError: boolean;
  leads: any[]; totalPages: number; totalElements: number;
  page: number; onPageChange: (p: number) => void;
  onClearFilters: () => void; hasFilters: boolean;
  // When true (staff viewing "Created by me"), rows link to the edit-only screen.
  editMode: boolean;
}) {
  if (isLoading) return (
    <div className="flex items-center justify-center py-20 gap-2 text-slate-400">
      <Loader2 className="size-5 animate-spin" /> Loading…
    </div>
  );

  if (isError) return (
    <div className="flex flex-col items-center justify-center py-20 gap-2 text-rose-500">
      <ServerCrash className="size-8 mb-1" />
      <p className="text-sm font-semibold">Server error — please contact your Admin.</p>
    </div>
  );

  if (leads.length === 0) return (
    <div className="flex flex-col items-center justify-center py-20 text-slate-400">
      <Handshake className="size-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">No results found</p>
      {hasFilters && (
        <button onClick={onClearFilters} className="mt-2 text-xs text-blue-500 hover:underline">
          Clear filters
        </button>
      )}
    </div>
  );

  const pageStart = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, i) => pageStart + i);

  // "Created by me" rows open the edit-only screen; everything else opens full detail.
  const hrefFor = (id: string) => (editMode ? `/leads/${id}?mode=edit` : `/leads/${id}`);

  return (
    <>
      <Table className="table-fixed">
        <colgroup>
          {COL_WIDTHS.map((w, i) => <col key={i} style={{ width: w }} />)}
        </colgroup>
        <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
          <TableRow hoverable={false}>
            {[
              { label: "#", w: "w-10" },
              { label: "Name / Contact", w: "" },
              { label: "Type / Company", w: "" },
              { label: "Phone", w: "" },
              { label: "Address", w: "" },
              { label: "Source", w: "" },
              { label: "Owner", w: "" },
              { label: "Created by", w: "" },
              { label: "Status", w: "" },
              { label: "SLA", w: "" },
              { label: "Created", w: "" },
              { label: "", w: "w-8" },
            ].map((h, i) => (
              <TableHead key={h.label || `col-${i}`} className={`py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap ${h.w}`}>
                {h.label}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {leads.map((lead, idx) => (
            <TableRow key={lead.leadId}
              className="group hover:bg-blue-50/40 transition-colors border-b border-slate-100">
              <TableCell className="py-3 px-4 text-xs text-slate-400 font-mono border-b-0">
                {page * PAGE_SIZE + idx + 1}
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                <Link href={hrefFor(lead.leadId)}
                  className="block group-hover:underline decoration-blue-300 underline-offset-2">
                  <Truncate text={lead.fullName} width={130}
                    className="font-semibold text-sm text-slate-800 group-hover:text-blue-600 transition-colors" />
                </Link>
                <div className="text-[11px] mt-0.5">
                  {lead.email ? <Truncate text={lead.email} width={130} className="text-slate-400" /> : <Unknown />}
                </div>
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                <span className="flex items-center gap-1.5 text-xs text-slate-600" title={lead.isCorporate ? "Organization" : "Individual"}>
                  {lead.isCorporate
                    ? <Building2 className="size-3.5 text-slate-400 shrink-0" />
                    : <User className="size-3.5 text-slate-400 shrink-0" />}
                  {lead.isCorporate
                    ? (lead.companyName ? lead.companyName : <Unknown />)
                    : <span className="text-slate-400">Individual</span>}
                </span>
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                {lead.phone
                  ? <a href={`tel:${lead.phone}`} className="flex items-center gap-1 text-xs text-slate-500 hover:text-blue-600">
                    <Phone className="size-3 text-slate-300 shrink-0" />{lead.phone}
                  </a>
                  : <span className="text-xs"><Unknown /></span>}
              </TableCell>
              <TableCell className="py-3 px-4 text-xs text-slate-500 border-b-0">
                {lead.address ? <Truncate text={lead.address} width={120} /> : <Unknown />}
              </TableCell>
              <TableCell className="py-3 px-4 text-xs text-slate-500 whitespace-nowrap border-b-0">
                {lead.source ?? <Unknown />}
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                {lead.assignedUserName
                  ? <div className="flex items-center gap-2"><Avatar name={lead.assignedUserName} /><Truncate text={lead.assignedUserName} width={90} className="text-xs text-slate-600" /></div>
                  : <span className="text-xs text-slate-300 italic">Unassigned</span>}
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                {lead.createdByName
                  ? <div className="flex items-center gap-2"><Avatar name={lead.createdByName} /><Truncate text={lead.createdByName} width={90} className="text-xs text-slate-600" /></div>
                  : <span className="text-xs text-slate-300 italic">—</span>}
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0"><StatusBadge status={lead.status} /></TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                <SlaStatusBadge entityId={lead.leadId} entityType="LEAD" />
              </TableCell>
              <TableCell className="py-3 px-4 text-xs text-slate-400 whitespace-nowrap border-b-0">
                {lead.createdAt ? new Date(lead.createdAt).toLocaleDateString("en-US", { day: "2-digit", month: "short", year: "numeric" }) : "—"}
              </TableCell>
              <TableCell className="py-3 px-4 border-b-0">
                <Link href={hrefFor(lead.leadId)}
                  className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity hover:text-blue-800">
                  <ArrowUpRight className="size-3.5" />
                </Link>
              </TableCell>
            </TableRow>
          ))}
          {/* Invisible filler rows pad the body to PAGE_SIZE so the table height — and the
              pagination bar below — never shifts between a full page and a partial last page.
              The hidden two-line content mirrors a real Name/Contact cell's height exactly. */}
          {Array.from({ length: Math.max(0, PAGE_SIZE - leads.length) }).map((_, i) => (
            <TableRow key={`filler-${i}`} hoverable={false} className="border-b border-slate-100">
              <TableCell colSpan={COL_WIDTHS.length} className="py-3 px-4 border-b-0" aria-hidden="true">
                <span className="invisible block text-sm font-semibold">.</span>
                <span className="invisible block text-[11px] mt-0.5">.</span>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-5 py-3 border-t border-slate-100 bg-slate-50/50">
          <p className="text-xs text-slate-500">
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
            <span className="text-slate-400 ml-2">· {totalElements} results</span>
          </p>
          <div className="flex items-center gap-1">
            <button onClick={() => onPageChange(Math.max(0, page - 1))} disabled={page === 0}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
              <ChevronLeft className="size-3.5" /> Prev
            </button>
            {pageNumbers.map(p => (
              <button key={p} onClick={() => onPageChange(p)}
                className={`size-7 text-xs font-semibold rounded-lg border transition
                  ${p === page ? "bg-blue-600 text-white border-blue-600 shadow-sm" : "border-slate-200 text-slate-500 hover:bg-white"}`}>
                {p + 1}
              </button>
            ))}
            <button onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
              Next <ChevronRight className="size-3.5" />
            </button>
          </div>
        </div>
      )}
    </>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function LeadListScreen() {
  const user = useAuthStore(s => s.user);
  const role = getUserRole(user);
  const isStaff = role === "SALES";
  const canAssign = role === "MANAGER";

  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  // Staff-only owner view: "assigned" (default) vs "created" (leads I created).
  const [ownerView, setOwnerView] = useState<"assigned" | "created">("assigned");
  const [sortOption, setSortOption] = useState("status_desc");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showSortMenu, setShowSortMenu] = useState(false);
  const [page,        setPage]        = useState(0);
  const [drawerOpen,  setDrawer]      = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  // Block an inverted range (From later than To). Date inputs hold "YYYY-MM-DD",
  // which compares correctly as plain strings.
  const dateRangeInvalid = !!dateFrom && !!dateTo && dateFrom > dateTo;

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0); }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  // Close sort menu on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (sortRef.current && !sortRef.current.contains(e.target as Node)) setShowSortMenu(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const resetPage = useCallback(() => setPage(0), []);
  const [sortBy, sortDir] = sortOption.split("_") as [string, "asc" | "desc"];

  // Sales staff to populate the Manager's "Assign To" dropdown. Cached; only the
  // SALES-role users are offered as assignees.
  const { data: usersResp } = useUsers();
  const salesUsers: UserSummary[] = (usersResp?.data ?? []).filter(
    u => (u.roleName ?? "").toUpperCase() === "SALES",
  );

  const { data: resp, isLoading, isError } = useLeads({
    search: search || undefined,
    status: statusFilter || undefined,
    source: sourceFilter || undefined,
    isCorporate: typeFilter === "" ? undefined : typeFilter === "corporate",
    sortBy,
    sortDir,
    // Don't query with an invalid range — wait until the user fixes it.
    dateFrom: !dateRangeInvalid ? (dateFrom || undefined) : undefined,
    dateTo:   !dateRangeInvalid ? (dateTo   || undefined) : undefined,
    // Owner view is a staff concept; managers/admins are unscoped server-side.
    scope: isStaff ? ownerView : undefined,
    page,
    size: PAGE_SIZE,
  });

  const pageData = resp?.data;
  const leads = pageData?.content ?? [];
  const totalPages = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalPages : (pageData?.totalPages ?? 1);
  const totalElements = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalElements : (pageData?.totalElements ?? 0);

  const qualified = leads.filter(l => l.status === "QUALIFIED").length;
  const active = leads.filter(l => l.status !== "LOST" && l.status !== "CONVERTED").length;
  const qualifyRate = `${((qualified / (leads.length || 1)) * 100).toFixed(1)}%`;

  // Type now has its own always-visible segmented toggle, so it is excluded from the advanced-filter badge.
  const activeFilterCount = [statusFilter, sourceFilter, dateFrom, dateTo].filter(Boolean).length;
  const hasFilters = activeFilterCount > 0 || !!search;

  const clearAll = () => {
    setStatusFilter(""); setSourceFilter(""); setTypeFilter(""); setDateFrom(""); setDateTo("");
    setSearchInput(""); setSearch(""); setPage(0);
  };

  const currentSort = SORT_OPTIONS.find(o => o.value === sortOption) ?? SORT_OPTIONS[0];

  // ── Filter bar (reused in both normal + fullscreen) ──────────────────────
  const filterBar = (
    <div className="bg-white">
      <div className="flex flex-wrap items-center gap-2.5 px-4 py-3">
        {/* Search */}
        <div className="relative flex-1 min-w-[180px] max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
          {searchInput && (
            <button type="button" onClick={() => { setSearchInput(""); setSearch(""); setPage(0); }}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 p-0.5 rounded-full hover:bg-slate-200 text-slate-400">
              <X className="size-3" />
            </button>
          )}
          <input type="text" placeholder="Search name, company, email…" value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            className="w-full pl-9 pr-8 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none transition" />
        </div>

        {/* Staff-only owner view: Assigned to me (default) vs Created by me.
            Managers/Admins see every lead, so the toggle is hidden for them. */}
        {isStaff && (
          <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-slate-100 border border-slate-200">
            {([
              { value: "assigned", label: "Assigned to me", icon: UserCheck },
              { value: "created", label: "Created by me", icon: PenLine },
            ] as const).map(seg => {
              const active = ownerView === seg.value;
              return (
                <button key={seg.value} type="button"
                  onClick={() => { setOwnerView(seg.value); resetPage(); }}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-semibold transition
                    ${active
                      ? "bg-white text-blue-700 shadow-sm ring-1 ring-slate-200"
                      : "text-slate-500 hover:text-slate-700"}`}>
                  <seg.icon className="size-3.5" />
                  {seg.label}
                </button>
              );
            })}
          </div>
        )}

        {/* Status */}
        <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); resetPage(); }}
          className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
          <option value="">All statuses</option>
          {(Object.keys(STATUS_CONFIG) as LeadStatus[]).map(s => (
            <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>
          ))}
        </select>

        {/* Source */}
        <select value={sourceFilter} onChange={e => { setSourceFilter(e.target.value); resetPage(); }}
          className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
          <option value="">All sources</option>
          {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
        </select>

        {/* Sort */}
        <div className="relative" ref={sortRef}>
          <button type="button" onClick={() => setShowSortMenu(v => !v)}
            className="flex items-center gap-2 px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 hover:bg-white text-slate-600 font-medium transition">
            <currentSort.icon className="size-3.5 text-slate-400" />
            {currentSort.label}
            <ChevronDown className={`size-3 text-slate-400 transition-transform ${showSortMenu ? "rotate-180" : ""}`} />
          </button>
          {showSortMenu && (
            <div className="absolute right-0 top-full mt-1 z-20 bg-white border border-slate-200 rounded-xl shadow-xl py-1 w-40">
              {SORT_OPTIONS.map(opt => (
                <button key={opt.value} type="button"
                  onClick={() => { setSortOption(opt.value); setShowSortMenu(false); resetPage(); }}
                  className={`w-full flex items-center gap-2.5 px-3.5 py-2 text-xs font-medium transition
                    ${sortOption === opt.value ? "bg-blue-50 text-blue-700" : "text-slate-600 hover:bg-slate-50"}`}>
                  <opt.icon className="size-3.5 text-slate-400" />
                  {opt.label}
                  {sortOption === opt.value && <span className="ml-auto size-1.5 rounded-full bg-blue-500" />}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Advanced toggle */}
        <button type="button" onClick={() => setShowAdvanced(v => !v)}
          className={`flex items-center gap-2 px-3 py-2 text-xs border rounded-lg font-semibold transition
            ${showAdvanced || activeFilterCount > 0
              ? "border-blue-300 bg-blue-50 text-blue-700 hover:bg-blue-100"
              : "border-slate-200 bg-slate-50 text-slate-600 hover:bg-white"}`}>
          <SlidersHorizontal className="size-3.5" />
          Filters
          {activeFilterCount > 0 && (
            <span className="flex items-center justify-center size-4 rounded-full bg-blue-600 text-white text-[9px] font-extrabold">
              {activeFilterCount}
            </span>
          )}
        </button>

        {/* Right side: entry count + Individual/Organization segmented toggle */}
        <div className="ml-auto flex items-center gap-3">
          <span className="text-xs text-slate-400 hidden lg:block">
            {isLoading ? "Loading…" : <>Showing <strong className="text-slate-700">{leads.length}</strong> of {totalElements}</>}
          </span>
          <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-slate-100 border border-slate-200">
            {TYPE_SEGMENTS.map(seg => {
              const active = typeFilter === seg.value;
              return (
                <button key={seg.value || "all"} type="button" title={seg.label}
                  onClick={() => { setTypeFilter(seg.value); resetPage(); }}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-semibold transition
                    ${active
                      ? "bg-white text-blue-700 shadow-sm ring-1 ring-slate-200"
                      : "text-slate-500 hover:text-slate-700"}`}>
                  {seg.icon && <seg.icon className="size-3.5" />}
                  <span className={seg.icon ? "hidden sm:inline" : ""}>{seg.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Advanced panel */}
      {showAdvanced && (
        <div className="border-t border-slate-100 px-4 py-3 bg-slate-50/60">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex items-end gap-2">
              <div>
                <label className="flex items-center gap-1 text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
                  <CalendarDays className="size-3" /> From date
                </label>
                <input type="date" value={dateFrom} max={dateTo || undefined}
                  onChange={e => { setDateFrom(e.target.value); resetPage(); }}
                  className={`px-3 py-2 text-xs border rounded-lg bg-white focus:outline-none
                    ${dateRangeInvalid ? "border-rose-300 focus:border-rose-400" : "border-slate-200 focus:border-blue-400"}`} />
              </div>
              <span className="text-slate-400 text-xs pb-2.5">→</span>
              <div>
                <label className="flex items-center gap-1 text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
                  <CalendarDays className="size-3" /> To date
                </label>
                <input type="date" value={dateTo} min={dateFrom || undefined}
                  onChange={e => { setDateTo(e.target.value); resetPage(); }}
                  className={`px-3 py-2 text-xs border rounded-lg bg-white focus:outline-none
                    ${dateRangeInvalid ? "border-rose-300 focus:border-rose-400" : "border-slate-200 focus:border-blue-400"}`} />
              </div>
            </div>
            {hasFilters && (
              <button type="button" onClick={clearAll}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded-lg hover:bg-rose-100 transition">
                <X className="size-3" /> Clear all
              </button>
            )}
          </div>
          {dateRangeInvalid && (
            <p className="mt-2 flex items-center gap-1 text-xs text-rose-500">
              <AlertCircle className="size-3" /> “From date” must be on or before “To date”.
            </p>
          )}
        </div>
      )}

      {/* Active chips */}
      {(statusFilter || sourceFilter || dateFrom || dateTo) && (
        <div className="flex flex-wrap items-center gap-2 px-4 pb-3">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">Filtering:</span>
          {statusFilter && <FilterChip label={`Status: ${STATUS_CONFIG[statusFilter as LeadStatus]?.label}`} onRemove={() => { setStatusFilter(""); resetPage(); }} />}
          {sourceFilter && <FilterChip label={`Source: ${sourceFilter}`} onRemove={() => { setSourceFilter(""); resetPage(); }} />}
          {dateFrom && <FilterChip label={`From: ${new Date(dateFrom).toLocaleDateString("en-US")}`} onRemove={() => { setDateFrom(""); resetPage(); }} />}
          {dateTo && <FilterChip label={`To: ${new Date(dateTo).toLocaleDateString("en-US")}`} onRemove={() => { setDateTo(""); resetPage(); }} />}
        </div>
      )}
    </div>
  );

  // ── View ──────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-6">

      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Leads Register</h1>
          <p className="text-xs text-slate-400">Track and convert potential customers into bookings</p>
        </div>
        <div className="flex gap-2">
          <Button variant="primary" size="sm" onClick={() => setDrawer(true)}
            leftIcon={<Plus className="size-3.5" />}
            className="bg-blue-600 hover:bg-blue-700 font-semibold text-xs text-white">
            New Lead
          </Button>
        </div>
      </div>

      {/* Stats summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <Handshake className="size-3 text-slate-400" /> Total Leads
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">{totalElements} Leads</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <Users className="size-3 text-slate-400" /> Active
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">{active}</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <TrendingUp className="size-3 text-slate-400" /> Qualified
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">{qualified}</p>
        </div>
        <div className="px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <Percent className="size-3 text-slate-400" /> Qualify Rate
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">{qualifyRate}</p>
        </div>
      </div>

      {/* Filter bar — no overflow-hidden here, or it clips the Sort dropdown menu */}
      <Card className="border-slate-100 shadow-sm p-0">
        <CardContent>{filterBar}</CardContent>
      </Card>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <LeadTable
          isLoading={isLoading} isError={isError} leads={leads}
          totalPages={totalPages} totalElements={totalElements}
          page={page} onPageChange={setPage}
          onClearFilters={clearAll} hasFilters={hasFilters}
          editMode={isStaff && ownerView === "created"}
        />
      </div>

      {drawerOpen && <CreateLeadDrawer onClose={() => setDrawer(false)} canAssign={canAssign} users={salesUsers} />}
    </div>
  );
}
