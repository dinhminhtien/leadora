"use client";

import React, { useState, useEffect, useMemo, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import * as z from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  CreditCard,
  CheckCircle2,
  Search,
  Plus,
  X,
  RefreshCw,
  AlertTriangle,
  Calendar,
  FileText,
  Printer
} from "lucide-react";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";
import { StatusPill } from "@/components/ui/status-pill";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { PaymentDetailDrawer } from "@/features/deposit_payment/components/PaymentDetailDrawer";
import { toast } from "@/stores/toast_store";
import { useConfirm } from "@/components/ui/confirm-dialog";

/** Rows per page — mirrors the `size: 10` requested from the payments endpoint. */
const PAYMENTS_PAGE_SIZE = 10;

const PAYMENT_EXPORT_HEADERS = [
  "Booking code", "Guest", "Payment type", "Due date", "Status", "Amount (VND)",
];

function paymentExportRow(p: Payment): (string | number | null | undefined)[] {
  return [
    p.bookingCode ?? "", p.customerName ?? "", p.paymentType,
    p.dueDate ?? "", p.status, p.amount,
  ];
}
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import {
  depositPaymentService,
  type Payment,
  type PaymentStatus,
  type PaymentType,
} from "@/services/deposit_payment_service";
import {
  bookingConfirmationService,
  type Booking
} from "@/services/booking_confirmation_service";

const generatePaymentSchema = z.object({
  bookingId: z.string().min(1, "Booking reference is required"),
  amount: z.coerce.number().positive("Amount must be greater than 0"),
  paymentType: z.enum(["DEPOSIT", "FULL_PAYMENT"]),
  paymentMethod: z.string().min(1, "Payment method is required"),
  dueDate: z.string().min(1, "Due date is required"),
  notes: z.string().optional()
});

type GeneratePaymentFormData = z.infer<typeof generatePaymentSchema>;

export function DepositPaymentScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const canWrite = user?.permissions?.includes("PAYMENT_WRITE") ?? false;

  // Design-system confirmation (§3.16) replacing the native confirm().
  const { confirm, confirmElement } = useConfirm();
  const searchParams = useSearchParams();
  const initialSearch = searchParams ? searchParams.get("search") || "" : "";

  const [activeTab, setActiveTab] = useState<"requests" | "bookings">("requests");

  // Tab 1: Payment Requests states
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loadingPayments, setLoadingPayments] = useState(false);
  const [paymentsSearch, setPaymentsSearch] = useState(initialSearch);

  useEffect(() => {
    if (initialSearch) {
      setPaymentsSearch(initialSearch);
      setActiveTab("requests");
    }
  }, [initialSearch]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [paymentsPage, setPaymentsPage] = useState(0);
  const [paymentsTotalPages, setPaymentsTotalPages] = useState(0);
  const [paymentsTotalElements, setPaymentsTotalElements] = useState(0);

  // Tab 2: Confirmed Bookings states
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loadingBookings, setLoadingBookings] = useState(false);
  const [bookingsSearch, setBookingsSearch] = useState("");
  const [bookingsPage, setBookingsPage] = useState(0);
  const [bookingsTotalPages, setBookingsTotalPages] = useState(0);
  const [bookingsTotalElements, setBookingsTotalElements] = useState(0);

  // Modals and detail states
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showGenerateModal, setShowGenerateModal] = useState(false);
  const [selectedBookingForRequest, setSelectedBookingForRequest] = useState<Booking | null>(null);
  const [selectedBookingForDetails, setSelectedBookingForDetails] = useState<Booking | null>(null);

  // Printable Receipt states
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [receiptBooking, setReceiptBooking] = useState<Booking | null>(null);
  const [loadingReceiptBooking, setLoadingReceiptBooking] = useState(false);
  const [printingPayment, setPrintingPayment] = useState<Payment | null>(null);

  // Action states
  const [submittingRequest, setSubmittingRequest] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // Manual confirmation states
  const [showConfirmPaidForm, setShowConfirmPaidForm] = useState(false);
  const [verificationNote, setVerificationNote] = useState("");
  const [verificationNoteError, setVerificationNoteError] = useState("");

  // Form setup for Generate Request
  const {
    register,
    handleSubmit,
    setValue,
    reset,
    watch,
    formState: { errors }
  } = useForm<GeneratePaymentFormData>({
    resolver: zodResolver(generatePaymentSchema) as any,
    defaultValues: {
      bookingId: "",
      amount: 0,
      paymentType: "DEPOSIT",
      paymentMethod: "TRANSFER",
      dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
      notes: ""
    }
  });

  const watchPaymentMethod = watch("paymentMethod", "TRANSFER");
  const watchAmount = watch("amount", 0);

  // Fetch payments list
  const loadPayments = useCallback(async () => {
    setLoadingPayments(true);
    try {
      const res = await depositPaymentService.getList({
        search: paymentsSearch.trim() || undefined,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        paymentType: typeFilter === "ALL" ? undefined : typeFilter,
        page: paymentsPage,
        size: 10,
        sortBy: "createdAt",
        sortDir: "desc"
      });
      if (res.success && res.data) {
        setPayments(res.data.content || []);
        setPaymentsTotalPages(res.data.totalPages || 0);
        setPaymentsTotalElements(res.data.totalElements ?? (res.data.content?.length ?? 0));
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to load payment transactions.");
    } finally {
      setLoadingPayments(false);
    }
  }, [paymentsSearch, statusFilter, typeFilter, paymentsPage]);

  // Fetch bookings list
  const loadBookings = useCallback(async () => {
    setLoadingBookings(true);
    try {
      // Get all confirmed bookings to generate payment requests
      const res = await bookingConfirmationService.getList({
        search: bookingsSearch.trim() || undefined,
        status: "CONFIRMED",
        page: bookingsPage,
        size: 10,
        sortBy: "createdAt",
        sortDir: "desc"
      });
      if (res.success && res.data) {
        // Fetch all active payments to filter out bookings that already have active (PENDING or PAID) payments
        const paymentsRes = await depositPaymentService.getList({ size: 1000 });
        const existingBookingCodes = new Set(
          paymentsRes.data?.content
            ?.filter(p => p.status === "PENDING" || p.status === "PAID")
            .map(p => p.bookingCode)
            .filter(Boolean) || []
        );

        const filteredBookings = (res.data.content || []).filter(
          b => !existingBookingCodes.has(b.bookingCode)
        );
        setBookings(filteredBookings);
        setBookingsTotalPages(res.data.totalPages || 0);
        setBookingsTotalElements(res.data.totalElements ?? (res.data.content?.length ?? 0));
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to load bookings list.");
    } finally {
      setLoadingBookings(false);
    }
  }, [bookingsSearch, bookingsPage]);

  // Handle data load on active tab or page change
  useEffect(() => {
    if (activeTab === "requests") {
      loadPayments();
    } else {
      loadBookings();
    }
  }, [activeTab, loadPayments, loadBookings]);

  // Open Generate Request Modal for a specific booking
  const handleOpenGenerateModal = (booking: Booking) => {
    setSelectedBookingForRequest(booking);
    setValue("bookingId", booking.bookingId);
    setValue("amount", booking.totalAmount);
    setValue("paymentType", "DEPOSIT");
    setValue("paymentMethod", "TRANSFER");
    setValue("notes", `Deposit payment for booking reference ${booking.bookingCode}`);
    setShowGenerateModal(true);
  };

  // Submit Generate Payment Request (UC-21.1)
  const onGenerateSubmit = async (data: GeneratePaymentFormData) => {
    setSubmittingRequest(true);
    try {
      const res = await depositPaymentService.generate(data);
      if (res.success) {
        toast.success("Payment request generated successfully.");
        setShowGenerateModal(false);
        reset();
        setActiveTab("requests");
        loadPayments();
        loadBookings();
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || "Failed to generate payment request.";
      toast.error(msg);
    } finally {
      setSubmittingRequest(false);
    }
  };

  // Open print modal and fetch booking details on demand
  const handleOpenPrintModal = async (payment: Payment) => {
    setPrintingPayment(payment);
    setSelectedPayment(null);
    setShowPrintModal(true);
    setLoadingReceiptBooking(true);
    setReceiptBooking(null);
    try {
      const res = await bookingConfirmationService.getById(payment.bookingId);
      if (res.success && res.data) {
        setReceiptBooking(res.data);
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to load booking details for receipt.");
    } finally {
      setLoadingReceiptBooking(false);
    }
  };

  // Download VietQR code image
  const handleDownloadQR = async (url: string, paymentId: string) => {
    try {
      const response = await fetch(url);
      const blob = await response.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      a.download = `VietQR_Payment_${paymentId}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);
    } catch (err) {
      // Fallback: Open in new tab if CORS prevents direct download
      const a = document.createElement("a");
      a.href = url;
      a.target = "_blank";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
  };

  // View Payment Details (UC-21.3)
  const handleViewDetails = async (paymentId: string) => {
    setActionLoading(true);
    try {
      const res = await depositPaymentService.getById(paymentId);
      if (res.success && res.data) {
        setSelectedPayment(res.data);
        setShowDetailModal(true);
        setShowConfirmPaidForm(false);
        setVerificationNote("");
        setVerificationNoteError("");
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to load payment transaction details.");
    } finally {
      setActionLoading(false);
    }
  };

  // Confirm Manual Payment PAID (UC-21.4)
  const handleConfirmPaidSubmit = async () => {
    if (!verificationNote.trim()) {
      setVerificationNoteError("Verification note / Reference ID is required.");
      return;
    }
    if (!selectedPayment) return;

    setActionLoading(true);
    try {
      const res = await depositPaymentService.updateStatus(selectedPayment.paymentId, {
        status: "PAID",
        verificationNote: verificationNote.trim()
      });
      if (res.success) {
        toast.success("Payment status updated to PAID.");
        setShowDetailModal(false);
        loadPayments();
        loadBookings();
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || "Failed to confirm payment.";
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  // Cancel Payment Request (UC-21.5).
  //
  // Destructive and irreversible for the customer's link, so it uses the
  // design-system confirmation (§1.5 / §3.16) with `danger` severity rather
  // than the native `confirm`, which could not convey either fact.
  const handleCancelRequest = async (paymentId: string) => {
    const { ok } = await confirm({
      title: "Cancel this payment request?",
      description:
        "The VietQR code and checkout link stop working immediately. The guest will need a new request to pay.",
      severity: "danger",
      confirmLabel: "Cancel request",
      cancelLabel: "Keep it",
    });
    if (!ok) return;

    setActionLoading(true);
    try {
      const res = await depositPaymentService.cancel(paymentId);
      if (res.success) {
        toast.success("Payment request has been cancelled.");
        setShowDetailModal(false);
        loadPayments();
        loadBookings();
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || "Failed to cancel payment request.";
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success("Copied to clipboard!");
  };

  // Render status badge helper
  const getStatusBadge = (status: PaymentStatus) => {
    switch (status) {
      case "PAID":
        return <Badge variant="success" className="bg-emerald-55 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-450 dark:border-emerald-900 font-bold uppercase text-[9px] py-1 min-w-22.5 justify-center text-center">PAID</Badge>;
      case "PENDING":
        return <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-950/20 dark:text-amber-400 border border-amber-200 dark:border-amber-900 uppercase text-[9px] font-bold py-1 min-w-22.5 justify-center text-center">PENDING</Badge>;
      case "CANCELLED":
        return <Badge variant="default" className="bg-slate-100 text-slate-800 dark:bg-zinc-800 dark:text-zinc-400 border border-slate-200 dark:border-zinc-700 uppercase text-[9px] font-bold py-1 min-w-22.5 justify-center text-center">CANCELLED</Badge>;
      case "FAILED":
        return <Badge variant="danger" className="bg-rose-50 text-rose-700 dark:bg-rose-950/20 dark:text-rose-400 border border-rose-200 dark:border-rose-900 uppercase text-[9px] font-bold py-1 min-w-22.5 justify-center text-center">FAILED</Badge>;
      case "EXPIRED":
        return <Badge variant="default" className="bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-700 uppercase text-[9px] font-bold py-1 min-w-22.5 justify-center text-center">EXPIRED</Badge>;
      default:
        return <Badge>{status}</Badge>;
    }
  };

  /** Column set — Blueprint §10.11 transaction register. */
  const paymentColumns: ColumnDef<Payment>[] = useMemo(() => [
    {
      id: "booking",
      header: "Booking Reference",
      sticky: "left",
      cell: (p) => (
        <span className="flex items-center gap-1.5 whitespace-nowrap text-xs font-bold text-primary">
          <CreditCard className="size-3.5 shrink-0 text-muted-foreground" />
          {p.bookingCode || "N/A"}
        </span>
      ),
    },
    {
      id: "guest",
      header: "Guest Name",
      className: "max-w-37.5 truncate text-xs font-bold",
      cell: (p) => <span title={p.customerName || "N/A"}>{p.customerName || "N/A"}</span>,
    },
    {
      id: "type",
      header: "Payment Type",
      minWidth: "md",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (p) => (p.paymentType === "DEPOSIT" ? "Deposit Hold" : "Full Bill Settlement"),
    },
    {
      id: "dueDate",
      header: "Due Date",
      minWidth: "lg",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (p) => p.dueDate || "N/A",
    },
    {
      id: "status",
      header: "Gateway Status",
      cell: (p) => getStatusBadge(p.status),
    },
    {
      id: "amount",
      header: "Amount Paid",
      numeric: true,
      sticky: "right",
      className: "font-bold",
      cell: (p) => `${p.amount?.toLocaleString("vi-VN") ?? 0} ₫`,
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const paymentControls = useTableControls<Payment>("payments", paymentColumns);

  /**
   * Confirmed bookings that have no payment request yet (UC-21.1). Status is
   * always CONFIRMED here — the query filters on it — so the pill is a constant
   * label rather than a variable one.
   */
  const awaitingColumns: ColumnDef<Booking>[] = useMemo(() => [
    {
      id: "code",
      header: "Booking Reference",
      sticky: "left",
      className: "whitespace-nowrap text-xs font-bold",
      cell: (b) => b.bookingCode,
    },
    {
      id: "customer",
      header: "Customer Name",
      className: "max-w-37.5 truncate text-xs font-bold",
      cell: (b) => <span title={b.customerName}>{b.customerName}</span>,
    },
    {
      id: "checkIn",
      header: "Check In",
      minWidth: "md",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (b) => b.checkInDate,
    },
    {
      id: "checkOut",
      header: "Check Out",
      minWidth: "md",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (b) => b.checkOutDate,
    },
    {
      id: "status",
      header: "Booking Status",
      cell: (b) => <StatusPill size="sm" domain="booking" value={b.status} />,
    },
    {
      id: "total",
      header: "Total Invoice",
      numeric: true,
      sticky: "right",
      className: "font-bold",
      cell: (b) => `${b.totalAmount?.toLocaleString("vi-VN") ?? 0} ₫`,
    },
  ], []);

  const awaitingControls = useTableControls<Booking>("payments-awaiting", awaitingColumns);

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      <PageHeader
        {...PAGE_META.depositPayment}
        actions={
          <Badge className="bg-emerald-50 text-emerald-700 dark:bg-emerald-950/20 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900 px-3 py-1 font-bold text-xs uppercase flex items-center gap-1.5 shadow-sm">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            Direct Bank Integration (SePay) Active
          </Badge>
        }
      />

      {/* Tabs Menu */}
      <div className="flex border-b border-slate-200 dark:border-zinc-800 w-full mb-4">
        <button
          onClick={() => { setActiveTab("requests"); setPaymentsPage(0); }}
          className={`px-5 py-3 text-xs font-bold border-b-2 transition-all ${activeTab === "requests"
            ? "border-primary text-blue-600 dark:border-blue-400 dark:text-blue-400 font-extrabold"
            : "border-transparent text-slate-400 dark:text-zinc-400 hover:text-slate-600 dark:hover:text-zinc-300"
            }`}
        >
          Payment Transactions List
        </button>
        {canWrite && userRole !== "FO" && (
          <button
            onClick={() => { setActiveTab("bookings"); setBookingsPage(0); }}
            className={`px-5 py-3 text-xs font-bold border-b-2 transition-all ${activeTab === "bookings"
              ? "border-primary text-blue-600 dark:border-blue-400 dark:text-blue-400 font-extrabold"
              : "border-transparent text-slate-400 dark:text-zinc-400 hover:text-slate-600 dark:hover:text-zinc-300"
              }`}
          >
            Generate Requests (Pending Bookings)
          </button>
        )}
      </div>

      {/* Tab content 1: Payment list */}
      {activeTab === "requests" && (
        <div className="space-y-4">
          {/* Filters Card */}
          <Card className="border border-slate-100 dark:border-zinc-800 shadow-sm bg-white dark:bg-zinc-900">
            <CardContent className="py-3 px-4 flex flex-col md:flex-row gap-4 items-center justify-between">
              <div className="relative w-full md:w-80">
                <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 dark:text-zinc-500" />
                <input
                  type="text"
                  placeholder="Search by Booking code, Guest name..."
                  value={paymentsSearch}
                  onChange={e => { setPaymentsSearch(e.target.value); setPaymentsPage(0); }}
                  className="w-full pl-8 pr-3 h-9 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-xs text-slate-800 dark:text-zinc-100 placeholder-slate-400 dark:placeholder-zinc-500 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>
              <div className="flex flex-wrap gap-2 w-full md:w-auto items-center">
                <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-zinc-400 font-medium">
                  <span>Status:</span>
                  <Select
                    value={statusFilter}
                    onChange={e => { setStatusFilter(e.target.value); setPaymentsPage(0); }}
                    className="h-9 px-3 bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 rounded-lg text-xs text-slate-700 dark:text-zinc-200 focus:outline-none focus:border-blue-500"
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="PENDING">PENDING</option>
                    <option value="PAID">PAID</option>
                    <option value="FAILED">FAILED</option>
                    <option value="CANCELLED">CANCELLED</option>
                    <option value="EXPIRED">EXPIRED</option>
                  </Select>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-zinc-400 font-medium">
                  <span>Type:</span>
                  <Select
                    value={typeFilter}
                    onChange={e => { setTypeFilter(e.target.value); setPaymentsPage(0); }}
                    className="h-9 px-3 bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 rounded-lg text-xs text-slate-700 dark:text-zinc-200 focus:outline-none focus:border-blue-500"
                  >
                    <option value="ALL">All Types</option>
                    <option value="DEPOSIT">DEPOSIT</option>
                    <option value="FULL_PAYMENT">FULL PAYMENT</option>
                  </Select>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={loadPayments}
                  className="flex items-center justify-center h-9 w-9 p-0 rounded-lg shrink-0 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-700 shadow-sm text-slate-650 dark:text-zinc-300"
                  title="Refresh payments"
                >
                  <RefreshCw className="size-3.5" />
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* Table list */}
          <div className="bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm overflow-hidden">
            <DataTable
              label="Payment transactions"
              rows={payments}
              columns={paymentControls.visibleColumns}
              rowId={(p) => p.paymentId}
              isLoading={loadingPayments}
              density={paymentControls.density}
              sortBy={paymentControls.sortBy}
              sortDir={paymentControls.sortDir}
              onSortChange={paymentControls.onSortChange}
              onRowClick={(p) => handleViewDetails(p.paymentId)}
              selectedIds={paymentControls.selectedIds}
              onSelectionChange={paymentControls.setSelectedIds}
              bulkActions={
                <ExportMenu
                  filename={`payments-selected-${new Date().toISOString().slice(0, 10)}`}
                  headers={PAYMENT_EXPORT_HEADERS}
                  rows={payments.filter((p) => paymentControls.selectedIds.has(p.paymentId)).map(paymentExportRow)}
                />
              }
              emptyTitle="No payment records"
              emptyMessage="Deposit and settlement requests appear here once raised against a booking."
              footer={
                <TablePagination
                  page={paymentsPage}
                  pageSize={PAYMENTS_PAGE_SIZE}
                  totalElements={paymentsTotalElements}
                  totalPages={paymentsTotalPages}
                  onPageChange={setPaymentsPage}
                />
              }
            />
          </div>
        </div>
      )}

      {/* Tab content 2: Confirmed Bookings waiting for payment request */}
      {activeTab === "bookings" && canWrite && userRole !== "FO" && (
        <div className="space-y-4">
          {/* Filters Card */}
          <Card className="border border-slate-100 dark:border-zinc-800 shadow-sm bg-white dark:bg-zinc-900">
            <CardContent className="py-3 px-4 flex flex-row items-center justify-between gap-4 flex-wrap lg:flex-nowrap w-full">
              <div className="relative w-full lg:w-72 shrink-0">
                <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 dark:text-zinc-500" />
                <input
                  type="text"
                  placeholder="Search bookings by code, guest name..."
                  value={bookingsSearch}
                  onChange={e => { setBookingsSearch(e.target.value); setBookingsPage(0); }}
                  className="w-full pl-8 pr-3 h-9 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-xs text-slate-800 dark:text-zinc-100 placeholder-slate-400 dark:placeholder-zinc-500 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 focus:bg-white dark:focus:bg-zinc-900 transition"
                />
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={loadBookings}
                className="flex items-center justify-center h-9 w-9 p-0 rounded-lg shrink-0 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-700 shadow-sm text-slate-650 dark:text-zinc-300"
                title="Refresh bookings"
              >
                <RefreshCw className="size-3.5" />
              </Button>
            </CardContent>
          </Card>

          {/* Bookings Table */}
          <DataTable
            label="Confirmed bookings awaiting a payment request"
            rows={bookings}
            columns={awaitingControls.visibleColumns}
            rowId={(b) => b.bookingId}
            isLoading={loadingBookings}
            density={awaitingControls.density}
            sortBy={awaitingControls.sortBy}
            sortDir={awaitingControls.sortDir}
            onSortChange={awaitingControls.onSortChange}
            onRowClick={(b) => setSelectedBookingForDetails(b)}
            emptyTitle="Nothing awaiting a payment link"
            emptyMessage="Confirmed bookings without a payment request appear here."
            footer={
              <TablePagination
                page={bookingsPage}
                pageSize={PAYMENTS_PAGE_SIZE}
                totalElements={bookingsTotalElements}
                totalPages={bookingsTotalPages}
                onPageChange={setBookingsPage}
              />
            }
          />
        </div>
      )}

      {/* Modal: Generate Payment Request Form (UC-21.1) */}
      {showGenerateModal && selectedBookingForRequest && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm transition-opacity">
          <div className="relative w-full max-w-md bg-white dark:bg-zinc-900 rounded-xl shadow-2xl border border-slate-100 dark:border-zinc-800 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800/30 px-6 py-4 border-b border-slate-200 dark:border-zinc-800">
              <div>
                <h3 className="font-extrabold text-sm text-slate-800 dark:text-zinc-100">Generate Payment Request</h3>
                <p className="text-[10px] text-slate-400 dark:text-zinc-500">Initialize direct webhook invoice link for booking confirmation.</p>
              </div>
              <button
                onClick={() => { setShowGenerateModal(false); reset(); }}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 rounded p-1 hover:bg-slate-100 dark:hover:bg-zinc-800 transition"
              >
                <X className="size-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit(onGenerateSubmit)} className="p-6 space-y-4 text-xs">
              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Booking Code</label>
                <div className="px-3 py-2 bg-slate-50 dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 rounded-lg font-bold text-slate-800 dark:text-zinc-200">
                  {selectedBookingForRequest.bookingCode} ({selectedBookingForRequest.customerName})
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Payment Type</label>
                <Select
                  {...register("paymentType")}
                  className="w-full px-3 py-2 border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition"
                >
                  <option value="DEPOSIT">Deposit Hold</option>
                  <option value="FULL_PAYMENT">Full Invoice Settlement</option>
                </Select>
                {errors.paymentType && (
                  <p className="text-red-500 text-[10px] font-semibold mt-1">{errors.paymentType.message}</p>
                )}
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Payment Method</label>
                <Select
                  {...register("paymentMethod")}
                  className="w-full px-3 py-2 border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition"
                >
                  <option value="TRANSFER">Bank Transfer (VietQR dynamic)</option>
                  <option value="CASH">Cash direct</option>
                  <option value="CARD">Credit/Debit Card Online</option>
                </Select>
                {errors.paymentMethod && (
                  <p className="text-red-500 text-[10px] font-semibold mt-1">{errors.paymentMethod.message}</p>
                )}
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">
                  Amount Request (VND)
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-zinc-500 font-bold">
                    ₫
                  </span>
                  <input
                    type="number"
                    placeholder="Enter amount in VND..."
                    {...register("amount", { valueAsNumber: true })}
                    className="w-full pl-7 pr-3 py-2 border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition font-bold"
                  />
                </div>
                {watchPaymentMethod === "CASH" ? (
                  <p className="text-[10px] text-slate-450 dark:text-zinc-400 mt-1 italic font-medium">
                    Note: Manual cash collection logged via system.
                  </p>
                ) : (
                  <p className="text-[10px] text-slate-455 dark:text-zinc-400 mt-1 italic font-medium">
                    Note: Direct transfer via VietQR dynamic gateway.
                  </p>
                )}
                {errors.amount && (
                  <p className="text-red-500 text-[10px] font-semibold mt-1">{errors.amount.message}</p>
                )}
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Expiration / Due Date</label>
                <div className="relative">
                  <Calendar className="absolute right-3 top-1/2 -translate-y-1/2 size-3.5 text-slate-400 dark:text-zinc-500" />
                  <input
                    type="date"
                    {...register("dueDate")}
                    className="w-full px-3 py-2 border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition font-semibold"
                  />
                </div>
                {errors.dueDate && (
                  <p className="text-red-500 text-[10px] font-semibold mt-1">{errors.dueDate.message}</p>
                )}
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Add description note</label>
                <textarea
                  placeholder="Memo references for customer billing..."
                  rows={2}
                  {...register("notes")}
                  className="w-full px-3 py-2 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition"
                />
              </div>

              <div className="flex justify-end gap-2 border-t border-slate-100 dark:border-zinc-800 pt-4 mt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={submittingRequest}
                  onClick={() => { setShowGenerateModal(false); reset(); }}
                  className="text-xs font-semibold px-4 py-2 border-slate-200 dark:border-zinc-700 text-slate-700 dark:text-zinc-200 bg-white dark:bg-zinc-800"
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="success"
                  size="sm"
                  disabled={submittingRequest}
                  className="text-xs font-bold px-5 py-2 bg-primary hover:bg-primary/90 text-white flex items-center gap-1.5"
                >
                  {submittingRequest ? <RefreshCw className="size-3.5 animate-spin" /> : null}
                  Generate Payment Request
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      <PaymentDetailDrawer
        payment={showDetailModal ? selectedPayment : null}
        onOpenChange={(open) => !open && setShowDetailModal(false)}
        onDownloadQr={handleDownloadQR}
        onPrintReceipt={handleOpenPrintModal}
        onCopyLink={copyToClipboard}
        actions={
          selectedPayment
            ? [
                {
                  label: "Print receipt",
                  icon: Printer,
                  variant: "outline" as const,
                  onClick: () => handleOpenPrintModal(selectedPayment),
                },
                // A settled or cancelled request accepts neither transition —
                // the server refuses both, so they are absent (§12.13).
                ...(selectedPayment.status === "PENDING" && !showConfirmPaidForm
                  ? [
                      ...(userRole !== "SALES"
                        ? [
                            {
                              label: "Confirm paid",
                              icon: CheckCircle2,
                              variant: "success" as const,
                              disabled: actionLoading,
                              // BR-29: PAID needs a verification note, so this opens
                              // the note form rather than firing the mutation.
                              onClick: () => setShowConfirmPaidForm(true),
                            },
                          ]
                        : []),
                      ...(userRole !== "FO"
                        ? [
                            {
                              label: "Cancel request",
                              icon: X,
                              variant: "danger" as const,
                              disabled: actionLoading,
                              onClick: () => handleCancelRequest(selectedPayment.paymentId),
                            },
                          ]
                        : []),
                    ]
                  : []),
              ]
            : []
        }
      >
        {showConfirmPaidForm && (
          <div className="space-y-3 rounded-lg border border-warning/30 bg-warning/10 p-3">
            <p className="flex items-center gap-1.5 text-[12px] font-semibold text-warning">
              <AlertTriangle className="size-3.5 shrink-0" />
              Manual payment confirmation
            </p>
            <div>
              <label
                htmlFor="verification-note"
                className="mb-1 block text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground"
              >
                Verification note / reference ID
              </label>
              <textarea
                id="verification-note"
                rows={2}
                value={verificationNote}
                onChange={(e) => {
                  setVerificationNote(e.target.value);
                  setVerificationNoteError("");
                }}
                placeholder="Cashier note, authorisation ID or transfer reference…"
                aria-invalid={!!verificationNoteError}
                aria-describedby={verificationNoteError ? "verification-note-error" : undefined}
                className="w-full rounded-md border border-border bg-input px-3 py-2 text-[12.5px] text-foreground focus-ring"
              />
              {verificationNoteError && (
                <p id="verification-note-error" className="mt-1 text-[11.5px] font-medium text-danger">
                  {verificationNoteError}
                </p>
              )}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setShowConfirmPaidForm(false)}>
                Cancel
              </Button>
              <Button
                variant="success"
                size="sm"
                onClick={handleConfirmPaidSubmit}
                isLoading={actionLoading}
              >
                Verify &amp; mark paid
              </Button>
            </div>
          </div>
        )}
      </PaymentDetailDrawer>

      {/* Modal: View Booking Detail (Pending Payment) */}
      {selectedBookingForDetails && (
        <div className="fixed inset-0 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-xl w-full max-w-2xl shadow-xl border border-slate-100 dark:border-zinc-800 flex flex-col max-h-[90vh] overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-t-xl">
              <div>
                <h3 className="font-bold text-slate-800 dark:text-zinc-100 text-base flex items-center gap-1.5">
                  <FileText className="size-4.5 text-blue-600 dark:text-blue-400" />
                  Booking Details
                </h3>
                <p className="text-xs text-slate-400 dark:text-zinc-500 mt-0.5">
                  Booking Code: <span className="font-bold text-slate-700 dark:text-zinc-300">{selectedBookingForDetails.bookingCode}</span>
                </p>
              </div>
              <button
                onClick={() => setSelectedBookingForDetails(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 transition p-1.5 hover:bg-slate-100 dark:hover:bg-zinc-800 rounded-lg"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Modal Booking Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Guest Name</span>
                  <span className="text-slate-800 dark:text-zinc-200 font-bold text-sm">{selectedBookingForDetails.customerName}</span>
                </div>
                <div>
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Booking Status</span>
                  <Badge variant="success" className="bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-400 dark:border-emerald-900 mt-0.5 py-1 font-bold">CONFIRMED</Badge>
                </div>
                <div className="col-span-2 border-t border-slate-100 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1">Stay Period</span>
                  <span className="font-semibold text-slate-800 dark:text-zinc-200 flex items-center gap-1">
                    <Calendar className="size-4 text-slate-400" />
                    {selectedBookingForDetails.checkInDate} to {selectedBookingForDetails.checkOutDate}
                  </span>
                </div>
                <div className="col-span-2 border-t border-slate-100 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-1">Rooms & Inventory Allocations</span>
                  {selectedBookingForDetails.details && selectedBookingForDetails.details.length > 0 ? (
                    <div className="space-y-1.5 mt-1.5">
                      {selectedBookingForDetails.details.map((d) => (
                        <div key={d.bookingDetailId} className="flex justify-between items-center bg-slate-50 dark:bg-zinc-850 p-2.5 rounded-lg border border-slate-100 dark:border-zinc-800/60">
                          <div>
                            <span className="font-bold text-slate-800 dark:text-zinc-200 block text-xs">{d.productName}</span>
                            <span className="text-[10px] text-slate-400 dark:text-zinc-500">Qty: {d.quantity} | Nights: {d.nights}</span>
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
                    <span className="text-slate-500 dark:text-zinc-400 italic">No room allocations details.</span>
                  )}
                </div>
                <div className="col-span-2 border-t border-slate-100 dark:border-zinc-800 pt-3">
                  <span className="text-slate-400 dark:text-zinc-500 block mb-0.5">Total Amount</span>
                  <span className="font-extrabold text-slate-800 dark:text-zinc-200 text-base">{selectedBookingForDetails.totalAmount?.toLocaleString('vi-VN')} ₫</span>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-end px-6 py-4 border-t border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/30 rounded-b-xl gap-3">
              <Button
                variant="ghost"
                onClick={() => setSelectedBookingForDetails(null)}
                className="text-slate-700 dark:text-zinc-300"
              >
                Close
              </Button>
              <Button
                variant="primary"
                leftIcon={<Plus className="size-3.5" />}
                onClick={() => {
                  handleOpenGenerateModal(selectedBookingForDetails);
                  setSelectedBookingForDetails(null);
                }}
                className="bg-primary hover:bg-primary/90 text-white font-bold"
              >
                Generate Payment Request
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Printable Receipt Modal Overlay */}
      {showPrintModal && printingPayment && (
        <PrintableReceiptModal
          payment={printingPayment}
          booking={receiptBooking}
          loading={loadingReceiptBooking}
          onClose={() => {
            setShowPrintModal(false);
            setPrintingPayment(null);
          }}
        />
      )}
      {confirmElement}
    </div>
  );
}

interface PrintableReceiptModalProps {
  payment: Payment;
  booking: Booking | null;
  loading: boolean;
  onClose: () => void;
}

function PrintableReceiptModal({
  payment,
  booking,
  loading,
  onClose
}: PrintableReceiptModalProps) {
  const handlePrint = () => {
    window.print();
  };

  const receiptId = `PAY-${payment.paymentId.substring(0, 8).toUpperCase()}`;

  return (
    <div className="fixed inset-0 z-[100] bg-black/60 backdrop-blur-sm flex justify-center items-start overflow-y-auto p-4 sm:p-10 print-modal-overlay">
      <style>{`
        @media print {
          @page {
            size: portrait;
            margin: 6mm 10mm 6mm 10mm;
          }
          body * {
            visibility: hidden;
            height: 0;
            overflow: hidden;
          }
          .print-modal-overlay, .print-card, .print-card * {
            visibility: visible;
            height: auto !important;
            overflow: visible !important;
          }
          .print-modal-overlay {
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 100% !important;
            height: 100% !important;
            background: transparent !important;
            box-shadow: none !important;
            border: none !important;
            padding: 0 !important;
            margin: 0 !important;
          }
          .print-card {
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 100% !important;
            max-width: 100% !important;
            border: none !important;
            box-shadow: none !important;
            background: white !important;
            color: black !important;
            padding: 0 !important;
            margin: 0 !important;
            font-size: 10.5px !important;
            line-height: 1.35 !important;
          }
          .print-card h2 {
            font-size: 13px !important;
            margin-top: 8px !important;
            margin-bottom: 4px !important;
          }
          .print-card h4 {
            font-size: 9px !important;
            margin-top: 10px !important;
            margin-bottom: 3px !important;
          }
          .print-card .border-t {
            margin-top: 10px !important;
            padding-top: 10px !important;
          }
          .print-card .grid {
            gap: 6px !important;
          }
          .print-card table th, .print-card table td {
            padding-top: 3px !important;
            padding-bottom: 3px !important;
          }
          .print-card img.logo {
            width: 32px !important;
            height: 32px !important;
          }
          .print-card img.qr-code {
            width: 135px !important;
            height: 135px !important;
          }
          .no-print {
            display: none !important;
            height: 0 !important;
            width: 0 !important;
            overflow: hidden !important;
          }
        }
      `}</style>

      <div className="print-card bg-white border border-slate-200 rounded-2xl shadow-2xl p-6 sm:p-8 max-w-2xl w-full mx-auto my-auto relative text-slate-800 flex flex-col gap-5">

        {/* Print Actions */}
        <div className="flex justify-between items-center no-print border-b border-slate-100 pb-4">
          <h3 className="text-sm font-bold text-slate-700">Payment Form / Receipt Preview</h3>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={onClose}
              className="text-slate-700 border-slate-200 hover:bg-slate-50"
            >
              Close Preview
            </Button>
            <Button
              variant="primary"
              onClick={handlePrint}
              disabled={loading}
              className="font-bold shadow-sm disabled:opacity-50"
            >
              <span className="flex items-center justify-center gap-1.5">
                <Printer className="size-3.5 shrink-0" />
                <span>Print / Save PDF</span>
              </span>
            </Button>
          </div>
        </div>

        {/* Brand Header with Logo */}
        <div className="flex items-center justify-between border-b border-slate-200 pb-3">
          <div className="flex items-center gap-3">
            <img
              src="/logo1.jpg"
              alt="Leadora Logo"
              className="logo h-10 w-10 object-cover rounded-lg border border-slate-100 print:border-slate-200"
            />
            <div className="flex flex-col text-left">
              <span className="text-lg font-extrabold tracking-widest text-slate-900 leading-none">LEADORA</span>
              <span className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mt-1">Sales & Workflow Management System</span>
              <span className="text-[7.5px] text-slate-400 mt-0.5 print:text-black font-semibold">Contact: minhplnce180439@fpt.edu.vn | Hotline: +84 (0) 96 495 9652</span>
            </div>
          </div>
          <div className="text-right">
            <h2 className="text-xs font-black text-slate-800 uppercase tracking-wide">
              {payment.status === "PAID" ? "PAYMENT RECEIPT" : "PAYMENT REQUEST"}
            </h2>
            <span className="text-[9px] font-semibold text-slate-455 uppercase tracking-wider">System-Generated Invoice</span>
          </div>
        </div>

        {/* Info Metadata Grid */}
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <div>
              <span className="text-slate-455 font-semibold block text-[10px]">Receipt ID:</span>
              <span className="font-bold text-slate-800 select-all">{receiptId}</span>
            </div>
          </div>
          <div className="space-y-1 text-right">
            <div>
              <span className="text-slate-455 font-semibold block text-[10px]">Date Created:</span>
              <span className="font-medium text-slate-700">{new Date(payment.createdAt).toLocaleString("en-US")}</span>
            </div>
            {payment.status === "PAID" && payment.paidAt && (
              <div>
                <span className="text-slate-455 font-semibold block text-[10px]">Date Settled:</span>
                <span className="font-bold text-emerald-600">{new Date(payment.paidAt).toLocaleString("en-US")}</span>
              </div>
            )}
          </div>
        </div>

        {/* Billing Section */}
        <div className="border-t border-slate-100 pt-3 space-y-2">
          <h4 className="text-[9px] font-black text-slate-400 uppercase tracking-wider">Billing & Booking Details</h4>
          <div className="bg-slate-50 border border-slate-150 rounded-xl p-3 grid grid-cols-2 gap-y-2 gap-x-4 text-xs text-slate-700">
            <div>
              <span className="text-slate-400 block text-[10px] font-semibold">Guest Name:</span>
              <span className="font-bold text-slate-900">{payment.customerName || booking?.customerName || "Walk-in Guest"}</span>
            </div>
            <div>
              <span className="text-slate-400 block text-[10px] font-semibold">Booking Ref Code:</span>
              <span className="font-bold text-slate-900 select-all">{payment.bookingCode}</span>
            </div>
            {booking && (
              <>
                <div>
                  <span className="text-slate-400 block text-[10px] font-semibold">Check-In Date:</span>
                  <span className="font-medium">{booking.checkInDate}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px] font-semibold">Check-Out Date:</span>
                  <span className="font-medium">{booking.checkOutDate}</span>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Room Allocation Pricing Table */}
        <div className="border-t border-slate-100 pt-3 space-y-1.5">
          <h4 className="text-[9px] font-black text-slate-400 uppercase tracking-wider">Billing Item Breakdown</h4>
          <table className="w-full text-xs text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-200 text-slate-400 font-bold text-[10px]">
                <th className="py-1.5">Description / Room Info</th>
                <th className="py-1.5 text-center w-16">Nights</th>
                <th className="py-1.5 text-right w-24">Price (VND)</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={3} className="py-3 text-center text-slate-400 italic">Loading details...</td>
                </tr>
              ) : booking?.details && booking.details.length > 0 ? (
                booking.details.map((d) => (
                  <tr key={d.bookingDetailId} className="border-b border-slate-100 text-slate-700">
                    <td className="py-1.5 font-semibold">
                      {d.productName} {d.roomNumber ? `(Room ${d.roomNumber})` : ""}
                    </td>
                    <td className="py-1.5 text-center">{d.nights}</td>
                    <td className="py-1.5 text-right font-medium">{d.unitPrice?.toLocaleString()} ₫</td>
                  </tr>
                ))
              ) : (
                <tr className="border-b border-slate-100 text-slate-700">
                  <td className="py-1.5 font-semibold">
                    {payment.paymentType === "DEPOSIT" ? "Security Room Deposit" : "Room Rental Fee"} for Ref #{payment.bookingCode}
                  </td>
                  <td className="py-1.5 text-center">—</td>
                  <td className="py-1.5 text-right font-medium">{payment.amount?.toLocaleString()} ₫</td>
                </tr>
              )}

              {/* Final calculations */}
              <tr className="text-slate-900 border-t border-slate-200">
                <td colSpan={2} className="py-2 text-right font-black text-xs uppercase">Total Due/Settled:</td>
                <td className="py-2 text-right font-black text-sm text-blue-650">
                  {payment.amount?.toLocaleString("vi-VN")} ₫
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* Payment Section / QR / PAID Stamp */}
        <div className="border-t border-slate-100 pt-3 w-full">
          {payment.status === "PENDING" && payment.paymentMethod === "TRANSFER" && payment.qrCodeUrl ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full items-stretch">

              {/* Left Column: QR Code Card */}
              <div className="flex flex-col items-center justify-center gap-1 border border-slate-200 rounded-xl p-3 bg-slate-50 w-full print:bg-slate-50">
                <span className="text-[9px] font-black text-slate-400 uppercase tracking-wider">Dynamic Payment VietQR</span>
                <img
                  src={payment.qrCodeUrl}
                  alt="Napas VietQR"
                  className="qr-code size-40 object-contain rounded-lg bg-white p-1 border border-slate-150"
                />
                <span className="text-[8px] text-slate-450 text-center font-medium">Scan QR code using any Mobile Banking App</span>
              </div>

              {/* Right Column: Bank Details Table */}
              <div className="text-[9px] text-slate-500 bg-slate-50 rounded-xl p-3 flex flex-col justify-center border border-slate-150 w-full">
                <p className="font-bold text-slate-600 text-center uppercase tracking-wider text-[8px] border-b border-slate-200 pb-1 mb-2">Bank Transfer Info</p>
                <div className="grid grid-cols-3 gap-y-1.5 text-left text-slate-700 print:text-black">
                  <span className="font-semibold text-slate-450">Account Holder:</span>
                  <span className="col-span-2 font-bold text-slate-700 print:text-black">TRINH MINH NGOC</span>
                  <span className="font-semibold text-slate-455">Account Number:</span>
                  <span className="col-span-2 font-bold text-slate-800 print:text-black select-all">22224102004</span>
                  <span className="font-semibold text-slate-455">Receiving Bank:</span>
                  <span className="col-span-2 font-medium text-slate-750 print:text-black leading-tight">MB Bank</span>
                  <span className="font-semibold text-slate-450">Transfer Content:</span>
                  <span className="col-span-2 font-bold text-blue-600 print:text-black select-all break-all leading-tight">
                    LEADORAPAY{payment.paymentId}
                  </span>
                </div>
              </div>

            </div>
          ) : payment.status === "PAID" ? (
            <div className="flex flex-col items-center justify-center mx-auto border-2 border-dashed border-emerald-450 bg-emerald-50 rounded-xl p-4 w-full max-w-70 text-center gap-1 relative overflow-hidden">
              <span className="text-[9px] font-black text-emerald-700 uppercase tracking-widest">Transaction Cleared</span>
              <span className="text-lg font-black text-emerald-800 uppercase tracking-wider">★ PAID ★</span>
              <span className="text-[8px] text-emerald-600 font-medium">Receipt authorized by Auto-Settlement Gateway</span>

              {/* Stamp visual accent */}
              <div className="absolute -right-4 -bottom-4 w-12 h-12 rounded-full border-4 border-emerald-200/40 rotate-12 flex items-center justify-center text-[10px] font-black text-emerald-250/20">
                OK
              </div>
            </div>
          ) : null}
        </div>

        {/* Footer Note */}
        <div className="border-t border-slate-100 pt-3 text-center text-[8px] text-slate-400 font-medium italic">
          Thank you for choosing our services! For immediate support, please contact the hotel front desk. Powered by Leadora – Follow-up & Sales Workflow Management System.
        </div>
      </div>
    </div>
  );
}
