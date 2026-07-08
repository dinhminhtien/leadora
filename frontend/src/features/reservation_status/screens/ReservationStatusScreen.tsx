"use client";

import React, { useState, useEffect, useMemo, useCallback } from "react";
import { Search, User, CheckCircle2, XCircle, Info, Calendar, Banknote, ArrowRight, Loader2, AlertCircle, RefreshCw } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { reservationStatusService, type ReservationStatus } from "@/services/reservation_status_service";

export function ReservationStatusScreen() {
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
        alert(res.message || "Failed to fetch reservation detail.");
      }
    } catch (err: any) {
      alert(err.response?.data?.message || err.message || "Failed to fetch reservation detail.");
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
      } else {
        alert(res.message || `Failed to update status to ${newStatus}.`);
      }
    } catch (err: any) {
      alert(err.response?.data?.message || err.message || "Failed to update reservation status.");
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
      } else {
        alert(res.message || "Failed to cancel reservation.");
      }
    } catch (err: any) {
      alert(err.response?.data?.message || err.message || "Failed to cancel reservation.");
    } finally {
      setActionLoading(null);
      setCancelId(null);
    }
  };

  return (
    <div className="space-y-6" style={{ scrollbarGutter: "stable" }}>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Lodging & Rooms Status</h1>
          <p className="text-xs text-slate-400">Track current room occupancy, check-in dates, and guest bookings</p>
        </div>
        <div className="flex items-center gap-3 self-start sm:self-auto">
          <Button variant="outline" size="sm" onClick={fetchList} className="flex items-center gap-1.5 text-xs text-slate-600 bg-white h-9">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Badge variant="primary" className="text-xs h-9 px-3 flex items-center justify-center font-bold uppercase bg-blue-100 text-blue-800 rounded-lg">
            PMS Live Sync
          </Badge>
        </div>
      </div>

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

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <div className="w-full overflow-x-auto">
          <Table className="w-full table-fixed min-w-[1200px]">
            <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
              <TableRow hoverable={false}>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[18%] text-left! whitespace-nowrap">Guest Name</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[11%] text-center! whitespace-nowrap">Reservation Ref</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[13%] text-left! whitespace-nowrap">Room Type</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[17%] text-center! whitespace-nowrap">Check-in / Check-out</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[10%] text-center! whitespace-nowrap">Total Amount</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[11%] text-center! whitespace-nowrap">Occupancy Status</TableHead>
                <TableHead className="px-4! py-3! font-semibold! text-xs! text-slate-500! w-[20%] text-center! whitespace-nowrap">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow hoverable={false}>
                  <TableCell colSpan={7} className="py-12 text-center text-slate-400 text-xs">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Loader2 className="size-6 text-blue-500 animate-spin" />
                      <span>Loading reservations...</span>
                    </div>
                  </TableCell>
                </TableRow>
              ) : reservations.length > 0 ? (
                reservations.map((res) => (
                  <TableRow key={res.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                    <TableCell className="py-3! px-4! text-xs! font-bold! text-slate-800! text-left! whitespace-nowrap">
                      <span className="flex items-center gap-1.5">
                        <User className="size-3.5 text-slate-400" />
                        {res.guestName}
                      </span>
                    </TableCell>
                    <TableCell className="py-3! px-4! text-xs! font-bold! text-slate-700! text-center! whitespace-nowrap">{res.reservationNo}</TableCell>
                    <TableCell className="py-3! px-4! text-xs! text-slate-600! text-left! whitespace-nowrap">{res.roomType}</TableCell>
                    <TableCell className="py-3! px-4! text-xs! text-slate-500! text-center! whitespace-nowrap">
                      <span className="flex items-center justify-center gap-1">
                        <Calendar className="size-3 text-slate-400" />
                        {res.checkInDate}
                        <ArrowRight className="size-3 text-slate-400" />
                        {res.checkOutDate}
                      </span>
                    </TableCell>
                    <TableCell className="py-3! px-4! text-xs! font-bold! text-slate-700! text-center! whitespace-nowrap">
                      {res.totalAmount?.toLocaleString("vi-VN")} ₫
                    </TableCell>
                    <TableCell className="py-3! px-4! text-center! whitespace-nowrap">
                      <div className="flex justify-center">
                        <Badge
                          variant={
                            res.status === "CHECKED_IN"
                              ? "success"
                              : res.status === "CONFIRMED"
                              ? "primary"
                              : res.status === "CHECKED_OUT"
                              ? "default"
                              : res.status === "CANCELLED"
                              ? "danger"
                              : "default"
                          }
                          size="sm"
                          className="font-bold text-[9px] uppercase min-w-[90px] justify-center text-center py-1"
                        >
                          {res.status}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="py-3! px-4! text-center! whitespace-nowrap">
                      <div className="flex items-center justify-center gap-1.5">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleOpenDetail(res.id)}
                        className="p-1 px-2.5 text-[10px] font-bold border-slate-200 text-slate-600 hover:bg-slate-50 h-7"
                      >
                        <Info className="size-3 mr-1" />
                        Detail
                      </Button>

                      {actionLoading === res.id ? (
                        <Button disabled size="sm" className="p-1 px-2.5 text-[10px] h-7">
                          <Loader2 className="size-3 animate-spin mr-1" />
                          Processing
                        </Button>
                      ) : (
                        <>
                          {res.status === "CONFIRMED" && (
                            <Button
                              variant="primary"
                              size="sm"
                              onClick={() => handleStatusChange({ id: res.id, newStatus: "CHECKED_IN", reason: "Guest Checked In" })}
                              className="p-1 px-2.5 text-[10px] font-bold bg-blue-600 hover:bg-blue-700 text-white h-7"
                            >
                              Check In
                            </Button>
                          )}
                          {res.status === "CHECKED_IN" && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleStatusChange({ id: res.id, newStatus: "CHECKED_OUT", reason: "Guest Checked Out" })}
                              className="p-1 px-2.5 text-[10px] font-bold border-slate-200 text-emerald-600 hover:bg-emerald-50 hover:border-emerald-200 h-7"
                            >
                              Check Out
                            </Button>
                          )}
                          {res.status !== "CANCELLED" && res.status !== "CHECKED_OUT" && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenCancel(res.id)}
                              className="p-1 px-2.5 text-[10px] font-bold border-slate-200 text-red-600 hover:bg-red-50 hover:border-red-200 h-7"
                            >
                              <XCircle className="size-3 mr-1" />
                              Cancel
                            </Button>
                          )}
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No reservations matched your query.
                </TableCell>
              </TableRow>
            )}
            </TableBody>
          </Table>
        </div>

        {/* Pagination Section */}
        {totalPages > 1 && (
          <div className="flex justify-between items-center px-4 py-3 bg-slate-50 border-t border-slate-100 text-xs">
            <span className="text-slate-500">
              Showing page {currentPage + 1} of {totalPages} ({totalElements} elements)
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={currentPage === 0}
                onClick={() => setCurrentPage(currentPage - 1)}
                className="py-1 px-3 border-slate-200"
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={currentPage === totalPages - 1}
                onClick={() => setCurrentPage(currentPage + 1)}
                className="py-1 px-3 border-slate-200"
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {showDetailModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 overflow-y-auto transition-all">
          <div className="bg-white rounded-2xl border border-slate-100 shadow-xl w-full max-w-2xl max-h-[85vh] overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center px-6 py-4 bg-slate-50 border-b border-slate-100">
              <h2 className="font-bold text-slate-800 flex items-center gap-2">
                <Info className="size-4 text-blue-500" />
                Reservation Details
              </h2>
              <button
                onClick={() => setShowDetailModal(false)}
                className="text-slate-400 hover:text-slate-600 text-lg transition"
              >
                &times;
              </button>
            </div>
            
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {detailLoading ? (
                <div className="flex flex-col items-center justify-center py-12 gap-2">
                  <Loader2 className="size-8 text-blue-500 animate-spin" />
                  <span className="text-slate-500 text-xs">Loading detail data...</span>
                </div>
              ) : detailData ? (
                <div className="space-y-6 text-xs text-slate-700">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                      <div className="text-slate-400 mb-1">Reservation Code</div>
                      <div className="font-bold text-slate-800 text-sm">{detailData.reservationNo}</div>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                      <div className="text-slate-400 mb-1">Guest Name</div>
                      <div className="font-bold text-slate-800 text-sm">{detailData.guestName}</div>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                      <div className="text-slate-400 mb-1">Check-in / Check-out Dates</div>
                      <div className="font-bold text-slate-800 flex items-center gap-1.5">
                        <Calendar className="size-3 text-slate-400" />
                        {detailData.checkInDate} <ArrowRight className="size-3 text-slate-400" /> {detailData.checkOutDate}
                      </div>
                    </div>
                    <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                      <div className="text-slate-400 mb-1">Total Amount</div>
                      <div className="font-bold text-blue-600 text-sm flex items-center">
                        <Banknote className="size-3 text-blue-500 mr-1" />
                        {detailData.totalAmount?.toLocaleString("vi-VN")} ₫
                      </div>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <div className="font-bold text-slate-800 text-sm">Status Information</div>
                    <div className="flex items-center gap-2">
                      <Badge
                        variant={
                          detailData.status === "CHECKED_IN"
                            ? "success"
                            : detailData.status === "CONFIRMED"
                            ? "primary"
                            : detailData.status === "CHECKED_OUT"
                            ? "default"
                            : detailData.status === "CANCELLED"
                            ? "danger"
                            : "default"
                        }
                        size="sm"
                        className="font-bold uppercase text-[9px] min-w-[90px] justify-center text-center py-1"
                      >
                        {detailData.status}
                      </Badge>
                      {detailData.rejectionReason && (
                        <span className="text-red-500 font-semibold italic">Rejection Reason: {detailData.rejectionReason}</span>
                      )}
                    </div>
                  </div>

                  {detailData.specialRequests && (
                    <div className="space-y-1.5 p-3.5 bg-yellow-50/60 rounded-xl border border-yellow-100/60">
                      <div className="font-bold text-yellow-800">Special Requests & Notes</div>
                      <div className="text-slate-600 whitespace-pre-wrap">{detailData.specialRequests}</div>
                    </div>
                  )}

                  {/* Room Allocation Items */}
                  <div className="space-y-2">
                    <div className="font-bold text-slate-800 text-sm">Room Types & Allocations</div>
                    <div className="border border-slate-100 rounded-xl overflow-hidden shadow-sm">
                      <table className="w-full text-left border-collapse">
                        <thead>
                          <tr className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-100">
                            <th className="p-3 text-[10px] uppercase">Room Type</th>
                            <th className="p-3 text-[10px] uppercase">Room No</th>
                            <th className="p-3 text-[10px] uppercase">Qty</th>
                            <th className="p-3 text-[10px] uppercase">Nights</th>
                            <th className="p-3 text-[10px] uppercase">Price/Night</th>
                            <th className="p-3 text-[10px] uppercase">Alloc. Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detailData.details && detailData.details.length > 0 ? (
                            detailData.details.map((det) => (
                              <tr key={det.bookingDetailId} className="border-b border-slate-50 hover:bg-slate-50/50">
                                <td className="p-3 font-semibold text-slate-800">{det.productName}</td>
                                <td className="p-3 font-mono text-slate-600">{det.roomNumber || "Unassigned"}</td>
                                <td className="p-3 text-slate-500">{det.quantity}</td>
                                <td className="p-3 text-slate-500">{det.nights}</td>
                                <td className="p-3 text-slate-600">{det.unitPrice?.toLocaleString("vi-VN")} ₫</td>
                                <td className="p-3">
                                  <Badge
                                    variant={
                                      det.inventoryStatus === "CHECKED_IN"
                                        ? "success"
                                        : det.inventoryStatus === "ALLOCATED"
                                        ? "primary"
                                        : det.inventoryStatus === "RELEASED"
                                        ? "danger"
                                        : "default"
                                    }
                                    size="sm"
                                    className="font-bold text-[8px] uppercase min-w-[90px] justify-center text-center py-0.5"
                                  >
                                    {det.inventoryStatus}
                                  </Badge>
                                </td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={6} className="p-4 text-center text-slate-400">
                                No details found for this reservation.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="text-center py-12 text-slate-400">No data available.</div>
              )}
            </div>
            
            <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-3">
              {detailData && detailData.status === "CONFIRMED" && (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => handleStatusChange({ id: detailData.id, newStatus: "CHECKED_IN", reason: "Guest Checked In" })}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-bold"
                >
                  Check In Guest
                </Button>
              )}
              {detailData && detailData.status === "CHECKED_IN" && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleStatusChange({ id: detailData.id, newStatus: "CHECKED_OUT", reason: "Guest Checked Out" })}
                  className="border-slate-200 text-emerald-600 hover:bg-emerald-50 font-bold"
                >
                  Check Out Guest
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowDetailModal(false)}
                className="border-slate-200 text-slate-600 hover:bg-slate-50 font-bold"
              >
                Close
              </Button>
            </div>
          </div>
        </div>
      )}

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
