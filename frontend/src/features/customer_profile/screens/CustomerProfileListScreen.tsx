"use client";

import React, { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  Search,
  UserPlus,
  Users,
  Building2,
  Phone,
  Mail,
  ChevronLeft,
  ChevronRight,
  Loader2,
  UserCog,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { useCustomers, useCreateCustomer, useCustomerStats } from "@/features/customer_profile/hooks/use_customer_profiles";
import { useQuery } from "@tanstack/react-query";
import { userService } from "@/services/user_service";
import type { Customer, CustomerType, CustomerListParams } from "@/services/customer_profile_service";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";

// ── Helpers ──────────────────────────────────────────────────────────────────

function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  React.useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

const TYPE_BADGE: Record<CustomerType, string> = {
  INDIVIDUAL: "bg-blue-100 text-blue-700",
  CORPORATE: "bg-purple-100 text-purple-700",
};
const TYPE_LABEL: Record<CustomerType, string> = {
  INDIVIDUAL: "Individual",
  CORPORATE: "Corporate",
};
const STATUS_BADGE: Record<"ACTIVE" | "INACTIVE", import("@/components/ui/Badge").BadgeVariant> = {
  ACTIVE: "success",
  INACTIVE: "default",
};

const AVATAR_COLORS = [
  "bg-blue-100 text-blue-700",
  "bg-purple-100 text-purple-700",
  "bg-green-100 text-green-700",
  "bg-amber-100 text-amber-700",
  "bg-rose-100 text-rose-700",
];
function avatarColor(name: string) {
  const code = name.charCodeAt(0) + (name.charCodeAt(1) || 0);
  return AVATAR_COLORS[code % AVATAR_COLORS.length];
}
function initials(name: string) {
  return name
    .split(" ")
    .slice(0, 2)
    .map(w => w[0])
    .join("")
    .toUpperCase();
}

// ── Create Customer Drawer ────────────────────────────────────────────────────

function CreateCustomerDrawer({
  onClose,
  users,
}: {
  onClose: () => void;
  users: { userId: string; fullName: string }[];
}) {
  const [form, setForm] = useState({
    customerType: "INDIVIDUAL" as CustomerType,
    fullName: "",
    email: "",
    phone: "",
    companyName: "",
    taxCode: "",
    address: "",
    assignedUserId: "",
  });

  const createMutation = useCreateCustomer();

  function set(key: keyof typeof form, value: string) {
    setForm(f => ({ ...f, [key]: value }));
  }

  function handleSubmit(e: React.FormEvent | React.MouseEvent) {
    e.preventDefault();
    if (!form.fullName.trim()) return;
    createMutation.mutate(
      {
        fullName: form.fullName.trim(),
        customerType: form.customerType,
        email: form.email || undefined,
        phone: form.phone || undefined,
        companyName: form.companyName || undefined,
        taxCode: form.taxCode || undefined,
        address: form.address || undefined,
        assignedUserId: form.assignedUserId || undefined,
      },
      {
        onSuccess: () => {
          toast.success("Customer profile created successfully.");
          onClose();
        },
        onError: err => toast.error(getApiErrorMessage(err, "Failed to create customer.")),
      }
    );
  }

  const isCorporate = form.customerType === "CORPORATE";

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="w-full max-w-md bg-white shadow-2xl flex flex-col h-full">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <div>
            <h2 className="text-base font-bold text-slate-800">New Customer Profile</h2>
            <p className="text-xs text-slate-400 mt-0.5">Fill in the customer details below</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 transition">
            <X className="size-4 text-slate-500" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {/* Customer Type */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1.5">Customer Type *</label>
            <div className="grid grid-cols-2 gap-2">
              {(["INDIVIDUAL", "CORPORATE"] as const).map(type => (
                <button
                  key={type}
                  type="button"
                  onClick={() => set("customerType", type)}
                  className={`py-2 rounded-xl text-xs font-semibold border transition ${form.customerType === type
                      ? "border-blue-500 bg-blue-50 text-blue-700"
                      : "border-slate-200 text-slate-600 hover:border-slate-300"
                    }`}
                >
                  {type === "INDIVIDUAL" ? "Individual" : "Corporate / Org"}
                </button>
              ))}
            </div>
          </div>

          {/* Full Name */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">
              {isCorporate ? "Representative Name" : "Full Name"} *
            </label>
            <Input
              required
              value={form.fullName}
              onChange={e => set("fullName", e.target.value)}
              placeholder={isCorporate ? "Contact person name" : "Customer full name"}
            />
          </div>

          {/* Corporate: Company Name */}
          {isCorporate && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Company Name</label>
              <Input
                value={form.companyName}
                onChange={e => set("companyName", e.target.value)}
                placeholder="Company or organization name"
              />
            </div>
          )}

          {/* Contact: Phone + Email */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                {isCorporate ? "Business Phone" : "Phone"}
              </label>
              <Input
                type="tel"
                value={form.phone}
                onChange={e => set("phone", e.target.value)}
                placeholder="0909 xxx xxx"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                {isCorporate ? "Business Email" : "Email"}
              </label>
              <Input
                type="email"
                value={form.email}
                onChange={e => set("email", e.target.value)}
                placeholder="email@example.com"
              />
            </div>
          </div>

          {/* Corporate: Tax Code */}
          {isCorporate && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Tax Code</label>
              <Input
                value={form.taxCode}
                onChange={e => set("taxCode", e.target.value)}
                placeholder="Tax identification number"
              />
            </div>
          )}

          {/* Address */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">Address</label>
            <textarea
              value={form.address}
              onChange={e => set("address", e.target.value)}
              rows={2}
              placeholder={isCorporate ? "Company address" : "Customer address"}
              className="w-full px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition resize-none"
            />
          </div>

          {/* Assigned To */}
          {users.length > 0 && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Assigned To</label>
              <select
                value={form.assignedUserId}
                onChange={e => set("assignedUserId", e.target.value)}
                className="w-full px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition"
              >
                <option value="">— Unassigned —</option>
                {users.map(u => (
                  <option key={u.userId} value={u.userId}>{u.fullName}</option>
                ))}
              </select>
            </div>
          )}
        </form>

        {/* Footer */}
        <div className="flex gap-2 px-5 py-4 border-t border-slate-100">
          <Button type="button" variant="ghost" className="flex-1" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            className="flex-1"
            disabled={createMutation.isPending}
            onClick={handleSubmit}
          >
            {createMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : "Create Customer"}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────

export function CustomerProfileListScreen() {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<CustomerType | "">("");
  const [statusFilter, setStatusFilter] = useState<"ACTIVE" | "INACTIVE" | "">("");
  const [page, setPage] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  const debouncedSearch = useDebounce(search);

  const params: CustomerListParams = useMemo(() => ({
    search: debouncedSearch || undefined,
    customerType: typeFilter || undefined,
    status: statusFilter || undefined,
    page,
    size: 10,
    sortBy: "createdAt",
    sortDir: "desc",
  }), [debouncedSearch, typeFilter, statusFilter, page]);

  const { data, isLoading } = useCustomers(params);
  const { data: statsData } = useCustomerStats();
  const { data: usersData } = useQuery({
    queryKey: ["users-list-summary"],
    queryFn: () => userService.getList({ size: 100, status: "ACTIVE" }),
  });

  const customers = data?.data?.content ?? [];
  const totalPages = (() => {
    const p = data?.data?.page;
    if (!p) return 1;
    if (typeof p === "number") return Math.ceil((data?.data as { totalElements?: number })?.totalElements ?? 0 / 10);
    return p.totalPages ?? 1;
  })();
  const totalElements = (() => {
    const p = data?.data?.page;
    if (!p) return 0;
    if (typeof p === "number") return 0;
    return p.totalElements ?? 0;
  })();
  const users = usersData?.data?.content ?? [];

  const stats = {
    total: statsData?.data?.total ?? totalElements,
    active: statsData?.data?.active ?? 0,
    individual: statsData?.data?.individual ?? 0,
    corporate: statsData?.data?.corporate ?? 0,
  };

  function handlePageChange(newPage: number) {
    if (newPage < 0 || newPage >= totalPages) return;
    setPage(newPage);
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Customer Profiles</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            Manage individual and corporate customer relationships
          </p>
        </div>
        <Button
          variant="primary"
          size="sm"
          onClick={() => setIsCreateOpen(true)}
          leftIcon={<UserPlus className="size-3.5" />}
        >
          New Customer
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: "Total Customers", value: totalElements, icon: <Users className="size-4 text-slate-400" />, color: "text-slate-800" },
          { label: "Active", value: stats.active, icon: <div className="size-2 rounded-full bg-green-500 mt-1" />, color: "text-green-700" },
          { label: "Individual", value: stats.individual, icon: <Users className="size-4 text-blue-400" />, color: "text-blue-700" },
          { label: "Corporate", value: stats.corporate, icon: <Building2 className="size-4 text-purple-400" />, color: "text-purple-700" },
        ].map(stat => (
          <div key={stat.label} className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">{stat.label}</span>
              {stat.icon}
            </div>
            <p className={`text-2xl font-bold ${stat.color}`}>{stat.value}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search name, email, phone, company..."
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(0); }}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>

          {/* Type filter */}
          <div className="flex items-center gap-1 p-0.5 bg-slate-100 rounded-lg">
            {([["", "All"], ["INDIVIDUAL", "Individual"], ["CORPORATE", "Corporate"]] as const).map(([val, label]) => (
              <button
                key={val}
                onClick={() => { setTypeFilter(val as CustomerType | ""); setPage(0); }}
                className={`px-3 py-1 rounded-md text-[11px] font-semibold transition ${typeFilter === val ? "bg-white shadow text-slate-800" : "text-slate-500 hover:text-slate-700"
                  }`}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Status filter */}
          <select
            value={statusFilter}
            onChange={e => { setStatusFilter(e.target.value as "ACTIVE" | "INACTIVE" | ""); setPage(0); }}
            className="px-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition"
          >
            <option value="">All Status</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100">
            <TableRow hoverable={false}>
              <TableHead className="text-xs font-semibold text-slate-500 pl-4">Customer</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Type</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Contact</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Company</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Assigned To</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Status</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500">Joined</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-12 text-center">
                  <Loader2 className="size-5 animate-spin text-slate-400 mx-auto" />
                </TableCell>
              </TableRow>
            ) : customers.length === 0 ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-12 text-center text-slate-400 text-xs">
                  No customer profiles found.
                </TableCell>
              </TableRow>
            ) : (
              customers.map(c => <CustomerRow key={c.customerId} customer={c} onClick={() => router.push(`/customer-profiles/${c.customerId}`)} />)
            )}
          </TableBody>
        </Table>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100">
            <span className="text-xs text-slate-400">
              Page {page + 1} of {totalPages} · {totalElements} customers
            </span>
            <div className="flex items-center gap-1">
              <button
                disabled={page === 0}
                onClick={() => handlePageChange(page - 1)}
                className="p-1.5 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition"
              >
                <ChevronLeft className="size-4 text-slate-600" />
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => handlePageChange(page + 1)}
                className="p-1.5 rounded-lg hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition"
              >
                <ChevronRight className="size-4 text-slate-600" />
              </button>
            </div>
          </div>
        )}
      </div>

      {isCreateOpen && (
        <CreateCustomerDrawer
          onClose={() => setIsCreateOpen(false)}
          users={users.map(u => ({ userId: u.userId, fullName: u.fullName }))}
        />
      )}
    </div>
  );
}

function CustomerRow({ customer, onClick }: { customer: Customer; onClick: () => void }) {
  const ac = avatarColor(customer.fullName);
  const joined = new Date(customer.createdAt).toLocaleDateString("en-GB", {
    day: "2-digit", month: "short", year: "numeric",
  });

  return (
    <TableRow className="hover:bg-slate-50/70 border-b border-slate-100 cursor-pointer transition" onClick={onClick}>
      <TableCell className="py-3 pl-4">
        <div className="flex items-center gap-2.5">
          <span className={`size-8 rounded-full text-[11px] font-bold flex items-center justify-center shrink-0 ${ac}`}>
            {initials(customer.fullName)}
          </span>
          <div>
            <p className="text-xs font-semibold text-slate-800">{customer.fullName}</p>
            {customer.email && (
              <p className="text-[10px] text-slate-400 truncate max-w-[160px]">{customer.email}</p>
            )}
          </div>
        </div>
      </TableCell>
      <TableCell className="py-3">
        <span className={`inline-block text-[10px] font-bold px-2 py-0.5 rounded-full ${TYPE_BADGE[customer.customerType]}`}>
          {TYPE_LABEL[customer.customerType]}
        </span>
      </TableCell>
      <TableCell className="py-3">
        <div className="space-y-0.5">
          {customer.phone && (
            <div className="flex items-center gap-1">
              <Phone className="size-3 text-slate-400 shrink-0" />
              <span className="text-[11px] text-slate-600">{customer.phone}</span>
            </div>
          )}
          {customer.email && (
            <div className="flex items-center gap-1">
              <Mail className="size-3 text-slate-400 shrink-0" />
              <span className="text-[11px] text-slate-500 truncate max-w-[140px]">{customer.email}</span>
            </div>
          )}
        </div>
      </TableCell>
      <TableCell className="py-3">
        {customer.companyName ? (
          <div className="flex items-center gap-1.5">
            <Building2 className="size-3.5 text-slate-400 shrink-0" />
            <span className="text-xs text-slate-600">{customer.companyName}</span>
          </div>
        ) : (
          <span className="text-xs text-slate-300">—</span>
        )}
      </TableCell>
      <TableCell className="py-3">
        {customer.assignedUserName ? (
          <div className="flex items-center gap-1.5">
            <UserCog className="size-3.5 text-slate-400" />
            <span className="text-xs text-slate-600">{customer.assignedUserName}</span>
          </div>
        ) : (
          <span className="text-xs text-slate-300">Unassigned</span>
        )}
      </TableCell>
      <TableCell className="py-3">
        <Badge variant={STATUS_BADGE[customer.status]} size="sm" className="text-[10px] font-bold">
          {customer.status}
        </Badge>
      </TableCell>
      <TableCell className="py-3 text-xs text-slate-500 whitespace-nowrap">{joined}</TableCell>
    </TableRow>
  );
}
