"use client";

import React, { useState, useEffect, useMemo } from "react";
import { Star, ThumbsUp, Search, User, Filter, ChevronLeft, ChevronRight, X, CheckCircle, AlertTriangle } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { customerFeedbackService, type CustomerFeedback } from "@/services/customer_feedback_service";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";

export function CustomerFeedbackListScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const isManagerOrAdmin = userRole === "MANAGER" || userRole === "ADMIN";

  const [feedbacks, setFeedbacks] = useState<CustomerFeedback[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Filtering & Pagination state
  const [search, setSearch] = useState("");
  const [ratingFilter, setRatingFilter] = useState<string>("all");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Selected feedback for detail modal
  const [selectedFeedback, setSelectedFeedback] = useState<CustomerFeedback | null>(null);
  const [updatingStatus, setUpdatingStatus] = useState(false);

  // Fetch feedback list
  const fetchFeedbacks = async () => {
    setLoading(true);
    try {
      const paramsObj: any = {
        page,
        size: pageSize,
      };
      if (search.trim()) {
        paramsObj.search = search.trim();
      }
      if (ratingFilter !== "all") {
        paramsObj.rating = Number(ratingFilter);
      }
      if (statusFilter !== "all") {
        paramsObj.reviewStatus = statusFilter;
      }

      const response = await customerFeedbackService.getList(paramsObj);
      if (response.success && response.data) {
        // Handle both standard Page interface and direct wrap
        const data = response.data as any;
        if (data.content) {
          setFeedbacks(data.content);
          setTotalElements(data.totalElements || 0);
          setTotalPages(data.totalPages || 0);
        } else {
          setFeedbacks(data);
          setTotalElements(data.length || 0);
          setTotalPages(1);
        }
      }
    } catch (error) {
      console.error("Error fetching feedbacks:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFeedbacks();
  }, [page, ratingFilter, statusFilter]);

  // Handle search with local debounce or trigger on button/enter
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchFeedbacks();
  };

  // Status transition handler
  const handleUpdateStatus = async (feedbackId: string, newStatus: "REVIEWED" | "DISMISSED") => {
    setUpdatingStatus(true);
    try {
      const response = await customerFeedbackService.updateReviewStatus(feedbackId, newStatus);
      if (response.success) {
        // Refresh detail view if open
        if (selectedFeedback && selectedFeedback.feedbackId === feedbackId) {
          setSelectedFeedback({
            ...selectedFeedback,
            reviewStatus: newStatus,
            reviewedByName: user?.name || "Manager",
            reviewedAt: new Date().toISOString(),
          });
        }
        fetchFeedbacks();
      }
    } catch (error: any) {
      console.error("Error updating feedback review status:", error);
      alert(error.response?.data?.message || "Failed to update review status.");
    } finally {
      setUpdatingStatus(false);
    }
  };

  // NPS & Feedback Statistics (calculated locally based on currently loaded feedbacks or overall summary)
  const stats = useMemo(() => {
    const total = feedbacks.length;
    if (total === 0) {
      return { total: 0, avgRating: "0.0", positivePct: "0" };
    }
    const submitted = feedbacks.filter(f => f.rating > 0);
    const totalSubmitted = submitted.length;
    if (totalSubmitted === 0) {
      return { total, avgRating: "0.0", positivePct: "0" };
    }
    const avg = submitted.reduce((sum, f) => sum + f.rating, 0) / totalSubmitted;
    const positive = submitted.filter(f => f.rating >= 4).length;
    return {
      total: totalElements,
      avgRating: avg.toFixed(1),
      positivePct: ((positive / totalSubmitted) * 100).toFixed(0)
    };
  }, [feedbacks, totalElements]);

  return (
    <div className="space-y-6">
      {/* Title */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Guest Reviews & Feedback</h1>
          <p className="text-xs text-slate-400">Monitor Sales Staff service quality and track customer satisfaction levels</p>
        </div>
        <Badge variant="success" className="text-xs px-2.5 font-bold uppercase bg-emerald-100 text-emerald-800 border-none">
          NPS Target 95%
        </Badge>
      </div>

      {/* Statistics Ribbon */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Total Reviews</span>
          <span className="text-lg font-bold text-slate-800 block mt-1">{stats.total} responses received</span>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-0 md:px-4">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Average Star Rating</span>
          <span className="text-lg font-bold text-slate-800 mt-1 flex items-center gap-1">
            <Star className="size-4 text-amber-400 fill-amber-400" />
            {stats.avgRating} / 5.0
          </span>
        </div>
        <div className="px-0 md:px-4">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Satisfaction Rate (Good/Excellent)</span>
          <span className="text-lg font-bold text-blue-600 mt-1 flex items-center gap-1">
            <ThumbsUp className="size-4 text-blue-500 fill-blue-500" />
            {stats.positivePct}% satisfied guests
          </span>
        </div>
      </div>

      {/* Filters Card */}
      <Card className="border-slate-100 shadow-sm bg-white overflow-visible">
        <CardContent className="py-4 px-4 flex flex-col md:flex-row gap-4 items-end">
          <form onSubmit={handleSearchSubmit} className="flex-1 w-full flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search guest name, booking code, comments..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-slate-200 bg-slate-50 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>
            <Button type="submit" variant="primary" className="py-2 px-4 text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700">
              Search
            </Button>
          </form>

          <div className="flex gap-3 w-full md:w-auto">
            <div className="w-1/2 md:w-40">
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">Star Rating</label>
              <Select
                value={ratingFilter}
                onChange={e => {
                  setRatingFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="all">All stars</option>
                <option value="5">5 Stars</option>
                <option value="4">4 Stars</option>
                <option value="3">3 Stars</option>
                <option value="2">2 Stars</option>
                <option value="1">1 Star</option>
              </Select>
            </div>

            <div className="w-1/2 md:w-44">
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">Review Status</label>
              <Select
                value={statusFilter}
                onChange={e => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="all">All statuses</option>
                <option value="PENDING">Pending review (PENDING)</option>
                <option value="REVIEWED">Reviewed (REVIEWED)</option>
                <option value="DISMISSED">Dismissed (DISMISSED)</option>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Feedbacks Table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-slate-400 text-xs flex flex-col items-center justify-center gap-2">
            <div className="size-8 rounded-full border-2 border-slate-200 border-t-blue-600 animate-spin" />
            Loading feedback data...
          </div>
        ) : (
          <>
            <Table>
              <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
                <TableRow hoverable={false}>
                  <TableHead className="font-bold text-xs text-slate-500">Customer / Booking Code</TableHead>
                  <TableHead className="font-bold text-xs text-slate-500">Assigned Sales Staff</TableHead>
                  <TableHead className="font-bold text-xs text-slate-500 text-center">Rating</TableHead>
                  <TableHead className="font-bold text-xs text-slate-500">Feedback Comment</TableHead>
                  <TableHead className="font-bold text-xs text-slate-500">Review Status</TableHead>
                  <TableHead className="font-bold text-xs text-slate-500 text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {feedbacks.length > 0 ? (
                  feedbacks.map(f => (
                    <TableRow key={f.feedbackId} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                      <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                        <div>{f.customerName}</div>
                        <div className="text-[10px] text-slate-400 font-semibold mt-0.5">{f.bookingCode}</div>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-slate-700 font-semibold">
                        <span className="inline-flex items-center gap-1">
                          <User className="size-3 text-slate-400" />
                          {f.salesStaffName}
                        </span>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-center">
                        {f.rating ? (
                          <div className="inline-flex items-center gap-0.5 text-xs font-bold bg-amber-50 text-amber-700 px-2 py-0.5 rounded-md">
                            <Star className="size-3 text-amber-500 fill-amber-500" />
                            {f.rating}.0
                          </div>
                        ) : (
                          <span className="text-[10px] text-slate-400 italic">Not rated</span>
                        )}
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium max-w-xs truncate">
                        {f.comment ? `"${f.comment}"` : <span className="text-slate-300 italic">No comment</span>}
                      </TableCell>
                      <TableCell className="py-3 px-4">
                        <Badge
                          variant={f.reviewStatus === "REVIEWED" ? "success" : f.reviewStatus === "DISMISSED" ? "default" : "warning"}
                          className="font-bold text-[9px] uppercase border-none"
                        >
                          {f.reviewStatus === "REVIEWED" ? "Reviewed" : f.reviewStatus === "DISMISSED" ? "Dismissed" : "Pending"}
                        </Badge>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-right">
                        <Button
                          variant="ghost"
                          onClick={() => setSelectedFeedback(f)}
                          className="text-xs text-blue-600 hover:text-blue-700 hover:bg-blue-50 py-1 px-2.5 rounded-lg"
                        >
                          Details
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow hoverable={false}>
                    <TableCell colSpan={6} className="py-12 text-center text-slate-400 text-xs">
                      No matching feedback comments found.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="p-4 border-t border-slate-100 flex items-center justify-between bg-slate-50/50">
                <span className="text-xs text-slate-500">
                  Showing <span className="font-semibold">{feedbacks.length}</span> of <span className="font-semibold">{totalElements}</span> reviews
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    disabled={page === 0}
                    onClick={() => setPage(p => p - 1)}
                    className="p-1.5 border border-slate-200 rounded-lg hover:bg-slate-100 text-slate-600 disabled:opacity-50"
                  >
                    <ChevronLeft className="size-4" />
                  </Button>
                  <span className="text-xs font-semibold self-center px-2">
                    Page {page + 1} of {totalPages}
                  </span>
                  <Button
                    variant="ghost"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage(p => p + 1)}
                    className="p-1.5 border border-slate-200 rounded-lg hover:bg-slate-100 text-slate-600 disabled:opacity-50"
                  >
                    <ChevronRight className="size-4" />
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Detail Modal */}
      {selectedFeedback && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-white rounded-2xl w-full max-w-lg shadow-2xl border border-slate-100 flex flex-col max-h-[90vh] overflow-hidden transform scale-100 transition-all duration-300">
            {/* Modal Header */}
            <div className="p-5 border-b border-slate-100 flex justify-between items-start">
              <div>
                <h2 className="text-base font-bold text-slate-800">Service Feedback Details</h2>
                <p className="text-xs text-slate-400 mt-1">Booking Code: <span className="font-semibold text-slate-700">{selectedFeedback.bookingCode}</span></p>
              </div>
              <button
                onClick={() => setSelectedFeedback(null)}
                className="p-1.5 hover:bg-slate-100 rounded-lg text-slate-400 hover:text-slate-600 transition"
              >
                <X className="size-4" />
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 overflow-y-auto space-y-5">
              {/* Customer and Staff summary */}
              <div className="grid grid-cols-2 gap-4 bg-slate-50 p-4 rounded-xl border border-slate-100">
                <div>
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Customer</span>
                  <span className="text-xs font-bold text-slate-800 block mt-1">{selectedFeedback.customerName}</span>
                </div>
                <div>
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Assigned Sales Staff</span>
                  <span className="text-xs font-bold text-slate-800 block mt-1">{selectedFeedback.salesStaffName}</span>
                </div>
              </div>

              {/* Star and Comment */}
              <div className="space-y-2">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Satisfaction Level</span>
                {selectedFeedback.rating ? (
                  <div className="flex gap-1 items-center mt-1">
                    {[1, 2, 3, 4, 5].map(star => (
                      <Star
                        key={star}
                        className={`size-5 ${
                          star <= selectedFeedback.rating
                            ? "text-amber-400 fill-amber-400"
                            : "text-slate-200"
                        }`}
                      />
                    ))}
                    <span className="text-xs font-bold text-slate-700 ml-1.5">{selectedFeedback.rating}.0 / 5.0</span>
                  </div>
                ) : (
                  <span className="text-xs text-slate-400 italic">No evaluation submitted yet</span>
                )}
              </div>

              {/* Feedback Comment */}
              <div className="space-y-1.5">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Comments from Guest</span>
                <div className="bg-slate-50/50 border border-slate-150 p-4 rounded-xl text-xs text-slate-700 font-medium italic leading-relaxed">
                  {selectedFeedback.comment ? `"${selectedFeedback.comment}"` : <span className="text-slate-350 italic">No detailed comments provided.</span>}
                </div>
              </div>

              {/* Review Audit History */}
              {selectedFeedback.reviewStatus !== "PENDING" && (
                <div className="pt-2 border-t border-slate-100 space-y-2">
                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Review Audit Trail</span>
                  <div className="flex items-center gap-2 text-xs">
                    {selectedFeedback.reviewStatus === "REVIEWED" ? (
                      <CheckCircle className="size-4 text-emerald-500" />
                    ) : (
                      <AlertTriangle className="size-4 text-slate-400" />
                    )}
                    <span className="text-slate-700 font-semibold">
                      {selectedFeedback.reviewStatus === "REVIEWED" ? "Reviewed" : "Dismissed"} by{" "}
                      <span className="font-bold">{selectedFeedback.reviewedByName || "Manager"}</span>
                    </span>
                    {selectedFeedback.reviewedAt && (
                      <span className="text-slate-400 text-[11px]">
                        on {new Date(selectedFeedback.reviewedAt).toLocaleDateString()}
                      </span>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer / Actions */}
            <div className="p-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-3">
              <Button
                variant="ghost"
                onClick={() => setSelectedFeedback(null)}
                className="text-xs border border-slate-200 bg-white hover:bg-slate-50 py-1.5 px-4 font-semibold rounded-xl text-slate-600"
              >
                Close
              </Button>
              {isManagerOrAdmin && selectedFeedback.reviewStatus === "PENDING" && selectedFeedback.rating > 0 && (
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    disabled={updatingStatus}
                    onClick={() => handleUpdateStatus(selectedFeedback.feedbackId, "DISMISSED")}
                    className="text-xs border border-slate-200 bg-white hover:bg-slate-50 py-1.5 px-4 font-semibold rounded-xl text-slate-500 hover:text-slate-700"
                  >
                    Dismiss
                  </Button>
                  <Button
                    variant="primary"
                    disabled={updatingStatus}
                    onClick={() => handleUpdateStatus(selectedFeedback.feedbackId, "REVIEWED")}
                    className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white py-1.5 px-4 font-semibold rounded-xl"
                  >
                    Approve
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
