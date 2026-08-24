"use client";

import React, { useState, useEffect } from "react";
import { Workflow, ClipboardList, Search, Plus, Edit, X, RefreshCw, AlertTriangle, Calendar } from "lucide-react";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Badge } from "@/components/ui/Badge";
import { StatusPill } from "@/components/ui/status-pill";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/Tabs";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";
import { operationalHandoverService, type OperationalHandoverPayload } from "@/services/operational_handover_service";
import { bookingConfirmationService, type Booking } from "@/services/booking_confirmation_service";
import { type ArrivalHandover } from "@/services/arrival_handover_service";

const HANDOVER_EXPORT_HEADERS = [
  "Booking code", "Customer", "Check-in", "Check-out", "Rooms", "Handover status", "FO readiness",
];

function handoverExportRow(h: ArrivalHandover): (string | number | null | undefined)[] {
  return [
    h.bookingCode, h.customerName, h.checkInDate, h.checkOutDate,
    h.roomSummary ?? "", h.status ?? "", h.readinessStatus ?? "",
  ];
}

/** Rows per page — mirrors the `size: 10` requested from the logs endpoint. */
const LOGS_PAGE_SIZE = 10;
import { HandoverDetailDrawer } from "@/features/front_office_handover/components/HandoverDetailDrawer";
import { userService } from "@/services/user_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";

const handoverSchema = z.object({
  specialRequests: z.string().optional(),
  roomPreferences: z.string().optional(),
  vipNotes: z.string().optional(),
  operationalNotes: z.string().optional(),
  assignedFoUserId: z.string().optional(),
  status: z.enum(["DRAFT", "SUBMITTED"]),
}).refine(
  (data) => {
    if (data.status === "SUBMITTED") {
      return data.assignedFoUserId && data.assignedFoUserId.trim().length > 0;
    }
    return true;
  },
  {
    message: "Responsible Front Office Staff is required.",
    path: ["assignedFoUserId"],
  }
).refine(
  (data) => {
    if (data.status === "SUBMITTED") {
      return (
        (data.specialRequests && data.specialRequests.trim().length > 0) ||
        (data.roomPreferences && data.roomPreferences.trim().length > 0) ||
        (data.vipNotes && data.vipNotes.trim().length > 0) ||
        (data.operationalNotes && data.operationalNotes.trim().length > 0)
      );
    }
    return true;
  },
  {
    message: "Missing required handover information.",
    path: ["specialRequests"],
  }
);

type HandoverFormData = z.infer<typeof handoverSchema>;

export function OperationalHandoverScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const canWrite = user?.permissions?.includes("HANDOVER_WRITE") ?? false;

  const [activeTab, setActiveTab] = useState<"logs" | "pending">("logs");
  
  // Handover Logs tab states
  const [handovers, setHandovers] = useState<ArrivalHandover[]>([]);
  const [logsSearch, setLogsSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [logsPage, setLogsPage] = useState(0);
  const [logsPageSize, setLogsPageSize] = useState(10);
  const [logsTotalPages, setLogsTotalPages] = useState(0);
  const [logsTotalElements, setLogsTotalElements] = useState(0);
  const [loadingLogs, setLoadingLogs] = useState(false);

  // Confirmed Bookings tab states
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [bookingsSearch, setBookingsSearch] = useState("");
  const [bookingsPage, setBookingsPage] = useState(0);
  const [bookingsPageSize, setBookingsPageSize] = useState(10);
  const [bookingsTotalPages, setBookingsTotalPages] = useState(0);
  const [bookingsTotalElements, setBookingsTotalElements] = useState(0);
  const [loadingBookings, setLoadingBookings] = useState(false);

  // Users / FO list
  const [foUsers, setFoUsers] = useState<{ userId: string; fullName: string }[]>([]);

  // Modal states
  const [selectedHandover, setSelectedHandover] = useState<ArrivalHandover | null>(null);
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [createHandoverBooking, setCreateHandoverBooking] = useState<Booking | null>(null);
  const [editHandover, setEditHandover] = useState<ArrivalHandover | null>(null);
  const [submittingForm, setSubmittingForm] = useState(false);

  // Form setups
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<HandoverFormData>({
    resolver: zodResolver(handoverSchema),
    defaultValues: {
      specialRequests: "",
      roomPreferences: "",
      vipNotes: "",
      operationalNotes: "",
      assignedFoUserId: "",
      status: "DRAFT",
    },
  });

  const formStatus = watch("status");
  const assignedFoUserVal = watch("assignedFoUserId");

  // Fetch FO Users list once
  useEffect(() => {
    userService.getSummariesByRole("FO")
      .then(res => {
        if (res.data) {
          setFoUsers(res.data.map(u => ({ userId: u.userId, fullName: u.fullName })));
        }
      })
      .catch(err => console.error("Error loading FO staff list: ", err));
  }, []);

  // Fetch Handover Logs
  const fetchLogs = React.useCallback(async () => {
    setLoadingLogs(true);
    try {
      const res = await operationalHandoverService.getList({
        search: logsSearch,
        status: statusFilter,
        page: logsPage,
        size: logsPageSize,
      });
      if (res.data) {
        setHandovers(res.data.content || []);
        // Spring serialises Page as { content, page: {...} }; the flat totalPages is absent, so
        // this resolved to 0 and the logs pager never appeared.
        const pageMeta =
          res.data.page && typeof res.data.page === "object" ? res.data.page : null;
        setLogsTotalPages(pageMeta ? pageMeta.totalPages : (res.data.totalPages ?? 0));
        setLogsTotalElements(
          pageMeta ? pageMeta.totalElements : (res.data.totalElements ?? 0),
        );
      }
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to load operational handovers"));
    } finally {
      setLoadingLogs(false);
    }
  }, [logsSearch, statusFilter, logsPage, logsPageSize]);

  // Fetch Confirmed Bookings waiting for Handover
  const fetchBookings = React.useCallback(async () => {
    setLoadingBookings(true);
    try {
      const res = await bookingConfirmationService.getList({
        search: bookingsSearch,
        status: "CONFIRMED",
        page: bookingsPage,
        size: bookingsPageSize,
      });
      if (res.data) {
        // Which bookings already have a handover — asked of the server. Deriving it here from the
        // paged handover list was wrong three ways: it requested more rows than the API allows, it
        // read page metadata from a field the API does not send (so it only saw the first page),
        // and the list is owner-scoped, so a colleague's handover was invisible and its booking was
        // offered for a second one.
        const idsRes = await operationalHandoverService.getBookingIdsWithHandover();
        const existingBookingIds = new Set(idsRes.data ?? []);

        // This tab is "confirmed bookings *waiting for* a handover", so the ones that already have
        // one are what must be dropped. The predicate was inverted — it kept exactly those — while
        // the comment above it said the opposite.
        const filtered = (res.data.content || []).filter(
          b => !existingBookingIds.has(b.bookingId)
        );

        setBookings(filtered);
        const pageMeta =
          res.data.page && typeof res.data.page === "object" ? res.data.page : null;
        setBookingsTotalPages(pageMeta ? pageMeta.totalPages : (res.data.totalPages ?? 0));
        setBookingsTotalElements(
          pageMeta ? pageMeta.totalElements : (res.data.totalElements ?? filtered.length),
        );
      }
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to load bookings"));
    } finally {
      setLoadingBookings(false);
    }
  }, [bookingsSearch, bookingsPage]);

  useEffect(() => {
    if (activeTab === "logs") {
      fetchLogs();
    } else {
      fetchBookings();
    }
  }, [activeTab, fetchLogs, fetchBookings]);

  // Reset form when modal opens/closes
  useEffect(() => {
    if (editHandover) {
      reset({
        specialRequests: editHandover.specialRequests || "",
        roomPreferences: editHandover.roomPreferences || "",
        vipNotes: editHandover.vipNotes || "",
        operationalNotes: editHandover.operationalNotes || "",
        assignedFoUserId: editHandover.assignedFoUserId || "",
        status: (editHandover.status === "SUBMITTED" || editHandover.status === "ACKNOWLEDGED" || editHandover.status === "READY") ? "SUBMITTED" : "DRAFT",
      });
    } else if (createHandoverBooking) {
      reset({
        specialRequests: "",
        roomPreferences: "",
        vipNotes: "",
        operationalNotes: "",
        assignedFoUserId: "",
        status: "DRAFT",
      });
    }
  }, [editHandover, createHandoverBooking, reset]);

  // Handle Form Submit
  const onSubmit = async (data: HandoverFormData) => {
    setSubmittingForm(true);
    try {
      const payload: OperationalHandoverPayload = {
        specialRequests: data.specialRequests,
        roomPreferences: data.roomPreferences,
        vipNotes: data.vipNotes,
        operationalNotes: data.operationalNotes,
        assignedFoUserId: data.assignedFoUserId || undefined,
        status: data.status,
      };

      if (createHandoverBooking) {
        payload.bookingId = createHandoverBooking.bookingId;
        await operationalHandoverService.create(payload);
        toast.success(
          data.status === "SUBMITTED"
            ? "Operational handover submitted to Front Office successfully."
            : "Operational handover saved as draft successfully."
        );
        setCreateHandoverBooking(null);
      } else if (editHandover) {
        await operationalHandoverService.update(editHandover.handoverId, payload);
        toast.success(
          data.status === "SUBMITTED"
            ? "Operational handover updated and submitted successfully."
            : "Draft handover updated successfully."
        );
        setEditHandover(null);
      }

      if (activeTab === "logs") fetchLogs();
      else fetchBookings();
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to complete operational handover"));
    } finally {
      setSubmittingForm(false);
    }
  };

  /**
   * Handover and readiness statuses via the canonical bindings (Blueprint §2.7).
   *
   * These were two switch statements pinning raw Tailwind palettes
   * (`bg-amber-50`, `bg-sky-50`, `bg-purple-50`…) with no dark-mode variants, so
   * the badges relied on the global compatibility shim to be legible at night.
   * Token-backed pills theme themselves.
   */
  const getStatusBadge = (status?: string) =>
    status ? <StatusPill size="sm" domain="handover" value={status} /> : null;

  const getReadinessBadge = (readiness?: string) =>
    readiness ? <StatusPill size="sm" domain="readiness" value={readiness} /> : null;

  /** Column set — Blueprint §10.12 outgoing handover log. */
  const handoverLogColumns: ColumnDef<ArrivalHandover>[] = React.useMemo(() => [
    {
      id: "bookingCode",
      header: "Booking Code",
      sticky: "left",
      className: "whitespace-nowrap text-xs font-bold",
      cell: (h) => h.bookingCode,
    },
    {
      id: "customer",
      header: "Customer / Guest",
      className: "max-w-[180px] truncate text-xs font-bold",
      cell: (h) => <span title={h.customerName}>{h.customerName}</span>,
    },
    {
      id: "stay",
      header: "Check-In / Out",
      minWidth: "md",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (h) => `${h.checkInDate} / ${h.checkOutDate}`,
    },
    {
      id: "rooms",
      header: "Room Allocations",
      minWidth: "lg",
      className: "max-w-[220px] truncate text-xs text-muted-foreground",
      cell: (h) => <span title={h.roomSummary || ""}>{h.roomSummary || "—"}</span>,
    },
    {
      id: "status",
      header: "Handover Status",
      cell: (h) => getStatusBadge(h.status),
    },
    {
      id: "readiness",
      header: "FO Readiness",
      sticky: "right",
      cell: (h) => (
        <div className="flex flex-col items-start gap-1">
          {getReadinessBadge(h.readinessStatus)}
          {h.readinessStatus === "NEED_CLARIFICATION" && h.clarificationNote && (
            <span
              className="max-w-[120px] truncate rounded border border-danger/30 bg-danger/10 px-1.5 py-0.5 text-[10px] italic text-danger"
              title={h.clarificationNote}
            >
              {h.clarificationNote}
            </span>
          )}
        </div>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const logControls = useTableControls<ArrivalHandover>("handover-logs", handoverLogColumns);

  /**
   * Confirmed bookings that have no handover yet. The query already filters to
   * CONFIRMED, so the status column is a constant — kept because a Front Office
   * reader scanning the tab still expects to see it stated.
   */
  const pendingBookingColumns: ColumnDef<Booking>[] = React.useMemo(() => [
    {
      id: "code",
      header: "Booking Code",
      sticky: "left",
      className: "whitespace-nowrap text-xs font-bold",
      cell: (b) => b.bookingCode,
    },
    {
      id: "customer",
      header: "Customer / Guest",
      className: "max-w-[200px] truncate text-xs font-bold",
      cell: (b) => <span title={b.customerName}>{b.customerName}</span>,
    },
    {
      id: "stay",
      header: "Check-In / Out",
      minWidth: "md",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (b) => `${b.checkInDate} / ${b.checkOutDate}`,
    },
    {
      id: "total",
      header: "Total Amount",
      numeric: true,
      className: "font-bold",
      cell: (b) => `${b.totalAmount?.toLocaleString("vi-VN") ?? 0} ₫`,
    },
    {
      id: "status",
      header: "Status",
      sticky: "right",
      cell: (b) => <StatusPill size="sm" domain="booking" value={b.status} />,
    },
  ], []);

  const pendingControls = useTableControls<Booking>("handover-pending", pendingBookingColumns);

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      <PageHeader {...PAGE_META.operationalHandover} />

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "logs" | "pending")}>
        <TabsList className="border-b border-slate-200 dark:border-zinc-800 w-full mb-4">
          <TabsTrigger value="logs">Handover Logs</TabsTrigger>
          {canWrite && (
            <TabsTrigger value="pending" className="relative">
              Pending Bookings
              {bookings.length > 0 && (
                <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-red-500 text-white text-[10px] font-bold">
                  {bookings.length}
                </span>
              )}
            </TabsTrigger>
          )}
        </TabsList>

        {/* ==================== TAB CONTENT: Handover Logs ==================== */}
        <TabsContent value="logs" className="space-y-4">
          <Card className="border-slate-100 dark:border-zinc-800 shadow-sm bg-white dark:bg-zinc-900">
            <CardContent className="py-3 px-4 flex flex-row items-center justify-between gap-4 flex-wrap lg:flex-nowrap w-full">
              <div className="flex flex-row items-center gap-3 flex-1 w-full lg:w-auto">
                <div className="relative w-full lg:w-72">
                  <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 dark:text-zinc-500" />
                  <input
                    type="text"
                    placeholder="Search booking code, customer..."
                    value={logsSearch}
                    onChange={e => { setLogsSearch(e.target.value); setLogsPage(0); }}
                    className="w-full pl-8 pr-3 h-9 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-xs text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition"
                  />
                </div>
                <select
                  value={statusFilter}
                  onChange={e => { setStatusFilter(e.target.value); setLogsPage(0); }}
                  className="h-9 px-3 bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 rounded-lg text-xs text-slate-700 dark:text-zinc-200 focus:outline-none focus:border-blue-500"
                >
                  <option value="">All Statuses</option>
                  <option value="DRAFT">Draft</option>
                  <option value="SUBMITTED">Submitted</option>
                  <option value="ACKNOWLEDGED">Acknowledged</option>
                  <option value="READY">Ready</option>
                </select>
              </div>

              {/* Refresh button inside Logs Filter Card */}
              <Button
                variant="outline"
                size="sm"
                onClick={fetchLogs}
                className="flex items-center justify-center text-slate-650 dark:text-zinc-300 bg-white dark:bg-zinc-800 h-9 w-9 p-0 rounded-lg shrink-0 border-slate-200 dark:border-zinc-700"
                title="Refresh logs"
              >
                <RefreshCw className="size-3.5" />
              </Button>
            </CardContent>
          </Card>

          <DataTable
            label="Handover logs"
            rows={handovers}
            columns={logControls.visibleColumns}
            rowId={(h) => h.handoverId}
            isLoading={loadingLogs}
            density={logControls.density}
            sortBy={logControls.sortBy}
            sortDir={logControls.sortDir}
            onSortChange={logControls.onSortChange}
            onRowClick={(h) => setSelectedHandover(h)}
            selectedIds={logControls.selectedIds}
            onSelectionChange={logControls.setSelectedIds}
            bulkActions={
              <ExportMenu
                filename={`handovers-selected-${new Date().toISOString().slice(0, 10)}`}
                headers={HANDOVER_EXPORT_HEADERS}
                rows={handovers.filter((h) => logControls.selectedIds.has(h.handoverId)).map(handoverExportRow)}
              />
            }
            emptyTitle="No outgoing handovers"
            emptyMessage="Once a booking is confirmed, its handover to Front Office is recorded here."
            footer={
              <TablePagination
                page={logsPage}
                pageSize={logsPageSize}
                totalElements={logsTotalElements}
                totalPages={logsTotalPages}
                onPageChange={setLogsPage}
                onPageSizeChange={(s) => {
                  setLogsPageSize(s);
                  setLogsPage(0);
                }}
                pageSizeOptions={[10, 20, 50]}
              />
            }
          />
        </TabsContent>

        {/* ==================== TAB CONTENT: Pending Bookings ==================== */}
        {canWrite && (
          <TabsContent value="pending" className="space-y-4">
          <Card className="border-slate-100 dark:border-zinc-800 shadow-sm bg-white dark:bg-zinc-900">
            <CardContent className="py-3 px-4 flex flex-row items-center justify-between gap-4 flex-wrap lg:flex-nowrap w-full">
              <div className="relative w-full lg:w-72 shrink-0">
                <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 dark:text-zinc-500" />
                <input
                  type="text"
                  placeholder="Search booking code..."
                  value={bookingsSearch}
                  onChange={e => { setBookingsSearch(e.target.value); setBookingsPage(0); }}
                  className="w-full pl-8 pr-3 h-9 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-xs text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>

              {/* Refresh button inside Bookings Filter Card */}
              <Button
                variant="outline"
                size="sm"
                onClick={fetchBookings}
                className="flex items-center justify-center text-slate-655 dark:text-zinc-300 bg-white dark:bg-zinc-800 h-9 w-9 p-0 rounded-lg shrink-0 border-slate-200 dark:border-zinc-700"
                title="Refresh bookings"
              >
                <RefreshCw className="size-3.5" />
              </Button>
            </CardContent>
          </Card>

          <DataTable
            label="Confirmed bookings awaiting handover"
            rows={bookings}
            columns={pendingControls.visibleColumns}
            rowId={(b) => b.bookingId}
            isLoading={loadingBookings}
            density={pendingControls.density}
            sortBy={pendingControls.sortBy}
            sortDir={pendingControls.sortDir}
            onSortChange={pendingControls.onSortChange}
            onRowClick={(b) => setSelectedBooking(b)}
            emptyTitle="Nothing waiting for handover"
            emptyMessage="Confirmed bookings without an operational handover appear here."
            footer={
              <TablePagination
                page={bookingsPage}
                pageSize={bookingsPageSize}
                totalElements={bookingsTotalElements}
                totalPages={bookingsTotalPages}
                onPageChange={setBookingsPage}
                onPageSizeChange={(s) => {
                  setBookingsPageSize(s);
                  setBookingsPage(0);
                }}
                pageSizeOptions={[10, 20, 50]}
              />
            }
          />
        </TabsContent>
        )}
      </Tabs>

      {/* ==================== MODAL: Create / Edit Handover Form ==================== */}
      {(createHandoverBooking || editHandover) && (
        <div className="fixed inset-0 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-xl w-full max-w-2xl shadow-xl border border-slate-100 dark:border-zinc-800 flex flex-col max-h-[90vh]">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-t-xl">
              <div>
                <h3 className="font-bold text-slate-800 dark:text-zinc-100 text-base flex items-center gap-1.5">
                  <Workflow className="size-4.5 text-blue-600 dark:text-blue-400" />
                  {createHandoverBooking ? "Create Handover" : "Edit Handover"}
                </h3>
                <p className="text-xs text-slate-400 dark:text-zinc-500 mt-0.5">
                  Booking Code: <span className="font-bold text-slate-600 dark:text-zinc-300">{createHandoverBooking?.bookingCode || editHandover?.bookingCode}</span>
                </p>
              </div>
              <button
                onClick={() => { setCreateHandoverBooking(null); setEditHandover(null); }}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 transition p-1.5 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Modal Form Content */}
            <form onSubmit={handleSubmit(onSubmit)} className="flex-1 overflow-y-auto p-6 space-y-4">
              {/* Need Clarification Warning Box */}
              {editHandover?.readinessStatus === "NEED_CLARIFICATION" && editHandover.clarificationNote && (
                <div className="p-3 bg-rose-50 dark:bg-rose-950/20 border border-rose-100 dark:border-rose-900 rounded-xl flex items-start gap-2.5">
                  <AlertTriangle className="size-4.5 text-rose-500 shrink-0 mt-0.5" />
                  <div>
                    <h5 className="font-bold text-xs text-rose-800 dark:text-rose-400">FO requested clarification:</h5>
                    <p className="text-xs text-rose-700 dark:text-rose-300 mt-1 italic font-medium">&ldquo;{editHandover.clarificationNote}&rdquo;</p>
                  </div>
                </div>
              )}

              {/* Special Requests */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Special Requests</label>
                <textarea
                  {...register("specialRequests")}
                  rows={2}
                  placeholder="e.g. Honeymoon setup, high floor, quiet room..."
                  className="w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>

              {/* Room Preferences */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Room Preferences</label>
                <textarea
                  {...register("roomPreferences")}
                  rows={2}
                  placeholder="e.g. King-size bed, glass bathroom, lake view..."
                  className="w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>

              {/* VIP Notes */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">VIP Notes</label>
                <textarea
                  {...register("vipNotes")}
                  rows={2}
                  placeholder="e.g. Frequent guest, CEO of NovaX Company, ensure warm welcome..."
                  className="w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-rose-800 dark:text-rose-400 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition font-semibold placeholder:font-normal placeholder:text-slate-400"
                />
              </div>

              {/* Operational Notes */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Other Operational Notes</label>
                <textarea
                  {...register("operationalNotes")}
                  rows={2}
                  placeholder="e.g. Allow early check-in at 10 AM, serve breakfast in room on day 1..."
                  className="w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>

              {/* Front Office Staff Assignment */}
              <div className="space-y-1.5">
                <label className="text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider flex items-center gap-1">
                  Assigned FO Staff
                  {formStatus === "SUBMITTED" ? (
                    <span className="text-[10px] text-red-500 font-bold">*</span>
                  ) : (
                    <span className="text-[10px] text-slate-400 dark:text-zinc-500 font-normal normal-case">(Optional for Draft)</span>
                  )}
                </label>
                <select
                  {...register("assignedFoUserId")}
                  className={`w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition ${
                    !assignedFoUserVal ? "text-slate-400 dark:text-zinc-500" : "text-slate-800 dark:text-zinc-100 font-medium"
                  }`}
                >
                  <option value="" className="text-slate-400 opacity-65">Select FO Staff</option>
                  {foUsers.map(u => (
                    <option key={u.userId} value={u.userId} className="text-slate-850 dark:text-zinc-100 font-medium bg-white dark:bg-zinc-900">
                      {u.fullName}
                    </option>
                  ))}
                </select>
                {errors.assignedFoUserId && (
                  <p className="text-[10px] text-red-500 font-bold mt-0.5">{errors.assignedFoUserId.message}</p>
                )}
              </div>

              {/* Form Validation Errors */}
              {errors.specialRequests && (
                <div className="p-3 bg-red-50 dark:bg-red-950/20 text-red-600 dark:text-red-400 rounded-lg text-xs font-semibold border border-red-100 dark:border-red-900 flex items-center gap-1.5">
                  <AlertTriangle className="size-4 shrink-0" />
                  {errors.specialRequests.message}
                </div>
              )}

              {/* Action Buttons inside Form Footer */}
              <div className="flex flex-col sm:flex-row items-center justify-between border-t border-slate-100 dark:border-zinc-800 pt-5 gap-3">
                <div className="flex gap-2 w-full sm:w-auto">
                  <Button
                    type="submit"
                    variant="secondary"
                    onClick={() => setValue("status", "DRAFT")}
                    disabled={submittingForm}
                  >
                    Save Draft
                  </Button>
                </div>
                <div className="flex gap-2 w-full sm:w-auto justify-end">
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => { setCreateHandoverBooking(null); setEditHandover(null); }}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    onClick={() => setValue("status", "SUBMITTED")}
                    isLoading={submittingForm && formStatus === "SUBMITTED"}
                  >
                    Submit to Front Office
                  </Button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <HandoverDetailDrawer
        handover={selectedHandover}
        onOpenChange={(open) => !open && setSelectedHandover(null)}
        actions={
          // A submitted handover is locked; only a draft or one Front Office
          // sent back for clarification may be edited (§12.13).
          selectedHandover && canWrite &&
          (selectedHandover.status === "DRAFT" ||
            selectedHandover.readinessStatus === "NEED_CLARIFICATION")
            ? [
                {
                  label: "Edit handover",
                  icon: Edit,
                  variant: "primary" as const,
                  onClick: () => {
                    const target = selectedHandover;
                    setSelectedHandover(null);
                    setEditHandover(target);
                  },
                },
              ]
            : []
        }
      />

      {/* ==================== MODAL: View Booking Detail (Pending Handover) ==================== */}
      {selectedBooking && (
        <div className="fixed inset-0 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-xl w-full max-w-2xl shadow-xl border border-slate-100 dark:border-zinc-800 flex flex-col max-h-[90vh]">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-t-xl">
              <div>
                <h3 className="font-bold text-slate-800 dark:text-zinc-100 text-base flex items-center gap-1.5">
                  <ClipboardList className="size-4.5 text-blue-600 dark:text-blue-400" />
                  Booking Details
                </h3>
                <p className="text-xs text-slate-400 dark:text-zinc-500 mt-0.5">
                  Booking Code: <span className="font-bold text-slate-700 dark:text-zinc-300">{selectedBooking.bookingCode}</span>
                </p>
              </div>
              <button
                onClick={() => setSelectedBooking(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 transition p-1.5 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Modal Booking Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="text-xs">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Guest Name</span>
                  <span className="text-slate-855 dark:text-zinc-200 font-bold text-sm">{selectedBooking.customerName}</span>
                </div>
                <div className="text-xs">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Booking Status</span>
                  <Badge variant="success" className="bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-450 dark:border-emerald-900 mt-0.5 py-1 font-bold">CONFIRMED</Badge>
                </div>
                <div className="text-xs col-span-2 border-t border-slate-50 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1">Stay Period</span>
                  <span className="font-semibold text-slate-800 dark:text-zinc-200 flex items-center gap-1">
                    <Calendar className="size-4 text-slate-400" />
                    {selectedBooking.checkInDate} to {selectedBooking.checkOutDate}
                  </span>
                </div>
                <div className="text-xs col-span-2 border-t border-slate-50 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1">Rooms & Inventory Allocations</span>
                  {selectedBooking.details && selectedBooking.details.length > 0 ? (
                    <div className="space-y-1.5 mt-1.5">
                      {selectedBooking.details.map((d) => (
                        <div key={d.bookingDetailId} className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800 p-2.5 rounded-lg">
                          <div>
                            <span className="font-bold text-slate-800 dark:text-zinc-200 block text-xs">{d.productName}</span>
                            <span className="text-[10px] text-slate-400 dark:text-zinc-500">Qty: {d.quantity} | Nights: {d.nights} | Status: {d.inventoryStatus}</span>
                          </div>
                          {d.roomNumber ? (
                            <Badge className="bg-blue-100 text-blue-800 dark:bg-blue-950/20 dark:text-blue-400 font-bold border-blue-200 dark:border-blue-900">
                              {d.roomNumber}
                            </Badge>
                          ) : (
                            <span className="text-[10px] text-amber-600 dark:text-amber-450 font-medium">Not assigned yet</span>
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <span className="text-slate-500 dark:text-zinc-500 italic">No room allocations details.</span>
                  )}
                </div>
                <div className="text-xs col-span-2 border-t border-slate-50 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Total Amount</span>
                  <span className="font-extrabold text-slate-800 dark:text-zinc-200 text-base">{selectedBooking.totalAmount?.toLocaleString("vi-VN")} ₫</span>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end px-6 py-4 border-t border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-b-xl gap-3">
              <Button
                variant="ghost"
                onClick={() => setSelectedBooking(null)}
              >
                Cancel
              </Button>
              {canWrite && (
                <Button
                  variant="primary"
                  leftIcon={<Plus className="size-3.5" />}
                  onClick={() => {
                    setCreateHandoverBooking(selectedBooking);
                    setSelectedBooking(null);
                  }}
                >
                  Create Handover
                </Button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
