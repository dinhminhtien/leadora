"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  BellOff,
  CheckCheck,
  ExternalLink,
  Filter,
  MailOpen,
  AlertTriangle,
  CheckCircle2,
  Info,
  ChevronLeft,
  ChevronRight,
  Users,
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  useNotifications,
  useUnreadNotificationCount,
  useMarkNotificationRead,
  useMarkAllRead,
} from "@/features/notification/hooks/use_notifications";
import { notificationService, type Notification, type NotificationPriority } from "@/services/notification_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { useAuthStore } from "@/stores/auth_store";
import { hasFullAccess } from "@/shared/auth/access";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";

const PAGE_SIZE = 20;

const TYPE_LABEL: Record<string, string> = {
  LEAD_ASSIGNED: "Lead",
  QUOTATION_APPROVAL: "Approval",
  QUOTATION_SENT: "Quotation",
  CUSTOMER_RESPONSE: "Response",
  BOOKING_UPDATE: "Booking",
  SLA_WARNING: "SLA Warning",
  SLA_BREACH: "SLA Breach",
  TASK_OVERDUE: "Task",
  REMINDER: "Reminder",
  REMINDER_ESCALATED: "Reminder",
  REMINDER_OVERDUE: "Reminder",
  HANDOVER: "Handover",
};

const TYPE_VARIANT: Record<string, "danger" | "warning" | "success" | "primary" | "default"> = {
  SLA_WARNING: "warning",
  SLA_BREACH: "danger",
  TASK_OVERDUE: "danger",
  QUOTATION_APPROVAL: "warning",
  BOOKING_UPDATE: "success",
  CUSTOMER_RESPONSE: "success",
  LEAD_ASSIGNED: "primary",
  QUOTATION_SENT: "primary",
  REMINDER: "default",
  REMINDER_ESCALATED: "danger",
  REMINDER_OVERDUE: "warning",
  HANDOVER: "default",
};

const FILTER_OPTIONS = [
  { value: "", label: "All types" },
  { value: "LEAD_ASSIGNED", label: "Lead Assigned" },
  { value: "QUOTATION_APPROVAL", label: "Approval" },
  { value: "QUOTATION_SENT", label: "Quotation Sent" },
  { value: "CUSTOMER_RESPONSE", label: "Customer Response" },
  { value: "BOOKING_UPDATE", label: "Booking Update" },
  { value: "SLA_WARNING", label: "SLA Warning" },
  { value: "SLA_BREACH", label: "SLA Breach" },
  { value: "TASK_OVERDUE", label: "Task Overdue" },
  { value: "REMINDER", label: "Reminder" },
  { value: "REMINDER_ESCALATED", label: "Reminder Escalated" },
  { value: "REMINDER_OVERDUE", label: "Reminder Overdue" },
  { value: "HANDOVER", label: "Handover" },
];

const PRIORITY_OPTIONS: { value: NotificationPriority | ""; label: string }[] = [
  { value: "", label: "All priorities" },
  { value: "URGENT", label: "Urgent" },
  { value: "HIGH", label: "High" },
  { value: "NORMAL", label: "Normal" },
  { value: "LOW", label: "Low" },
];

const PRIORITY_VARIANT: Record<string, "danger" | "warning" | "success" | "primary" | "default"> = {
  URGENT: "danger",
  HIGH: "warning",
  NORMAL: "default",
  LOW: "default",
};

function getRelatedRoute(n: Notification): string | null {
  if (!n.relatedEntity || !n.relatedId) return null;
  const entity = n.relatedEntity.toUpperCase();
  const highlight = `highlight=${encodeURIComponent(n.relatedId)}`;
  if (entity === "LEAD") return ROUTE_PATHS.leadDetail(n.relatedId);
  // No standalone quotation detail page exists (only /quotations,
  // /quotations/[id]/revise) — route to the list (with highlight), not
  // ROUTE_PATHS.quotationDetail, which 404s since that page was never built.
  if (entity === "QUOTATION") return `${ROUTE_PATHS.quotations}?${highlight}`;
  if (entity === "BOOKING") return `${ROUTE_PATHS.bookingConfirmation}?${highlight}`;
  if (entity === "REMINDER") return `${ROUTE_PATHS.reminders}?${highlight}`;
  if (entity === "TASK") return `${ROUTE_PATHS.followUpTasks}?${highlight}`;
  if (entity === "SLA") return `${ROUTE_PATHS.sla}?${highlight}`;
  if (entity === "HANDOVER") return `${ROUTE_PATHS.frontOfficeHandover}?${highlight}`;
  return null;
}

function TypeIcon({ type }: { type: string }) {
  if (type === "SLA_WARNING" || type === "SLA_BREACH" || type === "TASK_OVERDUE" || type === "REMINDER_ESCALATED")
    return <AlertTriangle className="size-3.5 text-red-500" />;
  if (type === "BOOKING_UPDATE" || type === "CUSTOMER_RESPONSE") return <CheckCircle2 className="size-3.5 text-emerald-500" />;
  return <Info className="size-3.5 text-blue-500" />;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "Just now";
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h ago`;
  return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}

export function NotificationListScreen() {
  const router = useRouter();
  const { user } = useAuthStore();
  const canViewAll = hasFullAccess(user);

  const [unreadOnly, setUnreadOnly] = useState(false);
  const [typeFilter, setTypeFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState<NotificationPriority | "">("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [sortBy, setSortBy] = useState<"createdAt" | "priority">("createdAt");
  const [page, setPage] = useState(0);
  // Manager/Admin only — org-wide "who did what" activity feed instead of just their own.
  const [viewAllUsers, setViewAllUsers] = useState(false);
  const allUsers = canViewAll && viewAllUsers;

  const { data: pageData, isLoading, isError } = useNotifications({
    unreadOnly,
    allUsers,
    type: typeFilter || undefined,
    priority: priorityFilter || undefined,
    createdFrom: dateFrom ? new Date(dateFrom).toISOString() : undefined,
    createdTo: dateTo ? new Date(dateTo + "T23:59:59").toISOString() : undefined,
    sortBy: sortBy === "priority" ? "priority" : undefined,
    page,
    size: PAGE_SIZE,
  });
  const { data: unreadCount = 0 } = useUnreadNotificationCount();
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllRead();

  // Server already applies every active filter — no client-side re-filtering,
  // which previously desynced the displayed rows from the "Page X of Y" count.
  const notifications = pageData?.content ?? [];
  const totalPages = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalPages : (pageData?.totalPages ?? 1);
  const totalElements = (pageData?.page && typeof pageData.page === "object") ? pageData.page.totalElements : (pageData?.totalElements ?? 0);

  // Team Activity rows belong to someone else — viewing them must not flip their
  // read state (the backend rejects this anyway; keep the client from even trying).
  const isOwnNotification = (n: Notification) => !n.recipientId || n.recipientId === user?.id;

  // UC-15.2: check access (and mark-as-read, for your own notifications) via
  // GET /notifications/{id} BEFORE navigating — a 403 must block the redirect,
  // not just fire a best-effort mark-read alongside it.
  const handleNotificationClick = async (n: Notification) => {
    const route = getRelatedRoute(n);
    if (!route) return;
    try {
      await notificationService.getById(n.id);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "You do not have permission to access this notification."));
      return;
    }
    router.push(route);
    // getById already marks it read server-side for your own notifications; this
    // just refreshes the list/badge cache to match.
    if (!n.isRead && isOwnNotification(n)) {
      markRead.mutate({ id: n.id, read: true });
    }
  };

  const handleToggleRead = async (e: React.MouseEvent, n: Notification) => {
    e.stopPropagation();
    if (!isOwnNotification(n)) return;
    await markRead.mutateAsync({ id: n.id, read: !n.isRead });
  };

  const handleMarkAllRead = async () => {
    await markAllRead.mutateAsync();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-800">Notification Center</h1>
            {unreadCount > 0 && (
              <span className="inline-flex items-center justify-center h-5 min-w-5 px-1.5 rounded-full bg-primary text-white text-[10px] font-bold">
                {unreadCount}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Leads, quotations, bookings, reminders, handovers, and SLA alerts
          </p>
        </div>

        <div className="flex items-center gap-2">
          {unreadCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleMarkAllRead}
              isLoading={markAllRead.isPending}
              className="gap-1.5 border-slate-200 text-xs text-slate-600 font-semibold"
            >
              <CheckCheck className="size-3.5" />
              Mark all read
            </Button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1.5 text-xs text-slate-500 font-semibold">
          <Filter className="size-3.5" />
          Filter:
        </div>
        <select
          value={typeFilter}
          onChange={(e) => { setTypeFilter(e.target.value); setPage(0); }}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        >
          {FILTER_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>

        <select
          value={priorityFilter}
          onChange={(e) => { setPriorityFilter(e.target.value as NotificationPriority | ""); setPage(0); }}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        >
          {PRIORITY_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>

        <input
          type="date"
          value={dateFrom}
          onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        />
        <span className="text-xs text-slate-400">to</span>
        <input
          type="date"
          value={dateTo}
          onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        />

        <select
          value={sortBy}
          onChange={(e) => { setSortBy(e.target.value as "createdAt" | "priority"); setPage(0); }}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        >
          <option value="createdAt">Sort: Newest first</option>
          <option value="priority">Sort: Priority</option>
        </select>

        <label className="flex items-center gap-2 cursor-pointer select-none">
          <div
            onClick={() => { setUnreadOnly((v) => !v); setPage(0); }}
            className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
              unreadOnly ? "bg-primary" : "bg-slate-200"
            }`}
          >
            <span
              className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${
                unreadOnly ? "translate-x-4" : "translate-x-1"
              }`}
            />
          </div>
          <span className="text-xs text-slate-600 font-semibold">Unread only</span>
        </label>

        {canViewAll && (
          <label className="flex items-center gap-2 cursor-pointer select-none ml-auto">
            <div
              onClick={() => { setViewAllUsers((v) => !v); setPage(0); }}
              className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                viewAllUsers ? "bg-primary" : "bg-slate-200"
              }`}
            >
              <span
                className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow transition-transform ${
                  viewAllUsers ? "translate-x-4" : "translate-x-1"
                }`}
              />
            </div>
            <span className="text-xs text-slate-600 font-semibold flex items-center gap-1">
              <Users className="size-3.5" /> Team activity
            </span>
          </label>
        )}
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500 w-8" />
              <TableHead className="font-semibold text-xs text-slate-500">Notification</TableHead>
              {allUsers && <TableHead className="font-semibold text-xs text-slate-500">Recipient</TableHead>}
              <TableHead className="font-semibold text-xs text-slate-500">Type</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Time</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={allUsers ? 6 : 5} className="py-12 text-center text-xs text-slate-400">
                  Loading notifications…
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={allUsers ? 6 : 5} className="py-12 text-center text-xs text-red-500">
                  Failed to load notifications. Please refresh.
                </TableCell>
              </TableRow>
            ) : notifications.length === 0 ? (
              /* E3 — no notifications */
              <TableRow hoverable={false}>
                <TableCell colSpan={allUsers ? 6 : 5} className="py-14 text-center text-xs text-slate-400">
                  <div className="flex flex-col items-center gap-2">
                    <BellOff className="size-8 text-slate-300" />
                    <span className="font-bold text-slate-600 text-sm">No notifications available</span>
                    <span>
                      {unreadOnly || typeFilter || priorityFilter || dateFrom || dateTo
                        ? "Try clearing the filters to see all notifications."
                        : "You're all caught up — no alerts or updates right now."}
                    </span>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              notifications.map((n) => {
                const route = getRelatedRoute(n);
                const isClickable = !!route;
                return (
                  <TableRow
                    key={n.id}
                    onClick={() => handleNotificationClick(n)}
                    className={`border-b border-slate-100 transition ${
                      isClickable ? "cursor-pointer hover:bg-slate-50/80" : "hover:bg-slate-50/50"
                    } ${!n.isRead ? "bg-blue-50/30" : "opacity-70"}`}
                  >
                    {/* Unread dot */}
                    <TableCell className="py-3 px-3 text-center">
                      {!n.isRead ? (
                        <span className="inline-block w-2 h-2 rounded-full bg-primary" title="Unread" />
                      ) : (
                        <CheckCircle2 className="inline-block size-3 text-emerald-400" aria-label="Read" />
                      )}
                    </TableCell>

                    {/* Title + message */}
                    <TableCell className="py-3 px-4 max-w-sm">
                      <div className="flex items-start gap-2">
                        <TypeIcon type={n.type} />
                        <div>
                          <p className={!n.isRead ? "text-xs font-bold text-slate-800" : "text-xs font-normal text-slate-500"}>
                            {n.title}
                          </p>
                          <p className="text-[10px] text-slate-500 mt-0.5 line-clamp-2 leading-relaxed">
                            {n.message}
                          </p>
                        </div>
                      </div>
                    </TableCell>

                    {/* Recipient — Manager/Admin team activity view only */}
                    {allUsers && (
                      <TableCell className="py-3 px-4 text-xs font-semibold text-slate-600 whitespace-nowrap">
                        {n.recipientName ?? "—"}
                      </TableCell>
                    )}

                    {/* Type + priority badges */}
                    <TableCell className="py-3 px-4">
                      <div className="flex items-center gap-1">
                        <Badge
                          variant={TYPE_VARIANT[n.type] ?? "default"}
                          size="sm"
                          className="text-[9px] font-bold uppercase"
                        >
                          {TYPE_LABEL[n.type] ?? n.type}
                        </Badge>
                        {n.priority && n.priority !== "NORMAL" && (
                          <Badge
                            variant={PRIORITY_VARIANT[n.priority] ?? "default"}
                            size="sm"
                            className="text-[9px] font-bold uppercase"
                          >
                            {n.priority}
                          </Badge>
                        )}
                      </div>
                    </TableCell>

                    {/* Time */}
                    <TableCell className="py-3 px-4 text-[10px] text-slate-500 font-semibold whitespace-nowrap">
                      {formatTime(n.createdAt)}
                    </TableCell>

                    {/* Actions */}
                    <TableCell className="py-3 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        {route && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={(e) => { e.stopPropagation(); handleNotificationClick(n); }}
                            className="px-2 py-1 text-[10px] border-slate-200 font-bold hover:bg-slate-50 text-slate-600"
                          >
                            <ExternalLink className="size-3 mr-1 inline" /> Go to
                          </Button>
                        )}
                        {isOwnNotification(n) && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={(e) => handleToggleRead(e, n)}
                            disabled={markRead.isPending}
                            className="px-2 py-1 text-[10px] border-slate-200 font-bold hover:bg-slate-50 text-slate-500"
                          >
                            {n.isRead ? (
                              <><Bell className="size-3 mr-1 inline" /> Unread</>
                            ) : (
                              <><MailOpen className="size-3 mr-1 inline" /> Read</>
                            )}
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
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
              <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
                className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                <ChevronLeft className="size-3.5" /> Prev
              </button>
              <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg border border-slate-200 text-slate-500 hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                Next <ChevronRight className="size-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
