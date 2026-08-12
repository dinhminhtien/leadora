"use client";

import React, { useState, useEffect, useMemo } from "react";
import { Download, Search, Receipt, Plus, Check, X, RefreshCw, AlertTriangle } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";

const BOOKING_EXPORT_HEADERS = [
  "Booking code", "Guest", "Room type", "Check-in", "Check-out", "Total (VND)", "Status",
];

function bookingExportRow(b: Booking): (string | number | null | undefined)[] {
  return [
    b.bookingCode, b.customerName, b.details?.[0]?.productName ?? "",
    b.checkInDate, b.checkOutDate, b.totalAmount, b.status,
  ];
}
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { StatusPill } from "@/components/ui/status-pill";
import { BookingDetailDrawer } from "@/features/booking_confirmation/components/BookingDetailDrawer";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { bookingConfirmationService, type Booking, type RoomAvailability } from "@/services/booking_confirmation_service";
import { productService, type ProductService } from "@/services/product_service";
import { quotationService, type Quotation } from "@/services/quotation_service";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import { toast } from "@/stores/toast_store";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { BlockedHint } from "@/components/ui/guarded-action";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";

type TabType = "queue" | "checker";

export function BookingConfirmationScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const canWrite = user?.permissions?.includes("BOOKING_WRITE") ?? false;

  // Design-system confirmation (§3.16) replacing the native window.confirm.
  const { confirm, confirmElement } = useConfirm();
  const { highlightedId, setRowRef } = useHighlightRow();
  const [activeTab, setActiveTab] = useState<TabType>("queue");
  const [isNewRequestOpen, setIsNewRequestOpen] = useState(false);

  const handleOpenNewRequest = () => {
    setFormSuccess("");
    setFormError("");
    setIsNewRequestOpen(true);
  };

  // State for Booking Queue Tab
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loadingBookings, setLoadingBookings] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");

  /** Column set — Blueprint §10.9. */
  const bookingColumns: ColumnDef<Booking>[] = useMemo(() => [
    {
      id: "code",
      header: "Booking Number",
      sticky: "left",
      cell: (b) => (
        <span className="flex items-center gap-1.5 whitespace-nowrap text-xs font-bold text-primary">
          <Receipt className="size-3.5 text-muted-foreground/60" />
          {b.bookingCode}
        </span>
      ),
    },
    {
      id: "guest",
      header: "Guest Name",
      className: "max-w-37.5 truncate text-xs font-bold",
      cell: (b) => <span title={b.customerName}>{b.customerName}</span>,
    },
    {
      id: "roomType",
      header: "Room Type",
      minWidth: "md",
      className: "max-w-40 truncate text-xs text-muted-foreground",
      cell: (b) => {
        const product = b.details?.[0]?.productName ?? "N/A";
        return <span title={product}>{product}</span>;
      },
    },
    {
      id: "checkIn",
      header: "Check In",
      minWidth: "lg",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (b) => b.checkInDate,
    },
    {
      id: "checkOut",
      header: "Check Out",
      minWidth: "lg",
      className: "whitespace-nowrap text-xs text-muted-foreground",
      cell: (b) => b.checkOutDate,
    },
    {
      id: "amount",
      header: "Total Amount",
      numeric: true,
      className: "font-bold",
      cell: (b) => `${b.totalAmount.toLocaleString("vi-VN")} ₫`,
    },
    {
      id: "status",
      header: "Status",
      sticky: "right",
      cell: (b) => <StatusPill size="sm" domain="booking" value={b.status} />,
    },
  ], []);

  const bookingControls = useTableControls<Booking>("bookings", bookingColumns);
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectionReason, setRejectionReason] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  // State for Availability Checker Tab
  const [checkIn, setCheckIn] = useState("");
  const [checkOut, setCheckOut] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [availabilities, setAvailabilities] = useState<RoomAvailability[]>([]);
  const [loadingAvail, setLoadingAvail] = useState(false);
  const [availError, setAvailError] = useState("");

  // State for Create Request Tab

  const [quotations, setQuotations] = useState<Quotation[]>([]);
  const [roomProducts, setRoomProducts] = useState<ProductService[]>([]);
  const [formCustomerId, setFormCustomerId] = useState("");
  const [formQuotationId, setFormQuotationId] = useState("");
  const [formCheckIn, setFormCheckIn] = useState("");
  const [formCheckOut, setFormCheckOut] = useState("");
  const [formProductId, setFormProductId] = useState("");
  const [formQuantity, setFormQuantity] = useState(1);
  const [formNights, setFormNights] = useState(1);
  const [formSpecialRequests, setFormSpecialRequests] = useState("");
  const [submittingBooking, setSubmittingBooking] = useState(false);
  const [formSuccess, setFormSuccess] = useState("");
  const [formError, setFormError] = useState("");

  // Fetch bookings list from server
  const loadBookings = async () => {
    setLoadingBookings(true);
    setErrorMsg("");
    try {
      const statusParam = statusFilter === "all" ? undefined : statusFilter;
      const searchParam = search.trim() === "" ? undefined : search;
      const res = await bookingConfirmationService.getList({
        search: searchParam,
        status: statusParam,
        page: 0,
        size: 50,
        sortBy: "createdAt",
        sortDir: "desc"
      });
      if (res.success && res.data?.content) {
        setBookings(res.data.content);
      }
    } catch (err) {
      console.error(err);
      setErrorMsg("Failed to load booking list from live server.");
    } finally {
      setLoadingBookings(false);
    }
  };

  // Fetch dropdown data for the booking form
  const loadFormData = async () => {
    try {
      const prodRes = await productService.getList("ROOM");
      if (prodRes.success && prodRes.data) {
        setRoomProducts(prodRes.data);
      }
      if (isNewRequestOpen) {
        const quotRes = await quotationService.getList({ size: 100 });
        if (quotRes.success && quotRes.data?.content) {
          setQuotations(quotRes.data.content);
        }
      }
    } catch (err) {
      console.error("Failed to fetch dropdown catalog items from server", err);
    }
  };

  // React hook to load tab-specific data
  useEffect(() => {
    const timer = setTimeout(() => {
      if (activeTab === "queue") {
        loadBookings();
      }
      if (activeTab === "checker" || isNewRequestOpen) {
        loadFormData();
      }
    }, 0);
    return () => clearTimeout(timer);
  }, [activeTab, statusFilter, isNewRequestOpen]);

  const handleSearchKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      loadBookings();
    }
  };

  const handleViewDetails = async (bookingId: string) => {
    setErrorMsg("");
    try {
      const res = await bookingConfirmationService.getById(bookingId);
      if (res.success && res.data) {
        setSelectedBooking(res.data);
        setShowDetailModal(true);
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to load booking request details.");
    }
  };

  // UC-18.5: Approve booking request via live API call.
  //
  // The confirmation is a design-system modal rather than `window.confirm`
  // (Blueprint §1.5 / §3.16) — a native dialog cannot be themed, cannot explain
  // the consequence, and reads to the user as a browser error.
  const handleApprove = async (id: string) => {
    const { ok } = await confirm({
      title: "Approve this booking request?",
      description:
        "The rooms will be committed and a confirmation email is sent to the guest.",
      severity: "info",
      confirmLabel: "Approve booking",
    });
    if (!ok) return;

    setActionLoading(true);
    try {
      const res = await bookingConfirmationService.processRequest(id, {
        status: "CONFIRMED"
      });
      if (res.success) {
        toast.success("Booking request approved successfully.");
        setShowDetailModal(false);
        loadBookings();
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      toast.error(axiosError.response?.data?.message || "Failed to approve booking request.");
    } finally {
      setActionLoading(false);
    }
  };

  // UC-18.5: Reject booking request via live API call
  const handleRejectClick = () => {
    setShowRejectModal(true);
  };

  const handleRejectSubmit = async () => {
    if (!rejectionReason.trim()) {
      toast.warning("Please specify a rejection reason.");
      return;
    }
    if (!selectedBooking) return;
    setActionLoading(true);
    try {
      const res = await bookingConfirmationService.processRequest(selectedBooking.bookingId, {
        status: "REJECTED",
        statusReason: rejectionReason.trim()
      });
      if (res.success) {
        toast.success("Booking request rejected.");
        setShowRejectModal(false);
        setShowDetailModal(false);
        setRejectionReason("");
        loadBookings();
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      toast.error(axiosError.response?.data?.message || "Failed to reject booking request.");
    } finally {
      setActionLoading(false);
    }
  };

  // UC-18.1: Room availability check via live API call
  const handleCheckAvailability = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!checkIn || !checkOut) {
      setAvailError("Both Check-in and Check-out dates are required.");
      return;
    }
    const checkInD = new Date(checkIn);
    const checkOutD = new Date(checkOut);
    if (checkInD >= checkOutD) {
      setAvailError("Check-in date must be strictly before Check-out date.");
      return;
    }
    setLoadingAvail(true);
    setAvailError("");
    try {
      const res = await bookingConfirmationService.checkAvailability({
        checkInDate: checkIn,
        checkOutDate: checkOut,
        productId: selectedProductId !== "" ? selectedProductId : undefined
      });
      if (res.success && res.data) {
        setAvailabilities(res.data);
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setAvailError(axiosError.response?.data?.message || "Failed to check room availability.");
    } finally {
      setLoadingAvail(false);
    }
  };

  const handleQuotationChange = (selectedQuoteId: string) => {
    setFormQuotationId(selectedQuoteId);
    if (!selectedQuoteId) {
      setFormCustomerId("");
      setFormCheckIn("");
      setFormCheckOut("");
      setFormNights(1);
      setFormQuantity(1);
      setFormSpecialRequests("");
      setFormProductId("");
      return;
    }

    const q = quotations.find((item) => item.id === selectedQuoteId);
    if (q) {
      if (q.customerId) {
        setFormCustomerId(q.customerId);
      }
      if (q.checkInDate) setFormCheckIn(q.checkInDate);
      if (q.checkOutDate) setFormCheckOut(q.checkOutDate);
      if (q.checkInDate && q.checkOutDate) {
        const d1 = new Date(q.checkInDate);
        const d2 = new Date(q.checkOutDate);
        const diffTime = d2.getTime() - d1.getTime();
        const computedNights = diffTime > 0 ? Math.ceil(diffTime / (1000 * 60 * 60 * 24)) : 1;
        setFormNights(computedNights);
      } else if (q.nights) {
        setFormNights(q.nights);
      }
      if (q.numberOfRooms) setFormQuantity(q.numberOfRooms);
      if (q.notes) setFormSpecialRequests(q.notes);

      // Match room type name with product
      if (q.roomType) {
        const matchedProduct = roomProducts.find(
          (p) => p.name.toLowerCase() === q.roomType?.toLowerCase()
        );
        if (matchedProduct) {
          setFormProductId(matchedProduct.productId);
        }
      }
    }
  };

  // UC-18.2: Create booking request via live API call
  const handleCreateBooking = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormSuccess("");
    setFormError("");

    if (!formCustomerId || !formQuotationId || !formCheckIn || !formCheckOut || !formProductId) {
      setFormError("All mandatory fields marked with * are required.");
      return;
    }

    const checkInD = new Date(formCheckIn);
    const checkOutD = new Date(formCheckOut);
    if (checkInD >= checkOutD) {
      setFormError("Check-in date must be strictly before Check-out date.");
      return;
    }

    const selectedProduct = roomProducts.find(p => p.productId === formProductId);
    if (!selectedProduct) {
      setFormError("Invalid room type selection.");
      return;
    }

    setSubmittingBooking(true);
    try {
      const res = await bookingConfirmationService.submitRequest({
        customerId: formCustomerId,
        quotationId: formQuotationId,
        checkInDate: formCheckIn,
        checkOutDate: formCheckOut,
        specialRequests: formSpecialRequests,
        details: [
          {
            productId: formProductId,
            quantity: Number(formQuantity),
            unitPrice: selectedProduct.unitPrice,
            nights: Number(formNights)
          }
        ]
      });

      if (res.success) {
        toast.success(`Booking request submitted successfully! Booking Code: ${res.data.bookingCode}`);
        setFormCustomerId("");
        setFormQuotationId("");
        setFormCheckIn("");
        setFormCheckOut("");
        setFormProductId("");
        setFormQuantity(1);
        setFormNights(1);
        setFormSpecialRequests("");
        setIsNewRequestOpen(false);
        loadBookings();
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      setFormError(axiosError.response?.data?.message || "Failed to submit booking request. Verify stay dates or room type availability.");
    } finally {
      setSubmittingBooking(false);
    }
  };

  const selectedQuotationCustomerName = useMemo(() => {
    if (!formQuotationId) return "";
    const q = quotations.find(item => item.id === formQuotationId);
    if (q) {
      return `${q.contactName} (${q.email || "No email"})`;
    }
    return "";
  }, [formQuotationId, quotations]);

  const computedFormAmount = useMemo(() => {
    if (formQuotationId) {
      const q = quotations.find(item => item.id === formQuotationId);
      if (q && q.totalAmount) {
        return q.totalAmount;
      }
    }
    const selectedProduct = roomProducts.find(p => p.productId === formProductId);
    if (!selectedProduct) return 0;
    return selectedProduct.unitPrice * formQuantity * formNights;
  }, [formQuotationId, formProductId, formQuantity, formNights, roomProducts, quotations]);

  const handleDownload = (bNum: string) => {
    toast.success(`Generated PDF Booking Confirmation & Slip for reservation: ${bNum}`);
  };

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      <PageHeader
        {...PAGE_META.bookingConfirmation}
        actions={
          <div className="flex items-center gap-2 rounded-xl border border-border bg-muted/30 p-1">
          <Button
            variant={activeTab === "queue" ? "primary" : "ghost"}
            size="sm"
            onClick={() => {
              setActiveTab("queue");
              setSelectedBooking(null);
            }}
            className="rounded-lg"
          >
            Booking Queue
          </Button>
          {userRole !== "FO" && (
            <Button
              variant={activeTab === "checker" ? "primary" : "ghost"}
              size="sm"
              onClick={() => setActiveTab("checker")}
              className="rounded-lg"
            >
              Availability Checker
            </Button>
          )}
          </div>
        }
      />

      {/* Tab 1: Booking Queue List */}
      {activeTab === "queue" && (
        <div className="w-full block clear-both space-y-4">
          <Card className="shadow-sm border-border bg-background">
            <CardContent className="p-4 flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
              <div className="relative w-full md:w-80">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground/60" />
                <input
                  type="text"
                  placeholder="Search code, guest name, room type... (Press Enter)"
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  onKeyDown={handleSearchKeyPress}
                  className="w-full pl-9 pr-4 h-9 rounded-xl border border-border bg-input text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-primary transition"
                />
              </div>

              <div className="flex items-center gap-3 w-full md:w-auto justify-start md:justify-end">
                <span className="text-xs text-muted-foreground font-bold uppercase tracking-wide">Status:</span>
                <select
                  value={statusFilter}
                  onChange={e => setStatusFilter(e.target.value)}
                  className="h-9 rounded-xl border border-border bg-input px-3 text-xs text-foreground focus:outline-none focus:border-primary"
                >
                  <option value="all">All Request Queue</option>
                  <option value="PENDING">Pending</option>
                  <option value="CONFIRMED">Approved</option>
                  <option value="REJECTED">Rejected</option>
                </select>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={loadBookings}
                  isLoading={loadingBookings}
                  className="flex items-center justify-center h-9 w-9 p-0 rounded-xl shrink-0 border-border"
                  title="Refresh bookings"
                >
                  <RefreshCw className="size-3.5" />
                </Button>
                {canWrite && userRole !== "FO" && userRole !== "RESERVATION" && (
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={handleOpenNewRequest}
                    leftIcon={<Plus className="size-3.5" />}
                    className="h-9 font-semibold"
                  >
                    New Request
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>

          {errorMsg && (
            <div className="bg-red-50 dark:bg-red-950/20 text-danger border border-red-200 dark:border-red-900 rounded-xl p-3 text-xs flex items-center gap-2">
              <AlertTriangle className="size-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <DataTable
            label="Booking queue"
            rows={bookings}
            columns={bookingControls.visibleColumns}
            rowId={(b) => b.bookingId}
            isLoading={loadingBookings}
            density={bookingControls.density}
            sortBy={bookingControls.sortBy}
            sortDir={bookingControls.sortDir}
            onSortChange={bookingControls.onSortChange}
            highlightId={highlightedId}
            rowRef={setRowRef}
            onRowClick={(b) => handleViewDetails(b.bookingId)}
            selectedIds={bookingControls.selectedIds}
            onSelectionChange={bookingControls.setSelectedIds}
            bulkActions={
              <ExportMenu
                filename={`bookings-selected-${new Date().toISOString().slice(0, 10)}`}
                headers={BOOKING_EXPORT_HEADERS}
                rows={bookings.filter((b) => bookingControls.selectedIds.has(b.bookingId)).map(bookingExportRow)}
              />
            }
            isFiltered={!!search || statusFilter !== "all"}
            emptyTitle="No booking requests"
            emptyMessage="Requests raised from an accepted quotation land here for confirmation."
          />
        </div>
      )}

      {/* Tab 2: Availability Checker */}
      {activeTab === "checker" && userRole !== "FO" && (
        <div className="w-full block clear-both">
          <Card className="shadow-sm border-border bg-background">
            <CardHeader>
              <CardTitle>Room Inventory Availability Checker</CardTitle>
              <CardDescription>Input requested stay dates to verify available room count inside live hotel databases.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <form onSubmit={handleCheckAvailability} className="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground block">Check-in Date *</label>
                  <Input
                    type="date"
                    value={checkIn}
                    onChange={e => setCheckIn(e.target.value)}
                    required
                    className="w-full"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground block">Check-out Date *</label>
                  <Input
                    type="date"
                    value={checkOut}
                    onChange={e => setCheckOut(e.target.value)}
                    required
                    className="w-full"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground block">Room Type Option</label>
                  <Select
                    value={selectedProductId}
                    onChange={e => setSelectedProductId(e.target.value)}
                    className="w-full"
                  >
                    <option value="">All Room Catalogue</option>
                    {roomProducts.map(p => (
                      <option key={p.productId} value={p.productId}>{p.name}</option>
                    ))}
                  </Select>
                </div>
                <div className="w-full">
                  <Button type="submit" isLoading={loadingAvail} className="w-full h-9.5">
                    Check Room Availability
                  </Button>
                </div>
              </form>

              {availError && (
                <div className="bg-red-50 dark:bg-red-950/20 text-danger border border-red-200 dark:border-red-900 rounded-xl p-3 text-xs">
                  {availError}
                </div>
              )}

              {availabilities.length > 0 && (
                <div className="border border-border rounded-xl overflow-hidden bg-background">
                  <Table>
                    <TableHeader>
                      <TableRow hoverable={false}>
                        <TableHead className="font-bold text-xs uppercase min-w-37.5 whitespace-nowrap">Room Category Name</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-25 whitespace-nowrap">Base Rate</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-37.5 whitespace-nowrap">Committed in CRM</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {/* Only what this CRM actually owns is shown. The former "Available
                          Capacity" and Available/Fully-Booked columns were derived from a
                          hardcoded capacity of 20 that had nothing to do with the hotel —
                          reconcile these numbers against the PMS instead. */}
                      {availabilities.map(av => (
                          <TableRow key={av.productId}>
                            <TableCell className="font-bold text-xs text-foreground min-w-37.5 whitespace-nowrap">{av.name}</TableCell>
                            <TableCell className="text-xs text-muted-foreground min-w-25 whitespace-nowrap">{av.unitPrice.toLocaleString('vi-VN')} ₫/{av.unit || "night"}</TableCell>
                            <TableCell className="text-xs font-semibold text-foreground min-w-37.5 whitespace-nowrap">{av.totalBooked} {av.totalBooked === 1 ? "room" : "rooms"}</TableCell>
                          </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* UC-18.2: Create Booking Request Modal */}
      {isNewRequestOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white dark:bg-zinc-900 rounded-xl shadow-xl max-w-2xl w-full p-6 relative animate-in fade-in zoom-in-95 duration-200 border border-border">
            <button
              type="button"
              onClick={() => setIsNewRequestOpen(false)}
              className="absolute top-4 right-4 p-1 rounded-lg hover:bg-muted text-muted-foreground transition"
            >
              <X className="size-5" />
            </button>
            <div className="mb-4">
              <h3 className="text-lg font-bold text-foreground">Submit Booking Request Form (Sales UI)</h3>
              <p className="text-xs text-muted-foreground">Initiate a new booking request. All submissions save to the live database in PENDING state.</p>
            </div>
            
            <form onSubmit={handleCreateBooking} className="space-y-4">
              {formSuccess && (
                <div className="bg-green-100/70 dark:bg-green-950/20 text-green-800 dark:text-green-300 border border-green-200 dark:border-green-900 rounded-xl p-3 text-xs flex items-center gap-2">
                  <Check className="size-4 shrink-0" />
                  <span className="font-semibold">{formSuccess}</span>
                </div>
              )}

              {formError && (
                <div className="bg-red-100/70 dark:bg-red-950/20 text-red-800 dark:text-red-300 border border-red-200 dark:border-red-900 rounded-xl p-3 text-xs flex items-center gap-2">
                  <AlertTriangle className="size-4 shrink-0" />
                  <span className="font-semibold">{formError}</span>
                </div>
              )}

              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5 w-full">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Linked Quotation *</label>
                  <Select
                    value={formQuotationId}
                    onChange={e => handleQuotationChange(e.target.value)}
                    required
                    className="w-full"
                  >
                    <option value="">-- Choose Quotation Ref --</option>
                    {quotations.map(q => (
                      <option key={q.id} value={q.id}>{String(q.quoteNo || q.id).substring(0, 8)}... (Status: {q.status})</option>
                    ))}
                  </Select>
                  {/* BR-23 — stay details are taken from the approved quotation,
                      not retyped. Without this note the dates and room fields
                      simply grey out on selection and the form looks broken. */}
                  {!!formQuotationId && (
                    <BlockedHint reason="Stay dates, room type and quantity are taken from this quotation (BR-23). Clear the quotation to enter them manually." />
                  )}
                </div>

                <div className="flex flex-col gap-1.5 w-full">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Guest / Customer</label>
                  <Input
                    type="text"
                    value={selectedQuotationCustomerName || "Select a quotation to load guest details"}
                    disabled
                    className="w-full"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Check-in Date *</label>
                  <Input
                    type="date"
                    value={formCheckIn}
                    onChange={e => setFormCheckIn(e.target.value)}
                    required
                    className="w-full"
                    disabled={!!formQuotationId}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Check-out Date *</label>
                  <Input
                    type="date"
                    value={formCheckOut}
                    onChange={e => setFormCheckOut(e.target.value)}
                    required
                    className="w-full"
                    disabled={!!formQuotationId}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Nights Count *</label>
                  <Input
                    type="number"
                    min={1}
                    value={formNights}
                    onChange={e => setFormNights(Number(e.target.value))}
                    required
                    className="w-full"
                    disabled={!!formQuotationId}
                  />
                </div>
              </div>

              <div className="border-t border-border pt-4">
                <h4 className="text-xs font-black uppercase text-foreground mb-3 tracking-wider">Allocated Room Details</h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="md:col-span-2 flex flex-col gap-1.5">
                    <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Room Type Selection *</label>
                    <Select
                      value={formProductId}
                      onChange={e => setFormProductId(e.target.value)}
                      required
                      className="w-full"
                      disabled={!!formQuotationId}
                    >
                      <option value="">-- Select Room Type --</option>
                       {roomProducts.map(p => {
                        const isSelected = p.productId === formProductId;
                        let displayPrice = p.unitPrice;
                        if (isSelected && formQuotationId) {
                          const q = quotations.find(item => item.id === formQuotationId);
                          if (q && q.totalAmount) {
                            const nights = formNights || 1;
                            const qty = formQuantity || 1;
                            displayPrice = q.totalAmount / (qty * nights);
                          }
                        }
                        return (
                          <option key={p.productId} value={p.productId}>
                            {p.name} ({displayPrice.toLocaleString('vi-VN')} ₫/night)
                          </option>
                        );
                      })}
                    </Select>
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Quantity (Rooms) *</label>
                    <Input
                      type="number"
                      min={1}
                      value={formQuantity}
                      onChange={e => setFormQuantity(Number(e.target.value))}
                      required
                      className="w-full"
                      disabled={!!formQuotationId}
                    />
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-1.5 w-full">
                <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Special Requests Note</label>
                <textarea
                  rows={2}
                  value={formSpecialRequests}
                  onChange={e => setFormSpecialRequests(e.target.value)}
                  className="w-full rounded-xl border border-border bg-input py-2 px-3.5 text-sm text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] dark:shadow-none transition disabled:opacity-75 disabled:cursor-not-allowed"
                  placeholder="E.g., early check-in, high floor, quiet room..."
                  disabled={!!formQuotationId}
                />
              </div>

              <div className="bg-muted/30 rounded-xl border border-border p-3 flex justify-between items-center w-full">
                <div className="text-xs text-muted-foreground font-bold uppercase tracking-wide">
                  Autocalculated Total (UnitPrice * Qty * Nights):
                </div>
                <div className="text-lg font-black text-foreground">
                  {computedFormAmount.toLocaleString("vi-VN")} ₫
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <Button type="button" variant="outline" onClick={() => setIsNewRequestOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" isLoading={submittingBooking} className="px-6">
                  Submit Booking Request
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      <BookingDetailDrawer
        booking={showDetailModal ? selectedBooking : null}
        onOpenChange={(open) => !open && setShowDetailModal(false)}
        actions={
          selectedBooking
            ? [
                {
                  label: "Download slip",
                  icon: Download,
                  variant: "outline" as const,
                  onClick: () => handleDownload(selectedBooking.bookingCode),
                },
                // Approve/reject exist only on a PENDING request — the server
                // refuses the transition from any other state (§12.13).
                ...(selectedBooking.status === "PENDING" && canWrite && userRole !== "SALES" && userRole !== "FO"
                  ? [
                      {
                        label: "Reject request",
                        icon: X,
                        variant: "danger" as const,
                        disabled: actionLoading,
                        onClick: handleRejectClick,
                      },
                      {
                        label: "Approve booking",
                        icon: Check,
                        variant: "success" as const,
                        disabled: actionLoading,
                        onClick: () => handleApprove(selectedBooking.bookingId),
                      },
                    ]
                  : []),
              ]
            : []
        }
      />

      {/* Reject Modal */}
      {showRejectModal && selectedBooking && (
        <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-background rounded-2xl border border-border shadow-2xl max-w-md w-full p-6 space-y-4">
            <div>
              <h3 className="text-base font-bold text-foreground">Reject booking confirmation</h3>
              <p className="text-xs text-muted-foreground">Provide a rejection reason for customer request {selectedBooking.bookingCode}. This is logged for audit reporting.</p>
            </div>
            <div>
              <textarea
                value={rejectionReason}
                onChange={e => setRejectionReason(e.target.value)}
                rows={3}
                placeholder="E.g., Fully booked for selected room category on stay dates..."
                className="w-full rounded-xl border border-border bg-input py-2 px-3 text-sm text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] dark:shadow-none"
                required
              />
            </div>
            <div className="flex justify-end gap-2 text-xs font-semibold">
              <Button variant="outline" size="sm" onClick={() => setShowRejectModal(false)}>
                Cancel
              </Button>
              <Button variant="danger" size="sm" onClick={handleRejectSubmit} isLoading={actionLoading}>
                Confirm Rejection
              </Button>
            </div>
          </div>
        </div>
      )}
      {confirmElement}
    </div>
  );
}
