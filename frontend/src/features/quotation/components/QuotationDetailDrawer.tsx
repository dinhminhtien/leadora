"use client";

import React from "react";
import {
  X,
  FileSpreadsheet,
  Calendar,
  User,
  BedDouble,
  Clock,
  ShieldCheck,
  Send,
  GitBranch,
  CheckCircle2,
} from "lucide-react";
import { Portal } from "@/components/ui/Portal";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { StatusPill } from "@/components/ui/status-pill";
import type { Quotation, RoomLineDetail } from "@/services/quotation_service";

const PAYMENT_LABELS: Record<string, string> = {
  full_upfront: "Full Payment Upfront",
  "50_deposit": "50% Deposit on Booking",
  pay_on_arrival: "Pay on Arrival",
};

export interface QuotationDetailDrawerProps {
  quote: Quotation | null;
  onClose: () => void;
  onSend?: (quote: Quotation) => void;
  onRevise?: (quote: Quotation) => void;
  onConvertToBooking?: (quote: Quotation) => void;
}

export function QuotationDetailDrawer({
  quote,
  onClose,
  onSend,
  onRevise,
  onConvertToBooking,
}: QuotationDetailDrawerProps) {
  if (!quote) return null;

  const calculateNights = (): number => {
    if (!quote.checkInDate || !quote.checkOutDate) return 1;
    const inDate = new Date(quote.checkInDate);
    const outDate = new Date(quote.checkOutDate);
    const diffTime = outDate.getTime() - inDate.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24));
    return diffDays > 0 ? diffDays : 1;
  };

  const nights = quote.nights ?? calculateNights();

  // Extract room lines array or generate fallback for single room type
  const roomLines: RoomLineDetail[] =
    quote.roomLines && quote.roomLines.length > 0
      ? quote.roomLines
      : [
          {
            roomType: quote.roomType ?? "Standard Room",
            numberOfRooms: quote.numberOfRooms ?? 1,
            pricePerNight: quote.pricePerNight ?? quote.amount,
            nights: nights,
            lineTotal: (quote.numberOfRooms ?? 1) * (quote.pricePerNight ?? quote.amount) * nights,
          },
        ];

  return (
    <Portal>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Slide-over Drawer */}
      <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-xl flex-col bg-white shadow-2xl border-l border-slate-200 animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/60 px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-blue-100 text-blue-600">
              <FileSpreadsheet className="size-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-slate-800">{quote.quoteNo}</h2>
                <Badge variant="default" size="sm">
                  v{quote.version ?? 1}
                </Badge>
                <StatusPill size="sm" domain="quotation" value={quote.status} />
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                {quote.dealName ? `Deal: ${quote.dealName}` : "Direct Quotation"}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
          >
            <X className="size-4.5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Customer & Event Details */}
          <section className="rounded-xl border border-slate-100 bg-slate-50/40 p-4 space-y-3">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
              Customer &amp; Schedule Information
            </p>
            <div className="grid grid-cols-2 gap-4 text-xs">
              <div>
                <span className="text-slate-400 block text-[11px]">Client Name</span>
                <span className="font-semibold text-slate-800 flex items-center gap-1.5 mt-0.5">
                  <User className="size-3.5 text-slate-400" />
                  {quote.contactName}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block text-[11px]">Valid Until</span>
                <span className="font-semibold text-slate-800 flex items-center gap-1.5 mt-0.5">
                  <Clock className="size-3.5 text-slate-400" />
                  {quote.expiryDate ?? "—"}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block text-[11px]">Stay Dates</span>
                <span className="font-semibold text-slate-800 flex items-center gap-1.5 mt-0.5">
                  <Calendar className="size-3.5 text-slate-400" />
                  {quote.checkInDate ?? "—"} → {quote.checkOutDate ?? "—"} ({nights} night{nights !== 1 ? "s" : ""})
                </span>
              </div>
              <div>
                <span className="text-slate-400 block text-[11px]">Payment Terms</span>
                <span className="font-semibold text-slate-800 flex items-center gap-1.5 mt-0.5">
                  <ShieldCheck className="size-3.5 text-slate-400" />
                  {quote.paymentPolicy ? (PAYMENT_LABELS[quote.paymentPolicy] ?? quote.paymentPolicy) : "Standard"}
                </span>
              </div>
            </div>
          </section>

          {/* Multi-Room Line Items Table */}
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-slate-700 flex items-center gap-1.5 uppercase tracking-wider">
                <BedDouble className="size-4 text-blue-600" />
                Itemized Room Breakdown
              </h3>
              <span className="text-[11px] font-semibold text-slate-400">
                {roomLines.reduce((acc, r) => acc + (r.numberOfRooms ?? 1), 0)} Total Rooms
              </span>
            </div>

            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-[10px] uppercase font-bold text-slate-400 border-b border-slate-200">
                  <tr>
                    <th className="px-3.5 py-2.5">Room Type</th>
                    <th className="px-3.5 py-2.5 text-center">Qty</th>
                    <th className="px-3.5 py-2.5 text-right">Price / Night</th>
                    <th className="px-3.5 py-2.5 text-right">Nights</th>
                    <th className="px-3.5 py-2.5 text-right">Line Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {roomLines.map((line, idx) => {
                    const lineNights = line.nights ?? nights;
                    const lineTotal = line.lineTotal ?? (line.numberOfRooms ?? 1) * (line.pricePerNight ?? 0) * lineNights;
                    return (
                      <tr key={idx} className="hover:bg-slate-50/50">
                        <td className="px-3.5 py-2.5 font-bold text-slate-800">
                          {line.roomType ?? quote.roomType ?? `Room Option ${idx + 1}`}
                        </td>
                        <td className="px-3.5 py-2.5 text-center font-semibold text-slate-700">
                          <span className="inline-block rounded-md bg-blue-50 px-2 py-0.5 text-blue-700 font-bold text-[11px]">
                            {line.numberOfRooms ?? 1}
                          </span>
                        </td>
                        <td className="px-3.5 py-2.5 text-right text-slate-600">
                          {(line.pricePerNight ?? 0).toLocaleString("vi-VN")} ₫
                        </td>
                        <td className="px-3.5 py-2.5 text-right text-slate-500">
                          {lineNights}
                        </td>
                        <td className="px-3.5 py-2.5 text-right font-bold text-slate-800">
                          {lineTotal.toLocaleString("vi-VN")} ₫
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {/* Pricing Summary */}
          <section className="rounded-xl border border-slate-200 bg-white p-4 space-y-2">
            <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-3">
              Financial Summary
            </h3>

            <div className="flex justify-between text-xs text-slate-600">
              <span>Subtotal</span>
              <span>{(quote.subtotal ?? quote.amount).toLocaleString("vi-VN")} ₫</span>
            </div>

            {quote.discountPercent ? (
              <div className="flex justify-between text-xs text-amber-600 font-medium">
                <span>Discount ({quote.discountPercent}%)</span>
                <span>-{(quote.discountAmount ?? 0).toLocaleString("vi-VN")} ₫</span>
              </div>
            ) : null}

            <div className="border-t border-slate-100 pt-2.5 mt-2 flex justify-between items-center">
              <span className="text-xs font-bold text-slate-800">Total Quotation Value</span>
              <span className="text-base font-extrabold text-blue-600">
                {quote.amount.toLocaleString("vi-VN")} ₫
              </span>
            </div>
          </section>

          {/* Notes / Special Instructions */}
          {quote.notes && (
            <section className="space-y-1.5">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                Notes &amp; Inclusions
              </span>
              <p className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600 whitespace-pre-wrap">
                {quote.notes}
              </p>
            </section>
          )}
        </div>

        {/* Action Footer */}
        <div className="border-t border-slate-200 bg-slate-50/80 px-6 py-4 flex items-center justify-between gap-3 mt-auto z-10">
          <Button variant="ghost" size="sm" onClick={onClose} className="text-slate-500">
            Close
          </Button>

          <div className="flex items-center gap-2">
            {onRevise && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onClose();
                  onRevise(quote);
                }}
                leftIcon={<GitBranch className="size-3.5 text-slate-500" />}
              >
                Revise
              </Button>
            )}

            {onSend && quote.status === "approved" && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onClose();
                  onSend(quote);
                }}
                leftIcon={<Send className="size-3.5" />}
              >
                Send Quotation
              </Button>
            )}

            {onConvertToBooking && (quote.status === "accepted" || quote.status === "approved" || quote.status === "sent") && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onClose();
                  onConvertToBooking(quote);
                }}
                leftIcon={<CheckCircle2 className="size-3.5" />}
                className="bg-emerald-600 hover:bg-emerald-700"
              >
                Convert to Booking
              </Button>
            )}
          </div>
        </div>
      </div>
    </Portal>
  );
}
