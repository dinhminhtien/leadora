"use client";

import React, { useState, useMemo } from "react";
import {
  X,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Building2,
  User,
  Mail,
  Phone,
  BedDouble,
  CalendarDays,
  Hash,
  DollarSign,
  ShieldCheck,
  ClipboardCheck,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { Quotation } from "@/services/quotation_service";

interface ConvertToBookingModalProps {
  quote: Quotation;
  onClose: () => void;
  onConverted: (quotationId: string, bookingNo: string) => void;
}

function ReviewField({
  icon,
  label,
  value,
  required,
  onChange,
  type = "text",
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  required?: boolean;
  onChange: (v: string) => void;
  type?: string;
}) {
  const isMissing = required && !value.trim();

  return (
    <div className="flex items-start gap-3">
      <div className={`mt-2.5 shrink-0 ${isMissing ? "text-amber-400" : "text-slate-400"}`}>
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <label className="block text-[10px] font-semibold text-slate-500 mb-0.5">
          {label}
          {required && <span className="text-red-400 ml-0.5">*</span>}
          {isMissing && (
            <span className="ml-2 text-[9px] font-bold text-amber-600 bg-amber-100 px-1.5 py-0.5 rounded-full">
              MISSING
            </span>
          )}
        </label>
        <input
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={`w-full rounded-lg border px-3 py-1.5 text-xs text-slate-800 focus:outline-none transition ${
            isMissing
              ? "border-amber-300 bg-amber-50 focus:border-amber-500 placeholder:text-amber-400"
              : "border-slate-200 bg-slate-50 focus:border-blue-500 focus:bg-white"
          }`}
          placeholder={isMissing ? `Enter ${label.toLowerCase()}` : ""}
        />
      </div>
    </div>
  );
}

export function ConvertToBookingModal({ quote, onConverted, onClose }: ConvertToBookingModalProps) {
  const [contactName, setContactName] = useState(quote.contactName);
  const [email, setEmail] = useState(quote.email ?? "");
  const [phone, setPhone] = useState(quote.phone ?? "");
  const [roomType, setRoomType] = useState(quote.roomType ?? "");
  const [checkInDate, setCheckInDate] = useState(quote.checkInDate ?? "");
  const [checkOutDate, setCheckOutDate] = useState(quote.checkOutDate ?? "");
  const [e3Error, setE3Error] = useState<string | null>(null);
  const [e4Error, setE4Error] = useState<string | null>(null);
  const [success, setSuccess] = useState<{ bookingNo: string } | null>(null);

  const [bookingNo] = useState(
    () => `BK-2026-${String(Math.floor(Math.random() * 1000) + 8891).padStart(4, "0")}`
  );

  const availability = useMemo(() => {
    if (!roomType || !checkInDate || !checkOutDate) return null;
    const inDate = new Date(checkInDate);
    const outDate = new Date(checkOutDate);
    if (outDate <= inDate) return null;
    return { available: true };
  }, [roomType, checkInDate, checkOutDate]);

  // Pricing
  const nights = useMemo(() => {
    if (!checkInDate || !checkOutDate) return 0;
    const diff = new Date(checkOutDate).getTime() - new Date(checkInDate).getTime();
    return diff > 0 ? Math.floor(diff / (1000 * 60 * 60 * 24)) : 0;
  }, [checkInDate, checkOutDate]);

  const handleConvert = () => {
    setE3Error(null);
    setE4Error(null);

    // E4: missing required fields
    const missing: string[] = [];
    if (!contactName.trim()) missing.push("Contact Name");
    if (!email.trim()) missing.push("Email");
    if (!phone.trim()) missing.push("Phone");
    if (!roomType.trim()) missing.push("Room Type");
    if (!checkInDate.trim()) missing.push("Check-In Date");
    if (!checkOutDate.trim()) missing.push("Check-Out Date");

    if (missing.length > 0) {
      setE4Error(
        `Incomplete customer/booking details — please fill in: ${missing.join(", ")}.`
      );
      return;
    }

    // E3: room conflict
    if (availability && !availability.available) {
      setE3Error(
        `"${roomType}" is already confirmed (${(availability as { available: false; bookingNo: string }).bookingNo}) for overlapping dates. Adjust the dates or room type before converting.`
      );
      return;
    }

    setSuccess({ bookingNo });
    setTimeout(() => onConverted(quote.id, bookingNo), 1400);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" onClick={onClose} />

      <div className="relative z-10 w-full max-w-lg mx-4 bg-white rounded-2xl shadow-xl border border-slate-100 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="sticky top-0 bg-white flex items-start justify-between px-5 pt-5 pb-4 border-b border-slate-100 z-10">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 flex items-center justify-center">
              <Building2 className="size-4 text-emerald-600" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-slate-800">Convert to Booking</h2>
              <p className="text-[10px] text-slate-400 mt-0.5">
                {quote.quoteNo} · {quote.contactName} · ${quote.amount.toLocaleString('en-US')}
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
          /* Success state */
          <div className="py-10 flex flex-col items-center gap-4 px-6">
            <div className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center">
              <CheckCircle2 className="size-7 text-emerald-500" />
            </div>
            <div className="text-center">
              <p className="text-sm font-bold text-slate-800">Booking Confirmed!</p>
              <p className="text-xs text-slate-500 mt-1">
                Booking reference <strong className="text-slate-700">{success.bookingNo}</strong> has been created.
              </p>
            </div>
            <div className="flex items-center gap-2 bg-slate-50 rounded-xl px-4 py-2.5 border border-slate-200">
              <ClipboardCheck className="size-4 text-slate-400" />
              <span className="text-xs font-bold text-slate-700">{success.bookingNo}</span>
            </div>
            <p className="text-[10px] text-slate-400">Reservation Staff notified · SLA tracking started</p>
          </div>
        ) : (
          <div className="px-5 py-4 space-y-5">
            {/* Step banner */}
            <div className="flex items-center gap-2 text-[10px] text-slate-500 font-semibold">
              <span className="flex items-center gap-1">
                <span className="w-4 h-4 rounded-full bg-blue-600 text-white flex items-center justify-center text-[8px] font-bold">1</span>
                Review Details
              </span>
              <span className="flex-1 border-t border-slate-200" />
              <span className="flex items-center gap-1">
                <span className="w-4 h-4 rounded-full bg-slate-200 text-slate-500 flex items-center justify-center text-[8px] font-bold">2</span>
                Confirm
              </span>
            </div>

            {/* E4: pre-scan for missing fields */}
            {(!email || !phone || !roomType || !checkInDate || !checkOutDate) && (
              <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5">
                <AlertCircle className="size-3.5 text-amber-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-amber-700">
                  [E4] Some customer or booking details are incomplete. Fill in the highlighted fields before converting.
                </p>
              </div>
            )}

            {/* Customer Details */}
            <div className="space-y-3">
              <p className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <User className="size-3.5 text-slate-400" /> Customer Information
              </p>
              <div className="grid grid-cols-1 gap-3 pl-1">
                <ReviewField
                  icon={<User className="size-3.5" />}
                  label="Contact Name"
                  value={contactName}
                  required
                  onChange={setContactName}
                />
                <div className="grid grid-cols-2 gap-3">
                  <ReviewField
                    icon={<Mail className="size-3.5" />}
                    label="Email"
                    value={email}
                    required
                    onChange={setEmail}
                    type="email"
                  />
                  <ReviewField
                    icon={<Phone className="size-3.5" />}
                    label="Phone"
                    value={phone}
                    required
                    onChange={setPhone}
                  />
                </div>
              </div>
            </div>

            <div className="border-t border-slate-100" />

            {/* Room & Stay Details */}
            <div className="space-y-3">
              <p className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <BedDouble className="size-3.5 text-slate-400" /> Stay & Room Details
              </p>
              <div className="grid grid-cols-1 gap-3 pl-1">
                <ReviewField
                  icon={<BedDouble className="size-3.5" />}
                  label="Room Type"
                  value={roomType}
                  required
                  onChange={setRoomType}
                />
                <div className="grid grid-cols-2 gap-3">
                  <ReviewField
                    icon={<CalendarDays className="size-3.5" />}
                    label="Check-In Date"
                    value={checkInDate}
                    required
                    onChange={setCheckInDate}
                    type="date"
                  />
                  <ReviewField
                    icon={<CalendarDays className="size-3.5" />}
                    label="Check-Out Date"
                    value={checkOutDate}
                    required
                    onChange={setCheckOutDate}
                    type="date"
                  />
                </div>

                {/* Availability indicator */}
                {availability && (
                  <div className={`flex items-center gap-2 rounded-lg border px-3 py-2 ${
                    availability.available
                      ? "border-emerald-200 bg-emerald-50"
                      : "border-red-200 bg-red-50"
                  }`}>
                    {availability.available ? (
                      <CheckCircle2 className="size-3.5 text-emerald-500 shrink-0" />
                    ) : (
                      <XCircle className="size-3.5 text-red-500 shrink-0" />
                    )}
                    <span className={`text-[10px] font-semibold ${availability.available ? "text-emerald-700" : "text-red-600"}`}>
                      {availability.available
                        ? "Room type is available for selected dates"
                        : `[E3] Conflict with booking ${(availability as { available: false; bookingNo: string }).bookingNo}`}
                    </span>
                  </div>
                )}
              </div>
            </div>

            <div className="border-t border-slate-100" />

            {/* Booking Preview */}
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2.5">
              <p className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <ShieldCheck className="size-3.5 text-emerald-500" /> Booking Preview
              </p>
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
                <div className="flex items-center gap-1.5 text-slate-500">
                  <Hash className="size-3 shrink-0" />
                  <span>Reference</span>
                </div>
                <span className="font-bold text-slate-800">{bookingNo}</span>

                <div className="flex items-center gap-1.5 text-slate-500">
                  <CalendarDays className="size-3 shrink-0" />
                  <span>Duration</span>
                </div>
                <span className="font-semibold text-slate-700">
                  {nights > 0 ? `${nights} night${nights !== 1 ? "s" : ""}` : "—"}
                </span>

                <div className="flex items-center gap-1.5 text-slate-500">
                  <DollarSign className="size-3 shrink-0" />
                  <span>Amount</span>
                </div>
                <span className="font-black text-emerald-700">${quote.amount.toLocaleString('en-US')}</span>

                <div className="flex items-center gap-1.5 text-slate-500">
                  <CheckCircle2 className="size-3 shrink-0" />
                  <span>Status</span>
                </div>
                <span className="inline-flex items-center gap-1 font-bold text-emerald-700">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                  Confirmed
                </span>
              </div>
            </div>

            {/* Error banners */}
            {e4Error && (
              <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
                <AlertCircle className="size-3.5 text-amber-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-amber-700">[E4] {e4Error}</p>
              </div>
            )}
            {e3Error && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
                <XCircle className="size-3.5 text-red-500 mt-0.5 shrink-0" />
                <p className="text-[10px] font-semibold text-red-600">[E3] {e3Error}</p>
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-2 pt-1">
              <Button
                variant="primary"
                className="flex-1 text-xs font-bold bg-emerald-600 hover:bg-emerald-700"
                onClick={handleConvert}
                leftIcon={<Building2 className="size-3.5" />}
              >
                Confirm &amp; Convert to Booking
              </Button>
              <Button
                variant="outline"
                className="text-xs border-slate-200 text-slate-600 px-4"
                onClick={onClose}
              >
                Cancel
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
