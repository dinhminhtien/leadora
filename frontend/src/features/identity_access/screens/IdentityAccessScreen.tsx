"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  Users, ShieldCheck, Search, UserPlus, X, AlertCircle, Loader2, ServerCrash,
  ChevronLeft, ChevronRight, Pencil, KeyRound, Check, Plus, Save,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
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
    case "SALES":   return "Staff";
    case "MANAGER": return "Manager";
    case "ADMIN":   return "Admin";
    default:        return roleName ?? "—";
  }
}

// Name: letters (any language), spaces and basic name punctuation — no digits/symbols.
const NAME_ALLOWED = /^[\p{L}\s.'-]+$/u;

// Rows per page + fixed column widths so the table height (and the pagination bar
// below it) stays constant across pages — mirrors the Leads list.
const PAGE_SIZE = 10;
const COL_WIDTHS = ["24%", "28%", "13%", "12%", "13%", "10%"];

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

function StatusBadge({ status }: { status: UserStatus }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.INACTIVE;
  return <Badge variant={cfg.variant} size="sm" className="font-bold text-[9px] uppercase">{cfg.label}</Badge>;
}

function FieldError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="mt-1 text-xs text-rose-500 flex items-center gap-1"><AlertCircle className="size-3" />{msg}</p>;
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

    const onError = (err: any) => {
      if (err?.response?.status >= 500) setServerError("Server error — please contact your Admin.");
      else setServerError(err?.response?.data?.message || "Something went wrong. Please try again.");
    };

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
              <Input placeholder="e.g. 09xxxxxxxx" value={phone}
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
          <div className="relative flex-1 min-w-[180px] max-w-xs">
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
                  <TableCell className="py-3 px-4 text-xs text-slate-400 whitespace-nowrap border-b-0">
                    {u.createdAt ? new Date(u.createdAt).toLocaleDateString("en-US", { day: "2-digit", month: "short", year: "numeric" }) : "—"}
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

function RoleCard({ role, allPermissions }: { role: Role; allPermissions: Permission[] }) {
  const assignedIds = useMemo(() => new Set(role.permissions.map(p => p.permissionId)), [role.permissions]);
  const [selected, setSelected] = useState<Set<number>>(assignedIds);
  const [saved, setSaved] = useState(false);
  const setPermissions = useSetRolePermissions();

  // Re-sync when server data changes (e.g. after a successful save / refetch).
  useEffect(() => { setSelected(new Set(role.permissions.map(p => p.permissionId))); }, [role.permissions]);

  const dirty = useMemo(() => {
    if (selected.size !== assignedIds.size) return true;
    for (const id of selected) if (!assignedIds.has(id)) return true;
    return false;
  }, [selected, assignedIds]);

  const byId = useMemo(() => new Map(allPermissions.map(p => [p.permissionId, p])), [allPermissions]);

  // Toggle with dependency cascade:
  //  • turning a permission OFF also removes everything that depends on it
  //    (e.g. removing LEAD_VIEW removes LEAD_WRITE).
  //  • turning a permission ON also adds its prerequisite chain
  //    (e.g. enabling LEAD_WRITE enables LEAD_VIEW).
  const toggle = (perm: Permission) => {
    setSaved(false);
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(perm.permissionId)) {
        const removeWithDependents = (id: number) => {
          next.delete(id);
          allPermissions.filter(p => p.dependsOnId === id).forEach(child => removeWithDependents(child.permissionId));
        };
        removeWithDependents(perm.permissionId);
      } else {
        let cur: Permission | undefined = perm;
        while (cur) {
          next.add(cur.permissionId);
          cur = cur.dependsOnId != null ? byId.get(cur.dependsOnId) : undefined;
        }
      }
      return next;
    });
  };

  // Group permissions by module, ordered VIEW → WRITE → APPROVE within each module.
  const ACTION_ORDER: Record<string, number> = { VIEW: 0, WRITE: 1, APPROVE: 2 };
  const groups = useMemo(() => {
    const m = new Map<string, Permission[]>();
    for (const p of allPermissions) {
      const key = p.module ?? "OTHER";
      if (!m.has(key)) m.set(key, []);
      m.get(key)!.push(p);
    }
    for (const arr of m.values()) {
      arr.sort((a, b) => (ACTION_ORDER[a.action ?? ""] ?? 9) - (ACTION_ORDER[b.action ?? ""] ?? 9));
    }
    return Array.from(m.entries()).sort((a, b) => a[0].localeCompare(b[0]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allPermissions]);

  const handleSave = () => {
    setPermissions.mutate(
      { roleId: role.roleId, permissionIds: Array.from(selected) },
      { onSuccess: () => { setSaved(true); setTimeout(() => setSaved(false), 2000); } }
    );
  };

  return (
    <Card className="border-slate-100 shadow-sm bg-white">
      <CardContent className="p-0">
        <div className="flex items-start justify-between px-5 py-4 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <span className="size-9 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <ShieldCheck className="size-4.5" />
            </span>
            <div>
              <h3 className="text-sm font-bold text-slate-800">{role.roleName}</h3>
              <p className="text-[11px] text-slate-400">{role.description ?? "No description"} · {role.userCount} user{role.userCount === 1 ? "" : "s"}</p>
            </div>
          </div>
          <Button variant="primary" size="sm" onClick={handleSave} disabled={!dirty}
            isLoading={setPermissions.isPending}
            leftIcon={saved ? <Check className="size-3.5" /> : <Save className="size-3.5" />}
            className={`text-xs font-semibold ${saved ? "bg-emerald-600 hover:bg-emerald-600" : "bg-primary hover:bg-primary/90"} text-white disabled:opacity-40`}>
            {saved ? "Saved" : "Save"}
          </Button>
        </div>
        <div className="divide-y divide-slate-50">
          {allPermissions.length === 0 && <p className="px-5 py-4 text-xs text-slate-400">No permissions defined.</p>}
          {groups.map(([module, perms]) => (
            <div key={module} className="flex items-center justify-between gap-4 px-5 py-3">
              <span className="text-xs font-bold text-slate-600 capitalize w-32 shrink-0">{module.toLowerCase()}</span>
              <div className="flex flex-wrap gap-2 justify-end flex-1">
                {perms.map(p => {
                  const on = selected.has(p.permissionId);
                  const actionLabel = (p.action ?? p.permissionCode).charAt(0) + (p.action ?? "").slice(1).toLowerCase();
                  return (
                    <button key={p.permissionId} type="button" onClick={() => toggle(p)}
                      title={p.label ?? p.description ?? p.permissionCode}
                      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[11px] font-semibold border transition
                        ${on
                          ? "border-blue-500 bg-blue-50 text-blue-700"
                          : "border-slate-200 bg-white text-slate-400 hover:border-slate-300 hover:text-slate-600"}`}>
                      {on ? <Check className="size-3" /> : <Plus className="size-3" />}
                      {actionLabel || p.permissionCode}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function RolesTab() {
  const { data: rolesResp, isLoading, isError } = useRoles();
  const { data: permsResp } = usePermissions();
  // Admin is full-access by default — it is never configured here.
  const roles = (rolesResp?.data ?? []).filter(r => r.roleName.toUpperCase() !== "ADMIN");
  const permissions = permsResp?.data ?? [];

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

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-2 px-3 py-2.5 bg-blue-50/60 border border-blue-100 rounded-xl text-[11px] text-slate-500">
        <ShieldCheck className="size-4 text-blue-500 shrink-0 mt-px" />
        <span>
          <strong className="text-slate-700">Admin</strong> has full access by default and isn&apos;t listed here.
          Toggle a permission then press <strong className="text-slate-600">Save</strong> — changes apply immediately to every user holding the role.
        </span>
      </div>
      {roles.map(role => <RoleCard key={role.roleId} role={role} allPermissions={permissions} />)}
    </div>
  );
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
