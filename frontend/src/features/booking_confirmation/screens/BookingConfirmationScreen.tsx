"use client";

import React, { useState, useEffect, useMemo } from "react";
import { Download, Search, Receipt, Check, X, RefreshCw, AlertTriangle } from "lucide-react";
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
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { StatusPill } from "@/components/ui/status-pill";
import { BookingDetailDrawer } from "@/features/booking_confirmation/components/BookingDetailDrawer";
import { bookingConfirmationService, type Booking } from "@/services/booking_confirmation_service";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import { toast } from "@/stores/toast_store";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";

/**
 * The booking queue: what has been requested, and the Reservation team's decision on each.
 *
 * <p>Two things this screen used to do are gone, and neither belonged here:
 *
 * <ul>
 *   <li><b>"New Request"</b> created a booking straight from an accepted quotation, skipping the
 *       contract the customer has to acknowledge and the room check entirely, and leaving the
 *       quotation's allotment hold in place beside the new booking so the same rooms counted
 *       twice. Conversion happens on the quotation, through Convert to Booking, which does all
 *       of that in one transaction.</li>
 *   <li><b>"Availability Checker"</b> was titled as a live read of the hotel's system but only
 *       ever showed what this CRM had committed. Allocation is on the Room Availability screen,
 *       whose figures the Reservation team publish; anything it cannot settle goes to them as a
 *       room request from the quotation.</li>
 * </ul>
 */
export function BookingConfirmationScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  // Only the Reservation desk (and its MANAGER/ADMIN escalation) may decide a request.
  // Sales holds no BOOKING_WRITE at all now that the duplicate create endpoint is gone.
  const canWrite = user?.permissions?.includes("BOOKING_WRITE") ?? false;

  // Design-system confirmation (§3.16) replacing the native window.confirm.
  const { confirm, confirmElement } = useConfirm();
  const { highlightedId, setRowRef } = useHighlightRow();

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

  useEffect(() => {
    const timer = setTimeout(loadBookings, 0);
    return () => clearTimeout(timer);
  }, [statusFilter]);

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


  const handleDownload = (bNum: string) => {
    toast.success(`Generated PDF Booking Confirmation & Slip for reservation: ${bNum}`);
  };

  return (
    <div className="space-y-6 min-h-[101vh]" style={{ scrollbarGutter: "stable" }}>
      <PageHeader {...PAGE_META.bookingConfirmation} />

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
