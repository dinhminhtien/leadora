"use client";

import React, { useState, useEffect, useMemo, useCallback } from "react";
import { Search, User, XCircle, Info, Calendar, ArrowRight, Loader2, AlertCircle, RefreshCw, LogIn, LogOut } from "lucide-react";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Badge } from "@/components/ui/Badge";
import { StatusPill } from "@/components/ui/status-pill";
import { reservationStatusService, type ReservationStatus } from "@/services/reservation_status_service";

const RESERVATION_EXPORT_HEADERS = [
  "Guest", "Reservation ref", "Room type", "Check-in", "Check-out", "Total (VND)", "Status",
];

function reservationExportRow(r: ReservationStatus): (string | number | null | undefined)[] {
  return [r.guestName, r.reservationNo, r.roomType, r.checkInDate, r.checkOutDate, r.totalAmount, r.status];
}
import { ReservationDetailDrawer } from "@/features/reservation_status/components/ReservationDetailDrawer";
import { toast } from "@/stores/toast_store";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";

export function ReservationStatusScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const canWrite = user?.permissions?.includes("RESERVATION_WRITE") ?? false;

  /** Column set — Blueprint §10.10 front-desk view. */
  const reservationColumns: ColumnDef<ReservationStatus>[] = useMemo(() => [
    {
      id: "guest",
      header: "Guest Name",
      sticky: "left",
      cell: (res) => (
        <span className="flex items-center gap-1.5 text-xs font-bold text-foreground">
          <User className="size-3.5 text-muted-foreground" />
          {res.guestName}
        </span>
      ),
    },
    {
      id: "ref",
      header: "Reservation Ref",
      className: "text-xs font-bold",
      cell: (res) => res.reservationNo,
    },
    {
      id: "roomType",
      header: "Room Type",
      minWidth: "md",
      className: "text-xs text-muted-foreground",
      cell: (res) => res.roomType,
    },
    {
      id: "stay",
      header: "Check-in / Check-out",
      minWidth: "lg",
      cell: (res) => (
        <span className="flex items-center gap-1 whitespace-nowrap text-xs text-muted-foreground">
          <Calendar className="size-3" />
          {res.checkInDate}
          <ArrowRight className="size-3" />
          {res.checkOutDate}
        </span>
      ),
    },
    {
      id: "amount",
      header: "Total Amount",
      numeric: true,
      className: "font-bold",
      cell: (res) => `${res.totalAmount?.toLocaleString("vi-VN") ?? 0} ₫`,
    },
    {
      id: "status",
      header: "Occupancy Status",
      // Canonical booking binding (Blueprint §2.7) — the hand-rolled ternary this
      // replaced fell through to neutral grey for REJECTED and NO_SHOW.
      cell: (res) => <StatusPill size="sm" domain="booking" value={res.status} />,
    },
  ], []);

  const controls = useTableControls<ReservationStatus>("reservations", reservationColumns);

  const [reservations, setReservations] = useState<ReservationStatus[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);
  const [search, setSearch] = useState("");
  const [searchVal, setSearchVal] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Modal States
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailData, setDetailData] = useState<ReservationStatus | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);

  const [cancelId, setCancelId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [showCancelModal, setShowCancelModal] = useState(false);

  // Fetch List
  const fetchList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await reservationStatusService.getReservations({
        search,
        status: statusFilter,
        page: currentPage,
        size: pageSize,
        sortBy: "createdAt",
        sortDir: "desc"
      });
      if (data && data.success) {
        setReservations(data.data.content || []);
        if (data.data.page && typeof data.data.page === "object") {
          setTotalElements(data.data.page.totalElements || 0);
          setTotalPages(data.data.page.totalPages || 0);
        } else {
          setTotalElements(data.data.totalElements || 0);
          setTotalPages(data.data.totalPages || 0);
        }
      } else {
        setError(data.message || "Failed to fetch reservations.");
      }
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  }, [search, statusFilter, currentPage, pageSize]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  // Search input debouncer helper
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchVal(e.target.value);
  };

  const handleStatusFilterChange = (status: string) => {
    setStatusFilter(status);
    setCurrentPage(0);
  };

  // View Detail
  const handleOpenDetail = async (id: string) => {
    setSelectedId(id);
    setShowDetailModal(true);
    setDetailLoading(true);
    setDetailData(null);
    try {
      const res = await reservationStatusService.getReservationDetail(id);
      if (res && res.success) {
        setDetailData(res.data);
      } else {
        toast.error(res.message || "Failed to fetch reservation detail.");
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || err.message || "Failed to fetch reservation detail.");
    } finally {
      setDetailLoading(false);
    }
  };

  // Check-In / Check-Out Actions
  const handleStatusChange = async ({ id, newStatus, reason }: { id: string; newStatus: string; reason: string }) => {
    setActionLoading(id);
    try {
      const res = await reservationStatusService.updateReservationStatus(id, newStatus, reason);
      if (res && res.success) {
        // Refetch list and refresh details if opened
        await fetchList();
        if (showDetailModal && selectedId === id) {
          setDetailData(res.data);
        }
        toast.success(res.message || "Reservation status updated.");
      } else {
        toast.error(res.message || `Failed to update status to ${newStatus}.`);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || err.message || "Failed to update reservation status.");
    } finally {
      setActionLoading(null);
    }
  };

  // Open Cancel Modal
  const handleOpenCancel = (id: string) => {
    setCancelId(id);
    setCancelReason("");
    setShowCancelModal(true);
  };

  // Submit Cancel Action
  const handleCancelSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cancelId || !cancelReason.trim()) return;

    setActionLoading(cancelId);
    setShowCancelModal(false);
    try {
      const res = await reservationStatusService.cancelReservation(cancelId, cancelReason);
      if (res && res.success) {
        await fetchList();
        if (showDetailModal && selectedId === cancelId) {
          setDetailData(res.data);
        }
        toast.success(res.message || "Reservation cancelled.");
      } else {
        toast.error(res.message || "Failed to cancel reservation.");
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || err.message || "Failed to cancel reservation.");
    } finally {
      setActionLoading(null);
      setCancelId(null);
    }
  };

  return (
    <div className="space-y-6" style={{ scrollbarGutter: "stable" }}>
      <PageHeader
        {...PAGE_META.reservationStatus}
        actions={
          <>
            <Button variant="secondary" onClick={fetchList} leftIcon={<RefreshCw className="size-4" />}>
              Refresh
            </Button>
            <Badge variant="primary" className="text-xs h-9 px-3 flex items-center justify-center font-bold uppercase bg-blue-100 text-blue-800 rounded-lg">
              PMS Live Sync
            </Badge>
          </>
        }
      />

      {error && (
        <div className="flex items-center gap-2 p-4 text-sm text-red-800 bg-red-50 rounded-xl border border-red-100">
          <AlertCircle className="size-4 text-red-600 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Filter Options */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-4 px-4 flex flex-row items-center justify-between gap-4 flex-wrap lg:flex-nowrap w-full">
          <div className="relative w-full lg:w-72 shrink-0">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search guest name, room reference... (Press Enter)"
              value={searchVal}
              onChange={handleSearchChange}
              onKeyDown={e => {
                if (e.key === "Enter") {
                  setSearch(searchVal);
                  setCurrentPage(0);
                }
              }}
              className="w-full pl-8 pr-3 h-9 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>

          <div className="flex flex-row items-center gap-2 flex-wrap lg:flex-nowrap justify-start lg:justify-end w-full lg:w-auto">
            <Button
              variant={statusFilter === "" ? "primary" : "outline"}
              size="sm"
              onClick={() => handleStatusFilterChange("")}
              className="text-xs px-3 h-9 flex items-center justify-center whitespace-nowrap"
            >
              All
            </Button>
            <Button
              variant={statusFilter === "CONFIRMED" ? "primary" : "outline"}
              size="sm"
              onClick={() => handleStatusFilterChange("CONFIRMED")}
              className="text-xs px-3 h-9 flex items-center justify-center whitespace-nowrap"
            >
              Confirmed
            </Button>
            <Button
              variant={statusFilter === "CHECKED_IN" ? "primary" : "outline"}
              size="sm"
              onClick={() => handleStatusFilterChange("CHECKED_IN")}
              className="text-xs px-3 h-9 flex items-center justify-center whitespace-nowrap"
            >
              Checked In
            </Button>
            <Button
              variant={statusFilter === "CHECKED_OUT" ? "primary" : "outline"}
              size="sm"
              onClick={() => handleStatusFilterChange("CHECKED_OUT")}
              className="text-xs px-3 h-9 flex items-center justify-center whitespace-nowrap"
            >
              Checked Out
            </Button>
            <Button
              variant={statusFilter === "CANCELLED" ? "primary" : "outline"}
              size="sm"
              onClick={() => handleStatusFilterChange("CANCELLED")}
              className="text-xs px-3 h-9 flex items-center justify-center whitespace-nowrap"
            >
              Cancelled
            </Button>
          </div>
        </CardContent>
      </Card>

      <DataTable
        label="Reservations"
        rows={reservations}
        columns={controls.visibleColumns}
        rowId={(r) => r.id}
        isLoading={loading}
        density={controls.density}
        sortBy={controls.sortBy}
        sortDir={controls.sortDir}
        onSortChange={controls.onSortChange}
        onRowClick={(r) => handleOpenDetail(r.id)}
        selectedIds={controls.selectedIds}
        onSelectionChange={controls.setSelectedIds}
        bulkActions={
          <ExportMenu
            filename={`reservations-selected-${new Date().toISOString().slice(0, 10)}`}
            headers={RESERVATION_EXPORT_HEADERS}
            rows={reservations.filter((r) => controls.selectedIds.has(r.id)).map(reservationExportRow)}
          />
        }
        isFiltered={!!search || !!statusFilter}
        emptyTitle="No reservations"
        emptyMessage="Confirmed bookings appear here as arrivals approach."
        footer={
          <TablePagination
            page={currentPage}
            pageSize={pageSize}
            totalElements={totalElements}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
          />
        }
      />

      <ReservationDetailDrawer
        reservation={showDetailModal ? detailData : null}
        onOpenChange={(open) => !open && setShowDetailModal(false)}
        actions={
          detailData && canWrite && userRole !== "SALES"
            ? [
                // Check-in and check-out are sequential — the server accepts
                // each from exactly one prior status (§12.13).
                ...(detailData.status === "CONFIRMED"
                  ? [
                      {
                        label: "Check in guest",
                        icon: LogIn,
                        variant: "primary" as const,
                        onClick: () =>
                          handleStatusChange({
                            id: detailData.id,
                            newStatus: "CHECKED_IN",
                            reason: "Guest Checked In",
                          }),
                      },
                    ]
                  : []),
                ...(detailData.status === "CHECKED_IN"
                  ? [
                      {
                        label: "Check out guest",
                        icon: LogOut,
                        variant: "outline" as const,
                        onClick: () =>
                          handleStatusChange({
                            id: detailData.id,
                            newStatus: "CHECKED_OUT",
                            reason: "Guest Checked Out",
                          }),
                      },
                    ]
                  : []),
                ...(detailData.status !== "CANCELLED" && detailData.status !== "CHECKED_OUT"
                  ? [
                      {
                        label: "Cancel reservation",
                        icon: XCircle,
                        variant: "danger" as const,
                        onClick: () => handleOpenCancel(detailData.id),
                      },
                    ]
                  : []),
              ]
            : []
        }
      >
        {detailLoading && (
          <p className="flex items-center gap-2 text-[12px] text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> Loading reservation…
          </p>
        )}
      </ReservationDetailDrawer>

      {/* Cancel Request Modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 overflow-y-auto transition-all">
          <div className="bg-white rounded-2xl border border-slate-100 shadow-xl w-full max-w-md overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-200">
            <form onSubmit={handleCancelSubmit}>
              <div className="flex justify-between items-center px-6 py-4 bg-slate-50 border-b border-slate-100">
                <h2 className="font-bold text-slate-800 flex items-center gap-2 text-sm">
                  <XCircle className="size-4 text-red-500" />
                  Cancel Reservation
                </h2>
                <button
                  type="button"
                  onClick={() => setShowCancelModal(false)}
                  className="text-slate-400 hover:text-slate-600 text-lg transition"
                >
                  &times;
                </button>
              </div>
              
              <div className="p-6 space-y-4">
                <div className="text-xs text-slate-600 leading-relaxed">
                  Are you sure you want to cancel this reservation? The held room inventory will be released and returned to the active pool.
                </div>
                
                <div className="space-y-1.5">
                  <label htmlFor="reason" className="block text-xs font-bold text-slate-700">
                    Cancellation Reason <span className="text-red-500">*</span>
                  </label>
                  <textarea
                    id="reason"
                    rows={3}
                    placeholder="Enter reason for cancelling the reservation..."
                    value={cancelReason}
                    onChange={(e) => setCancelReason(e.target.value)}
                    required
                    className="w-full p-2.5 rounded-lg border border-slate-200 text-xs text-slate-800 focus:outline-none focus:border-blue-500 bg-slate-50 focus:bg-white transition"
                  />
                </div>
              </div>
              
              <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-3">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setShowCancelModal(false)}
                  className="border-slate-200 text-slate-600 hover:bg-slate-50 font-bold"
                >
                  Keep Booking
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  size="sm"
                  className="bg-red-600 hover:bg-red-700 text-white font-bold"
                >
                  Cancel Booking
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
