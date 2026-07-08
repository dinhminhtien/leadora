"use client";

import React, { useState, useEffect } from "react";
import { Workflow, ClipboardList, Search, Plus, Edit, X, RefreshCw, AlertTriangle, User, Calendar, CreditCard, Home } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/Tabs";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "@/stores/toast_store";
import { getApiErrorMessage } from "@/lib/api_error";
import { operationalHandoverService, type OperationalHandoverPayload } from "@/services/operational_handover_service";
import { bookingConfirmationService, type Booking } from "@/services/booking_confirmation_service";
import { type ArrivalHandover } from "@/services/arrival_handover_service";
import { userService } from "@/services/follow_up_task_service";

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
      return !!data.assignedFoUserId && data.assignedFoUserId.trim().length > 0;
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
  const [activeTab, setActiveTab] = useState<"logs" | "pending">("logs");
  
  // Handover Logs tab states
  const [handovers, setHandovers] = useState<ArrivalHandover[]>([]);
  const [logsSearch, setLogsSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [logsPage, setLogsPage] = useState(0);
  const [logsTotalPages, setLogsTotalPages] = useState(0);
  const [loadingLogs, setLoadingLogs] = useState(false);

  // Confirmed Bookings tab states
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [bookingsSearch, setBookingsSearch] = useState("");
  const [bookingsPage, setBookingsPage] = useState(0);
  const [bookingsTotalPages, setBookingsTotalPages] = useState(0);
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
    userService.getAll()
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
        size: 10,
      });
      if (res.data) {
        setHandovers(res.data.content || []);
        setLogsTotalPages(res.data.totalPages || 0);
      }
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to load operational handovers"));
    } finally {
      setLoadingLogs(false);
    }
  }, [logsSearch, statusFilter, logsPage]);

  // Fetch Confirmed Bookings waiting for Handover
  const fetchBookings = React.useCallback(async () => {
    setLoadingBookings(true);
    try {
      const res = await bookingConfirmationService.getList({
        search: bookingsSearch,
        status: "CONFIRMED",
        page: bookingsPage,
        size: 10,
      });
      if (res.data) {
        // Fetch all handovers to filter out bookings that already have handovers
        const handoversRes = await operationalHandoverService.getList({ size: 1000 });
        const existingBookingIds = new Set(
          handoversRes.data?.content?.map(h => h.bookingId).filter(Boolean) || []
        );

        const filtered = (res.data.content || []).filter(
          b => !existingBookingIds.has(b.bookingId)
        );

        setBookings(filtered);
        setBookingsTotalPages(res.data.totalPages || 0);
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
        assignedFoUserId: "",
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

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case "DRAFT":
        return <Badge variant="warning" className="font-bold bg-amber-50 text-amber-700 border-amber-200">DRAFT</Badge>;
      case "SUBMITTED":
        return <Badge variant="primary" className="font-bold bg-blue-50 text-blue-700 border-blue-200">SUBMITTED</Badge>;
      case "ACKNOWLEDGED":
        return <Badge className="font-bold bg-purple-50 text-purple-700 border-purple-200">ACKNOWLEDGED</Badge>;
      case "READY":
        return <Badge variant="success" className="font-bold bg-emerald-50 text-emerald-700 border-emerald-200">READY</Badge>;
      default:
        return <Badge variant="default">{status || "UNKNOWN"}</Badge>;
    }
  };

  const getReadinessBadge = (readiness?: string) => {
    switch (readiness) {
      case "PENDING_REVIEW":
        return <Badge variant="default" className="font-bold bg-slate-50 text-slate-500 border-slate-200">PENDING REVIEW</Badge>;
      case "REVIEWED":
        return <Badge variant="primary" className="font-bold bg-sky-50 text-sky-700 border-sky-200">REVIEWED</Badge>;
      case "READY_FOR_ARRIVAL":
        return <Badge variant="success" className="font-bold bg-emerald-50 text-emerald-700 border-emerald-200">READY FOR ARRIVAL</Badge>;
      case "NEED_CLARIFICATION":
        return <Badge variant="danger" className="font-bold bg-rose-50 text-rose-700 border-rose-200 flex items-center gap-1">NEED CLARIFICATION</Badge>;
      default:
        return null;
    }
  };

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800 dark:text-zinc-100">Operational Handovers</h1>
          <p className="text-xs text-slate-400 dark:text-zinc-400">Transfer deal details, room lists, VIP requirements, and instructions to hotel operations</p>
        </div>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "logs" | "pending")}>
        <TabsList className="border-b border-slate-200 dark:border-zinc-800 w-full mb-4">
          <TabsTrigger value="logs">Handover Logs</TabsTrigger>
          <TabsTrigger value="pending" className="relative">
            Pending Bookings
            {bookings.length > 0 && (
              <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-red-500 text-white text-[10px] font-bold">
                {bookings.length}
              </span>
            )}
          </TabsTrigger>
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

          {loadingLogs ? (
            <div className="text-center py-12 text-slate-400 text-xs flex items-center justify-center gap-2 bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm">
              <RefreshCw className="size-4 animate-spin text-blue-500" /> Loading handover logs...
            </div>
          ) : (
            <div className="bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm overflow-hidden">
              <div className="w-full overflow-x-auto">
                <Table className="w-full table-fixed min-w-[1100px]">
                  <TableHeader className="bg-slate-50 dark:bg-zinc-800/40 border-b border-slate-100 dark:border-zinc-850">
                    <TableRow hoverable={false}>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[12%] !text-left whitespace-nowrap">Booking Code</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[20%] !text-left whitespace-nowrap">Customer / Guest</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[20%] !text-center whitespace-nowrap">Check-In / Out Date</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[24%] !text-left whitespace-nowrap">Room Allocations</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[12%] !text-center whitespace-nowrap">Handover Status</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[12%] !text-center whitespace-nowrap">FO Readiness</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {handovers.length > 0 ? (
                      handovers.map(h => (
                        <TableRow
                          key={h.handoverId}
                          onClick={() => setSelectedHandover(h)}
                          className="hover:bg-slate-50/70 dark:hover:bg-zinc-800/30 border-b border-slate-100 dark:border-zinc-800 transition cursor-pointer select-none"
                        >
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-left whitespace-nowrap">
                            {h.bookingCode}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-800 dark:!text-zinc-200 !text-left whitespace-nowrap truncate max-w-[180px]" title={h.customerName}>
                            {h.customerName}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-500 dark:!text-zinc-400 !text-center whitespace-nowrap">
                            {h.checkInDate} / {h.checkOutDate}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-650 dark:!text-zinc-400 !text-left whitespace-nowrap truncate max-w-[220px]" title={h.roomSummary || ""}>
                            {h.roomSummary || "—"}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-center whitespace-nowrap">
                            <div className="flex justify-center">
                              {getStatusBadge(h.status)}
                            </div>
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-center whitespace-nowrap">
                            <div className="flex flex-col items-center justify-center gap-1">
                              {getReadinessBadge(h.readinessStatus)}
                              {h.readinessStatus === "NEED_CLARIFICATION" && h.clarificationNote && (
                                <span className="text-[10px] text-rose-600 dark:text-rose-450 max-w-[120px] truncate italic bg-rose-50 dark:bg-rose-950/20 px-1.5 py-0.5 rounded border border-rose-100 dark:border-rose-900" title={h.clarificationNote}>
                                  {h.clarificationNote}
                                </span>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow hoverable={false}>
                        <TableCell colSpan={6} className="py-12 text-center text-slate-400 dark:text-zinc-500 text-xs">
                          No outgoing handovers found.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>

              {/* Logs Pagination */}
              {logsTotalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 bg-slate-50 dark:bg-zinc-800/40 border-t border-slate-100 dark:border-zinc-800 text-xs text-slate-500 dark:text-zinc-400">
                  <div>
                    Page {logsPage + 1} of {logsTotalPages}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      disabled={logsPage === 0}
                      onClick={() => setLogsPage(prev => Math.max(0, prev - 1))}
                      className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-600 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                    >
                      Previous
                    </Button>
                    <Button
                      size="sm"
                      disabled={logsPage >= logsTotalPages - 1}
                      onClick={() => setLogsPage(prev => Math.min(logsTotalPages - 1, prev + 1))}
                      className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-600 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </TabsContent>

        {/* ==================== TAB CONTENT: Pending Bookings ==================== */}
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

          {loadingBookings ? (
            <div className="text-center py-12 text-slate-400 text-xs flex items-center justify-center gap-2 bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm">
              <RefreshCw className="size-4 animate-spin text-blue-500" /> Loading pending bookings...
            </div>
          ) : (
            <div className="bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm overflow-hidden">
              <div className="w-full overflow-x-auto">
                <Table className="w-full table-fixed min-w-[1000px]">
                  <TableHeader className="bg-slate-50 dark:bg-zinc-800/40 border-b border-slate-100 dark:border-zinc-850">
                    <TableRow hoverable={false}>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-left whitespace-nowrap">Booking Code</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[30%] !text-left whitespace-nowrap">Customer / Guest</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[25%] !text-center whitespace-nowrap">Check-In / Out Date</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[13%] !text-right whitespace-nowrap">Total Amount</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[17%] !text-center whitespace-nowrap">Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {bookings.length > 0 ? (
                      bookings.map(b => (
                        <TableRow
                          key={b.bookingId}
                          onClick={() => setSelectedBooking(b)}
                          className="hover:bg-slate-50/70 dark:hover:bg-zinc-800/30 border-b border-slate-100 dark:border-zinc-800 transition cursor-pointer select-none"
                        >
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-left whitespace-nowrap">
                            {b.bookingCode}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-800 dark:!text-zinc-200 !text-left whitespace-nowrap truncate max-w-[200px]" title={b.customerName}>
                            {b.customerName}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-500 dark:!text-zinc-400 !text-center whitespace-nowrap">
                            {b.checkInDate} / {b.checkOutDate}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-right whitespace-nowrap">
                            ${b.totalAmount?.toLocaleString()}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-center whitespace-nowrap">
                            <div className="flex justify-center">
                              <Badge variant="success" className="bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-400 dark:border-emerald-900 font-bold py-1">CONFIRMED</Badge>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow hoverable={false}>
                        <TableCell colSpan={5} className="py-12 text-center text-slate-400 dark:text-zinc-500 text-xs">
                          No confirmed bookings waiting for handover.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>

              {/* Bookings Pagination */}
              {bookingsTotalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 bg-slate-50 dark:bg-zinc-800/40 border-t border-slate-100 dark:border-zinc-800 text-xs text-slate-500 dark:text-zinc-400">
                  <div>
                    Page {bookingsPage + 1} of {bookingsTotalPages}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      disabled={bookingsPage === 0}
                      onClick={() => setBookingsPage(prev => Math.max(0, prev - 1))}
                      className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-600 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                    >
                      Previous
                    </Button>
                    <Button
                      size="sm"
                      disabled={bookingsPage >= bookingsTotalPages - 1}
                      onClick={() => setBookingsPage(prev => Math.min(bookingsTotalPages - 1, prev + 1))}
                      className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-600 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </TabsContent>
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
                    <p className="text-xs text-rose-700 dark:text-rose-300 mt-1 italic font-medium">"{editHandover.clarificationNote}"</p>
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
                <label className="block text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider flex items-center gap-1">
                  Assigned FO Staff
                  {formStatus === "SUBMITTED" ? (
                    <span className="text-[10px] text-red-500 font-bold">*</span>
                  ) : (
                    <span className="text-[10px] text-slate-400 dark:text-zinc-500 font-normal normal-case">(Optional for Draft)</span>
                  )}
                </label>
                <select
                  {...register("assignedFoUserId")}
                  className={`w-full p-2.5 text-xs rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-850 dark:text-zinc-100 focus:outline-none focus:border-blue-500 focus:bg-white dark:focus:bg-zinc-900 transition ${
                    !assignedFoUserVal ? "text-slate-450 opacity-60 dark:text-zinc-500" : "text-slate-800 dark:text-zinc-100 font-medium"
                  }`}
                >
                  <option value="" className="text-slate-400 opacity-65">Select FO staff</option>
                  {foUsers.map(u => (
                    <option key={u.userId} value={u.userId} className="text-slate-800 dark:text-zinc-100 font-medium bg-white dark:bg-zinc-900">
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

      {/* ==================== MODAL: View Handover Detail ==================== */}
      {selectedHandover && (
        <div className="fixed inset-0 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-xl w-full max-w-4xl shadow-xl border border-slate-100 dark:border-zinc-800 flex flex-col max-h-[90vh]">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-t-xl">
              <div>
                <h3 className="font-bold text-slate-800 dark:text-zinc-100 text-base flex items-center gap-1.5">
                  <Workflow className="size-4.5 text-blue-600 dark:text-blue-400" />
                  Handover Logs Detail
                </h3>
                <p className="text-xs text-slate-400 dark:text-zinc-500 mt-0.5">
                  Booking Code: <span className="font-bold text-slate-700 dark:text-zinc-300">{selectedHandover.bookingCode}</span>
                </p>
              </div>
              <button
                onClick={() => setSelectedHandover(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 transition p-1.5 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Modal Detail Body */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Warnings / Readiness Status */}
              {selectedHandover.readinessStatus === "NEED_CLARIFICATION" && selectedHandover.clarificationNote && (
                <div className="p-4 bg-rose-50 dark:bg-rose-950/20 border border-rose-100 dark:border-rose-900 rounded-lg flex items-start gap-3 shadow-sm">
                  <AlertTriangle className="size-4.5 text-rose-500 shrink-0 mt-0.5" />
                  <div>
                    <h5 className="font-bold text-xs text-rose-800 dark:text-rose-400">FO requested clarification on this handover:</h5>
                    <p className="text-xs text-rose-700 dark:text-rose-300 mt-1 italic font-semibold">"{selectedHandover.clarificationNote}"</p>
                  </div>
                </div>
              )}

              {/* Status Header Block */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 bg-slate-50 dark:bg-zinc-800 p-4 rounded-lg border border-slate-100 dark:border-zinc-800">
                <div className="text-xs">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1 uppercase tracking-wider font-semibold">Handover Status</span>
                  {getStatusBadge(selectedHandover.status)}
                </div>
                <div className="text-xs">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1 uppercase tracking-wider font-semibold">FO Readiness</span>
                  {getReadinessBadge(selectedHandover.readinessStatus) || <span className="text-slate-400 font-bold text-[10px] uppercase">PENDING REVIEW</span>}
                </div>
                <div className="text-xs">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1 uppercase tracking-wider font-semibold">Last Updated By</span>
                  <span className="text-slate-800 dark:text-zinc-200 font-bold text-xs flex items-center gap-1 mt-0.5">
                    <User className="size-3.5 text-slate-400" />
                    {selectedHandover.updatedByName || "N/A"}
                  </span>
                </div>
              </div>

              {/* Content Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Column 1: Booking & Room summary */}
                <div className="space-y-4">
                  {/* Customer Block */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-3 shadow-sm bg-white dark:bg-zinc-900">
                    <h4 className="font-bold text-xs text-slate-400 dark:text-zinc-500 uppercase tracking-wider flex items-center gap-1">
                      <User className="size-4 text-blue-500" /> Customer & Booking Context
                    </h4>
                    <div className="grid grid-cols-2 gap-y-2 text-xs">
                      <div>
                        <span className="text-slate-400 block">Guest Name</span>
                        <span className="font-bold text-slate-800 dark:text-zinc-250">{selectedHandover.customerName}</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Phone Number</span>
                        <span className="font-bold text-slate-800 dark:text-zinc-250">{selectedHandover.customerPhone || "—"}</span>
                      </div>
                      <div className="col-span-2 border-t border-slate-50 dark:border-zinc-800 pt-2 flex items-center gap-2">
                        <Calendar className="size-4 text-slate-400" />
                        <div>
                          <span className="text-slate-400 block">Stay Duration</span>
                          <span className="font-bold text-slate-800 dark:text-zinc-200">{selectedHandover.checkInDate} to {selectedHandover.checkOutDate}</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Room Allocation Block */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-3 shadow-sm bg-white dark:bg-zinc-900">
                    <h4 className="font-bold text-xs text-slate-400 dark:text-zinc-500 uppercase tracking-wider flex items-center gap-1">
                      <Home className="size-4 text-emerald-500" /> Room Allocations
                    </h4>
                    {selectedHandover.rooms && selectedHandover.rooms.length > 0 ? (
                      <div className="space-y-2">
                        {selectedHandover.rooms.map((r, i) => (
                          <div key={i} className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800 p-2.5 rounded-lg text-xs">
                            <div>
                              <span className="font-bold text-slate-800 dark:text-zinc-200 block">{r.productName}</span>
                              <span className="text-[10px] text-slate-400 dark:text-zinc-500">Qty: {r.quantity} | Nights: {r.nights}</span>
                            </div>
                            {r.roomNumber ? (
                              <Badge className="bg-blue-100 text-blue-800 dark:bg-blue-950/20 dark:text-blue-400 font-bold border-blue-200 dark:border-blue-900">
                                {r.roomNumber}
                              </Badge>
                            ) : (
                              <span className="text-[10px] text-amber-600 dark:text-amber-400 font-medium">Not assigned yet</span>
                            )}
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-slate-400 dark:text-zinc-500 text-xs py-2 italic">
                        No detailed room allocations found.
                      </div>
                    )}
                  </div>

                  {/* Payment Block */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-2 shadow-sm bg-white dark:bg-zinc-900">
                    <h4 className="font-bold text-xs text-slate-400 dark:text-zinc-500 uppercase tracking-wider flex items-center gap-1">
                      <CreditCard className="size-4 text-purple-500" /> Payment & Deposit Reference
                    </h4>
                    <div className="text-xs font-semibold text-slate-800 dark:text-zinc-200">
                      {selectedHandover.paymentReference || "—"}
                    </div>
                  </div>
                </div>

                {/* Column 2: Handover details */}
                <div className="space-y-4">
                  {/* Special Requests */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-1 bg-white dark:bg-zinc-900 shadow-sm">
                    <span className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider block">Special Requests</span>
                    <div className="text-xs text-slate-700 dark:text-zinc-300 whitespace-pre-wrap leading-relaxed min-h-[40px] italic">
                      {selectedHandover.specialRequests || "None"}
                    </div>
                  </div>

                  {/* Room Preferences */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-1 bg-white dark:bg-zinc-900 shadow-sm">
                    <span className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider block">Room Preferences</span>
                    <div className="text-xs text-slate-700 dark:text-zinc-300 whitespace-pre-wrap leading-relaxed min-h-[40px] italic">
                      {selectedHandover.roomPreferences || "None"}
                    </div>
                  </div>

                  {/* VIP Notes */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-1 bg-white dark:bg-zinc-900 shadow-sm">
                    <span className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider block">VIP Notes</span>
                    <div className="text-xs text-rose-700 dark:text-rose-400 whitespace-pre-wrap leading-relaxed min-h-[40px] font-semibold">
                      {selectedHandover.vipNotes || "None"}
                    </div>
                  </div>

                  {/* Operational Notes */}
                  <div className="border border-slate-100 dark:border-zinc-800 rounded-lg p-4 space-y-1 bg-white dark:bg-zinc-900 shadow-sm">
                    <span className="text-xs font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider block">Other Operational Notes</span>
                    <div className="text-xs text-slate-700 dark:text-zinc-300 whitespace-pre-wrap leading-relaxed min-h-[40px] italic">
                      {selectedHandover.operationalNotes || "None"}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end px-6 py-4 border-t border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-b-xl gap-3">
              <Button
                variant="ghost"
                onClick={() => setSelectedHandover(null)}
              >
                Close
              </Button>
              
              {(selectedHandover.status === "DRAFT" || selectedHandover.readinessStatus === "NEED_CLARIFICATION") && (
                <Button
                  variant="primary"
                  leftIcon={<Edit className="size-3.5" />}
                  onClick={() => {
                    setEditHandover(selectedHandover);
                    setSelectedHandover(null);
                  }}
                >
                  Edit Handover
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

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
                  <span className="font-extrabold text-slate-800 dark:text-zinc-200 text-base">${selectedBooking.totalAmount?.toLocaleString()}</span>
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
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
