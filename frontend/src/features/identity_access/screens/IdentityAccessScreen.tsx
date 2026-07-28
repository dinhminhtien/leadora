"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  Users, ShieldCheck, Search, UserPlus, X, AlertCircle, Loader2, ServerCrash,
  ChevronLeft, ChevronRight, Pencil, KeyRound, Check, Plus, Save,
  Target, Building2, Briefcase, GitBranch, ClipboardList, FileText, MessagesSquare,
  CalendarCheck, BedDouble, PackageCheck, CreditCard, Bell, AlarmClock, Timer,
  BarChart3, Star, Bot, DoorOpen, type LucideIcon,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Badge } from "@/components/ui/Badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import {
  useUserAccounts, useCreateUser, useUpdateUser,
  useRoles, usePermissions, useSetRolePermissions,
} from "@/features/identity_access/hooks/use_identity";
import type {
  UserAccount, UserStatus, CreateUserPayload, UpdateUserPayload, Role, Permission,
} from "@/services/user_service";

// ── Constants & helpers ─────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<UserStatus, { label: string; variant: "success" | "default" | "danger" }> = {
  ACTIVE:   { label: "Active",   variant: "success" },
  INACTIVE: { label: "Inactive", variant: "default" },
  LOCKED:   { label: "Locked",   variant: "danger" },
};

const STATUS_OPTIONS: UserStatus[] = ["ACTIVE", "INACTIVE", "LOCKED"];

// Display label for the as-built DB role codes (ADMIN/SALES/MANAGER) → Admin/Staff/Manager.
function roleLabel(roleName?: string | null): string {
  switch ((roleName ?? "").toUpperCase()) {
    case "SALES":        return "Staff";
    case "MANAGER":      return "Manager";
    case "ADMIN":        return "Admin";
    case "FO":
    case "FRONT_OFFICE": return "Front Office";
    case "RESERVATION":  return "Reservation";
    default:             return roleName ?? "—";
  }
}

// Name: letters (any language), spaces and basic name punctuation — no digits/symbols.
const NAME_ALLOWED = /^[\p{L}\s.'-]+$/u;

// Rows per page + fixed column widths so the table height (and the pagination bar
// below it) stays constant across pages — mirrors the Leads list.
const PAGE_SIZE = 10;
const COL_WIDTHS = ["21%", "24%", "11%", "11%", "14%", "11%", "8%"];

/** Short date, e.g. "28 Jul 2026". */
function formatDate(value?: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("en-US", { day: "2-digit", month: "short", year: "numeric" });
}

/**
 * Last-login stamp (UC-6.1). Shows the time too — an Admin auditing accounts cares whether
 * someone signed in this morning or three weeks ago, and "Never" is itself a finding.
 */
function formatLastLogin(value?: string | null) {
  if (!value) return "Never";
  const d = new Date(value);
  return `${d.toLocaleDateString("en-US", { day: "2-digit", month: "short", year: "numeric" })} · ${d.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })}`;
}

function initials(name: string) {
  return (name || "?").split(" ").map(p => p[0]).slice(0, 2).join("").toUpperCase();
}

function Avatar({ name }: { name: string }) {
  const colors = ["bg-blue-100 text-blue-700", "bg-violet-100 text-violet-700", "bg-emerald-100 text-emerald-700", "bg-amber-100 text-amber-700"];
  const color = colors[(name?.charCodeAt(0) ?? 0) % colors.length];
  return (
    <span className={`inline-flex items-center justify-center rounded-full font-bold size-7 text-[10px] shrink-0 ${color}`}>
      {initials(name)}
    </span>
  );
}

/**
 * Account status via the canonical binding (Blueprint §2.7):
 * ACTIVE success · INACTIVE muted · LOCKED danger.
 * `STATUS_CONFIG` below is retained only for the filter dropdown's labels.
 */
function StatusBadge({ status }: { status: UserStatus }) {
  return <StatusPill size="sm" domain="user" value={status} />;
}

/**
 * Turns an axios-style rejection into the message to show. A 5xx is never surfaced verbatim —
 * the user gets the generic line and the detail stays in the network log.
 */
function errorMessage(err: unknown, fallback = "Something went wrong. Please try again."): string {
  const response = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
  if (typeof response?.status === "number" && response.status >= 500) {
    return "Server error — please contact your Admin.";
  }
  return response?.data?.message ?? fallback;
}

// ── User form drawer (UC-6.2 create / UC-6.3 update) ────────────────────────────

type UserFormErrors = { fullName?: string; email?: string; password?: string; phone?: string; roleId?: string };

function UserFormDrawer({
  mode, user, roles, onClose,
}: {
  mode: "create" | "edit";
  user?: UserAccount;
  roles: Role[];
  onClose: () => void;
}) {
  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [roleId, setRoleId] = useState<number | "">(user?.roleId ?? "");
  const [status, setStatus] = useState<UserStatus>(user?.status ?? "ACTIVE");
  const [errors, setErrors] = useState<UserFormErrors>({});
  const [serverError, setServerError] = useState("");

  // Admin cannot be assigned here. The only exception is editing an account that is
  // already an Admin, so its current role can still be displayed/kept.
  const editingAdmin = mode === "edit" && (user?.roleName ?? "").toUpperCase() === "ADMIN";
  const assignableRoles = roles.filter(
    (r) => r.roleName.toUpperCase() !== "ADMIN" || editingAdmin,
  );

  const createMutation = useCreateUser();
  const updateMutation = useUpdateUser(user?.userId ?? "");
  const isPending = createMutation.isPending || updateMutation.isPending;

  const validate = (): UserFormErrors => {
    const e: UserFormErrors = {};
    const name = fullName.trim();
    if (!name) e.fullName = "Full name is required";
    else if (/\d/.test(name)) e.fullName = "Full name cannot contain numbers";
    else if (!NAME_ALLOWED.test(name)) e.fullName = "Full name cannot contain special characters";
    if (!email.trim()) e.email = "Email is required";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) e.email = "Invalid email format (e.g. name@domain.com)";
    const validatePassword = (pwd: string) => {
      if (pwd.length < 6) return "Password must be at least 6 characters";
      if (!/[A-Z]/.test(pwd)) return "Must contain at least one uppercase letter";
      if (!/[a-z]/.test(pwd)) return "Must contain at least one lowercase letter";
      if (!/\d/.test(pwd)) return "Must contain at least one digit";
      if (!/[^A-Za-z\d\s]/.test(pwd)) return "Must contain at least one symbol";
      return null;
    };

    if (mode === "create") {
      const pwdError = validatePassword(password);
      if (pwdError) e.password = pwdError;
    }
    if (mode === "edit" && password) {
      const pwdError = validatePassword(password);
      if (pwdError) e.password = pwdError;
    }
    if (phone) {
      const digits = phone.replace(/\s/g, "");
      if (/[^\d]/.test(digits)) e.phone = "Phone number can only contain digits (no letters or symbols)";
      else if (!/^\d{8,15}$/.test(digits)) e.phone = "Phone must be 8–15 digits";
    }
    if (roleId === "") e.roleId = "Role is required";
    return e;
  };

  const handleSubmit = (ev: React.FormEvent) => {
    ev.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({});
    setServerError("");

    const onError = (err: unknown) => setServerError(errorMessage(err));

    if (mode === "create") {
      const payload: CreateUserPayload = {
        fullName: fullName.trim(), email: email.trim(), password,
        phone: phone.trim() || undefined, roleId: Number(roleId), status,
      };
      createMutation.mutate(payload, { onSuccess: onClose, onError });
    } else {
      const payload: UpdateUserPayload = {
        fullName: fullName.trim(), email: email.trim(),
        phone: phone.trim() || undefined, roleId: Number(roleId), status,
        ...(password ? { password } : {}),
      };
      updateMutation.mutate(payload, { onSuccess: onClose, onError });
    }
  };

  return (
    <>
      <div className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 z-50 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 flex flex-col animate-in slide-in-from-right duration-300">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              {mode === "create" ? <UserPlus className="size-4.5 text-blue-600" /> : <Pencil className="size-4.5 text-blue-600" />}
              {mode === "create" ? "Create User Account" : "Edit User Account"}
            </h3>
            <p className="text-[10px] text-slate-400 mt-0.5">
              {mode === "create" ? "Provision a new internal account and assign a role" : "Update details, role and account status"}
            </p>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition">
            <X className="size-4.5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {serverError && (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-600">
              <ServerCrash className="size-4 shrink-0" />{serverError}
            </div>
          )}

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Full Name *</label>
            <Input placeholder="e.g. Jane Cooper" value={fullName}
              onChange={e => setFullName(e.target.value)} error={errors.fullName} className="py-1.5 text-xs" />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600">Email *</label>
            <Input type="text" placeholder="name@leadora.com" value={email}
              onChange={e => setEmail(e.target.value)} error={errors.email} className="py-1.5 text-xs" />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-slate-600 flex items-center gap-1">
              <KeyRound className="size-3" />
              {mode === "create" ? "Initial Password *" : "New Password"}
            </label>
            <Input type="password"
              placeholder={mode === "create" ? "Min 6 chars, uppercase, lowercase, digit, symbol" : "Leave blank to keep current"}
              value={password} onChange={e => setPassword(e.target.value)} error={errors.password} className="py-1.5 text-xs" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Phone</label>
              <Input phoneOnly placeholder="e.g. 09xxxxxxxx" value={phone}
                onChange={e => setPhone(e.target.value)} error={errors.phone} className="py-1.5 text-xs" />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-600">Role *</label>
              <Select value={roleId} onChange={e => setRoleId(e.target.value === "" ? "" : Number(e.target.value))}
                error={errors.roleId} className="py-1.5 text-xs">
                <option value="">Select role…</option>
                {assignableRoles.map(r => <option key={r.roleId} value={r.roleId}>{roleLabel(r.roleName)}</option>)}
              </Select>
            </div>
          </div>

          {/* Status: new accounts always start Active; only an edit may set Inactive/Locked. */}
          {mode === "create" ? (
            <div className="flex items-center gap-2 px-3 py-2.5 bg-emerald-50 border border-emerald-200 rounded-xl text-[11px] text-emerald-700">
              <Check className="size-3.5 shrink-0" />
              New accounts are created with status <strong>Active</strong>.
            </div>
          ) : (
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-600">Account Status</label>
              <div className="grid grid-cols-3 gap-2">
                {STATUS_OPTIONS.map(s => {
                  const selected = status === s;
                  return (
                    <button key={s} type="button" onClick={() => setStatus(s)}
                      className={`px-2 py-2 rounded-xl border text-[11px] font-bold uppercase transition
                        ${selected ? "border-blue-500 bg-blue-50 text-blue-700 shadow-sm" : "border-slate-200 bg-white text-slate-400 hover:border-slate-300"}`}>
                      {STATUS_CONFIG[s].label}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          <div className="pt-4 flex gap-3 border-t border-slate-100">
            <Button type="submit" variant="primary" isLoading={isPending}
              className="w-full bg-primary hover:bg-primary/90 text-xs font-semibold">
              {mode === "create" ? "Create Account" : "Save Changes"}
            </Button>
            <Button type="button" variant="outline" onClick={onClose}
              className="w-full border-slate-200 text-xs text-slate-600">Cancel</Button>
          </div>
        </form>
      </div>
    </>
  );
}

// ── Users tab (UC-6.1) ──────────────────────────────────────────────────────────

function UsersTab({ roles }: { roles: Role[] }) {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<number | "">("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [drawer, setDrawer] = useState<{ mode: "create" | "edit"; user?: UserAccount } | null>(null);

  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(0); }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  const { data: resp, isLoading, isError } = useUserAccounts({
    search: search || undefined,
    roleId: roleFilter === "" ? undefined : Number(roleFilter),
    status: statusFilter || undefined,
    sortBy: "createdAt", sortDir: "desc", page, size: PAGE_SIZE,
  });

  const pageData = resp?.data;
  const users = pageData?.content ?? [];
  const totalPages = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalPages : (pageData?.totalPages ?? 1);
  const totalElements = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalElements : (pageData?.totalElements ?? 0);

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <Card className="border-slate-100 shadow-sm bg-white p-0">
        <CardContent className="flex flex-wrap items-center gap-2.5 px-4 py-3">
          <div className="relative flex-1 min-w-45 max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
            <input type="text" placeholder="Search name or email…" value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none transition" />
          </div>

          <select value={roleFilter} onChange={e => { setRoleFilter(e.target.value === "" ? "" : Number(e.target.value)); setPage(0); }}
            className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
            <option value="">All roles</option>
            {roles.map(r => <option key={r.roleId} value={r.roleId}>{roleLabel(r.roleName)}</option>)}
          </select>

          <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
            className="px-3 py-2 text-xs border border-slate-200 rounded-lg bg-slate-50 focus:bg-white focus:border-blue-400 focus:outline-none text-slate-700 cursor-pointer">
            <option value="">All statuses</option>
            {STATUS_OPTIONS.map(s => <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>)}
          </select>

          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-slate-400 hidden lg:block">
              {isLoading ? "Loading…" : <>Showing <strong className="text-slate-700">{users.length}</strong> of {totalElements}</>}
            </span>
            <Button variant="primary" size="sm" onClick={() => setDrawer({ mode: "create" })}
              leftIcon={<UserPlus className="size-3.5" />}
              className="bg-primary hover:bg-primary/90 text-white text-xs font-semibold">
              Create User
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20 gap-2 text-slate-400">
            <Loader2 className="size-5 animate-spin" /> Loading…
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center py-20 gap-2 text-rose-500">
            <ServerCrash className="size-8 mb-1" />
            <p className="text-sm font-semibold">Server error — please contact your Admin.</p>
          </div>
        ) : users.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400">
            <Users className="size-10 mb-3 opacity-30" />
            <p className="text-sm font-medium">No user accounts found</p>
          </div>
        ) : (
          <Table className="table-fixed">
            <colgroup>
              {COL_WIDTHS.map((w, i) => <col key={i} style={{ width: w }} />)}
            </colgroup>
            <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
              <TableRow hoverable={false}>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Staff Member</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Email</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Role</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Status</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Last Login</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Created</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-semibold text-slate-500 uppercase tracking-wide text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map(u => (
                <TableRow key={u.userId} className="hover:bg-blue-50/40 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 border-b-0">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={u.fullName} />
                      <span className="text-xs font-bold text-slate-800 truncate" title={u.fullName}>{u.fullName}</span>
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 border-b-0">
                    <span className="block truncate" title={u.email}>{u.email}</span>
                  </TableCell>
                  <TableCell className="py-3 px-4 border-b-0">
                    <Badge variant="primary" size="sm" className="font-bold text-[10px]">{roleLabel(u.roleName)}</Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 border-b-0"><StatusBadge status={u.status} /></TableCell>
                  <TableCell className="py-3 px-4 text-xs whitespace-nowrap border-b-0">
                    <span className={u.lastLoginAt ? "text-slate-500" : "text-slate-300 italic"}>
                      {formatLastLogin(u.lastLoginAt)}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-400 whitespace-nowrap border-b-0">
                    {formatDate(u.createdAt)}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-right border-b-0">
                    <Button variant="outline" size="sm" onClick={() => setDrawer({ mode: "edit", user: u })}
                      leftIcon={<Pencil className="size-3" />}
                      className="border-slate-200 text-slate-600 text-[11px] font-semibold">Edit</Button>
                  </TableCell>
                </TableRow>
              ))}
              {/* Filler rows keep the table height — and the pagination bar below — fixed. */}
              {Array.from({ length: Math.max(0, PAGE_SIZE - users.length) }).map((_, i) => (
                <TableRow key={`filler-${i}`} hoverable={false} className="border-b border-slate-100">
                  <TableCell colSpan={COL_WIDTHS.length} className="py-3 px-4 border-b-0" aria-hidden="true">
                    <span className="invisible flex items-center gap-2.5"><span className="size-7" /><span className="text-xs">.</span></span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-slate-100 bg-slate-50/50">
            <p className="text-xs text-slate-500">
              Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
              <span className="text-slate-400 ml-2">· {totalElements} results</span>
            </p>
            <div className="flex items-center gap-1">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                <ChevronLeft className="size-3.5" /> Prev
              </button>
              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => Math.max(0, Math.min(page - 2, totalPages - 5)) + i).map(p => (
                <button key={p} onClick={() => setPage(p)}
                  className={`size-7 text-xs font-semibold rounded-lg border transition
                    ${p === page ? "bg-primary text-white border-primary shadow-sm" : "border-slate-200 text-slate-500 hover:bg-white"}`}>
                  {p + 1}
                </button>
              ))}
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                Next <ChevronRight className="size-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {drawer && (
        <UserFormDrawer mode={drawer.mode} user={drawer.user} roles={roles} onClose={() => setDrawer(null)} />
      )}
    </div>
  );
}

// ── Roles & Permissions tab (UC-6.4) ────────────────────────────────────────────

/**
 * Only Staff and Manager are permission-driven, so the screen shows them side by side as one
 * matrix rather than as separate cards: with two columns the interesting question is almost
 * always "what can a Manager do that Staff can't?", and a comparison view answers it at a glance.
 * The server marks each role `configurable`, so this never hard-codes role names.
 */

const MODULE_META: Record<string, { label: string; Icon: LucideIcon; blurb: string }> = {
  LEAD:         { label: "Leads",              Icon: Target,         blurb: "Inbound enquiries before they become customers" },
  CUSTOMER:     { label: "Customers",          Icon: Building2,      blurb: "Customer and contact profiles" },
  DEAL:         { label: "Deals",              Icon: Briefcase,      blurb: "Sales opportunities and their value" },
  PIPELINE:     { label: "Sales Pipeline",     Icon: GitBranch,      blurb: "The stage board across all open deals" },
  TASK:         { label: "Follow-up Tasks",    Icon: ClipboardList,  blurb: "Assigned follow-ups and their deadlines" },
  QUOTATION:    { label: "Quotations",         Icon: FileText,       blurb: "Room quotations, versions and approvals" },
  INTERACTION:  { label: "Interactions",       Icon: MessagesSquare, blurb: "The customer interaction timeline" },
  BOOKING:      { label: "Bookings",           Icon: CalendarCheck,  blurb: "Booking requests and their processing" },
  RESERVATION:  { label: "Reservations",       Icon: BedDouble,      blurb: "Confirmed reservations and cancellations" },
  HANDOVER:     { label: "Handovers",          Icon: PackageCheck,   blurb: "Operational handover to the front desk" },
  PAYMENT:      { label: "Payments",           Icon: CreditCard,     blurb: "Deposits, payment requests and status" },
  NOTIFICATION: { label: "Notifications",      Icon: Bell,           blurb: "The in-app notification feed" },
  REMINDER:     { label: "Reminders",          Icon: AlarmClock,     blurb: "Manual and automatic reminders" },
  SLA:          { label: "SLA",                Icon: Timer,          blurb: "SLA monitoring and rule configuration" },
  REPORTING:    { label: "Reporting",          Icon: BarChart3,      blurb: "Sales, task and pipeline reports" },
  FEEDBACK:     { label: "Customer Feedback",  Icon: Star,           blurb: "Feedback records and their review status" },
  CHAT:         { label: "AI Assistant",       Icon: Bot,            blurb: "The read-only internal chat assistant" },
  ROOM_REQUEST: { label: "Room Requests",      Icon: DoorOpen,       blurb: "Room availability requests" },
};

const ACTION_ORDER: Record<string, number> = { VIEW: 0, WRITE: 1, APPROVE: 2 };

const ACTION_STYLE: Record<string, { on: string; label: string }> = {
  VIEW:    { on: "border-sky-500/70 bg-sky-50 text-sky-700",           label: "View" },
  WRITE:   { on: "border-violet-500/70 bg-violet-50 text-violet-700",  label: "Edit" },
  APPROVE: { on: "border-emerald-500/70 bg-emerald-50 text-emerald-700", label: "Approve" },
};

const OFF_STYLE = "border-slate-200 bg-white text-slate-300 hover:border-slate-300 hover:text-slate-500";

const ROLE_COLUMN_ORDER = ["SALES", "MANAGER", "FO", "FRONT_OFFICE", "RESERVATION"];

/** Column position for a role; anything unlisted sorts to the end rather than to the front. */
function roleColumnRank(roleName: string): number {
  const i = ROLE_COLUMN_ORDER.indexOf(roleName.toUpperCase());
  return i === -1 ? ROLE_COLUMN_ORDER.length : i;
}

/** Accent per role column, so the desks read as a different kind of role from the sales pair. */
function roleAccent(roleName: string): string {
  switch (roleName.toUpperCase()) {
    case "MANAGER":                 return "bg-violet-50 text-violet-600";
    case "FO": case "FRONT_OFFICE": return "bg-amber-50 text-amber-600";
    case "RESERVATION":             return "bg-teal-50 text-teal-600";
    default:                        return "bg-sky-50 text-sky-600";
  }
}

function moduleMeta(module: string) {
  return MODULE_META[module] ?? { label: module.replace(/_/g, " ").toLowerCase(), Icon: ShieldCheck, blurb: "" };
}

function setsEqual(a: Set<number>, b: Set<number>) {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}

/** One View/Edit/Approve chip. */
function PermissionChip({
  permission, checked, changed, onToggle,
}: {
  permission: Permission;
  checked: boolean;
  changed: boolean;
  onToggle: () => void;
}) {
  const action = permission.action ?? "VIEW";
  const style = ACTION_STYLE[action] ?? ACTION_STYLE.VIEW;
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={checked}
      title={`${permission.label ?? permission.permissionCode}${changed ? " · unsaved" : ""}`}
      className={`relative inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-[11px] font-semibold
        transition-all duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400
        ${checked ? style.on : OFF_STYLE}
        ${changed ? "ring-2 ring-amber-300 ring-offset-1" : ""}`}
    >
      {checked ? <Check className="size-3" strokeWidth={3} /> : <Plus className="size-3" />}
      {style.label}
    </button>
  );
}

/** The two-column comparison grid, one row per module. */
function PermissionMatrix({ roles, permissions }: { roles: Role[]; permissions: Permission[] }) {
  const byId = useMemo(() => new Map(permissions.map(p => [p.permissionId, p])), [permissions]);

  const serverSets = useMemo(() => {
    const m = new Map<number, Set<number>>();
    roles.forEach(r => m.set(r.roleId, new Set(r.permissions.map(p => p.permissionId))));
    return m;
  }, [roles]);

  const [draft, setDraft] = useState<Map<number, Set<number>>>(serverSets);
  const [query, setQuery] = useState("");
  const [savedAt, setSavedAt] = useState<number | null>(null);
  const [serverError, setServerError] = useState("");
  const setPermissions = useSetRolePermissions();

  // Re-sync when the server data changes (after a save, or a refetch). Adjusting state during
  // render rather than in an effect means the grid never paints the stale set first.
  const [syncedFrom, setSyncedFrom] = useState(serverSets);
  if (syncedFrom !== serverSets) {
    setSyncedFrom(serverSets);
    setDraft(serverSets);
  }

  const dirtyRoleIds = useMemo(
    () => roles.filter(r => !setsEqual(draft.get(r.roleId) ?? new Set(), serverSets.get(r.roleId) ?? new Set()))
               .map(r => r.roleId),
    [roles, draft, serverSets],
  );

  const changedCount = useMemo(() => {
    let n = 0;
    for (const role of roles) {
      const now = draft.get(role.roleId) ?? new Set<number>();
      const was = serverSets.get(role.roleId) ?? new Set<number>();
      permissions.forEach(p => { if (now.has(p.permissionId) !== was.has(p.permissionId)) n++; });
    }
    return n;
  }, [roles, draft, serverSets, permissions]);

  const isChanged = (roleId: number, permissionId: number) =>
    (draft.get(roleId)?.has(permissionId) ?? false) !== (serverSets.get(roleId)?.has(permissionId) ?? false);

  /**
   * Toggle with the dependency cascade: granting an Edit/Approve pulls in its View, and revoking a
   * View drops everything that depends on it. The server prunes the same way, so this only keeps
   * the UI from showing a state the API would silently reject.
   */
  const toggle = (roleId: number, permission: Permission) => {
    setSavedAt(null);
    setServerError("");
    setDraft(prev => {
      const next = new Map(prev);
      const current = new Set(next.get(roleId) ?? []);
      if (current.has(permission.permissionId)) {
        const removeWithDependents = (id: number) => {
          current.delete(id);
          permissions.filter(p => p.dependsOnId === id).forEach(c => removeWithDependents(c.permissionId));
        };
        removeWithDependents(permission.permissionId);
      } else {
        let cur: Permission | undefined = permission;
        while (cur) {
          current.add(cur.permissionId);
          cur = cur.dependsOnId != null ? byId.get(cur.dependsOnId) : undefined;
        }
      }
      next.set(roleId, current);
      return next;
    });
  };

  const groups = useMemo(() => {
    const m = new Map<string, Permission[]>();
    for (const p of permissions) {
      const key = p.module ?? "OTHER";
      if (!m.has(key)) m.set(key, []);
      m.get(key)!.push(p);
    }
    for (const arr of m.values()) {
      arr.sort((a, b) => (ACTION_ORDER[a.action ?? ""] ?? 9) - (ACTION_ORDER[b.action ?? ""] ?? 9));
    }
    const q = query.trim().toLowerCase();
    return Array.from(m.entries())
      .filter(([module]) => !q || moduleMeta(module).label.toLowerCase().includes(q) || module.toLowerCase().includes(q))
      .sort((a, b) => moduleMeta(a[0]).label.localeCompare(moduleMeta(b[0]).label));
  }, [permissions, query]);

  const discard = () => { setDraft(serverSets); setServerError(""); setSavedAt(null); };

  const save = async () => {
    setServerError("");
    try {
      for (const roleId of dirtyRoleIds) {
        await setPermissions.mutateAsync({ roleId, permissionIds: Array.from(draft.get(roleId) ?? []) });
      }
      setSavedAt(Date.now());
    } catch (err) {
      setServerError(errorMessage(err));
    }
  };

  return (
    <div className="space-y-4">
      {/* Explainer */}
      <div className="flex items-start gap-2.5 px-3.5 py-3 bg-gradient-to-r from-blue-50/80 to-transparent border border-blue-100 rounded-xl">
        <ShieldCheck className="size-4 text-blue-500 shrink-0 mt-0.5" />
        <div className="text-[11px] leading-relaxed text-slate-500">
          <p>
            Every job role is configured here except <strong className="text-slate-700">Admin</strong>, which holds
            each permission by default. <strong className="text-slate-700">Front Office</strong> and{" "}
            <strong className="text-slate-700">Reservation</strong> are narrow desks — a module left blank for them is
            simply not part of that job.
          </p>
          <p className="mt-1">
            Turning off <span className="font-semibold text-sky-700">View</span> also removes{" "}
            <span className="font-semibold text-violet-700">Edit</span> and{" "}
            <span className="font-semibold text-emerald-700">Approve</span> for that module. Saved changes take effect
            on the next request each affected user makes — no sign-out required.
          </p>
        </div>
      </div>

      {/* Sticky action bar */}
      <div className="sticky top-0 z-20 -mx-1 px-1">
        <div className="flex flex-wrap items-center gap-3 px-3.5 py-2.5 bg-white/95 backdrop-blur border border-slate-200 rounded-xl shadow-sm">
          <div className="relative flex-1 min-w-45 max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 pointer-events-none" />
            <input
              type="text" value={query} onChange={e => setQuery(e.target.value)}
              placeholder="Filter modules…"
              className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg bg-slate-50
                         focus:bg-white focus:border-blue-400 focus:outline-none transition"
            />
          </div>

          <div className="ml-auto flex items-center gap-2.5">
            {serverError && (
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-rose-600">
                <ServerCrash className="size-3.5" />{serverError}
              </span>
            )}
            {!serverError && savedAt && changedCount === 0 && (
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-600">
                <Check className="size-3.5" strokeWidth={3} /> All changes saved
              </span>
            )}
            {changedCount > 0 && (
              <span className="flex items-center gap-1.5 text-[11px] font-semibold text-amber-600">
                <AlertCircle className="size-3.5" />
                {changedCount} unsaved change{changedCount === 1 ? "" : "s"}
              </span>
            )}
            <Button
              variant="ghost" size="sm" onClick={discard} disabled={changedCount === 0 || setPermissions.isPending}
              className="text-xs"
            >
              Discard
            </Button>
            <Button
              variant="primary" size="sm" onClick={save}
              disabled={changedCount === 0} isLoading={setPermissions.isPending}
              leftIcon={<Save className="size-3.5" />}
              className="text-xs"
            >
              Save changes
            </Button>
          </div>
        </div>
      </div>

      {/* Matrix */}
      <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          {/* Wide enough that four role columns keep their chips on one line; the wrapper scrolls. */}
          <table className="w-full min-w-260 border-collapse">
            <thead>
              <tr className="bg-slate-50/80 border-b border-slate-200">
                <th className="text-left py-3 px-4 w-[24%] min-w-56">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">Module</span>
                </th>
                {roles.map(role => {
                  const granted = draft.get(role.roleId)?.size ?? 0;
                  const dirty = dirtyRoleIds.includes(role.roleId);
                  return (
                    <th key={role.roleId} className="text-left py-2.5 px-4 border-l border-slate-200">
                      <div className="flex items-center gap-2">
                        <span className={`size-7 rounded-lg flex items-center justify-center shrink-0
                          ${roleAccent(role.roleName)}`}>
                          <ShieldCheck className="size-3.5" />
                        </span>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs font-bold text-slate-800">{roleLabel(role.roleName)}</span>
                            {dirty && <span className="size-1.5 rounded-full bg-amber-400" title="Unsaved changes" />}
                          </div>
                          <span className="block text-[10px] text-slate-400 font-medium">
                            {role.userCount} user{role.userCount === 1 ? "" : "s"} · {granted} permission{granted === 1 ? "" : "s"}
                          </span>
                        </div>
                      </div>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {groups.length === 0 && (
                <tr>
                  <td colSpan={roles.length + 1} className="py-14 text-center text-sm text-slate-400">
                    No module matches “{query}”.
                  </td>
                </tr>
              )}
              {groups.map(([module, perms], i) => {
                const meta = moduleMeta(module);
                return (
                  <tr key={module}
                      className={`border-b border-slate-100 last:border-b-0 transition-colors hover:bg-slate-50/60
                                  ${i % 2 === 1 ? "bg-slate-50/30" : ""}`}>
                    <td className="py-2.5 px-4 align-middle">
                      <div className="flex items-center gap-2.5">
                        <span className="size-7 rounded-lg bg-slate-100 text-slate-500 flex items-center justify-center shrink-0">
                          <meta.Icon className="size-3.5" />
                        </span>
                        <div className="min-w-0">
                          <span className="block text-xs font-semibold text-slate-700 truncate">{meta.label}</span>
                          {meta.blurb && (
                            <span className="block text-[10px] text-slate-400 truncate">{meta.blurb}</span>
                          )}
                        </div>
                      </div>
                    </td>
                    {roles.map(role => (
                      <td key={role.roleId} className="py-2.5 px-4 align-middle border-l border-slate-100">
                        <div className="flex flex-wrap gap-1.5">
                          {perms.map(p => (
                            <PermissionChip
                              key={p.permissionId}
                              permission={p}
                              checked={draft.get(role.roleId)?.has(p.permissionId) ?? false}
                              changed={isChanged(role.roleId, p.permissionId)}
                              onToggle={() => toggle(role.roleId, p)}
                            />
                          ))}
                        </div>
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Legend */}
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 px-4 py-2.5 border-t border-slate-100 bg-slate-50/50">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">Legend</span>
          {(["VIEW", "WRITE", "APPROVE"] as const).map(a => (
            <span key={a} className="flex items-center gap-1.5 text-[11px] text-slate-500">
              <span className={`inline-flex items-center gap-1 rounded-lg border px-2 py-0.5 text-[10px] font-semibold ${ACTION_STYLE[a].on}`}>
                <Check className="size-2.5" strokeWidth={3} />{ACTION_STYLE[a].label}
              </span>
              {a === "VIEW" ? "open the screen" : a === "WRITE" ? "create & edit records" : "approve or reject"}
            </span>
          ))}
          <span className="flex items-center gap-1.5 text-[11px] text-slate-500">
            <span className="inline-block size-3 rounded ring-2 ring-amber-300" /> unsaved
          </span>
        </div>
      </div>
    </div>
  );
}

function RolesTab() {
  const { data: rolesResp, isLoading, isError } = useRoles();
  const { data: permsResp, isLoading: permsLoading } = usePermissions();

  // The API marks which roles are permission-driven. Sales sits leftmost and Manager next, so the
  // first two columns read as "base set → what Manager adds on top"; the operational desks follow.
  const roles = useMemo(
    () => (rolesResp?.data ?? [])
      .filter(r => r.configurable)
      .sort((a, b) => roleColumnRank(a.roleName) - roleColumnRank(b.roleName)),
    [rolesResp],
  );
  const permissions = permsResp?.data ?? [];

  if (isLoading || permsLoading) return (
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
  if (roles.length === 0) return (
    <div className="flex flex-col items-center justify-center py-20 text-slate-400">
      <ShieldCheck className="size-10 mb-3 opacity-30" />
      <p className="text-sm font-medium">No configurable role found</p>
    </div>
  );

  return <PermissionMatrix roles={roles} permissions={permissions} />;
}

// ── Screen ──────────────────────────────────────────────────────────────────────

export function IdentityAccessScreen() {
  const [tab, setTab] = useState<"users" | "roles">("users");
  const { data: rolesResp } = useRoles();
  const roles = rolesResp?.data ?? [];

  const TABS = [
    { key: "users" as const, label: "User Accounts", icon: Users },
    { key: "roles" as const, label: "Roles & Permissions", icon: ShieldCheck },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-slate-800">Identity &amp; Access Control</h1>
        <p className="text-xs text-slate-400">Manage internal user accounts, assign roles, and configure role permissions</p>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 p-0.5 rounded-lg bg-slate-100 border border-slate-200 w-fit">
        {TABS.map(t => {
          const active = tab === t.key;
          return (
            <button key={t.key} onClick={() => setTab(t.key)}
              className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-md text-xs font-semibold transition
                ${active ? "bg-white text-blue-700 shadow-sm ring-1 ring-slate-200" : "text-slate-500 hover:text-slate-700"}`}>
              <t.icon className="size-3.5" />
              {t.label}
            </button>
          );
        })}
      </div>

      {tab === "users" ? <UsersTab roles={roles} /> : <RolesTab />}
    </div>
  );
}
