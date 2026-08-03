"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Search, Plus, X, Handshake, Users, TrendingUp, UserX,
  Phone, Building2, User, ArrowUpRight, ChevronLeft, ChevronRight,
  Loader2, AlertCircle, AlertTriangle, SlidersHorizontal, CalendarDays, ArrowUpDown,
  ArrowUp, ArrowDown, ArrowDownWideNarrow, ChevronDown, ServerCrash,
  UserCheck, PenLine, UserCog,
} from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { toast } from "@/stores/toast_store";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { useLeads, useLeadStats, useCreateLead, useLeadDetail, useUpdateLead } from "@/features/lead/hooks/use_leads";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import type { UserSummary } from "@/services/follow_up_task_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import type { Lead, LeadStatus, CreateLeadPayload, UpdateLeadPayload } from "@/services/lead_service";
import {
  LeadEditDrawer, validateLeadForm, seedLeadForm, leadApiError, leadApiStatus,
  type LeadEditErrors,
} from "@/features/lead/components/LeadEditDrawer";
import { InterestedServiceInput } from "@/features/lead/components/InterestedServiceInput";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { StatusPill } from "@/components/ui/status-pill";
import { LeadDetailDrawer } from "@/features/lead/components/LeadDetailDrawer";

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
  fullName: "", email: "", phone: "", companyName: "", address: "", isCorporate: false, source: "Website Inquiry", interestedService: "", notes: "",
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

/**
 * Create and edit are validated by the same function. This screen used to carry its own copy —
 * a third one, after the detail screen's and the drawer's — and the three had already diverged on
 * which fields they checked at all.
 */
type FormErrors = LeadEditErrors;

const validateForm = (f: CreateLeadPayload): FormErrors => validateLeadForm(f);

// ── Helpers ───────────────────────────────────────────────────────────────────

function Avatar({ name }: { name: string | null }) {
  const initials = (name ?? "?").split(" ").map(p => p[0]).slice(0, 2).join("").toUpperCase();
  const colors = ["bg-blue-100 text-blue-700", "bg-violet-100 text-violet-700", "bg-emerald-100 text-emerald-700", "bg-amber-100 text-amber-700"];
  const color = colors[(name?.charCodeAt(0) ?? 0) % colors.length];
  return (
    // shrink-0: the avatar sits in a flex row next to a name that can be far wider than its
    // column. Without it the circle is the flex item that gives way — squashed into an ellipse
    // whose width depends on the neighbouring name. IdentityAccessScreen's copy already had it.
    <span className={`inline-flex items-center justify-center rounded-full font-bold size-7 text-[10px] shrink-0 ${color}`}>
      {initials}
    </span>
  );
}

/**
 * Lead status now renders through the canonical `StatusPill` (Blueprint §2.7).
 *
 * The local `STATUS_CONFIG` colours are kept only for the *filter dropdown
 * labels* below; the pill itself no longer reads them, so `QUALIFIED` is the
 * same green here as on the detail page, the pipeline card and the dashboard.
 */
function StatusBadge({ status }: { status: LeadStatus }) {
  return <StatusPill size="sm" domain="lead" value={status} />;
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{msg}</p>;
}

/**
 * A field the record simply does not carry.
 *
 * <p>It used to render a red italic "Unknown", which reads as an error the user has to fix — but
 * most of these fields are optional by design (a walk-in lead with no email is a valid lead, not a
 * broken one). A whole column of red text also drowns out the warnings that do mean something. A
 * muted dash says "nothing here" without claiming anything is wrong.
 */
function Unknown() {
  return <span className="text-slate-300" aria-label="No value">—</span>;
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
  // A duplicate (same email/phone) is shown as a warning with a link to whatever it collided
  // with, not as a generic server error. Two kinds collide: another LEAD, or — the case that used
  // to pass silently — an existing CUSTOMER, i.e. a returning guest being typed in as new.
  const [duplicate, setDuplicate] = useState<
    { kind: "lead" | "customer"; message: string; id?: string } | null>(null);
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
        const code: string | undefined = data?.errorCode;
        const isCustomerClash =
          code === "DUPLICATE_CUSTOMER_EMAIL" || code === "DUPLICATE_CUSTOMER_PHONE";
        // Match on the error CODE, never on the 409 alone. DataIntegrityViolation also answers
        // 409, and its `details` is a log correlation id — treating that as a duplicate produced
        // a "Possible duplicate lead" banner for a constraint failure, with a "View the existing
        // lead" link pointing at /leads/<log-reference>, which is always Not Found.
        if (code === "DUPLICATE_LEAD" || isCustomerClash) {
          setDuplicate({
            kind: isCustomerClash ? "customer" : "lead",
            message: data?.message || "A record with these contact details already exists.",
            id: data?.details || undefined,
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
                <p className="font-semibold">
                  {duplicate.kind === "customer" ? "Already a customer" : "Possible duplicate lead"}
                </p>
                <p className="text-amber-700">
                  {duplicate.kind === "customer"
                    ? `${duplicate.message} Open their profile to add a new deal instead of creating a second record.`
                    : duplicate.message}
                </p>
                {duplicate.id && (
                  <Link
                    href={duplicate.kind === "customer"
                      ? `/customer-profiles/${duplicate.id}`
                      : ROUTE_PATHS.leadDetail(duplicate.id)}
                    className="inline-flex items-center gap-1 font-semibold text-amber-900 underline underline-offset-2 hover:text-amber-950">
                    {duplicate.kind === "customer" ? "Open the customer profile" : "View the existing lead"}
                    <ArrowUpRight className="size-3" />
                  </Link>
                )}
              </div>
            </div>
          )}

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Full Name *</label>
            <Input maxLength={40} placeholder="e.g. John Smith" value={form.fullName}
              onChange={e => field("fullName", e.target.value)}
              error={errors.fullName}
              className="py-1.5 text-xs" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Email</label>
              <Input type="text" maxLength={40} placeholder="example@gmail.com" value={form.email}
                onChange={e => field("email", e.target.value)}
                error={errors.email}
                className="py-1.5 text-xs" />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Phone Number</label>
              <Input phoneOnly placeholder="e.g. 09xxxxxxxx" value={form.phone}
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

          {/* BR-05: required before a lead enters active follow-up. */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Interested Service</label>
            <InterestedServiceInput
              value={form.interestedService ?? ""}
              onChange={v => field("interestedService", v)}
              className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100 transition" />
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
              className="w-full bg-primary hover:bg-primary/90 text-xs font-semibold">
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
// Seven columns instead of twelve. Address, Created-by, Type and the separate
// Created date were dropped from the list: they are rarely what a rep scans for,
// they forced every cell narrow enough to truncate, and all of them are one
// click away in the detail drawer.
const COL_WIDTHS = ["4%", "24%", "22%", "10%", "12%", "10%", "14%", "4%"];

// Rows per page. The table always renders this many row slots (real rows + invisible
// fillers) so its height — and therefore the pagination bar pinned below it — stays
// fixed regardless of how many leads the current page actually holds.
const PAGE_SIZE = 10;

// ── Unassigned-lead editor ────────────────────────────────────────────────────

/**
 * The edit slide-over for a lead this staff member created but does not own.
 *
 * <p>It has no Status control on purpose. An unassigned lead cannot change status — the server
 * refuses it (BR-06) because follow-up only starts once a Manager has given the lead to someone —
 * so offering a dropdown here would put a control on screen whose every option is rejected.
 *
 * <p>Loads the lead itself rather than reusing the row: the list row carries a summary, and saving
 * a partial form would blank whatever the summary omits.
 */
function UnassignedLeadDrawer({ leadId, onClose }: { leadId: string; onClose: () => void }) {
  const { data: resp, isLoading } = useLeadDetail(leadId);
  const updateMutation = useUpdateLead(leadId);
  const lead = resp?.data;

  // The form is derived, not copied: the lead arrives one render after this mounts, and seeding it
  // from an effect would set state during render and cascade. Edits are held separately and laid
  // over the loaded values, so there is no window where the form exists but is empty.
  const [draft, setDraft] = useState<Partial<UpdateLeadPayload>>({});
  const [errors, setErrors] = useState<LeadEditErrors>({});
  const [serverError, setServerError] = useState("");

  const form: UpdateLeadPayload | null = lead ? { ...seedLeadForm(lead), ...draft } : null;

  if (isLoading || !form) {
    return (
      <>
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-40" onClick={onClose} />
        <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl border-l border-slate-200
          flex items-center justify-center gap-2 text-slate-400 animate-in slide-in-from-right duration-300">
          <Loader2 className="size-5 animate-spin" /> Loading lead…
        </aside>
      </>
    );
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validateLeadForm(form);
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setErrors({});
    setServerError("");
    updateMutation.mutate(
      {
        ...form,
        // Status is not editable here, so it is not sent — the server keeps whatever it has
        // rather than being echoed a value this form never offered to change.
        status: undefined,
        assignedUserId: form.assignedUserId || undefined,
      },
      {
        onSuccess: onClose,
        onError: err => {
          setServerError((leadApiStatus(err) ?? 0) >= 500
            ? "Server error — please contact your Admin."
            : leadApiError(err)?.message || "Update failed. Please try again.");
        },
      },
    );
  };

  return (
    <LeadEditDrawer
      form={form}
      errors={errors}
      serverError={serverError}
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
        setDraft(d => ({ ...d, ...patch }));
        setErrors(prev => {
          const next = { ...prev };
          for (const key of Object.keys(patch)) delete next[key as keyof LeadEditErrors];
          return next;
        });
      }}
      onSubmit={handleSubmit}
      onClose={onClose}
    />
  );
}

// ── Lead Table ────────────────────────────────────────────────────────────────

function LeadTable({
  isLoading, isError, leads, totalPages, totalElements, page, onPageChange, onClearFilters, hasFilters,
  editMode, onEditLead, onOpenLead,
}: {
  isLoading: boolean; isError: boolean;
  leads: Lead[]; totalPages: number; totalElements: number;
  page: number; onPageChange: (p: number) => void;
  onClearFilters: () => void; hasFilters: boolean;
  // When true (staff viewing "Created by me"), a row opens the edit drawer instead of navigating.
  editMode: boolean;
  onEditLead?: (leadId: string) => void;
  // Row click peeks at the record in the detail drawer without losing the list's
  // filters, page or scroll. The name cell still links to the full page.
  onOpenLead: (lead: Lead) => void;
}) {
  if (isLoading) return (
    <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
      <Loader2 className="size-5 animate-spin" /> Loading…
    </div>
  );

  if (isError) return (
    <div className="flex flex-col items-center justify-center py-20 gap-2 text-destructive">
      <ServerCrash className="size-8 mb-1" />
      <p className="text-sm font-semibold">Server error — please contact your Admin.</p>
    </div>
  );

  if (leads.length === 0) return (
    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
      <Handshake className="size-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">No results found</p>
      {hasFilters && (
        <button onClick={onClearFilters} className="mt-2 text-xs text-primary hover:underline">
          Clear filters
        </button>
      )}
    </div>
  );

  const pageStart = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pageNumbers = Array.from({ length: Math.min(5, totalPages) }, (_, i) => pageStart + i);

  // "Created by me" rows open the edit-only screen; everything else opens full detail.
  // In editMode (staff looking at leads they created but do not own) a row opens the edit
  // slide-over in place, over this list — matching how every other edit in the app behaves.
  // Everyone else navigates to the full detail screen.
  //
  // A plain function rather than a component: declaring a component inside render would give it a
  // new identity every pass and remount the cell on each keystroke in the filter box.
  // The non-edit branch is a real <Link> rather than a click handler even though the row
  // already opens the drawer: it is what makes ctrl/middle-click open the lead in a new tab.
  // The href is the list URL with the lead pre-selected — there is no full-page detail to
  // navigate to any more, so a new tab lands on this same list with the drawer open.
  const rowOpen = (id: string, className: string, children: React.ReactNode) =>
    editMode
      ? <button type="button" onClick={() => onEditLead?.(id)} className={`${className} text-left w-full`}>{children}</button>
      : <Link href={ROUTE_PATHS.leadDetail(id)} className={className}>{children}</Link>;

  return (
    <>
      <Table className="table-fixed">
        <colgroup>
          {COL_WIDTHS.map((w, i) => <col key={i} style={{ width: w }} />)}
        </colgroup>
        <TableHeader className="bg-muted border-b border-border text-muted-foreground">
          <TableRow hoverable={false}>
            {[
              { label: "#", w: "w-10" },
              { label: "Lead", w: "" },
              { label: "Contact", w: "" },
              { label: "Source", w: "" },
              { label: "Status", w: "" },
              { label: "SLA", w: "" },
              { label: "Owner", w: "" },
              { label: "", w: "w-8" },
            ].map((h, i) => (
              <TableHead key={h.label || `col-${i}`} className={`py-3 px-4 text-[11px] font-semibold uppercase tracking-wide whitespace-nowrap ${h.w}`}>
                {h.label}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {leads.map((lead, idx) => (
            <TableRow
              key={lead.leadId}
              // In editMode the row belongs to a lead this staff member created
              // but does not own — editing is the only thing they can do with it,
              // so the row goes straight there instead of to a read-only drawer.
              onClick={() => (editMode ? onEditLead?.(lead.leadId) : onOpenLead(lead))}
              className="group cursor-pointer border-b border-border transition-colors hover:bg-surface-2"
            >
              <TableCell className="py-3 px-4 text-xs text-muted-foreground font-mono border-b-0">
                {page * PAGE_SIZE + idx + 1}
              </TableCell>

              {/* Lead — name, plus the company/individual line beneath it. Two
                  fields in one column instead of two columns of mostly-blank
                  cells. */}
              <TableCell className="py-3 px-4 border-b-0">
                {rowOpen(lead.leadId, "block group-hover:underline decoration-blue-300 underline-offset-2",
                  <Truncate text={lead.fullName} width={130}
                    className="font-semibold text-sm text-slate-800 group-hover:text-blue-600 transition-colors" />)}
                {/* The company/individual line lives under the name — it is not a column of its
                    own. A separate Type cell used to be rendered here with no matching header,
                    which pushed every following column one slot out of alignment. */}
                <span className="mt-0.5 flex items-center gap-1.5 text-[11px] text-muted-foreground"
                  title={lead.isCorporate ? "Organization" : "Individual"}>
                  {lead.isCorporate
                    ? <Building2 className="size-3 shrink-0" />
                    : <User className="size-3 shrink-0" />}
                  {lead.isCorporate
                    ? (lead.companyName ? <Truncate text={lead.companyName} width={110} /> : <Unknown />)
                    : "Individual"}
                </span>
              </TableCell>

              {/* Contact — email over phone, the two channels a rep acts on. */}
              <TableCell className="py-3 px-4 border-b-0">
                {lead.email
                  ? <Truncate text={lead.email} width={170} className="text-xs text-foreground" />
                  : <span className="text-xs"><Unknown /></span>}
                <span className="mt-0.5 block text-[11px] text-muted-foreground">
                  {lead.phone ?? "—"}
                </span>
              </TableCell>

              <TableCell className="py-3 px-4 text-xs text-muted-foreground whitespace-nowrap border-b-0">
                {lead.source ?? <Unknown />}
              </TableCell>

              <TableCell className="py-3 px-4 border-b-0"><StatusBadge status={lead.status} /></TableCell>

              <TableCell className="py-3 px-4 border-b-0">
                <SlaStatusBadge entityId={lead.leadId} entityType="LEAD" />
              </TableCell>

              <TableCell className="py-3 px-4 border-b-0">
                {lead.assignedUserName
                  ? <div className="flex items-center gap-2"><Avatar name={lead.assignedUserName} /><Truncate text={lead.assignedUserName} width={100} className="text-xs text-muted-foreground" /></div>
                  : <span className="text-xs italic text-muted-foreground">Unassigned</span>}
              </TableCell>

              <TableCell className="py-3 px-4 border-b-0">
                {rowOpen(lead.leadId,
                  "inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity hover:text-blue-800",
                  <ArrowUpRight className="size-3.5" />)}
              </TableCell>
            </TableRow>
          ))}
          {/* Invisible filler rows pad the body to PAGE_SIZE so the table height — and the
              pagination bar below — never shifts between a full page and a partial last page.
              The hidden two-line content mirrors a real Lead cell's height exactly. */}
          {Array.from({ length: Math.max(0, PAGE_SIZE - leads.length) }).map((_, i) => (
            <TableRow key={`filler-${i}`} hoverable={false} className="border-b border-border">
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
                  ${p === page ? "bg-primary text-white border-primary shadow-sm" : "border-slate-200 text-slate-500 hover:bg-white"}`}>
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
  const router = useRouter();
  const searchParams = useSearchParams();
  // `/leads?lead=<id>` opens that lead's drawer straight away. Every link to a lead in the
  // app points here — notifications, SLA escalations, task drawers, customer profiles — and
  // the old `/leads/{id}` page redirects here too. There is no full-page lead detail.
  const deepLinkId = searchParams.get("lead");

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
  // Manager-only: the queue of leads nobody owns yet (BR-06 — nothing starts until one is
  // assigned). Meaningless for a sales rep: an unassigned lead is, by definition, not theirs.
  const [needsAssignment, setNeedsAssignment] = useState(false);
  // Newest first. "Status" priority used to be the default, which is a judgement about what
  // matters rather than a neutral starting point — and it put the same rows on page 1 no matter
  // how the list had changed since the user last looked. Creation order is the one ordering the
  // user can reason about without knowing the ranking rules.
  const [sortOption, setSortOption] = useState("createdAt_desc");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showSortMenu, setShowSortMenu] = useState(false);
  const [page,        setPage]        = useState(0);
  const [drawerOpen,  setDrawer]      = useState(false);
  const [editingLeadId, setEditingLeadId] = useState<string | null>(null);
  // The row the detail drawer is showing. Holding the record rather than an id
  // lets the drawer paint immediately from the list data it already has.
  const [detailLead, setDetailLead] = useState<Lead | null>(null);
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
    unassigned: canAssign && needsAssignment ? true : undefined,
    page,
    size: PAGE_SIZE,
  });

  const pageData = resp?.data;
  const leads = pageData?.content ?? [];
  const totalPages = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalPages : (pageData?.totalPages ?? 1);
  const totalElements = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalElements : (pageData?.totalElements ?? 0);

  // ── Deep-linked lead ──────────────────────────────────────────────────────
  // If the linked lead happens to be on the page in front of us, use that row and paint
  // immediately; otherwise fetch it. A deep link usually arrives with the default filters,
  // so the lead is often *not* in the current page — but checking first saves a request on
  // the common case of clicking a name in this very table.
  const rowForDeepLink = deepLinkId ? leads.find(l => l.leadId === deepLinkId) ?? null : null;
  const { data: deepLinkResp, isError: deepLinkFailed } =
    useLeadDetail(deepLinkId && !rowForDeepLink ? deepLinkId : undefined);

  const clearDeepLink = useCallback(() => {
    if (deepLinkId) router.replace(ROUTE_PATHS.leads, { scroll: false });
  }, [deepLinkId, router]);

  // A link to a lead that no longer exists, or that this rep is not allowed to see (403),
  // used to land on a dedicated "Lead not found" / "Access Denied" page. That page is gone,
  // so say it once and drop the parameter — otherwise the URL keeps promising a drawer that
  // never opens, and a refresh retries the same dead id.
  useEffect(() => {
    if (!deepLinkFailed) return;
    toast.error("That lead is unavailable — it may have been removed, or it belongs to another sales rep.");
    clearDeepLink();
  }, [deepLinkFailed, clearDeepLink]);

  // Counted server-side over every lead matching the current filters. These used to be derived
  // from `leads` — the ten rows of the current page — so they changed when the user paged or
  // re-sorted even though the data had not. Same filters and same owner scope as the list below.
  const { data: statsResp } = useLeadStats({
    search: search || undefined,
    status: statusFilter || undefined,
    source: sourceFilter || undefined,
    isCorporate: typeFilter === "" ? undefined : typeFilter === "corporate",
    dateFrom: !dateRangeInvalid ? (dateFrom || undefined) : undefined,
    dateTo:   !dateRangeInvalid ? (dateTo   || undefined) : undefined,
    scope: isStaff ? ownerView : undefined,
    unassigned: canAssign && needsAssignment ? true : undefined,
  });
  const stats = statsResp?.data;
  // A dash, not "0.0%": with no leads there is nothing to measure, which is not the same as
  // measuring zero conversions.
  const pct = (v: number | null | undefined) => (v == null ? "—" : `${v.toFixed(1)}%`);

  // Type now has its own always-visible segmented toggle, so it is excluded from the advanced-filter badge.
  const activeFilterCount = [statusFilter, sourceFilter, dateFrom, dateTo].filter(Boolean).length
    + (needsAssignment ? 1 : 0);
  const hasFilters = activeFilterCount > 0 || !!search;

  const clearAll = () => {
    setStatusFilter(""); setSourceFilter(""); setTypeFilter(""); setDateFrom(""); setDateTo("");
    setSearchInput(""); setSearch(""); setNeedsAssignment(false); setPage(0);
  };

  const currentSort = SORT_OPTIONS.find(o => o.value === sortOption) ?? SORT_OPTIONS[0];

  // A row click wins over the URL: clicking row B while ?lead=A is still in the address
  // bar must show B, not A. Both routes end at the same drawer.
  const drawerLead: Lead | null = detailLead ?? rowForDeepLink ?? deepLinkResp?.data ?? null;

  // ── Filter bar (reused in both normal + fullscreen) ──────────────────────
  const filterBar = (
    <div className="bg-white">
      <div className="flex flex-wrap items-center gap-2.5 px-4 py-3">
        {/* Search */}
        <div className="relative flex-1 min-w-45 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
          {searchInput && (
            <button type="button" onClick={() => { setSearchInput(""); setSearch(""); setPage(0); }}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 p-0.5 rounded-full hover:bg-slate-200 text-slate-400">
              <X className="size-3" />
            </button>
          )}
          <input type="text" placeholder="Search name, phone, email, company…" value={searchInput}
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

        {/* Manager only: the distribution queue. A toggle rather than a value in the Status
            dropdown — "needs assigning" is orthogonal to where the lead sits in the pipeline, and
            a Manager wants it combined with the other filters, not instead of them. */}
        {canAssign && (
          <button type="button"
            onClick={() => { setNeedsAssignment(v => !v); resetPage(); }}
            aria-pressed={needsAssignment}
            title="Leads with no owner yet — assign them to a sales rep"
            className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold border transition
              ${needsAssignment
                ? "bg-amber-50 border-amber-300 text-amber-800 shadow-sm"
                : "bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100"}`}>
            <UserCog className={`size-3.5 ${needsAssignment ? "text-amber-600" : "text-slate-400"}`} />
            Assignment needed
          </button>
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
                  {sortOption === opt.value && <span className="ml-auto size-1.5 rounded-full bg-primary" />}
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
            <span className="flex items-center justify-center size-4 rounded-full bg-primary text-white text-[9px] font-extrabold">
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
            className="bg-primary hover:bg-primary/90 font-semibold text-xs text-white">
            New Lead
          </Button>
        </div>
      </div>

      {/* Stats summary cards.
          Outcome first, then work in progress: converted and lost are what the pipeline actually
          produced, and each carries its share of the total right beneath the count so the figure
          and its weight are read together — which also keeps this to four columns. */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <Handshake className="size-3 text-slate-400" /> Total Leads
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats?.total ?? totalElements} Leads</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <TrendingUp className="size-3 text-emerald-500" /> Converted
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">
            {stats?.converted ?? "—"}
            <span className="ml-1.5 text-xs font-semibold text-emerald-600">
              {pct(stats?.convertedRate)}
            </span>
          </p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <UserX className="size-3 text-rose-400" /> Lost
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">
            {stats?.lost ?? "—"}
            <span className="ml-1.5 text-xs font-semibold text-rose-500">
              {pct(stats?.lostRate)}
            </span>
          </p>
        </div>
        <div className="px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase flex items-center gap-1">
            <Users className="size-3 text-slate-400" /> Active
          </p>
          <p className="text-lg font-bold text-slate-800 mt-1">
            {stats?.active ?? "—"}
            <span className="ml-1.5 text-xs font-semibold text-slate-400">
              {stats ? `${stats.qualified} qualified` : ""}
            </span>
          </p>
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
          onEditLead={setEditingLeadId}
          onOpenLead={setDetailLead}
        />
      </div>

      {drawerOpen && <CreateLeadDrawer onClose={() => setDrawer(false)} canAssign={canAssign} users={salesUsers} />}

      {/* A lead this staff member created but does not own: edited in place, over the list.
          Keyed by id so switching rows remounts the form rather than leaving the previous
          lead's values behind. */}
      {editingLeadId && (
        <UnassignedLeadDrawer key={editingLeadId} leadId={editingLeadId}
          onClose={() => setEditingLeadId(null)} />
      )}

      {/* Full lead detail — Overview / Edit / Activity, plus Convert and the status
          ladder. This is the *only* place a lead opens; there is no second, full-page
          copy of it any more. Keyed by id so switching rows resets the edit form. */}
      <LeadDetailDrawer
        key={drawerLead?.leadId ?? "none"}
        lead={drawerLead}
        onOpenChange={(open) => {
          if (open) return;
          setDetailLead(null);
          clearDeepLink();
        }}
      />
    </div>
  );
}
