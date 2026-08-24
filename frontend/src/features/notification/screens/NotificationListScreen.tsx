"use client";

import React, { useState, useMemo } from "react";
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
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { useTableControls } from "@/components/ui/table-controls";
import { RowActions, OwnerCell } from "@/components/ui/row-actions";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
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
  if (entity === "LEAD") return `${ROUTE_PATHS.leads}?lead=${encodeURIComponent(n.relatedId)}&${highlight}`;
  if (entity === "QUOTATION") return `${ROUTE_PATHS.quotations}?${highlight}`;
  if (entity === "BOOKING") return `${ROUTE_PATHS.bookingConfirmation}?${highlight}`;
  if (entity === "REMINDER") return `${ROUTE_PATHS.reminders}?${highlight}`;
  if (entity === "TASK") return `${ROUTE_PATHS.followUpTasks}?${highlight}`;
  if (entity === "SLA") return `${ROUTE_PATHS.sla}?${highlight}`;
  if (entity === "HANDOVER") return `${ROUTE_PATHS.frontOfficeHandover}?${highlight}`;
  if (entity === "CUSTOMER") return `${ROUTE_PATHS.customerProfiles}?${highlight}`;
  if (entity === "DEAL") return `${ROUTE_PATHS.deals}?${highlight}`;
  if (entity === "ROOM_REQUEST") return `${ROUTE_PATHS.roomRequests}?${highlight}`;
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
  const [pageSize, setPageSize] = useState(20);
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
    size: pageSize,
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

  /**
   * Column set — Blueprint §10.17.
   *
   * The Recipient column only exists in the Manager/Admin "team activity" view;
   * for everyone else every row is their own, so the column would be a wall of
   * the reader's own name.
   */
  const notificationColumns: ColumnDef<Notification>[] = useMemo(() => {
    const cols: ColumnDef<Notification>[] = [
      {
        id: "read",
        header: "",
        width: "w-8",
        cell: (n) =>
          !n.isRead ? (
            <span className="inline-block size-2 rounded-full bg-primary" title="Unread" />
          ) : (
            <CheckCircle2 className="inline-block size-3 text-success/70" aria-label="Read" />
          ),
      },
      {
        id: "notification",
        header: "Notification",
        sticky: "left",
        cell: (n) => (
          <div className="flex items-start gap-2">
            <TypeIcon type={n.type} />
            <div className="min-w-0">
              <p className={n.isRead ? "text-xs text-muted-foreground" : "text-xs font-bold text-foreground"}>
                {n.title}
              </p>
              <p className="mt-0.5 line-clamp-2 text-[10px] leading-relaxed text-muted-foreground">
                {n.message}
              </p>
            </div>
          </div>
        ),
      },
    ];

    if (allUsers) {
      cols.push({
        id: "recipient",
        header: "Recipient",
        minWidth: "md",
        cell: (n) => <OwnerCell name={n.recipientName} />,
      });
    }

    cols.push(
      {
        id: "type",
        header: "Type",
        minWidth: "lg",
        cell: (n) => (
          <div className="flex items-center gap-1">
            <Badge variant={TYPE_VARIANT[n.type] ?? "default"} size="sm" className="text-[9px] font-bold uppercase">
              {TYPE_LABEL[n.type] ?? n.type}
            </Badge>
            {n.priority && n.priority !== "NORMAL" && (
              <Badge variant={PRIORITY_VARIANT[n.priority] ?? "default"} size="sm" className="text-[9px] font-bold uppercase">
                {n.priority}
              </Badge>
            )}
          </div>
        ),
      },
      {
        id: "time",
        header: "Time",
        minWidth: "md",
        className: "whitespace-nowrap text-[10px] font-semibold text-muted-foreground",
        cell: (n) => formatTime(n.createdAt),
      },
      {
        id: "actions",
        header: "",
        width: "w-12",
        sticky: "right",
        cell: (n) => {
          const route = getRelatedRoute(n);
          return (
            <div className="flex justify-end">
              <RowActions
                label="Notification actions"
                actions={[
                  {
                    key: "open",
                    label: "Go to record",
                    icon: ExternalLink,
                    reason: route ? null : "This notification has no linked record to open.",
                    onSelect: () => handleNotificationClick(n),
                  },
                  {
                    key: "read",
                    label: n.isRead ? "Mark as unread" : "Mark as read",
                    icon: n.isRead ? Bell : MailOpen,
                    reason: isOwnNotification(n)
                      ? null
                      : "This alert belongs to another user — only they can change its read state.",
                    onSelect: () => markRead.mutate({ id: n.id, read: !n.isRead }),
                  },
                ]}
              />
            </div>
          );
        },
      },
    );

    return cols;
    // Handlers are stable for the screen's lifetime; `allUsers` changes the shape.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allUsers]);

  const controls = useTableControls<Notification>("notifications", notificationColumns);

  return (
    <div className="space-y-6">
      <PageHeader
        {...PAGE_META.notifications}
        meta={
          unreadCount > 0 ? (
            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-bold text-white">
              {unreadCount}
            </span>
          ) : undefined
        }
        actions={
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
        }
      />

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

      <DataTable
        label="Notifications"
        rows={notifications}
        columns={controls.visibleColumns}
        rowId={(n) => n.id}
        isLoading={isLoading}
        error={isError ? new Error("Could not load notifications.") : undefined}
        density={controls.density}
        onRowClick={(n) => handleNotificationClick(n)}
        selectedIds={controls.selectedIds}
        onSelectionChange={controls.setSelectedIds}
        bulkActions={
          <Button
            size="xs"
            variant="secondary"
            leftIcon={<MailOpen className="size-3.5" />}
            isLoading={markRead.isPending}
            onClick={() => {
              // Bulk mark-as-read only touches the user's own rows; a manager
              // viewing team activity cannot mark someone else's notification.
              notifications
                .filter((n) => controls.selectedIds.has(n.id) && isOwnNotification(n) && !n.isRead)
                .forEach((n) => markRead.mutate({ id: n.id, read: true }));
              controls.clearSelection();
            }}
          >
            Mark read
          </Button>
        }
        isFiltered={!!typeFilter || !!priorityFilter}
        emptyTitle="You are all caught up"
        emptyMessage="New alerts about leads, quotations, bookings and SLA breaches land here."
        footer={
          <TablePagination
            page={page}
            pageSize={pageSize}
            totalElements={totalElements}
            totalPages={totalPages}
            onPageChange={setPage}
            onPageSizeChange={(s) => {
              setPageSize(s);
              setPage(0);
            }}
            pageSizeOptions={[10, 20, 50]}
          />
        }
      />
    </div>
  );
}
