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
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { useAuthStore } from "@/stores/auth_store";
import {
  useNotifications,
  useMarkNotificationRead,
  useMarkAllRead,
} from "@/features/notification/hooks/use_notifications";
import type { Notification } from "@/services/notification_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

const TYPE_LABEL: Record<string, string> = {
  LEAD_ASSIGNED: "Lead",
  QUOTATION_APPROVAL: "Approval",
  QUOTATION_SENT: "Quotation",
  CUSTOMER_RESPONSE: "Response",
  BOOKING_UPDATE: "Booking",
  PAYMENT_REMINDER: "Payment",
  SLA_WARNING: "SLA",
  TASK_OVERDUE: "Task",
};

const TYPE_VARIANT: Record<string, "danger" | "warning" | "success" | "primary" | "default"> = {
  SLA_WARNING: "danger",
  TASK_OVERDUE: "danger",
  PAYMENT_REMINDER: "warning",
  QUOTATION_APPROVAL: "warning",
  BOOKING_UPDATE: "success",
  CUSTOMER_RESPONSE: "success",
  LEAD_ASSIGNED: "primary",
  QUOTATION_SENT: "primary",
};

const FILTER_OPTIONS = [
  { value: "", label: "All types" },
  { value: "LEAD_ASSIGNED", label: "Lead Assigned" },
  { value: "QUOTATION_APPROVAL", label: "Approval" },
  { value: "QUOTATION_SENT", label: "Quotation Sent" },
  { value: "CUSTOMER_RESPONSE", label: "Customer Response" },
  { value: "BOOKING_UPDATE", label: "Booking Update" },
  { value: "PAYMENT_REMINDER", label: "Payment" },
  { value: "SLA_WARNING", label: "SLA Warning" },
  { value: "TASK_OVERDUE", label: "Task Overdue" },
];

function getRelatedRoute(n: Notification): string | null {
  if (!n.relatedEntity || !n.relatedId) return null;
  const entity = n.relatedEntity.toUpperCase();
  if (entity === "LEAD") return ROUTE_PATHS.leadDetail(n.relatedId);
  if (entity === "QUOTATION") return ROUTE_PATHS.quotations;
  if (entity === "BOOKING") return ROUTE_PATHS.bookingConfirmation;
  if (entity === "REMINDER") return ROUTE_PATHS.reminders;
  if (entity === "TASK") return ROUTE_PATHS.followUpTasks;
  if (entity === "SLA") return ROUTE_PATHS.sla;
  return null;
}

function TypeIcon({ type }: { type: string }) {
  if (type === "SLA_WARNING" || type === "TASK_OVERDUE") return <AlertTriangle className="size-3.5 text-red-500" />;
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
  const userId = user?.id;

  const [unreadOnly, setUnreadOnly] = useState(false);
  const [typeFilter, setTypeFilter] = useState("");

  const { data: notifications = [], isLoading, isError } = useNotifications(userId, unreadOnly);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllRead();

  const filtered = typeFilter
    ? notifications.filter((n) => n.type === typeFilter)
    : notifications;

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  const handleNotificationClick = async (n: Notification) => {
    if (!n.isRead) {
      await markRead.mutateAsync({ id: n.id, read: true });
    }
    const route = getRelatedRoute(n);
    if (route) router.push(route);
  };

  const handleToggleRead = async (e: React.MouseEvent, n: Notification) => {
    e.stopPropagation();
    await markRead.mutateAsync({ id: n.id, read: !n.isRead });
  };

  const handleMarkAllRead = async () => {
    if (!userId) return;
    await markAllRead.mutateAsync(userId);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-800">Notification Center</h1>
            {unreadCount > 0 && (
              <span className="inline-flex items-center justify-center h-5 min-w-5 px-1.5 rounded-full bg-blue-500 text-white text-[10px] font-bold">
                {unreadCount}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Leads, quotations, bookings, reminders, and SLA alerts
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
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-1.5 text-xs text-slate-500 font-semibold">
          <Filter className="size-3.5" />
          Filter:
        </div>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-slate-400 transition"
        >
          {FILTER_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>

        <label className="flex items-center gap-2 cursor-pointer select-none">
          <div
            onClick={() => setUnreadOnly((v) => !v)}
            className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
              unreadOnly ? "bg-blue-500" : "bg-slate-200"
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
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500 w-8" />
              <TableHead className="font-semibold text-xs text-slate-500">Notification</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Type</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Time</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={5} className="py-12 text-center text-xs text-slate-400">
                  Loading notifications…
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={5} className="py-12 text-center text-xs text-red-500">
                  Failed to load notifications. Please refresh.
                </TableCell>
              </TableRow>
            ) : filtered.length === 0 ? (
              /* E3 — no notifications */
              <TableRow hoverable={false}>
                <TableCell colSpan={5} className="py-14 text-center text-xs text-slate-400">
                  <div className="flex flex-col items-center gap-2">
                    <BellOff className="size-8 text-slate-300" />
                    <span className="font-bold text-slate-600 text-sm">No notifications available</span>
                    <span>
                      {unreadOnly || typeFilter
                        ? "Try clearing the filters to see all notifications."
                        : "You're all caught up — no alerts or updates right now."}
                    </span>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((n) => {
                const route = getRelatedRoute(n);
                const isClickable = !!route;
                return (
                  <TableRow
                    key={n.id}
                    onClick={() => handleNotificationClick(n)}
                    className={`border-b border-slate-100 transition ${
                      isClickable ? "cursor-pointer hover:bg-slate-50/80" : "hover:bg-slate-50/50"
                    } ${!n.isRead ? "bg-blue-50/20" : ""}`}
                  >
                    {/* Unread dot */}
                    <TableCell className="py-3 px-3 text-center">
                      {!n.isRead ? (
                        <span className="inline-block w-2 h-2 rounded-full bg-blue-500" />
                      ) : (
                        <span className="inline-block w-2 h-2 rounded-full bg-slate-200" />
                      )}
                    </TableCell>

                    {/* Title + message */}
                    <TableCell className="py-3 px-4 max-w-sm">
                      <div className="flex items-start gap-2">
                        <TypeIcon type={n.type} />
                        <div>
                          <p className={`text-xs font-bold text-slate-800 ${!n.isRead ? "" : "font-semibold text-slate-600"}`}>
                            {n.title}
                          </p>
                          <p className="text-[10px] text-slate-500 mt-0.5 line-clamp-2 leading-relaxed">
                            {n.message}
                          </p>
                        </div>
                      </div>
                    </TableCell>

                    {/* Type badge */}
                    <TableCell className="py-3 px-4">
                      <Badge
                        variant={TYPE_VARIANT[n.type] ?? "default"}
                        size="sm"
                        className="text-[9px] font-bold uppercase"
                      >
                        {TYPE_LABEL[n.type] ?? n.type}
                      </Badge>
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
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
