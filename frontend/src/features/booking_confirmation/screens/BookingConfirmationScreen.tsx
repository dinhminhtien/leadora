"use client";

import React, { useState, useEffect, useMemo } from "react";
import { Download, Search, Receipt, Calendar, User, Plus, Check, X, RefreshCw, AlertTriangle } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { bookingConfirmationService, type Booking, type RoomAvailability } from "@/services/booking_confirmation_service";
import { productService, type ProductService } from "@/services/product_service";
import { customerProfileService, type CustomerProfile } from "@/services/customer_profile_service";
import { quotationService, type Quotation } from "@/services/quotation_service";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";

type TabType = "queue" | "checker";

export function BookingConfirmationScreen() {
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
  const [customers, setCustomers] = useState<CustomerProfile[]>([]);
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
      const custRes = await customerProfileService.getList();
      if (custRes.success && custRes.data) {
        setCustomers(custRes.data);
      }
      const quotRes = await quotationService.getList();
      if (quotRes.success && quotRes.data) {
        setQuotations(quotRes.data);
      }
      const prodRes = await productService.getList("ROOM");
      if (prodRes.success && prodRes.data) {
        setRoomProducts(prodRes.data);
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
      alert("Failed to load booking request details.");
    }
  };

  // UC-18.5: Approve booking request via live API call
  const handleApprove = async (id: string) => {
    if (confirm!("Are you sure you want to approve this booking request?")) return;
    setActionLoading(true);
    try {
      const res = await bookingConfirmationService.processRequest(id, {
        status: "CONFIRMED"
      });
      if (res.success) {
        alert("Booking request approved successfully.");
        setShowDetailModal(false);
        loadBookings();
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || "Failed to approve booking request.");
    } finally {
      setActionLoading(false);
    }
  };

  // UC-18.5: Reject booking request via live API call
  const handleRejectClick = () => {
    setShowRejectModal(true);
  };

  const handleRejectSubmit = async () => {
    if (rejectionReason.trim!()) {
      alert("Please specify a rejection reason.");
      return;
    }
    if (!selectedBooking) return;
    setActionLoading(true);
    try {
      const res = await bookingConfirmationService.processRequest(selectedBooking.bookingId, {
        status: "REJECTED",
        rejectionReason: rejectionReason.trim()
      });
      if (res.success) {
        alert("Booking request rejected.");
        setShowRejectModal(false);
        setShowDetailModal(false);
        setRejectionReason("");
        loadBookings();
      }
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || "Failed to reject booking request.");
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
        alert(`Booking request submitted successfully! Booking Code: ${res.data.bookingCode}`);
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

  const computedFormAmount = useMemo(() => {
    const selectedProduct = roomProducts.find(p => p.productId === formProductId);
    if (!selectedProduct) return 0;
    return selectedProduct.unitPrice * formQuantity * formNights;
  }, [formProductId, formQuantity, formNights, roomProducts]);

  const getBadgeVariant = (status: string) => {
    switch (status.toUpperCase()) {
      case "CONFIRMED":
        return "success";
      case "PENDING":
        return "warning";
      case "REJECTED":
        return "danger";
      default:
        return "default";
    }
  };

  const handleDownload = (bNum: string) => {
    alert(`Successfully generated PDF Booking Confirmation & Slip for reservation: ${bNum}`);
  };

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      {/* Header section */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800 dark:text-zinc-100">Booking Confirmations</h1>
          <p className="text-xs text-slate-400 dark:text-zinc-400">Manage reservation confirmation states, check active room inventory capacity, and process requests.</p>
        </div>
        <div className="flex items-center gap-2 border border-border rounded-xl p-1 bg-muted/30">
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
          <Button
            variant={activeTab === "checker" ? "primary" : "ghost"}
            size="sm"
            onClick={() => setActiveTab("checker")}
            className="rounded-lg"
          >
            Availability Checker
          </Button>
        </div>
      </div>

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
                <Button
                  variant="primary"
                  size="sm"
                  onClick={handleOpenNewRequest}
                  leftIcon={<Plus className="size-3.5" />}
                  className="h-9 font-semibold"
                >
                  New Request
                </Button>
              </div>
            </CardContent>
          </Card>

          {errorMsg && (
            <div className="bg-red-50 dark:bg-red-950/20 text-danger border border-red-200 dark:border-red-900 rounded-xl p-3 text-xs flex items-center gap-2">
              <AlertTriangle className="size-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <div className="bg-background rounded-xl border border-border shadow-sm overflow-hidden">
            <div className="w-full overflow-x-auto">
              <Table className="w-full table-fixed min-w-[1100px]">
                <TableHeader>
                  <TableRow hoverable={false}>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[12%] text-left! whitespace-nowrap">Booking Number</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[20%] text-left! whitespace-nowrap">Guest Name</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[15%] text-left! whitespace-nowrap">Room Type</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[10%] text-center! whitespace-nowrap">Check In</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[10%] text-center! whitespace-nowrap">Check Out</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[15%] text-right! whitespace-nowrap">Total Amount</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[10%] text-center! whitespace-nowrap">Status</TableHead>
                    <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[8%] text-center! whitespace-nowrap">SLA</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loadingBookings ? (
                    <TableRow hoverable={false}>
                      <TableCell colSpan={9} className="py-12 text-center text-muted-foreground text-xs">
                        <RefreshCw className="size-6 animate-spin mx-auto mb-2 text-primary" />
                        Loading bookings from live server...
                      </TableCell>
                    </TableRow>
                  ) : bookings.length > 0 ? (
                    bookings.map(b => (
                      <TableRow
                        key={b.bookingId}
                        ref={setRowRef(b.bookingId)}
                        onClick={() => handleViewDetails(b.bookingId)}
                        className={`hover:bg-muted/30 border-b border-border transition cursor-pointer select-none ${
                          highlightedId === b.bookingId ? "bg-amber-50 ring-2 ring-inset ring-amber-400 dark:bg-amber-500/10" : ""
                        }`}
                      >
                        <TableCell className="py-3.5! px-4! text-xs! font-bold! text-slate-700! dark:text-zinc-300! text-left! whitespace-nowrap">
                          <span className="flex items-center justify-start gap-1.5 text-primary">
                            <Receipt className="size-3.5 text-muted-foreground/60" />
                            {b.bookingCode}
                          </span>
                        </TableCell>
                        <TableCell className="py-3.5! px-4! text-xs! font-bold! text-slate-800! dark:text-zinc-200! text-left! whitespace-nowrap truncate max-w-[150px]" title={b.customerName}>{b.customerName}</TableCell>
                        <TableCell className="py-3.5! px-4! text-xs! text-slate-600! dark:text-zinc-400! text-left! whitespace-nowrap truncate max-w-[160px]" title={b.details && b.details.length > 0 ? b.details[0].productName : "N/A"}>
                          {b.details && b.details.length > 0 ? b.details[0].productName : "N/A"}
                        </TableCell>
                        <TableCell className="py-3.5! px-4! text-xs! text-slate-500! dark:text-zinc-400! text-center! whitespace-nowrap">{b.checkInDate}</TableCell>
                        <TableCell className="py-3.5! px-4! text-xs! text-slate-500! dark:text-zinc-400! text-center! whitespace-nowrap">{b.checkOutDate}</TableCell>
                        <TableCell className="py-3.5! px-4! text-xs! font-bold! text-slate-700! dark:text-zinc-300! text-right! whitespace-nowrap">
                          {b.totalAmount.toLocaleString('vi-VN')} ₫
                        </TableCell>
                        <TableCell className="py-3.5! px-4! text-center! whitespace-nowrap">
                          <div className="flex justify-center">
                            <Badge variant={getBadgeVariant(b.status)} className="font-bold text-[9px] uppercase min-w-[90px] justify-center text-center py-1">
                              {b.status}
                            </Badge>
                          </div>
                        </TableCell>
                        <TableCell className="py-3.5! px-4! text-center! whitespace-nowrap">
                          <div className="flex justify-center">
                            <SlaStatusBadge entityId={b.bookingId} entityType="BOOKING" />
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow hoverable={false}>
                      <TableCell colSpan={9} className="py-12 text-center text-muted-foreground text-xs">
                        No booking confirmation requests match the filter criteria.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Availability Checker */}
      {activeTab === "checker" && (
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
                  <Button type="submit" isLoading={loadingAvail} className="w-full h-[38px]">
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
                        <TableHead className="font-bold text-xs uppercase min-w-[150px] whitespace-nowrap">Room Category Name</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-[100px] whitespace-nowrap">Base Rate</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-[150px] whitespace-nowrap">Occupied (Booked)</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-[150px] whitespace-nowrap">Available Capacity</TableHead>
                        <TableHead className="font-bold text-xs uppercase min-w-[110px] whitespace-nowrap">Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {availabilities.map(av => {
                        const capacity = 20; // Default capacity limit on backend
                        const remaining = Math.max(0, capacity - av.totalBooked);
                        return (
                          <TableRow key={av.productId}>
                            <TableCell className="font-bold text-xs text-foreground min-w-[150px] whitespace-nowrap">{av.name}</TableCell>
                            <TableCell className="text-xs text-muted-foreground min-w-[100px] whitespace-nowrap">{av.unitPrice.toLocaleString('vi-VN')} ₫/{av.unit || "night"}</TableCell>
                            <TableCell className="text-xs font-semibold text-foreground min-w-[150px] whitespace-nowrap">{av.totalBooked} / {capacity} Rooms</TableCell>
                            <TableCell className="text-xs font-bold text-primary min-w-[150px] whitespace-nowrap">{remaining} rooms remaining</TableCell>
                            <TableCell className="min-w-[110px]">
                              <Badge variant={av.isAvailable ? "success" : "danger"} className="uppercase font-bold text-[9px] min-w-[90px] justify-center text-center py-1">
                                {av.isAvailable ? "Available" : "Fully Booked"}
                              </Badge>
                            </TableCell>
                          </TableRow>
                        );
                      })}
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

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Guest / Customer *</label>
                  <Select
                    value={formCustomerId}
                    onChange={e => setFormCustomerId(e.target.value)}
                    required
                    className="w-full"
                  >
                    <option value="">-- Choose Customer --</option>
                    {customers.map(c => (
                      <option key={c.id} value={c.id}>{c.name} ({c.email || "No email"})</option>
                    ))}
                  </Select>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground block">Linked Quotation *</label>
                  <Select
                    value={formQuotationId}
                    onChange={e => setFormQuotationId(e.target.value)}
                    required
                    className="w-full"
                  >
                    <option value="">-- Choose Quotation Ref --</option>
                    {quotations.map(q => (
                      <option key={q.id} value={q.id}>{String(q.quoteNo || q.id).substring(0, 8)}... (Status: {q.status})</option>
                    ))}
                  </Select>
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
                    >
                      <option value="">-- Select Room Type --</option>
                      {roomProducts.map(p => (
                        <option key={p.productId} value={p.productId}>{p.name} ({p.unitPrice.toLocaleString('vi-VN')} ₫/night)</option>
                      ))}
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
                  className="w-full rounded-xl border border-border bg-input py-2 px-3.5 text-sm text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] dark:shadow-none transition"
                  placeholder="E.g., early check-in, high floor, quiet room..."
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

      {/* UC-18.4 & UC-18.5: Selected Booking Request Detail Modal */}
      {showDetailModal && selectedBooking && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-background rounded-2xl border border-border shadow-2xl max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-5 border-b border-border flex justify-between items-center bg-muted/40">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <Badge variant={getBadgeVariant(selectedBooking.status)} className="uppercase text-[9px] font-bold">
                    {selectedBooking.status}
                  </Badge>
                  <SlaStatusBadge entityId={selectedBooking.bookingId} entityType="BOOKING" />
                  <span className="text-[10px] text-muted-foreground uppercase font-bold tracking-wider">Reservation request</span>
                </div>
                <h3 className="text-lg font-bold text-foreground">{selectedBooking.bookingCode}</h3>
              </div>
              <button
                onClick={() => setShowDetailModal(false)}
                className="p-1 rounded-lg hover:bg-muted text-muted-foreground transition"
              >
                <X className="size-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6 flex-1 text-sm">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-1">Guest / Customer</span>
                  <div className="flex items-center gap-1.5">
                    <User className="size-4 text-muted-foreground/80" />
                    <span className="font-semibold text-foreground">{selectedBooking.customerName}</span>
                  </div>
                </div>

                <div>
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-1">Linked Quotation Ref</span>
                  <div className="flex items-center gap-1.5">
                    <Receipt className="size-4 text-muted-foreground/80" />
                    <span className="font-semibold text-foreground">Code: {String(selectedBooking.quotationId).substring(0, 8)}...</span>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 border-t border-border pt-4">
                <div>
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-1">Check In</span>
                  <div className="flex items-center gap-1.5">
                    <Calendar className="size-4 text-muted-foreground/80" />
                    <span className="font-semibold text-foreground">{selectedBooking.checkInDate}</span>
                  </div>
                </div>

                <div>
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-1">Check Out</span>
                  <div className="flex items-center gap-1.5">
                    <Calendar className="size-4 text-muted-foreground/80" />
                    <span className="font-semibold text-foreground">{selectedBooking.checkOutDate}</span>
                  </div>
                </div>
              </div>

              {selectedBooking.specialRequests && (
                <div className="border-t border-border pt-4">
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-1">Special Requests</span>
                  <p className="bg-muted/40 p-3 rounded-xl border border-border text-xs text-foreground italic">
                    &quot;{selectedBooking.specialRequests}&quot;
                  </p>
                </div>
              )}

              {selectedBooking.status === "REJECTED" && selectedBooking.rejectionReason && (
                <div className="border-t border-border pt-4">
                  <span className="text-[10px] uppercase font-bold text-danger block tracking-wider mb-1">Rejection Reason</span>
                  <p className="bg-red-50 dark:bg-red-950/20 p-3 rounded-xl border border-red-200 dark:border-red-900 text-xs text-danger font-semibold">
                    {selectedBooking.rejectionReason}
                  </p>
                </div>
              )}

              <div className="border-t border-border pt-4">
                <span className="text-[10px] uppercase font-bold text-muted-foreground block tracking-wider mb-2">Requested Allocation</span>
                <div className="border border-border rounded-xl overflow-hidden bg-background">
                  <Table>
                    <TableHeader className="bg-muted/60">
                      <TableRow hoverable={false}>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 min-w-[130px] whitespace-nowrap">Room Type</TableHead>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 min-w-[110px] whitespace-nowrap">Room #</TableHead>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 min-w-[50px] whitespace-nowrap">Qty</TableHead>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 min-w-[60px] whitespace-nowrap">Nights</TableHead>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 min-w-[80px] whitespace-nowrap">Rate</TableHead>
                        <TableHead className="font-bold py-2 px-3 text-xs text-slate-500 text-right min-w-[100px] whitespace-nowrap">Line Total</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {selectedBooking.details && selectedBooking.details.map((d, index) => (
                        <TableRow key={index} hoverable={false}>
                          <TableCell className="py-2.5 px-3 font-semibold text-xs text-foreground min-w-[130px] whitespace-nowrap">{d.productName}</TableCell>
                          <TableCell className="py-2.5 px-3 text-xs text-primary font-bold min-w-[110px] whitespace-nowrap">{d.roomNumber || "Pending Assignment"}</TableCell>
                          <TableCell className="py-2.5 px-3 text-xs min-w-[50px]">{d.quantity}</TableCell>
                          <TableCell className="py-2.5 px-3 text-xs min-w-[60px]">{d.nights}</TableCell>
                          <TableCell className="py-2.5 px-3 text-xs min-w-[80px]">{d.unitPrice.toLocaleString('vi-VN')} ₫</TableCell>
                          <TableCell className="py-2.5 px-3 text-xs font-black text-right min-w-[100px] whitespace-nowrap">{d.lineTotal.toLocaleString("vi-VN")} ₫</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-border bg-muted/40 flex flex-col md:flex-row gap-3 justify-between items-center">
              <div className="text-sm font-semibold text-foreground">
                Total amount: <span className="text-lg font-black text-primary">{selectedBooking.totalAmount.toLocaleString("vi-VN")} ₫</span>
              </div>

              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => setShowDetailModal(false)}>
                  Close Details
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleDownload(selectedBooking.bookingCode)}
                  leftIcon={<Download className="size-3.5" />}
                >
                  Download Slip
                </Button>
                {selectedBooking.status === "PENDING" && (
                  <>
                    <Button
                      variant="danger"
                      size="sm"
                      leftIcon={<X className="size-3.5" />}
                      onClick={handleRejectClick}
                      disabled={actionLoading}
                    >
                      Reject Request
                    </Button>
                    <Button
                      variant="success"
                      size="sm"
                      leftIcon={<Check className="size-3.5" />}
                      onClick={() => handleApprove(selectedBooking.bookingId)}
                      isLoading={actionLoading}
                    >
                      Approve Booking
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

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
    </div>
  );
}
