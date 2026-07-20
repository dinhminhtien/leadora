"use client";

import React, { useState } from "react";
import { CheckCircle2, XCircle, Sparkles, RotateCcw, AlertCircle, X, MessageSquare } from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { Quotation } from "@/services/quotation_service";
import { useTrackCustomerResponse } from "@/features/quotation/hooks/use_quotations";
import { useAuthStore } from "@/stores/auth_store";
import { Portal } from "@/components/ui/Portal";


type ResponseType = "accepted" | "rejected" | "interested" | "need_revision";

interface RecordResponseModalProps {
  quote: Quotation;
  onClose: () => void;
  onRecorded: (quotationId: string, newStatus: Quotation["status"]) => void;
}

const RESPONSE_OPTIONS: {
  value: ResponseType;
  label: string;
  description: string;
  icon: React.ReactNode;
  selectedCls: string;
  iconCls: string;
}[] = [
  {
    value: "accepted",
    label: "Accepted",
    description: "Customer agrees to the terms",
    icon: <CheckCircle2 className="size-5" />,
    selectedCls: "border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400",
    iconCls: "text-emerald-500",
  },
  {
    value: "rejected",
    label: "Rejected",
    description: "Customer declined the offer",
    icon: <XCircle className="size-5" />,
    selectedCls: "border-red-400 bg-red-50 ring-1 ring-red-400",
    iconCls: "text-red-500",
  },
  {
    value: "interested",
    label: "Interested",
    description: "Positive but not yet committed",
    icon: <Sparkles className="size-5" />,
    selectedCls: "border-blue-500 bg-blue-50 ring-1 ring-blue-400",
    iconCls: "text-blue-500",
  },
  {
    value: "need_revision",
    label: "Need Revision",
    description: "Customer requests changes",
    icon: <RotateCcw className="size-5" />,
    selectedCls: "border-amber-500 bg-amber-50 ring-1 ring-amber-400",
    iconCls: "text-amber-500",
  },
];

const LOST_REASONS = [
  "Price too high",
  "Went with competitor",
  "Budget cut / no budget",
  "Project cancelled or postponed",
  "Timing not right",
  "No response / lost contact",
  "Other (see notes)",
];

const RESPONSE_API_MAP: Record<ResponseType, "ACCEPTED" | "REJECTED" | "INTERESTED" | "NEED_REVISION"> = {
  accepted: "ACCEPTED",
  rejected: "REJECTED",
  interested: "INTERESTED",
  need_revision: "NEED_REVISION",
};

const STATUS_MAP: Record<ResponseType, Quotation["status"]> = {
  accepted: "accepted",
  rejected: "rejected",
  interested: "interested",
  need_revision: "pending_revision",
};

export function RecordResponseModal({ quote, onClose, onRecorded }: RecordResponseModalProps) {
  const { user } = useAuthStore();
  const trackResponse = useTrackCustomerResponse();

  const [selected, setSelected] = useState<ResponseType | null>(null);
  const [lostReason, setLostReason] = useState("");
  const [notes, setNotes] = useState("");
  const [e3Error, setE3Error] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async () => {
    setE3Error(null);
    setApiError(null);

    // E3: no response selected
    if (!selected) {
      setE3Error("Please select the customer's response before submitting.");
      return;
    }

    // E3: rejected but no lost reason
    if (selected === "rejected" && !lostReason.trim()) {
      setE3Error("Lost reason is required when the customer rejects a quotation.");
      return;
    }

    try {
      await trackResponse.mutateAsync({
        id: quote.id,
        payload: {
          customerResponse: RESPONSE_API_MAP[selected],
          lostReason: selected === "rejected" ? lostReason : undefined,
          notes: notes.trim() || undefined,
          recordedByName: user?.name ?? user?.email ?? "Staff",
          recordedByRole: user?.roles?.[0] ?? "SALES_STAFF",
        },
      });
      setSuccess(true);
      setTimeout(() => {
        onRecorded(quote.id, STATUS_MAP[selected]);
      }, 1000);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to record response. Please try again.";
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
            <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center">
              <MessageSquare className="size-4 text-blue-600" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Record Customer Response</h2>
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
            <CheckCircle2 className="size-10 text-emerald-500" />
            <p className="text-sm font-bold text-slate-700">Response recorded successfully</p>
          </div>
        ) : (
          <div className="px-5 py-4 space-y-4">
            {/* Response selector */}
            <div>
              <p className="text-xs font-semibold text-slate-600 mb-2.5">
                How did the customer respond? <span className="text-red-400">*</span>
              </p>
              <div className="grid grid-cols-2 gap-2">
                {RESPONSE_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => {
                      setSelected(opt.value);
                      setE3Error(null);
                    }}
                    className={`flex items-start gap-2.5 p-3 rounded-xl border text-left transition ${
                      selected === opt.value
                        ? opt.selectedCls
                        : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                    }`}
                  >
                    <span className={`mt-0.5 shrink-0 ${selected === opt.value ? opt.iconCls : "text-slate-400"}`}>
                      {opt.icon}
                    </span>
                    <div>
                      <p className="text-xs font-bold text-slate-800">{opt.label}</p>
                      <p className="text-[10px] text-slate-500 mt-0.5 leading-tight">{opt.description}</p>
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Lost reason (shown only when Rejected) */}
            {selected === "rejected" && (
              <div className="space-y-2 rounded-xl border border-red-100 bg-red-50/60 p-3">
                <p className="text-xs font-semibold text-red-700">
                  Lost Reason <span className="text-red-400">*</span>
                </p>
                <select
                  value={lostReason}
                  onChange={(e) => setLostReason(e.target.value)}
                  className="w-full rounded-lg border border-red-200 bg-white px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:border-red-400 transition"
                >
                  <option value="">-- Select a reason --</option>
                  {LOST_REASONS.map((r) => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
                {lostReason === "Other (see notes)" && (
                  <p className="text-[10px] text-red-500 font-semibold">
                    Please describe in the Notes field below.
                  </p>
                )}
              </div>
            )}

            {/* Notes */}
            <div>
              <p className="text-xs font-semibold text-slate-500 mb-1">
                Additional Notes <span className="text-slate-300">(optional)</span>
              </p>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={2}
                placeholder="Any context, follow-up actions, or customer comments..."
                className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white resize-none transition"
              />
            </div>

            {/* E3 validation error */}
            {e3Error && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                <AlertCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-red-600">
                  [E3] Invalid Input: {e3Error}
                </p>
              </div>
            )}

            {/* API error */}
            {apiError && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                <AlertCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-red-600">{apiError}</p>
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-2 pt-1">
              <Button
                variant="primary"
                className="flex-1 text-xs font-bold"
                onClick={handleSubmit}
                isLoading={trackResponse.isPending}
              >
                Record Response
              </Button>
              <Button
                variant="outline"
                className="flex-1 text-xs border-slate-200 text-slate-600"
                onClick={onClose}
                disabled={trackResponse.isPending}
              >
                Cancel
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
    </Portal>
  );
}
