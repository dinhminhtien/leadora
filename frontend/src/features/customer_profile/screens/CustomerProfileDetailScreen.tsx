"use client";

import React, { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  Phone,
  Mail,
  MapPin,
  Building2,
  User,
  ArrowLeft,
  Edit2,
  X,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Clock,
  Calendar,
  Link2,
  UserCog,
  Tag,
  ChevronRight,
  Briefcase,
  BookOpen,
  FileText,
  DollarSign,
} from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import {
  useCustomerDetail,
  useCustomerHistory,
  useCustomerTasks,
  useUpdateCustomer,
} from "@/features/customer_profile/hooks/use_customer_profiles";
import { useQuery } from "@tanstack/react-query";
import { userService } from "@/services/user_service";
import type { Customer, CustomerHistoryItem, CustomerType, CustomerStatus, UpdateCustomerPayload } from "@/services/customer_profile_service";
import type { Task } from "@/services/follow_up_task_service";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";

// ── Helpers ───────────────────────────────────────────────────────────────────

const TYPE_BADGE: Record<CustomerType, string> = {
  INDIVIDUAL: "bg-blue-100 text-blue-700 border-blue-200",
  CORPORATE:  "bg-purple-100 text-purple-700 border-purple-200",
};
const TYPE_LABEL: Record<CustomerType, string> = {
  INDIVIDUAL: "Individual",
  CORPORATE:  "Corporate",
};

function initials(name: string) {
  return name.split(" ").slice(0, 2).map(w => w[0]).join("").toUpperCase();
}

function isOverdue(task: Task) {
  return task.status === "OPEN" && !!task.endAt && new Date(task.endAt) < new Date();
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}
function fmtDateTime(iso: string) {
  return new Date(iso).toLocaleString("en-GB", {
    day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

// ── Edit Drawer ───────────────────────────────────────────────────────────────

function EditCustomerDrawer({
  customer,
  onClose,
  users,
}: {
  customer: Customer;
  onClose: () => void;
  users: { userId: string; fullName: string }[];
}) {
  const [form, setForm] = useState<UpdateCustomerPayload>({
    fullName: customer.fullName,
    customerType: customer.customerType,
    email: customer.email ?? "",
    phone: customer.phone ?? "",
    companyName: customer.companyName ?? "",
    taxCode: customer.taxCode ?? "",
    address: customer.address ?? "",
    status: customer.status,
    assignedUserId: customer.assignedUserId ?? "",
  });

  const updateMutation = useUpdateCustomer(customer.customerId);

  function set<K extends keyof typeof form>(key: K, value: typeof form[K]) {
    setForm(f => ({ ...f, [key]: value }));
  }

  function handleSubmit(e: React.FormEvent | React.MouseEvent) {
    e.preventDefault();
    if (!form.fullName?.trim()) return;
    updateMutation.mutate(
      {
        ...form,
        email: form.email || undefined,
        phone: form.phone || undefined,
        companyName: form.companyName || undefined,
        taxCode: form.taxCode || undefined,
        address: form.address || undefined,
        assignedUserId: form.assignedUserId || undefined,
      },
      {
        onSuccess: () => { toast.success("Customer profile updated."); onClose(); },
        onError: err => toast.error(getApiErrorMessage(err, "Failed to update customer.")),
      }
    );
  }

  const isCorporate = form.customerType === "CORPORATE";

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="w-full max-w-md bg-white shadow-2xl flex flex-col h-full">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <div>
            <h2 className="text-base font-bold text-slate-800">Edit Customer Profile</h2>
            <p className="text-xs text-slate-400 mt-0.5">{customer.fullName}</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100 transition">
            <X className="size-4 text-slate-500" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {/* Customer Type */}
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1.5">Customer Type</label>
            <div className="grid grid-cols-2 gap-2">
              {(["INDIVIDUAL", "CORPORATE"] as const).map(type => (
                <button
                  key={type}
                  type="button"
                  onClick={() => set("customerType", type)}
                  className={`py-2 rounded-xl text-xs font-semibold border transition ${
                    form.customerType === type
                      ? "border-blue-500 bg-blue-50 text-blue-700"
                      : "border-slate-200 text-slate-600 hover:border-slate-300"
                  }`}
                >
                  {type === "INDIVIDUAL" ? "Individual" : "Corporate / Org"}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">
              {isCorporate ? "Representative Name" : "Full Name"} *
            </label>
            <Input required value={form.fullName} onChange={e => set("fullName", e.target.value)} />
          </div>

          {isCorporate && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Company Name</label>
              <Input value={form.companyName ?? ""} onChange={e => set("companyName", e.target.value)} />
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                {isCorporate ? "Business Phone" : "Phone"}
              </label>
              <Input type="tel" value={form.phone ?? ""} onChange={e => set("phone", e.target.value)} />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">
                {isCorporate ? "Business Email" : "Email"}
              </label>
              <Input type="email" value={form.email ?? ""} onChange={e => set("email", e.target.value)} />
            </div>
          </div>

          {isCorporate && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Tax Code</label>
              <Input value={form.taxCode ?? ""} onChange={e => set("taxCode", e.target.value)} />
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">Address</label>
            <textarea
              value={form.address ?? ""}
              onChange={e => set("address", e.target.value)}
              rows={2}
              className="w-full px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition resize-none"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1">Status</label>
            <select
              value={form.status}
              onChange={e => set("status", e.target.value as CustomerStatus)}
              className="w-full px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-800 focus:outline-none focus:border-blue-400 focus:bg-white transition"
            >
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>

          {users.length > 0 && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1">Assigned To</label>
              <select
                value={form.assignedUserId ?? ""}
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

        <div className="flex gap-2 px-5 py-4 border-t border-slate-100">
          <Button type="button" variant="ghost" className="flex-1" onClick={onClose}>Cancel</Button>
          <Button
            type="submit"
            variant="primary"
            className="flex-1"
            disabled={updateMutation.isPending}
            onClick={handleSubmit}
          >
            {updateMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : "Save Changes"}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Task Card ─────────────────────────────────────────────────────────────────

function TaskCard({ task }: { task: Task }) {
  const overdue = isOverdue(task);
  const statusColor = overdue
    ? "bg-red-100 text-red-700"
    : task.status === "COMPLETED"
    ? "bg-green-100 text-green-700"
    : task.status === "CANCELLED"
    ? "bg-slate-100 text-slate-500"
    : "bg-yellow-100 text-yellow-700";

  const statusLabel = overdue ? "Overdue" : task.status === "OPEN" ? "Open" : task.status === "COMPLETED" ? "Completed" : "Cancelled";

  const priorityColor: Record<string, string> = {
    HIGH: "text-orange-600", MEDIUM: "text-yellow-600", LOW: "text-green-600", CRITICAL: "text-red-600",
  };

  return (
    <div className={`p-3.5 rounded-xl border transition ${overdue ? "border-red-200 bg-red-50/40" : "border-slate-200 bg-white hover:border-slate-300"}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-slate-800 truncate">{task.title}</p>
          {task.description && (
            <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{task.description}</p>
          )}
          <div className="flex items-center gap-3 mt-1.5">
            {task.endAt && (
              <div className="flex items-center gap-1">
                <Calendar className="size-3 text-slate-400" />
                <span className={`text-[11px] ${overdue ? "text-red-600 font-semibold" : "text-slate-500"}`}>
                  {fmtDate(task.endAt)}
                </span>
              </div>
            )}
            {task.assignedUserName && (
              <div className="flex items-center gap-1">
                <User className="size-3 text-slate-400" />
                <span className="text-[11px] text-slate-500">{task.assignedUserName}</span>
              </div>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end gap-1.5 shrink-0">
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${statusColor}`}>{statusLabel}</span>
          <span className={`text-[10px] font-semibold ${priorityColor[task.priority] ?? "text-slate-500"}`}>
            {task.priority}
          </span>
        </div>
      </div>
    </div>
  );
}

// ── Main Detail Screen ────────────────────────────────────────────────────────

export function CustomerProfileDetailScreen({ customerId }: { customerId: string }) {
  const router = useRouter();
  const [editOpen, setEditOpen] = useState(false);
  const [tab, setTab] = useState("overview");

  const { data: customerData, isLoading } = useCustomerDetail(customerId);
  const { data: tasksData } = useCustomerTasks(customerId);
  const { data: historyData } = useCustomerHistory(customerId);
  const { data: usersData } = useQuery({
    queryKey: ["users-list-summary"],
    queryFn: () => userService.getList({ size: 100, status: "ACTIVE" }),
  });

  const customer = customerData?.data as Customer | undefined;
  const tasks: Task[] = tasksData?.data?.content ?? [];
  const users = usersData?.data?.content ?? [];

  const taskStats = useMemo(() => ({
    total: tasks.length,
    open: tasks.filter(t => t.status === "OPEN" && !isOverdue(t)).length,
    overdue: tasks.filter(isOverdue).length,
    completed: tasks.filter(t => t.status === "COMPLETED").length,
    cancelled: tasks.filter(t => t.status === "CANCELLED").length,
  }), [tasks]);

  const openTasks     = useMemo(() => tasks.filter(t => t.status === "OPEN" && !isOverdue(t)), [tasks]);
  const overdueTasks  = useMemo(() => tasks.filter(isOverdue), [tasks]);
  const completedTasks = useMemo(() => tasks.filter(t => t.status === "COMPLETED"), [tasks]);
  const allSortedTasks = useMemo(() =>
    [...tasks].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()),
    [tasks]
  );

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="size-6 animate-spin text-slate-400" />
      </div>
    );
  }
  if (!customer) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3">
        <AlertCircle className="size-10 text-slate-300" />
        <p className="text-slate-500 text-sm">Customer profile not found.</p>
        <Button variant="ghost" size="sm" onClick={() => router.back()}>Go back</Button>
      </div>
    );
  }

  const isCorporate = customer.customerType === "CORPORATE";

  return (
    <div className="space-y-5 pb-10">
      {/* Back + Actions */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => router.push("/customer-profiles")}
          className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800 transition"
        >
          <ArrowLeft className="size-4" />
          <span>Customer Profiles</span>
        </button>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => setEditOpen(true)}
          leftIcon={<Edit2 className="size-3.5" />}
        >
          Edit Profile
        </Button>
      </div>

      {/* Profile Header Card */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        {/* Top gradient strip */}
        <div className={`h-2 ${isCorporate ? "bg-gradient-to-r from-purple-400 to-purple-600" : "bg-gradient-to-r from-blue-400 to-blue-600"}`} />
        <div className="px-6 py-5">
          <div className="flex items-start gap-5">
            {/* Avatar */}
            <div className={`size-16 rounded-2xl flex items-center justify-center text-xl font-bold shrink-0 ${
              isCorporate ? "bg-purple-100 text-purple-700" : "bg-blue-100 text-blue-700"
            }`}>
              {initials(customer.fullName)}
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 flex-wrap">
                <h1 className="text-xl font-bold text-slate-900">{customer.fullName}</h1>
                <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full border ${TYPE_BADGE[customer.customerType]}`}>
                  {TYPE_LABEL[customer.customerType]}
                </span>
                <Badge
                  variant={customer.status === "ACTIVE" ? "success" : "default"}
                  size="sm"
                  className="text-[10px] font-bold"
                >
                  {customer.status}
                </Badge>
              </div>

              {isCorporate && customer.companyName && (
                <div className="flex items-center gap-1.5 mt-1">
                  <Building2 className="size-3.5 text-slate-400" />
                  <span className="text-sm text-slate-600 font-medium">{customer.companyName}</span>
                </div>
              )}

              <div className="flex flex-wrap items-center gap-4 mt-3">
                {customer.phone && (
                  <a href={`tel:${customer.phone}`} className="flex items-center gap-1.5 text-sm text-blue-600 hover:underline">
                    <Phone className="size-3.5" />
                    {customer.phone}
                  </a>
                )}
                {customer.email && (
                  <a href={`mailto:${customer.email}`} className="flex items-center gap-1.5 text-sm text-blue-600 hover:underline truncate">
                    <Mail className="size-3.5" />
                    {customer.email}
                  </a>
                )}
                {customer.address && (
                  <div className="flex items-center gap-1.5 text-sm text-slate-500">
                    <MapPin className="size-3.5 text-slate-400" />
                    <span className="truncate max-w-[240px]">{customer.address}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Quick info grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { icon: <CheckCircle2 className="size-4 text-green-500" />, label: "Completed", value: taskStats.completed, color: "text-green-700" },
          { icon: <Clock className="size-4 text-yellow-500" />, label: "Open Tasks", value: taskStats.open, color: "text-yellow-700" },
          { icon: <AlertCircle className="size-4 text-red-500" />, label: "Overdue", value: taskStats.overdue, color: "text-red-700" },
          { icon: <Tag className="size-4 text-slate-400" />, label: "Total Tasks", value: taskStats.total, color: "text-slate-700" },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl border border-slate-100 shadow-sm px-4 py-3 flex items-center gap-3">
            {s.icon}
            <div>
              <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wide">{s.label}</p>
              <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="border-b border-slate-100 rounded-none px-2 w-full justify-start">
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="tasks">Tasks ({taskStats.total})</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
            <TabsTrigger value="info">Info</TabsTrigger>
          </TabsList>

          {/* Overview */}
          <TabsContent value="overview" className="p-5 space-y-5">
            {/* Overdue alert */}
            {taskStats.overdue > 0 && (
              <div className="p-4 bg-red-50 border border-red-200 rounded-xl flex items-start gap-3">
                <AlertCircle className="size-5 text-red-500 shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-semibold text-red-800">{taskStats.overdue} overdue task{taskStats.overdue > 1 ? "s" : ""} need attention</p>
                  <p className="text-xs text-red-600 mt-0.5">Please review and take action as soon as possible.</p>
                </div>
              </div>
            )}

            {/* Open tasks */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <p className="text-sm font-bold text-slate-700">Open Tasks</p>
                <button onClick={() => setTab("tasks")} className="text-xs text-blue-600 hover:underline flex items-center gap-0.5">
                  View all <ChevronRight className="size-3.5" />
                </button>
              </div>
              {openTasks.length === 0 ? (
                <p className="text-xs text-slate-400 py-4 text-center">No open tasks</p>
              ) : (
                <div className="space-y-2">
                  {openTasks.slice(0, 4).map(t => <TaskCard key={t.taskId} task={t} />)}
                </div>
              )}
            </div>

            {/* Overdue tasks */}
            {overdueTasks.length > 0 && (
              <div>
                <p className="text-sm font-bold text-red-700 mb-3">Overdue Tasks</p>
                <div className="space-y-2">
                  {overdueTasks.map(t => <TaskCard key={t.taskId} task={t} />)}
                </div>
              </div>
            )}

            {/* Customer details summary */}
            <div className="border-t border-slate-100 pt-5">
              <p className="text-sm font-bold text-slate-700 mb-3">Customer Details</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {isCorporate && customer.companyName && (
                  <InfoItem icon={<Building2 className="size-3.5" />} label="Company" value={customer.companyName} />
                )}
                {isCorporate && customer.taxCode && (
                  <InfoItem icon={<Tag className="size-3.5" />} label="Tax Code" value={customer.taxCode} />
                )}
                {customer.assignedUserName && (
                  <InfoItem icon={<UserCog className="size-3.5" />} label="Assigned To" value={customer.assignedUserName} />
                )}
                {customer.leadId && (
                  <InfoItem icon={<Link2 className="size-3.5" />} label="Converted From Lead" value="View Lead" href={`/leads/${customer.leadId}`} />
                )}
                <InfoItem icon={<Calendar className="size-3.5" />} label="Customer Since" value={fmtDate(customer.createdAt)} />
                {customer.createdByName && (
                  <InfoItem icon={<User className="size-3.5" />} label="Created By" value={customer.createdByName} />
                )}
              </div>
            </div>
          </TabsContent>

          {/* Tasks */}
          <TabsContent value="tasks" className="p-5 space-y-4">
            <div className="grid grid-cols-3 gap-3">
              {[
                { label: "Open", value: taskStats.open, color: "text-yellow-700 bg-yellow-50" },
                { label: "Completed", value: taskStats.completed, color: "text-green-700 bg-green-50" },
                { label: "Overdue", value: taskStats.overdue, color: "text-red-700 bg-red-50" },
              ].map(s => (
                <div key={s.label} className={`rounded-xl px-4 py-3 border border-slate-100 ${s.color}`}>
                  <p className="text-xs font-semibold opacity-70">{s.label}</p>
                  <p className="text-2xl font-bold mt-0.5">{s.value}</p>
                </div>
              ))}
            </div>
            {allSortedTasks.length === 0 ? (
              <p className="text-sm text-slate-400 text-center py-8">No tasks linked to this customer.</p>
            ) : (
              <div className="space-y-2">
                {allSortedTasks.map(t => <TaskCard key={t.taskId} task={t} />)}
              </div>
            )}
          </TabsContent>

          {/* History — unified timeline: tasks + deals + bookings + quotations */}
          <TabsContent value="history" className="p-5">
            <HistoryTimeline
              tasks={allSortedTasks}
              history={historyData?.data ?? []}
            />
          </TabsContent>

          {/* Info */}
          <TabsContent value="info" className="p-5">
            <div className="space-y-6">
              <section>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Contact Information</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <InfoField label="Full Name" value={customer.fullName} />
                  <InfoField label="Customer Type" value={TYPE_LABEL[customer.customerType]} />
                  <InfoField label="Phone" value={customer.phone} href={customer.phone ? `tel:${customer.phone}` : undefined} />
                  <InfoField label="Email" value={customer.email} href={customer.email ? `mailto:${customer.email}` : undefined} />
                  <InfoField label="Address" value={customer.address} className="sm:col-span-2" />
                </div>
              </section>

              {isCorporate && (
                <section>
                  <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Corporate Information</p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <InfoField label="Company Name" value={customer.companyName} />
                    <InfoField label="Tax Code" value={customer.taxCode} />
                  </div>
                </section>
              )}

              <section>
                <p className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3">Account Details</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <InfoField label="Status" value={customer.status} />
                  <InfoField label="Assigned To" value={customer.assignedUserName} />
                  <InfoField label="Created By" value={customer.createdByName} />
                  {customer.leadId && (
                    <InfoField label="Source Lead" value={`Lead ${customer.leadId.slice(0, 8)}…`} href={`/leads/${customer.leadId}`} />
                  )}
                  <InfoField label="Customer Since" value={fmtDateTime(customer.createdAt)} />
                  <InfoField label="Last Updated" value={fmtDateTime(customer.updatedAt)} />
                </div>
              </section>
            </div>
          </TabsContent>
        </Tabs>
      </div>

      {editOpen && (
        <EditCustomerDrawer
          customer={customer}
          onClose={() => setEditOpen(false)}
          users={users.map(u => ({ userId: u.userId, fullName: u.fullName }))}
        />
      )}
    </div>
  );
}

// ── History Timeline ──────────────────────────────────────────────────────────

const HISTORY_CONFIG: Record<CustomerHistoryItem["type"], {
  label: string;
  iconBg: string;
  badgeCls: string;
  icon: React.ReactNode;
}> = {
  DEAL:      { label: "Deal",      iconBg: "bg-indigo-100", badgeCls: "bg-indigo-100 text-indigo-700", icon: <Briefcase className="size-3.5 text-indigo-600" /> },
  BOOKING:   { label: "Booking",   iconBg: "bg-teal-100",   badgeCls: "bg-teal-100 text-teal-700",     icon: <BookOpen  className="size-3.5 text-teal-600" /> },
  QUOTATION: { label: "Quotation", iconBg: "bg-amber-100",  badgeCls: "bg-amber-100 text-amber-700",   icon: <FileText  className="size-3.5 text-amber-600" /> },
};

const TASK_STATUS_CFG: Record<string, string> = {
  COMPLETED: "bg-green-100 text-green-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  OPEN:      "bg-yellow-100 text-yellow-700",
  OVERDUE:   "bg-red-100 text-red-700",
};

function statusLabel(raw: string | null) {
  if (!raw) return "—";
  return raw.replace(/_/g, " ");
}

function HistoryTimeline({
  tasks,
  history,
}: {
  tasks: Task[];
  history: CustomerHistoryItem[];
}) {
  // Merge tasks + history items into one sorted list (newest first)
  type Entry =
    | { kind: "task"; ts: number; item: Task }
    | { kind: "history"; ts: number; item: CustomerHistoryItem };

  const entries: Entry[] = [
    ...tasks.map(t => ({
      kind: "task" as const,
      ts: new Date(t.updatedAt).getTime(),
      item: t,
    })),
    ...history.map(h => ({
      kind: "history" as const,
      ts: h.createdAt ? new Date(h.createdAt).getTime() : 0,
      item: h,
    })),
  ].sort((a, b) => b.ts - a.ts);

  if (entries.length === 0) {
    return <p className="text-sm text-slate-400 text-center py-8">No service history available.</p>;
  }

  // Group entries by month
  const groups: { label: string; entries: Entry[] }[] = [];
  for (const entry of entries) {
    const d = new Date(entry.ts);
    const label = d.toLocaleDateString("en-GB", { month: "long", year: "numeric" });
    const last = groups[groups.length - 1];
    if (!last || last.label !== label) {
      groups.push({ label, entries: [entry] });
    } else {
      last.entries.push(entry);
    }
  }

  return (
    <div className="space-y-6">
      {groups.map(group => (
        <div key={group.label}>
          <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-3 px-1">
            {group.label}
          </p>
          <div className="relative">
            <div className="absolute left-4 top-0 bottom-0 w-px bg-slate-100" />
            <div className="space-y-2">
              {group.entries.map((entry, i) => {
                if (entry.kind === "task") {
                  const t = entry.item;
                  const overdue = isOverdue(t);
                  const statusCls = overdue
                    ? TASK_STATUS_CFG.OVERDUE
                    : TASK_STATUS_CFG[t.status] ?? TASK_STATUS_CFG.OPEN;
                  return (
                    <div key={`task-${t.taskId}`} className="flex gap-3 pb-2 last:pb-0">
                      <div className="relative z-10 w-8 shrink-0 flex justify-center pt-1">
                        <div className="bg-blue-100 rounded-full p-1">
                          <CheckCircle2 className="size-3.5 text-blue-600" />
                        </div>
                      </div>
                      <div className="flex-1 bg-white rounded-xl border border-slate-100 px-3.5 py-3 shadow-sm">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5 mb-0.5">
                              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-blue-100 text-blue-700">Task</span>
                              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${statusCls}`}>
                                {overdue ? "Overdue" : t.status}
                              </span>
                            </div>
                            <p className="text-sm font-semibold text-slate-800 truncate">{t.title}</p>
                            {t.description && <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{t.description}</p>}
                            {t.resultNote && (
                              <p className="text-xs text-slate-600 mt-1 italic border-l-2 border-slate-200 pl-2 line-clamp-1">
                                "{t.resultNote}"
                              </p>
                            )}
                          </div>
                        </div>
                        <div className="flex items-center gap-2 mt-1.5 text-[11px] text-slate-400">
                          <span>{fmtDateTime(t.updatedAt)}</span>
                          {t.assignedUserName && <span>· {t.assignedUserName}</span>}
                          {t.startAt && t.endAt && <span>· {fmtDate(t.startAt)} → {fmtDate(t.endAt)}</span>}
                        </div>
                      </div>
                    </div>
                  );
                }

                const h = entry.item;
                const cfg = HISTORY_CONFIG[h.type];
                return (
                  <div key={`${h.type}-${h.id}-${i}`} className="flex gap-3 pb-2 last:pb-0">
                    <div className="relative z-10 w-8 shrink-0 flex justify-center pt-1">
                      <div className={`${cfg.iconBg} rounded-full p-1`}>{cfg.icon}</div>
                    </div>
                    <div className="flex-1 bg-white rounded-xl border border-slate-100 px-3.5 py-3 shadow-sm">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 mb-0.5 flex-wrap">
                            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${cfg.badgeCls}`}>
                              {cfg.label}
                            </span>
                            {h.status && (
                              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-600">
                                {statusLabel(h.status)}
                              </span>
                            )}
                            {h.stage && (
                              <span className="text-[10px] text-slate-400">{statusLabel(h.stage)}</span>
                            )}
                          </div>
                          <p className="text-sm font-semibold text-slate-800 truncate">{h.title}</p>
                          {(h.checkIn || h.expectedClose) && (
                            <div className="flex items-center gap-1 mt-0.5">
                              <Calendar className="size-3 text-slate-400" />
                              <span className="text-xs text-slate-500">
                                {h.checkIn && h.checkOut
                                  ? `${h.checkIn} → ${h.checkOut}`
                                  : h.checkIn ?? h.expectedClose}
                              </span>
                            </div>
                          )}
                          {h.notes && (
                            <p className="text-xs text-slate-500 mt-0.5 line-clamp-1 italic">{h.notes}</p>
                          )}
                        </div>
                        {h.amount != null && (
                          <div className="flex items-center gap-1 shrink-0">
                            <DollarSign className="size-3 text-slate-400" />
                            <span className="text-sm font-bold text-slate-700">
                              {h.amount.toLocaleString()}
                            </span>
                          </div>
                        )}
                      </div>
                      {h.createdAt && (
                        <p className="text-[11px] text-slate-400 mt-1.5">{fmtDateTime(h.createdAt)}</p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

// ── Shared field components ───────────────────────────────────────────────────

function InfoItem({
  icon,
  label,
  value,
  href,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  href?: string;
}) {
  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="text-slate-400 shrink-0">{icon}</span>
      <span className="text-slate-500 shrink-0">{label}:</span>
      {href ? (
        <a href={href} className="text-blue-600 hover:underline truncate">{value}</a>
      ) : (
        <span className="text-slate-800 font-medium truncate">{value}</span>
      )}
    </div>
  );
}

function InfoField({
  label,
  value,
  href,
  className = "",
}: {
  label: string;
  value: string | null | undefined;
  href?: string;
  className?: string;
}) {
  return (
    <div className={`bg-slate-50 rounded-xl px-4 py-3 ${className}`}>
      <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide mb-0.5">{label}</p>
      {value ? (
        href ? (
          <a href={href} className="text-sm text-blue-600 hover:underline">{value}</a>
        ) : (
          <p className="text-sm font-medium text-slate-800">{value}</p>
        )
      ) : (
        <p className="text-sm text-slate-300">—</p>
      )}
    </div>
  );
}
