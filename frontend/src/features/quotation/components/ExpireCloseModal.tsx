"use client";

import React, { useState } from "react";
import {
  X,
  Archive,
  AlertCircle,
  CheckCircle2,
  XCircle,
  ShieldAlert,
  RefreshCw,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import type { Quotation } from "@/services/quotation_service";
import { useCloseQuotation } from "@/features/quotation/hooks/use_quotations";
import { useAuthStore } from "@/stores/auth_store";
import { Portal } from "@/components/ui/Portal";


interface ExpireCloseModalProps {
  quote: Quotation;
  onClose: () => void;
  onClosed: (quotationId: string, reason: string) => void;
}

const CLOSE_REASONS = [
  "Customer lost interest",
  "Budget constraints",
  "Went with competitor",
  "Deal no longer viable",
  "Duplicate quotation",
  "Customer request",
  "Sales strategy change",
  "Other (see notes)",
];

const CLOSEABLE_STATUSES: Quotation["status"][] = [
  "draft",
  "sent",
  "pending_approval",
  "pending_revision",
  "interested",
  "rejected",
];

const STATUS_LABEL: Partial<Record<Quotation["status"], string>> = {
  draft: "Draft",
  sent: "Sent",
  pending_approval: "Pending Approval",
  pending_revision: "Needs Revision",
  interested: "Interested",
  rejected: "Rejected",
};

export function ExpireCloseModal({ quote, onClose, onClosed }: ExpireCloseModalProps) {
  const { user } = useAuthStore();
  const closeQuotation = useCloseQuotation();

  const [reason, setReason] = useState("");
  const [customNotes, setCustomNotes] = useState("");
  const [simulateFailure, setSimulateFailure] = useState(false);
  const [failureTriggered, setFailureTriggered] = useState(false);
  const [e3Error, setE3Error] = useState<string | null>(null);
  const [e4Error, setE4Error] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const isEligible = CLOSEABLE_STATUSES.includes(quote.status);
  const isAlreadyConverted = quote.status === "converted";

  const handleClose = async () => {
    setE3Error(null);
    setE4Error(null);
    setApiError(null);

    // E3: already converted
    if (isAlreadyConverted || !isEligible) {
      setE3Error(
        `Quotation ${quote.quoteNo} has already been ${
          quote.status === "converted" ? "converted to a booking" : `closed (${quote.status})`
        } and cannot be closed again.`
      );
      return;
    }

    // Reason required
    if (!reason) {
      setE4Error("Please select a closure reason before confirming.");
      return;
    }

    // E4: simulated system failure — first attempt only
    if (simulateFailure && !failureTriggered) {
      setFailureTriggered(true);
      setE4Error(
        "[E4] System failure occurred while updating quotation status. The record has not been changed. Please retry the operation."
      );
      return;
    }

    const finalReason = reason === "Other (see notes)" && customNotes.trim()
      ? customNotes.trim()
      : reason;

    try {
      await closeQuotation.mutateAsync({
        id: quote.id,
        payload: {
          reason: finalReason,
          notes: customNotes.trim() || undefined,
          closedByName: user?.name ?? user?.email ?? "Staff",
          closedByRole: user?.roles?.[0] ?? "SALES_STAFF",
        },
      });
      setSuccess(true);
      setTimeout(() => onClosed(quote.id, finalReason), 1200);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to close quotation. Please try again.";
      setApiError(msg);
    }
  };

  return (
    <Portal>
      <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" onClick={onClose} />

      <div className="relative z-10 w-full max-w-md mx-4 bg-white rounded-2xl shadow-xl border border-slate-100">
        {/* Header */}
        <div className="flex items-start justify-between px-5 pt-5 pb-4 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center">
              <Archive className="size-4 text-slate-600" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Close Quotation</h2>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {quote.quoteNo} · {quote.contactName}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition"
          >
            <X className="size-4" />
          </button>
        </div>

        {success ? (
          <div className="py-10 flex flex-col items-center gap-3">
            <CheckCircle2 className="size-10 text-slate-500" />
            <p className="text-sm font-bold text-slate-700">Quotation closed</p>
            <p className="text-[10px] text-slate-400">
              {quote.quoteNo} · Audit log updated · Reminders resolved
            </p>
          </div>
        ) : (
          <div className="px-5 py-4 space-y-4">
            {/* Quotation summary */}
            <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
              <div>
                <p className="text-xs font-bold text-slate-800">{quote.dealName}</p>
                <p className="text-[10px] text-slate-500 mt-0.5">
                  {quote.contactName} · Valid until {quote.expiryDate}
                </p>
              </div>
              <div className="text-right space-y-1">
                <Badge variant="default" size="sm" className="font-bold text-[9px] bg-slate-200 text-slate-600">
                  {STATUS_LABEL[quote.status] ?? quote.status}
                </Badge>
                <p className="text-xs font-black text-slate-700">{quote.amount.toLocaleString("vi-VN")} ₫</p>
              </div>
            </div>

            {/* E3: not closeable */}
            {(!isEligible || isAlreadyConverted) && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                <XCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-red-600">
                  [E3] This quotation cannot be closed — it has already been{" "}
                  <strong>{quote.status}</strong>. Return to the list and select a different quotation.
                </p>
              </div>
            )}

            {isEligible && (
              <>
                {/* Warning */}
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
                  <AlertCircle className="size-3.5 text-amber-500 mt-0.5 shrink-0" />
                  <p className="text-[10px] text-amber-700 font-semibold">
                    This action will permanently close the quotation. All pending SLA tasks and reminders linked to this deal will be resolved.
                  </p>
                </div>

                {/* Closure reason */}
                <div>
                  <label className="block text-xs font-semibold text-slate-600 mb-1.5">
                    Closure Reason <span className="text-red-400">*</span>
                  </label>
                  <select
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:border-slate-400 transition"
                  >
                    <option value="">-- Select a reason --</option>
                    {CLOSE_REASONS.map((r) => (
                      <option key={r} value={r}>{r}</option>
                    ))}
                  </select>
                </div>

                {reason === "Other (see notes)" && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 mb-1">
                      Custom Reason
                    </label>
                    <textarea
                      value={customNotes}
                      onChange={(e) => setCustomNotes(e.target.value)}
                      rows={2}
                      placeholder="Describe the reason for closing..."
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-slate-400 resize-none transition"
                    />
                  </div>
                )}

                {/* E4 simulation toggle */}
                <label className="flex items-center gap-2 cursor-pointer select-none w-fit">
                  <input
                    type="checkbox"
                    checked={simulateFailure}
                    onChange={(e) => setSimulateFailure(e.target.checked)}
                    className="rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-[10px] text-slate-500 font-semibold">
                    Simulate: System failure on first attempt (E4 test)
                  </span>
                </label>

                {/* Error banners */}
                {e4Error && (
                  <div className="flex items-start gap-2 rounded-lg border border-orange-200 bg-orange-50 px-3 py-2.5">
                    <ShieldAlert className="size-3.5 text-orange-500 mt-0.5 shrink-0" />
                    <div className="flex-1">
                      <p className="text-[10px] font-semibold text-orange-700">{e4Error}</p>
                      {failureTriggered && (
                        <button
                          onClick={() => setE4Error(null)}
                          className="mt-1 flex items-center gap-1 text-[10px] font-bold text-orange-600 hover:text-orange-800"
                        >
                          <RefreshCw className="size-2.5" /> Retry now
                        </button>
                      )}
                    </div>
                  </div>
                )}
                {e3Error && (
                  <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                    <XCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                    <p className="text-[10px] font-semibold text-red-600">{e3Error}</p>
                  </div>
                )}
                {apiError && (
                  <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                    <AlertCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                    <p className="text-[10px] font-semibold text-red-600">{apiError}</p>
                  </div>
                )}

                <div className="flex gap-2 pt-1">
                  <Button
                    variant="outline"
                    className="flex-1 text-xs font-bold border-red-200 text-red-600 hover:bg-red-50"
                    onClick={handleClose}
                    isLoading={closeQuotation.isPending}
                    leftIcon={<Archive className="size-3.5" />}
                  >
                    Confirm Close
                  </Button>
                  <Button
                    variant="outline"
                    className="flex-1 text-xs border-slate-200 text-slate-600"
                    onClick={onClose}
                    disabled={closeQuotation.isPending}
                  >
                    Cancel
                  </Button>
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </div>
    </Portal>
  );
}
