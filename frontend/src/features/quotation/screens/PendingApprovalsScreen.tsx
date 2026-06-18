"use client";

import React, { useState, useMemo } from "react";
import {
  Search,
  CheckCircle2,
  XCircle,
  RotateCcw,
  AlertTriangle,
  ClipboardCheck,
  X,
  History,
  FileSpreadsheet,
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type { Quotation } from "@/services/quotation_service";

type ApprovalHistory = {
  id: string;
  quotationId: string;
  quoteNo: string;
  decision: Decision;
  decidedBy: string;
  role: string;
  decidedAt: string;
  notes: string;
  previousStatus: string;
};

type Decision = "approved" | "rejected" | "pending_revision";

const PAYMENT_LABELS: Record<string, string> = {
  full_upfront: "Full Payment Upfront",
  "50_deposit": "50% Deposit on Booking",
  pay_on_arrival: "Pay on Arrival",
};

// ── Approval Modal ─────────────────────────────────────────────────────────

function ApprovalModal({
  quote,
  onClose,
  onDecide,
}: {
  quote: Quotation;
  onClose: () => void;
  onDecide: (decision: Decision, notes: string) => void;
}) {
  const [notes, setNotes] = useState("");
  const [fieldError, setFieldError] = useState("");

  const isDataMissing = !quote.roomType || !quote.checkInDate || !quote.checkOutDate;

  const handleDecide = (decision: Decision) => {
    if ((decision === "rejected" || decision === "pending_revision") && !notes.trim()) {
      setFieldError("Please add a note explaining your decision.");
      return;
    }
    setFieldError("");
    onDecide(decision, notes.trim());
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white rounded-xl shadow-2xl">

        {/* Modal Header */}
        <div className="sticky top-0 flex items-center justify-between px-5 py-4 border-b border-slate-100 bg-white rounded-t-xl">
          <div>
            <h2 className="text-sm font-bold text-slate-800">Review Quotation</h2>
            <p className="text-xs text-blue-600 font-semibold flex items-center gap-1 mt-0.5">
              <FileSpreadsheet className="size-3.5" />
              {quote.quoteNo}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {/* E4: Missing Data Warning */}
          {isDataMissing && (
            <div className="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
              <AlertTriangle className="size-4 text-amber-500 mt-0.5 shrink-0" />
              <div>
                <p className="text-xs font-bold text-amber-700">Incomplete Quotation Data (E4)</p>
                <p className="text-xs text-amber-600 mt-0.5">
                  Room type or date information is missing. Approval is disabled until data is complete. You may Reject or Request Changes.
                </p>
              </div>
            </div>
          )}

          {/* Customer Info */}
          <section className="rounded-lg border border-slate-100 bg-slate-50/60 p-4">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2.5">Customer Information</p>
            <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs">
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[52px]">Contact:</span>
                <span className="font-semibold text-slate-700">{quote.contactName}</span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[52px]">Deal:</span>
                <span className="font-semibold text-slate-700">{quote.dealName}</span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[52px]">Email:</span>
                <span className="font-semibold text-slate-700">{quote.email ?? "—"}</span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[52px]">Phone:</span>
                <span className="font-semibold text-slate-700">{quote.phone ?? "—"}</span>
              </div>
            </div>
          </section>

          {/* Room Details */}
          <section className="rounded-lg border border-slate-100 bg-slate-50/60 p-4">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2.5">Room Details</p>
            <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs">
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Room Type:</span>
                <span className={`font-semibold ${!quote.roomType ? "text-red-400 italic" : "text-slate-700"}`}>
                  {quote.roomType ?? "Missing"}
                </span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Rooms:</span>
                <span className="font-semibold text-slate-700">{quote.numberOfRooms ?? "—"}</span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Check-in:</span>
                <span className={`font-semibold ${!quote.checkInDate ? "text-red-400 italic" : "text-slate-700"}`}>
                  {quote.checkInDate ?? "Missing"}
                </span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Check-out:</span>
                <span className={`font-semibold ${!quote.checkOutDate ? "text-red-400 italic" : "text-slate-700"}`}>
                  {quote.checkOutDate ?? "Missing"}
                </span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Rate/Night:</span>
                <span className="font-semibold text-slate-700">
                  {quote.pricePerNight ? `$${quote.pricePerNight.toLocaleString('en-US')}` : "—"}
                </span>
              </div>
              <div className="flex gap-1">
                <span className="text-slate-400 min-w-[72px]">Payment:</span>
                <span className="font-semibold text-slate-700">
                  {quote.paymentPolicy ? (PAYMENT_LABELS[quote.paymentPolicy] ?? quote.paymentPolicy) : "—"}
                </span>
              </div>
            </div>
          </section>

          {/* Pricing Summary */}
          <section className="rounded-lg border border-slate-100 bg-slate-50/60 p-4">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2.5">Pricing Summary</p>
            <div className="space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-500">Subtotal</span>
                <span className="font-semibold text-slate-700">${(quote.subtotal ?? 0).toLocaleString('en-US')}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="flex items-center gap-1.5 text-amber-600">
                  <AlertTriangle className="size-3" />
                  Discount ({quote.discountPercent ?? 0}%)
                  {(quote.discountPercent ?? 0) > 10 && (
                    <span className="text-[9px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-bold">
                      Exceeds 10% Authority
                    </span>
                  )}
                </span>
                <span className="font-semibold text-amber-600">-${(quote.discountAmount ?? 0).toLocaleString('en-US')}</span>
              </div>
              <div className="flex justify-between border-t border-slate-200 pt-2">
                <span className="font-bold text-slate-700">Total Amount</span>
                <span className="font-black text-slate-900 text-sm">${quote.amount.toLocaleString('en-US')}</span>
              </div>
              <div className="flex justify-between text-slate-400 pt-1">
                <span>Valid Until</span>
                <span className="font-semibold">{quote.expiryDate}</span>
              </div>
            </div>
          </section>

          {/* Staff Notes */}
          {quote.notes && (
            <section className="rounded-lg border border-blue-100 bg-blue-50/40 p-4">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Notes from Sales Staff</p>
              <p className="text-xs text-slate-600 leading-relaxed">{quote.notes}</p>
            </section>
          )}

          {/* Manager Decision Notes */}
          <section className="rounded-lg border border-slate-200 p-4">
            <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">
              Manager Decision Notes
              <span className="ml-1 text-red-400">*</span>
              <span className="text-slate-400 normal-case font-normal ml-1">(required for Reject or Request Changes)</span>
            </p>
            <textarea
              value={notes}
              onChange={(e) => {
                setNotes(e.target.value);
                setFieldError("");
              }}
              rows={3}
              placeholder="Describe your decision rationale, conditions, or required changes..."
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-500 resize-none transition placeholder-slate-400"
            />
            {fieldError && (
              <p className="mt-1 text-[10px] font-semibold text-red-500 flex items-center gap-1">
                <XCircle className="size-3" />
                {fieldError}
              </p>
            )}
          </section>

          {/* Action Buttons */}
          <div className="flex items-center gap-2 pt-1">
            <Button
              variant="success"
              onClick={() => handleDecide("approved")}
              disabled={isDataMissing}
              leftIcon={<CheckCircle2 className="size-3.5" />}
              className="flex-1 text-xs font-bold bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Approve
            </Button>
            <Button
              variant="outline"
              onClick={() => handleDecide("pending_revision")}
              leftIcon={<RotateCcw className="size-3.5" />}
              className="flex-1 text-xs font-bold border-amber-300 text-amber-700 hover:bg-amber-50"
            >
              Request Changes
            </Button>
            <Button
              variant="danger"
              onClick={() => handleDecide("rejected")}
              leftIcon={<XCircle className="size-3.5" />}
              className="flex-1 text-xs font-bold"
            >
              Reject
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main Screen ────────────────────────────────────────────────────────────

export function PendingApprovalsScreen() {
  const [pending, setPending] = useState<Quotation[]>([]);
  const [history, setHistory] = useState<ApprovalHistory[]>([]);
  const [selectedQuote, setSelectedQuote] = useState<Quotation | null>(null);
  const [search, setSearch] = useState("");
  const [e3Error, setE3Error] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  const filtered = useMemo(
    () =>
      pending.filter(
        (q) =>
          q.quoteNo.toLowerCase().includes(search.toLowerCase()) ||
          q.contactName.toLowerCase().includes(search.toLowerCase()) ||
          (q.dealName && q.dealName.toLowerCase().includes(search.toLowerCase()))
      ),
    [pending, search]
  );

  const handleReview = (quote: Quotation) => {
    const current = pending.find((q) => q.id === quote.id);
    if (!current || current.status !== "pending_approval") {
      setE3Error(`Quotation ${quote.quoteNo} has already been processed.`);
      setPending(prev => prev.filter((q) => q.status === "pending_approval"));
      return;
    }
    setE3Error("");
    setSuccessMsg("");
    setSelectedQuote(quote);
  };

  const handleDecide = (decision: Decision, notes: string) => {
    if (!selectedQuote) return;

    const current = pending.find((q) => q.id === selectedQuote.id);
    if (!current || current.status !== "pending_approval") {
      setE3Error(`Quotation ${selectedQuote.quoteNo} was already processed by another manager.`);
      setSelectedQuote(null);
      setPending(prev => prev.filter((q) => q.status === "pending_approval"));
      return;
    }

    const historyEntry: ApprovalHistory = {
      id: `APH-${history.length + 1}`,
      quotationId: selectedQuote.id,
      quoteNo: selectedQuote.quoteNo,
      decision,
      decidedBy: "John Doe",
      role: "MANAGER",
      decidedAt: new Date().toISOString(),
      notes,
      previousStatus: "pending_approval",
    };

    setPending((prev) => prev.filter((q) => q.id !== selectedQuote.id));
    setHistory((prev) => [historyEntry, ...prev]);
    setSelectedQuote(null);

    const decisionBadge =
      decision === "approved" ? "Approved" : decision === "rejected" ? "Rejected" : "Sent back for revision";
    setSuccessMsg(`${selectedQuote.quoteNo} — ${decisionBadge} successfully. Sales Staff has been notified.`);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Pending Approvals</h1>
          <p className="text-xs text-slate-400">
            Review quotations with discount authority exceptions. Approve, reject, or request changes.
          </p>
        </div>
        <Badge
          variant={pending.length > 0 ? "warning" : "success"}
          className="text-xs px-3 py-1 font-bold self-start sm:self-auto"
        >
          {pending.length} Awaiting Review
        </Badge>
      </div>

      {/* E3: Already-processed error */}
      {e3Error && (
        <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <AlertTriangle className="size-4 text-red-500 shrink-0" />
          <p className="text-xs font-semibold text-red-700 flex-1">{e3Error}</p>
          <button onClick={() => setE3Error("")} className="text-red-400 hover:text-red-600 transition">
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {/* Success message */}
      {successMsg && (
        <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3">
          <CheckCircle2 className="size-4 text-emerald-500 shrink-0" />
          <p className="text-xs font-semibold text-emerald-700 flex-1">{successMsg}</p>
          <button onClick={() => setSuccessMsg("")} className="text-emerald-400 hover:text-emerald-600 transition">
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {/* Pending List */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardHeader>
          <CardTitle className="text-sm font-bold text-slate-700">Quotations Requiring Approval</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="relative w-full md:w-72 mb-4">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search quote #, contact, deal..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>

          {filtered.length === 0 ? (
            <div className="py-14 text-center">
              <ClipboardCheck className="size-10 text-emerald-300 mx-auto mb-2.5" />
              <p className="text-sm font-semibold text-slate-400">
                {search ? "No matching quotations found." : "All clear — no pending approvals!"}
              </p>
              <p className="text-xs text-slate-300 mt-0.5">
                New submissions will appear here automatically.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader className="bg-slate-50 border-b border-slate-100">
                <TableRow hoverable={false}>
                  <TableHead className="text-xs font-semibold text-slate-500">Quote No</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Contact / Deal</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Room Type</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500 text-right">Discount %</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500 text-right">Total</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Submitted</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500 text-center">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((q) => (
                  <TableRow key={q.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                    <TableCell className="py-3 px-4 text-xs font-bold text-blue-600">
                      <span className="flex items-center gap-1.5">
                        <FileSpreadsheet className="size-3.5 text-slate-400" />
                        {q.quoteNo}
                      </span>
                    </TableCell>
                    <TableCell className="py-3 px-4">
                      <p className="text-xs font-bold text-slate-700">{q.contactName}</p>
                      <p className="text-[10px] text-slate-400">{q.dealName}</p>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-slate-500">
                      {q.roomType ? (
                        q.roomType
                      ) : (
                        <span className="text-amber-500 flex items-center gap-1 font-semibold">
                          <AlertTriangle className="size-3" /> Missing
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-right">
                      <span className="font-black text-amber-600">{q.discountPercent ?? 0}%</span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-right font-black text-slate-800">
                      ${q.amount.toLocaleString('en-US')}
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-slate-400">{(q as { sentDate?: string }).sentDate || q.expiryDate || "—"}</TableCell>
                    <TableCell className="py-3 px-4 text-center">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleReview(q)}
                        className="text-xs border-blue-200 text-blue-600 hover:bg-blue-50 font-semibold"
                      >
                        Review
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Approval History */}
      {history.length > 0 && (
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-700 flex items-center gap-2">
              <History className="size-4 text-slate-400" />
              Approval History
              <span className="text-xs font-normal text-slate-400">
                ({history.length} decision{history.length !== 1 ? "s" : ""})
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent className="px-2">
            <Table>
              <TableHeader className="bg-slate-50 border-b border-slate-100">
                <TableRow hoverable={false}>
                  <TableHead className="text-xs font-semibold text-slate-500">Quote No</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Decision</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Decided By</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Notes</TableHead>
                  <TableHead className="text-xs font-semibold text-slate-500">Date / Time</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {history.map((h) => (
                  <TableRow key={h.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                    <TableCell className="py-3 px-4 text-xs font-bold text-blue-600">{h.quoteNo}</TableCell>
                    <TableCell className="py-3 px-4">
                      <Badge
                        variant={
                          h.decision === "approved"
                            ? "success"
                            : h.decision === "rejected"
                            ? "danger"
                            : "warning"
                        }
                        size="sm"
                        className="font-bold text-[9px] uppercase"
                      >
                        {h.decision === "pending_revision" ? "Needs Revision" : h.decision}
                      </Badge>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs font-semibold text-slate-700">
                      {h.decidedBy}
                      <span className="ml-1 text-[9px] text-slate-400 font-normal">({h.role})</span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-slate-500 max-w-[200px]">
                      <span className="block truncate" title={h.notes}>
                        {h.notes || "—"}
                      </span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-slate-400">
                      {new Date(h.decidedAt).toLocaleString("en-US", {
                        month: "short",
                        day: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Approval Modal */}
      {selectedQuote && (
        <ApprovalModal
          quote={selectedQuote}
          onClose={() => setSelectedQuote(null)}
          onDecide={handleDecide}
        />
      )}
    </div>
  );
}
