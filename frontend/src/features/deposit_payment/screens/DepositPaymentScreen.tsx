"use client";

import React, { useState, useEffect, useMemo, useCallback } from "react";
import { useForm } from "react-hook-form";
import * as z from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  CreditCard,
  CheckCircle2,
  Search,
  Landmark,
  Plus,
  X,
  RefreshCw,
  AlertTriangle,
  Copy,
  ExternalLink,
  DollarSign,
  Calendar,
  FileText,
  Download
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { toast } from "@/stores/toast_store";
import {
  depositPaymentService,
  type Payment,
  type PaymentStatus,
  type PaymentType
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
  const [activeTab, setActiveTab] = useState<"requests" | "bookings">("requests");

  // Tab 1: Payment Requests states
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loadingPayments, setLoadingPayments] = useState(false);
  const [paymentsSearch, setPaymentsSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [paymentsPage, setPaymentsPage] = useState(0);
  const [paymentsTotalPages, setPaymentsTotalPages] = useState(0);

  // Tab 2: Confirmed Bookings states
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loadingBookings, setLoadingBookings] = useState(false);
  const [bookingsSearch, setBookingsSearch] = useState("");
  const [bookingsPage, setBookingsPage] = useState(0);
  const [bookingsTotalPages, setBookingsTotalPages] = useState(0);

  // Modals and detail states
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showGenerateModal, setShowGenerateModal] = useState(false);
  const [selectedBookingForRequest, setSelectedBookingForRequest] = useState<Booking | null>(null);
  const [selectedBookingForDetails, setSelectedBookingForDetails] = useState<Booking | null>(null);

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

  const [exchangeRate, setExchangeRate] = useState<number>(25400); // Live conversion rate

  // Fetch real-time exchange rate on mount
  useEffect(() => {
    const fetchRate = async () => {
      try {
        const response = await fetch("https://open.er-api.com/v6/latest/USD");
        const data = await response.json();
        if (data && data.result === "success" && data.rates && data.rates.VND) {
          const rate = data.rates.VND;
          setExchangeRate(rate);
          console.log("Fetched real-time USD to VND exchange rate: ", rate);
        }
      } catch (err) {
        console.warn("Failed to fetch real-time exchange rate. Using fallback 25400", err);
      }
    };
    fetchRate();
  }, []);

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

  // Cancel Payment Request (UC-21.5)
  const handleCancelRequest = async (paymentId: string) => {
    if (!confirm("Are you sure you want to cancel this payment request? This will invalidate the QR code and checkout link.")) return;

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
        return <Badge variant="success" className="bg-emerald-50 text-emerald-700 dark:bg-emerald-950/20 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900 uppercase text-[9px] font-bold py-1 min-w-[90px] justify-center text-center">PAID</Badge>;
      case "PENDING":
        return <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-950/20 dark:text-amber-400 border border-amber-200 dark:border-amber-900 uppercase text-[9px] font-bold py-1 min-w-[90px] justify-center text-center">PENDING</Badge>;
      case "CANCELLED":
        return <Badge variant="default" className="bg-slate-100 text-slate-800 dark:bg-zinc-800 dark:text-zinc-400 border border-slate-200 dark:border-zinc-700 uppercase text-[9px] font-bold py-1 min-w-[90px] justify-center text-center">CANCELLED</Badge>;
      case "FAILED":
        return <Badge variant="danger" className="bg-rose-50 text-rose-700 dark:bg-rose-950/20 dark:text-rose-400 border border-rose-200 dark:border-rose-900 uppercase text-[9px] font-bold py-1 min-w-[90px] justify-center text-center">FAILED</Badge>;
      case "EXPIRED":
        return <Badge variant="default" className="bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-700 uppercase text-[9px] font-bold py-1 min-w-[90px] justify-center text-center">EXPIRED</Badge>;
      default:
        return <Badge>{status}</Badge>;
    }
  };

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800 dark:text-zinc-100">Deposit & Payment Management</h1>
          <p className="text-xs text-slate-400 dark:text-zinc-400 mt-0.5">Generate payment requests, monitor transaction registers, and verify invoice clearances.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge className="bg-emerald-50 text-emerald-700 dark:bg-emerald-950/20 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900 px-3 py-1 font-bold text-xs uppercase flex items-center gap-1.5 shadow-sm">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            PayOS Sandbox Gateway Active
          </Badge>
        </div>
      </div>

      {/* Tabs Menu */}
      <div className="flex border-b border-slate-200 dark:border-zinc-800 w-full mb-4">
        <button
          onClick={() => { setActiveTab("requests"); setPaymentsPage(0); }}
          className={`px-5 py-3 text-xs font-bold border-b-2 transition-all ${activeTab === "requests"
            ? "border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400 font-extrabold"
            : "border-transparent text-slate-400 dark:text-zinc-400 hover:text-slate-600 dark:hover:text-zinc-300"
            }`}
        >
          Payment Transactions List
        </button>
        <button
          onClick={() => { setActiveTab("bookings"); setBookingsPage(0); }}
          className={`px-5 py-3 text-xs font-bold border-b-2 transition-all ${activeTab === "bookings"
            ? "border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400 font-extrabold"
            : "border-transparent text-slate-400 dark:text-zinc-400 hover:text-slate-600 dark:hover:text-zinc-300"
            }`}
        >
          Generate Requests (Pending Bookings)
        </button>
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
            {loadingPayments ? (
              <div className="py-12 text-center text-slate-400 dark:text-zinc-500 text-xs flex flex-col items-center justify-center gap-2">
                <RefreshCw className="size-4 animate-spin text-blue-500" />
                <span>Loading transaction logs from gateway...</span>
              </div>
            ) : (
              <>
                <Table className="w-full table-fixed min-w-[1100px]">
                  <TableHeader className="bg-slate-50 dark:bg-zinc-800/40 border-b border-slate-100 dark:border-zinc-850">
                    <TableRow hoverable={false}>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-left whitespace-nowrap">Booking Reference</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[25%] !text-left whitespace-nowrap">Guest Name</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[20%] !text-left whitespace-nowrap">Payment Type</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[13%] !text-center whitespace-nowrap">Due Date</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[12%] !text-center whitespace-nowrap">Gateway Status</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-right whitespace-nowrap">Amount Paid</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {payments.length > 0 ? (
                      payments.map(p => (
                        <TableRow
                          key={p.paymentId}
                          onClick={() => handleViewDetails(p.paymentId)}
                          className="hover:bg-slate-50/70 dark:hover:bg-zinc-800/30 border-b border-slate-100 dark:border-zinc-800 transition cursor-pointer select-none"
                        >
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-left whitespace-nowrap">
                            <span className="flex items-center gap-1.5 text-primary">
                              <CreditCard className="size-3.5 text-slate-400 dark:text-zinc-550 shrink-0" />
                              {p.bookingCode || "N/A"}
                            </span>
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-800 dark:!text-zinc-200 !text-left whitespace-nowrap truncate max-w-[150px]" title={p.customerName || "N/A"}>
                            {p.customerName || "N/A"}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-600 dark:!text-zinc-400 !text-left whitespace-nowrap truncate">
                            {p.paymentType === "DEPOSIT" ? "Deposit Hold" : "Full Bill Settlement"}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-500 dark:!text-zinc-400 !text-center whitespace-nowrap">
                            {p.dueDate || "N/A"}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-center whitespace-nowrap">
                            <div className="flex justify-center">
                              {getStatusBadge(p.status)}
                            </div>
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-right whitespace-nowrap">
                            <div>${p.amount?.toLocaleString('en-US', { minimumFractionDigits: 2 })}</div>
                            {p.paymentMethod === "TRANSFER" && (
                              <div className="text-[10px] text-slate-400 dark:text-zinc-500 font-semibold mt-0.5">
                                {Math.round(p.amount * exchangeRate).toLocaleString('vi-VN')} ₫
                              </div>
                            )}
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow hoverable={false}>
                        <TableCell colSpan={6} className="py-12 text-center text-slate-450 dark:text-zinc-500 text-xs">
                          No payment transaction records match filters.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>

                {/* Pagination */}
                {paymentsTotalPages > 1 && (
                  <div className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800/40 px-4 py-3 border-t border-slate-100 dark:border-zinc-800">
                    <span className="text-xs text-slate-550 dark:text-zinc-400 font-medium">Page {paymentsPage + 1} of {paymentsTotalPages}</span>
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        disabled={paymentsPage === 0}
                        onClick={() => setPaymentsPage(prev => Math.max(0, prev - 1))}
                        className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-650 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                      >
                        Previous
                      </Button>
                      <Button
                        size="sm"
                        disabled={paymentsPage + 1 >= paymentsTotalPages}
                        onClick={() => setPaymentsPage(prev => prev + 1)}
                        className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-650 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* Tab content 2: Confirmed Bookings waiting for payment request */}
      {activeTab === "bookings" && (
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
          <div className="bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-sm overflow-hidden">
            {loadingBookings ? (
              <div className="py-12 text-center text-slate-400 dark:text-zinc-500 text-xs flex flex-col items-center justify-center gap-2">
                <RefreshCw className="size-4 animate-spin text-blue-500" />
                <span>Loading confirmed bookings registry...</span>
              </div>
            ) : (
              <>
                <Table className="w-full table-fixed min-w-[1100px]">
                  <TableHeader className="bg-slate-50 dark:bg-zinc-800/40 border-b border-slate-100 dark:border-zinc-850">
                    <TableRow hoverable={false}>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-left whitespace-nowrap">Booking Reference</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[30%] !text-left whitespace-nowrap">Customer Name</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-center whitespace-nowrap">Check In</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-center whitespace-nowrap">Check Out</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[10%] !text-center whitespace-nowrap">Booking Status</TableHead>
                      <TableHead className="!px-4 !py-3 !font-semibold !text-xs !text-slate-500 dark:!text-zinc-400 w-[15%] !text-right whitespace-nowrap">Total Invoice</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {bookings.length > 0 ? (
                      bookings.map(b => (
                        <TableRow
                          key={b.bookingId}
                          onClick={() => setSelectedBookingForDetails(b)}
                          className="hover:bg-slate-50/70 dark:hover:bg-zinc-800/30 border-b border-slate-100 dark:border-zinc-800 transition cursor-pointer select-none"
                        >
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-left whitespace-nowrap">
                            {b.bookingCode}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-800 dark:!text-zinc-200 !text-left whitespace-nowrap truncate max-w-[150px]" title={b.customerName}>
                            {b.customerName}
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-500 dark:!text-zinc-400 !text-center whitespace-nowrap">{b.checkInDate}</TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !text-slate-500 dark:!text-zinc-400 !text-center whitespace-nowrap">{b.checkOutDate}</TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-center whitespace-nowrap">
                            <div className="flex justify-center">
                              <Badge variant="success" className="bg-emerald-55 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-450 dark:border-emerald-900 font-bold uppercase text-[9px] py-1 min-w-[90px] justify-center text-center">
                                {b.status}
                              </Badge>
                            </div>
                          </TableCell>
                          <TableCell className="!py-3.5 !px-4 !text-xs !font-bold !text-slate-700 dark:!text-zinc-300 !text-right whitespace-nowrap">
                            ${b.totalAmount?.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow hoverable={false}>
                        <TableCell colSpan={6} className="py-12 text-center text-slate-450 dark:text-zinc-550 text-xs">
                          No confirmed bookings waiting for payment link.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>

                {/* Pagination */}
                {bookingsTotalPages > 1 && (
                  <div className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800/40 px-4 py-3 border-t border-slate-100 dark:border-zinc-800">
                    <span className="text-xs text-slate-550 dark:text-zinc-400 font-medium">Page {bookingsPage + 1} of {bookingsTotalPages}</span>
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        disabled={bookingsPage === 0}
                        onClick={() => setBookingsPage(prev => Math.max(0, prev - 1))}
                        className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-650 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                      >
                        Previous
                      </Button>
                      <Button
                        size="sm"
                        disabled={bookingsPage + 1 >= bookingsTotalPages}
                        onClick={() => setBookingsPage(prev => prev + 1)}
                        className="px-3 border border-slate-200 dark:border-zinc-700 text-slate-655 dark:text-zinc-300 bg-white dark:bg-zinc-800"
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
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
                  Amount Request (USD)
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-zinc-500 font-bold">
                    $
                  </span>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="Enter amount in USD..."
                    {...register("amount", { valueAsNumber: true })}
                    className="w-full pl-7 pr-3 py-2 border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-blue-500 dark:focus:border-blue-400 transition font-bold"
                  />
                </div>
                {watchPaymentMethod === "TRANSFER" ? (
                  <p className="text-[10px] text-slate-450 dark:text-zinc-400 mt-1 italic font-medium">
                    Note: For VietQR (PayOS), this will be processed as{" "}
                    <strong className="text-blue-600 dark:text-blue-400 font-bold">
                      {Math.round((watchAmount || 0) * exchangeRate).toLocaleString("vi-VN")} ₫
                    </strong>{" "}
                    (using live rate: {exchangeRate.toLocaleString("vi-VN")} ₫/$).
                  </p>
                ) : (
                  <p className="text-[10px] text-slate-455 dark:text-zinc-400 mt-1 italic font-medium">
                    Note: Standard invoicing currency.
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
                  className="text-xs font-bold px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5"
                >
                  {submittingRequest ? <RefreshCw className="size-3.5 animate-spin" /> : null}
                  Generate Payment Request
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: View Payment Detail / Audit Control (UC-21.3, UC-21.4, UC-21.5) */}
      {showDetailModal && selectedPayment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 dark:bg-black/60 backdrop-blur-sm transition-opacity">
          <div className="relative w-full max-w-lg bg-white dark:bg-zinc-900 rounded-xl shadow-2xl border border-slate-100 dark:border-zinc-800 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center bg-slate-50 dark:bg-zinc-800/30 px-6 py-4 border-b border-slate-200 dark:border-zinc-800">
              <div>
                <h3 className="font-extrabold text-sm text-slate-800 dark:text-zinc-100">Audit Payment Log Record</h3>
                <p className="text-[10px] text-slate-400 dark:text-zinc-500">Verifying secure checksum signatures and payment logs references.</p>
              </div>
              <button
                onClick={() => { setShowDetailModal(false); }}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-zinc-300 rounded p-1 hover:bg-slate-100 dark:hover:bg-zinc-800 transition"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="p-6 space-y-4 max-h-[80vh] overflow-y-auto text-xs">
              {/* Payment basic info */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Payment ID</span>
                  <span className="text-xs font-semibold text-slate-700 dark:text-zinc-300 select-all">{selectedPayment.paymentId}</span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Booking Ref / Code</span>
                  <span className="text-xs font-bold text-blue-600 dark:text-blue-400">{selectedPayment.bookingCode || "N/A"}</span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Guest Account Name</span>
                  <span className="text-xs font-semibold text-slate-700 dark:text-zinc-300">{selectedPayment.customerName || "N/A"}</span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Payment Target type</span>
                  <span className="text-xs font-semibold text-slate-700 dark:text-zinc-300">{selectedPayment.paymentType}</span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Expected Due Date</span>
                  <span className="text-xs font-semibold text-slate-600 dark:text-zinc-400 flex items-center gap-1">
                    <Calendar className="size-3.5 text-slate-400 dark:text-zinc-500" />
                    {selectedPayment.dueDate || "N/A"}
                  </span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Cleared / Paid At</span>
                  <span className="text-xs font-semibold text-slate-600 dark:text-zinc-400">
                    {selectedPayment.paidAt ? new Date(selectedPayment.paidAt).toLocaleString('en-US') : "Pending clearing"}
                  </span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Method channel</span>
                  <span className="text-xs font-bold uppercase text-slate-600 dark:text-zinc-300 flex items-center gap-1">
                    <Landmark className="size-3.5 text-slate-400 dark:text-zinc-500" />
                    {selectedPayment.paymentMethod || "N/A"}
                  </span>
                </div>
                <div>
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-550 uppercase tracking-wider">Gateway status</span>
                  <span className="block mt-0.5">{getStatusBadge(selectedPayment.status)}</span>
                </div>
              </div>

              {/* Amount block */}
              <div className="bg-slate-50 dark:bg-zinc-800/40 border border-slate-100 dark:border-zinc-800 rounded-lg p-3 flex justify-between items-center">
                <div className="flex items-center gap-2">
                  <DollarSign className="size-5 text-slate-400 dark:text-zinc-500 shrink-0" />
                  <span className="text-xs font-bold text-slate-600 dark:text-zinc-400">Total Requested Amount</span>
                </div>
                <div className="text-right">
                  <span className="text-lg font-black text-slate-900 dark:text-zinc-100 block">
                    ${selectedPayment.amount?.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </span>
                  {selectedPayment.paymentMethod === "TRANSFER" && (
                    <span className="text-xs font-semibold text-slate-400 dark:text-zinc-500 block mt-0.5">
                      Equivalent to: {Math.round(selectedPayment.amount * exchangeRate).toLocaleString('vi-VN')} ₫
                    </span>
                  )}
                </div>
              </div>

              {/* PayOS Details */}
              <div className="border-t border-slate-100 dark:border-zinc-800 pt-4 space-y-3">
                <h4 className="text-[10px] font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider">PayOS Gateway Connection Data</h4>
                <div className="space-y-2">
                  <div className="flex justify-between items-center text-xs">
                    <span className="text-slate-500 dark:text-zinc-400 font-medium">Gateway Provider:</span>
                    <span className="font-bold text-slate-700 dark:text-zinc-300">{selectedPayment.gatewayProvider || "PAYOS"}</span>
                  </div>
                  <div className="flex justify-between items-center text-xs">
                    <span className="text-slate-500 dark:text-zinc-400 font-medium">Transaction reference link ID:</span>
                    <span className="font-semibold text-slate-700 dark:text-zinc-300 text-right max-w-[200px] truncate select-all">{selectedPayment.gatewayTransactionId || "N/A"}</span>
                  </div>
                </div>
              </div>

              {/* QR and Payment Link for Pending */}
              {selectedPayment.status === "PENDING" && (
                <div className="border-t border-slate-100 dark:border-zinc-800 pt-4 flex flex-col items-center gap-4">
                  {/* VietQR for TRANSFER only */}
                  {selectedPayment.paymentMethod === "TRANSFER" && selectedPayment.qrCodeUrl && (
                    <div className="flex flex-col items-center gap-2 bg-slate-50 dark:bg-zinc-800/30 border border-slate-200 dark:border-zinc-700 rounded-xl p-5 shadow-sm w-full max-w-[280px]">
                      <span className="text-[10px] font-bold text-slate-450 dark:text-zinc-400 uppercase tracking-wider mb-1">Dynamic VietQR</span>
                      <img
                        src={selectedPayment.qrCodeUrl}
                        alt="Napas VietQR Payment"
                        className="size-52 object-contain rounded-lg border border-white shadow bg-white p-1"
                      />
                      <span className="text-[10px] text-slate-400 dark:text-zinc-550">Scan using any Banking App</span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDownloadQR(selectedPayment.qrCodeUrl!, selectedPayment.paymentId)}
                        className="mt-1 w-full text-[10px] font-bold py-1.5 px-3 flex items-center justify-center gap-1 border-slate-200 dark:border-zinc-700 text-slate-600 dark:text-zinc-300 hover:bg-slate-50 dark:hover:bg-zinc-800"
                      >
                        <Download className="size-3" />
                        Download QR Image
                      </Button>
                    </div>
                  )}

                  {/* Payment link copy for TRANSFER or CARD */}
                  {(selectedPayment.paymentMethod === "TRANSFER" || selectedPayment.paymentMethod === "CARD") && selectedPayment.notes && selectedPayment.notes.startsWith("http") && (
                    <div className="w-full space-y-2">
                      <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider">Payment Link URL</span>
                      <div className="flex gap-2">
                        <input
                          type="text"
                          readOnly
                          value={selectedPayment.notes}
                          className="flex-1 bg-slate-50 dark:bg-zinc-805 border border-slate-200 dark:border-zinc-700 rounded-lg px-3 py-1.5 text-slate-600 dark:text-zinc-300 focus:outline-none select-all truncate"
                        />
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => copyToClipboard(selectedPayment.notes || "")}
                          className="p-2 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-700"
                        >
                          <Copy className="size-3.5 text-slate-500 dark:text-zinc-400" />
                        </Button>
                        <a
                          href={selectedPayment.notes}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="p-2 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-700 rounded-lg flex items-center justify-center"
                        >
                          <ExternalLink className="size-3.5 text-blue-500" />
                        </a>
                      </div>
                    </div>
                  )}

                  {/* CASH instruction alert */}
                  {selectedPayment.paymentMethod === "CASH" && (
                    <div className="w-full bg-amber-50/50 dark:bg-amber-950/10 border border-amber-100 dark:border-amber-900 text-amber-800 dark:text-amber-300 rounded-lg p-3 flex gap-2 font-medium">
                      <AlertTriangle className="size-4 text-amber-600 shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold">Cash Payment Instruction</p>
                        <p className="text-slate-650 dark:text-zinc-400 mt-0.5 text-[11px]">
                          Please collect the cash amount of <strong>${selectedPayment.amount?.toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong> directly from the guest at the front desk.
                          After receiving, click <strong>Manual Confirm PAID</strong> below to settle this request.
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Verification notes (if PAID) */}
              {selectedPayment.status === "PAID" && selectedPayment.notes && (
                <div className="border-t border-slate-100 dark:border-zinc-800 pt-4">
                  <span className="block text-[9px] font-bold text-slate-400 dark:text-zinc-500 uppercase tracking-wider mb-1">Audit Verification References</span>
                  <div className="bg-emerald-50/50 dark:bg-emerald-950/10 border border-emerald-100 dark:border-emerald-900 text-slate-700 dark:text-zinc-300 rounded-lg p-3 flex gap-2 font-semibold">
                    <FileText className="size-4 text-emerald-600 shrink-0 mt-0.5" />
                    <span>{selectedPayment.notes}</span>
                  </div>
                </div>
              )}

              {/* Action: Manual Confirm PAID Form Overlay */}
              {showConfirmPaidForm && (
                <div className="border-t border-amber-200 dark:border-amber-900 bg-amber-50/30 dark:bg-amber-950/10 rounded-xl p-4 space-y-3 border transition-all">
                  <div className="flex gap-1.5 items-center text-xs font-bold text-amber-800 dark:text-amber-400">
                    <AlertTriangle className="size-4 text-amber-600 shrink-0" />
                    <span>Manual Override Confirmation hold</span>
                  </div>
                  <div>
                    <label className="block text-[10px] font-bold text-slate-500 dark:text-zinc-400 uppercase tracking-wider mb-1">Verification Note / Reference ID (CASH/TRANSFER)</label>
                    <textarea
                      placeholder="Enter verification code, cashier note, or authorization ID references..."
                      rows={2}
                      value={verificationNote}
                      onChange={e => { setVerificationNote(e.target.value); setVerificationNoteError(""); }}
                      className="w-full px-3 py-2 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 rounded-lg focus:outline-none focus:border-amber-500 transition text-slate-800 dark:text-zinc-100"
                    />
                    {verificationNoteError && (
                      <p className="text-red-500 text-[10px] font-semibold mt-1">{verificationNoteError}</p>
                    )}
                  </div>
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setShowConfirmPaidForm(false)}
                      className="text-[10px] px-3 py-1 bg-white dark:bg-zinc-800 border border-slate-200 dark:border-zinc-700 text-slate-700 dark:text-zinc-200"
                    >
                      Cancel Override
                    </Button>
                    <Button
                      variant="success"
                      size="sm"
                      onClick={handleConfirmPaidSubmit}
                      className="text-[10px] px-3 py-1 bg-amber-600 hover:bg-amber-700 text-white font-bold"
                    >
                      Verify & Mark PAID
                    </Button>
                  </div>
                </div>
              )}

              {/* Action Buttons Row */}
              <div className="flex flex-wrap justify-between items-center gap-2 border-t border-slate-100 dark:border-zinc-800 pt-4">
                {selectedPayment.status === "PENDING" && !showConfirmPaidForm ? (
                  <div className="flex gap-2 w-full justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setShowConfirmPaidForm(true)}
                      className="text-xs font-bold text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-900 hover:bg-amber-50 dark:hover:bg-amber-950/20"
                    >
                      Manual Confirm PAID
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => handleCancelRequest(selectedPayment.paymentId)}
                      className="text-xs font-bold text-white bg-red-650 hover:bg-red-750"
                    >
                      Cancel Request
                    </Button>
                  </div>
                ) : (
                  <div className="flex justify-end w-full">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setShowDetailModal(false)}
                      className="text-xs font-semibold px-4 py-2 border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-850 text-slate-700 dark:text-zinc-200 hover:bg-slate-50 dark:hover:bg-zinc-750"
                    >
                      Close Audit Log
                    </Button>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

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
                  <span className="font-extrabold text-slate-800 dark:text-zinc-200 text-base">${selectedBookingForDetails.totalAmount?.toLocaleString()}</span>
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
                className="bg-blue-650 hover:bg-blue-700 text-white font-bold"
              >
                Generate Payment Request
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
