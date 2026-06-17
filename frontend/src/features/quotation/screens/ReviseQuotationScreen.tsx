"use client";

import React, { useMemo, useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  AlertCircle,
  CheckCircle2,
  Calculator,
  History,
  ChevronDown,
  ChevronUp,
  XCircle,
  ShieldAlert,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Badge } from "@/components/ui/Badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import type { Quotation } from "@/services/quotation_service";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

type VersionHistory = {
  id: string;
  quotationId: string;
  quoteNo: string;
  versionNumber: number;
  amount: number;
  roomType?: string;
  checkInDate?: string;
  checkOutDate?: string;
  numberOfRooms?: number;
  pricePerNight?: number;
  discountPercent?: number;
  discountAmount?: number;
  subtotal?: number;
  paymentPolicy?: string;
  notes?: string;
  changeReason: string;
  revisedAt: string;
  revisedBy: string;
  statusBefore: string;
  statusAfter: string;
};

const schema = z
  .object({
    contactName: z.string().min(1, "Required"),
    email: z.string().min(1, "Required").email("Invalid email address"),
    phone: z.string().min(1, "Required"),
    dealName: z.string().min(1, "Required"),
    roomType: z.string().min(1, "Select a room type"),
    checkInDate: z.string().min(1, "Required"),
    checkOutDate: z.string().min(1, "Required"),
    numberOfRooms: z.coerce.number().min(1, "At least 1 room"),
    pricePerNight: z.coerce.number().min(1, "Must be greater than 0"),
    discountPercent: z.coerce.number().min(0, "Cannot be negative").max(100, "Cannot exceed 100%"),
    paymentPolicy: z.string().min(1, "Select a payment policy"),
    validUntil: z.string().min(1, "Required"),
    notes: z.string().optional(),
    changeReason: z.string().min(1, "Please describe the reason for this revision"),
  })
  .refine(
    (data) =>
      !data.checkInDate ||
      !data.checkOutDate ||
      new Date(data.checkOutDate) > new Date(data.checkInDate),
    { message: "Check-out must be after check-in", path: ["checkOutDate"] }
  );

type FormValues = z.infer<typeof schema>;

const ROOM_TYPES = [
  "Deluxe Suite",
  "Superior Room",
  "Standard Queen",
  "Executive Suite",
  "Ocean View Room",
  "Banquet Hall",
  "Grand Ballroom Suite",
];

const PAYMENT_POLICIES: { value: string; label: string }[] = [
  { value: "full_upfront", label: "Full Payment Upfront" },
  { value: "50_deposit", label: "50% Deposit on Booking" },
  { value: "pay_on_arrival", label: "Pay on Arrival" },
];

function FieldLabel({ children, required }: { children: React.ReactNode; required?: boolean }) {
  return (
    <label className="block text-xs font-semibold text-slate-500 mb-1">
      {children}
      {required && <span className="text-red-400 ml-0.5">*</span>}
    </label>
  );
}

interface ReviseQuotationScreenProps {
  quotationId: string;
}

export function ReviseQuotationScreen({ quotationId: _quotationId }: ReviseQuotationScreenProps) {
  const router = useRouter();
  const [quotation] = useState<Quotation | null>(null);

  const [showHistory, setShowHistory] = useState(false);
  const [simulateNoManager, setSimulateNoManager] = useState(false);
  const [e3Error, setE3Error] = useState<string | null>(null);
  const [e4Error, setE4Error] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [versionHistory, setVersionHistory] = useState<VersionHistory[]>([]);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema) as unknown as Resolver<FormValues>,
    defaultValues: quotation
      ? {
          contactName: quotation.contactName,
          email: quotation.email ?? "",
          phone: quotation.phone ?? "",
          dealName: quotation.dealName,
          roomType: quotation.roomType ?? "",
          checkInDate: quotation.checkInDate ?? "",
          checkOutDate: quotation.checkOutDate ?? "",
          numberOfRooms: quotation.numberOfRooms ?? 1,
          pricePerNight: quotation.pricePerNight ?? 0,
          discountPercent: quotation.discountPercent ?? 0,
          paymentPolicy: quotation.paymentPolicy ?? "",
          validUntil: quotation.expiryDate ?? "",
          notes: quotation.notes ?? "",
          changeReason: "",
        }
      : {},
  });

  const [roomType, checkInDate, checkOutDate, numberOfRooms, pricePerNight, discountPercent] = watch([
    "roomType",
    "checkInDate",
    "checkOutDate",
    "numberOfRooms",
    "pricePerNight",
    "discountPercent",
  ]);

  const pricing = useMemo(() => {
    const inDate = checkInDate ? new Date(checkInDate) : null;
    const outDate = checkOutDate ? new Date(checkOutDate) : null;
    const nights =
      inDate && outDate && outDate > inDate
        ? Math.floor((outDate.getTime() - inDate.getTime()) / (1000 * 60 * 60 * 24))
        : 0;
    const rooms = numberOfRooms || 0;
    const price = pricePerNight || 0;
    const disc = discountPercent || 0;
    const subtotal = price * nights * rooms;
    const discountAmount = Math.round(subtotal * disc) / 100;
    const total = subtotal - discountAmount;
    return { nights, subtotal, discountAmount, total };
  }, [checkInDate, checkOutDate, numberOfRooms, pricePerNight, discountPercent]);

  const availability = useMemo(() => {
    if (!roomType || !checkInDate || !checkOutDate) return null;
    const inDate = new Date(checkInDate);
    const outDate = new Date(checkOutDate);
    if (outDate <= inDate) return null;
    return { available: true };
  }, [roomType, checkInDate, checkOutDate]);

  const requiresApproval = (discountPercent || 0) > 10;
  const nextVersion = (quotation?.version ?? 1) + 1;

  const onSubmit = (data: FormValues) => {
    setE3Error(null);
    setE4Error(null);

    // E3: block if room unavailable
    if (availability && !availability.available) {
      setE3Error(`"${data.roomType}" is already booked for the selected dates. Choose different dates or a different room type.`);
      return;
    }

    // E4: block if no manager available and discount > 10%
    if (simulateNoManager && data.discountPercent > 10) {
      setE4Error(
        `Discount of ${data.discountPercent}% exceeds Sales Staff authority (10%). No Sales Manager is currently available to approve. Please reduce the discount or try again later.`
      );
      return;
    }

    const newStatus = data.discountPercent > 10 ? "pending_approval" : "draft";
    const today = new Date().toISOString().split("T")[0];

    if (quotation) {
      setVersionHistory(prev => [{
        id: `QV-${prev.length + 1}`,
        quotationId: quotation.id,
        quoteNo: quotation.quoteNo,
        versionNumber: quotation.version ?? 1,
        amount: quotation.amount,
        roomType: quotation.roomType,
        checkInDate: quotation.checkInDate,
        checkOutDate: quotation.checkOutDate,
        numberOfRooms: quotation.numberOfRooms,
        pricePerNight: quotation.pricePerNight,
        discountPercent: quotation.discountPercent,
        discountAmount: quotation.discountAmount,
        subtotal: quotation.subtotal,
        paymentPolicy: quotation.paymentPolicy,
        notes: quotation.notes,
        changeReason: data.changeReason,
        revisedAt: today,
        revisedBy: "Sarah Connor",
        statusBefore: quotation.status,
        statusAfter: newStatus,
      }, ...prev]);
    }

    setSubmitSuccess(true);
    setTimeout(() => router.push(ROUTE_PATHS.quotations), 1200);
  };

  if (!quotation) {
    return (
      <div className="py-20 text-center space-y-3">
        <p className="text-sm text-slate-500">Quotation not found.</p>
        <Button variant="outline" size="sm" onClick={() => router.push(ROUTE_PATHS.quotations)}>
          Back to Quotations
        </Button>
      </div>
    );
  }

  if (submitSuccess) {
    return (
      <div className="py-20 text-center space-y-3">
        <CheckCircle2 className="size-10 text-emerald-500 mx-auto" />
        <p className="text-sm font-bold text-slate-700">
          Quotation revised — Version {nextVersion} saved
        </p>
        <p className="text-xs text-slate-400">Redirecting…</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => router.push(ROUTE_PATHS.quotations)}
          leftIcon={<ArrowLeft className="size-4" />}
          className="text-slate-500 hover:text-slate-800 -ml-1"
        >
          Back to Quotations
        </Button>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-xl font-bold text-slate-800">Revise Quotation</h1>
            <Badge variant="info" size="sm" className="font-bold text-[10px]">
              {quotation.quoteNo}
            </Badge>
            <Badge variant="default" size="sm" className="font-bold text-[10px] bg-slate-100 text-slate-500">
              v{quotation.version ?? 1} → v{nextVersion}
            </Badge>
          </div>
          <p className="text-xs text-slate-400">
            Editing a new version of this quotation. Previous version will be saved to history.
          </p>
        </div>
      </div>

      {/* Version History collapsible */}
      {(versionHistory.length > 0 || true) && (
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <button
              type="button"
              onClick={() => setShowHistory((v) => !v)}
              className="flex items-center justify-between w-full"
            >
              <CardTitle className="text-sm font-bold text-slate-700 flex items-center gap-2">
                <History className="size-4 text-blue-400" />
                Version History
                <span className="ml-1 text-[10px] font-normal text-slate-400">
                  ({versionHistory.length} revision{versionHistory.length !== 1 ? "s" : ""})
                </span>
              </CardTitle>
              {showHistory ? (
                <ChevronUp className="size-4 text-slate-400" />
              ) : (
                <ChevronDown className="size-4 text-slate-400" />
              )}
            </button>
          </CardHeader>
          {showHistory && (
            <CardContent className="pt-0">
              {versionHistory.length === 0 ? (
                <p className="text-xs text-slate-400 italic py-2">
                  No previous revisions. Current is the original version (v{quotation.version ?? 1}).
                </p>
              ) : (
                <Table>
                  <TableHeader className="bg-slate-50">
                    <TableRow hoverable={false}>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Ver.</TableHead>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Date</TableHead>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Revised By</TableHead>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Amount</TableHead>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Discount</TableHead>
                      <TableHead className="text-[10px] font-semibold text-slate-500">Change Reason</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {versionHistory.map((v) => (
                      <TableRow key={v.id} className="border-b border-slate-100">
                        <TableCell className="py-2 text-xs font-bold text-slate-600">v{v.versionNumber}</TableCell>
                        <TableCell className="py-2 text-xs text-slate-500">{v.revisedAt}</TableCell>
                        <TableCell className="py-2 text-xs text-slate-600">{v.revisedBy}</TableCell>
                        <TableCell className="py-2 text-xs font-semibold text-slate-700">${v.amount.toLocaleString('en-US')}</TableCell>
                        <TableCell className="py-2 text-xs text-slate-500">{v.discountPercent ?? 0}%</TableCell>
                        <TableCell className="py-2 text-xs text-slate-500 max-w-[200px] truncate">{v.changeReason}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          )}
        </Card>
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Left: form sections */}
          <div className="lg:col-span-2 space-y-5">

            {/* Section 1: Customer Info */}
            <Card className="border-slate-100 shadow-sm bg-white">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-slate-700">Customer Information</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <FieldLabel required>Contact Name</FieldLabel>
                  <Input {...register("contactName")} placeholder="e.g. Emily Miller" error={errors.contactName?.message} />
                </div>
                <div>
                  <FieldLabel required>Email Address</FieldLabel>
                  <Input {...register("email")} type="email" placeholder="e.g. emily@example.com" error={errors.email?.message} />
                </div>
                <div>
                  <FieldLabel required>Phone Number</FieldLabel>
                  <Input {...register("phone")} placeholder="e.g. +1 555-0100" error={errors.phone?.message} />
                </div>
                <div>
                  <FieldLabel required>Deal / Event Name</FieldLabel>
                  <Input {...register("dealName")} placeholder="e.g. Miller Wedding Booking" error={errors.dealName?.message} />
                </div>
              </CardContent>
            </Card>

            {/* Section 2: Booking Details */}
            <Card className="border-slate-100 shadow-sm bg-white">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-slate-700">Room Booking Details</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="sm:col-span-2">
                  <FieldLabel required>Room Type</FieldLabel>
                  <Select {...register("roomType")} error={errors.roomType?.message}>
                    <option value="">-- Select room type --</option>
                    {ROOM_TYPES.map((rt) => (
                      <option key={rt} value={rt}>{rt}</option>
                    ))}
                  </Select>
                </div>
                <div>
                  <FieldLabel required>Check-In Date</FieldLabel>
                  <Input {...register("checkInDate")} type="date" error={errors.checkInDate?.message} />
                </div>
                <div>
                  <FieldLabel required>Check-Out Date</FieldLabel>
                  <Input {...register("checkOutDate")} type="date" error={errors.checkOutDate?.message} />
                </div>
                <div>
                  <FieldLabel required>Number of Rooms</FieldLabel>
                  <Input {...register("numberOfRooms")} type="number" min={1} placeholder="1" error={errors.numberOfRooms?.message} />
                </div>
                <div className="flex flex-col justify-end gap-1">
                  {pricing.nights > 0 && (
                    <span className="text-xs text-slate-500 font-semibold pb-1">
                      Duration: <strong className="text-slate-800">{pricing.nights} night{pricing.nights !== 1 ? "s" : ""}</strong>
                    </span>
                  )}
                  {/* E3 availability indicator */}
                  {availability && (
                    <span className={`text-[10px] font-semibold flex items-center gap-1 ${availability.available ? "text-emerald-600" : "text-red-500"}`}>
                      {availability.available ? (
                        <><CheckCircle2 className="size-3" /> Room type available</>
                      ) : (
                        <><XCircle className="size-3" /> Booking conflict: {(availability as { available: false; bookingNo: string }).bookingNo}</>
                      )}
                    </span>
                  )}
                </div>
              </CardContent>
            </Card>

            {/* Section 3: Pricing */}
            <Card className="border-slate-100 shadow-sm bg-white">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-slate-700">Pricing & Discount</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <FieldLabel required>Price Per Night / Unit (USD)</FieldLabel>
                  <Input {...register("pricePerNight")} type="number" min={0} step={0.01} placeholder="0.00" error={errors.pricePerNight?.message} />
                </div>
                <div>
                  <FieldLabel>Discount (%)</FieldLabel>
                  <Input {...register("discountPercent")} type="number" min={0} max={100} step={0.1} placeholder="0" error={errors.discountPercent?.message} />
                  {requiresApproval && (
                    <p className="mt-1 text-[10px] text-amber-600 font-semibold flex items-center gap-1">
                      <AlertCircle className="size-3" />
                      Discount &gt;10% requires Manager approval
                    </p>
                  )}
                </div>
                {/* E4 simulation toggle */}
                <div className="sm:col-span-2">
                  <label className="flex items-center gap-2 cursor-pointer select-none w-fit">
                    <input
                      type="checkbox"
                      checked={simulateNoManager}
                      onChange={(e) => setSimulateNoManager(e.target.checked)}
                      className="rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-[10px] text-slate-500 font-semibold">
                      Simulate: No Sales Manager available (E4 test)
                    </span>
                  </label>
                </div>
              </CardContent>
            </Card>

            {/* Section 4: Policy */}
            <Card className="border-slate-100 shadow-sm bg-white">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-slate-700">Payment Policy & Validity</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <FieldLabel required>Payment Policy</FieldLabel>
                  <Select {...register("paymentPolicy")} error={errors.paymentPolicy?.message}>
                    <option value="">-- Select policy --</option>
                    {PAYMENT_POLICIES.map((p) => (
                      <option key={p.value} value={p.value}>{p.label}</option>
                    ))}
                  </Select>
                </div>
                <div>
                  <FieldLabel required>Quote Valid Until</FieldLabel>
                  <Input {...register("validUntil")} type="date" error={errors.validUntil?.message} />
                </div>
                <div className="sm:col-span-2">
                  <FieldLabel>Notes / Special Requests</FieldLabel>
                  <textarea
                    {...register("notes")}
                    rows={3}
                    placeholder="Any special requests, inclusions, or terms..."
                    className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white resize-none transition"
                  />
                </div>
              </CardContent>
            </Card>

            {/* Section 5: Change Reason */}
            <Card className="border-amber-100 shadow-sm bg-amber-50/40">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-amber-700 flex items-center gap-2">
                  <History className="size-4" />
                  Revision Change Reason
                </CardTitle>
              </CardHeader>
              <CardContent>
                <FieldLabel required>Why is this quotation being revised?</FieldLabel>
                <textarea
                  {...register("changeReason")}
                  rows={3}
                  placeholder="e.g. Customer requested different check-in dates; Price correction after room allocation update..."
                  className="w-full rounded-lg border border-amber-200 bg-white px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-amber-400 resize-none transition"
                />
                {errors.changeReason && (
                  <p className="mt-1 text-[10px] text-red-500 font-semibold flex items-center gap-1">
                    <AlertCircle className="size-3" />
                    {errors.changeReason.message}
                  </p>
                )}
              </CardContent>
            </Card>

          </div>

          {/* Right: price summary + submit */}
          <div className="space-y-4">
            <Card className="border-slate-100 shadow-sm bg-white sticky top-6">
              <CardHeader>
                <CardTitle className="text-sm font-bold text-slate-700 flex items-center gap-2">
                  <Calculator className="size-4 text-blue-500" />
                  Price Summary
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2 text-xs">
                  <div className="flex justify-between text-slate-500">
                    <span>Nights</span>
                    <span className="font-semibold text-slate-700">{pricing.nights}</span>
                  </div>
                  <div className="flex justify-between text-slate-500">
                    <span>Rooms</span>
                    <span className="font-semibold text-slate-700">{numberOfRooms || 0}</span>
                  </div>
                  <div className="flex justify-between text-slate-500">
                    <span>Rate/Night</span>
                    <span className="font-semibold text-slate-700">${(pricePerNight || 0).toLocaleString('en-US')}</span>
                  </div>
                  <div className="border-t border-slate-100 pt-2 flex justify-between text-slate-600">
                    <span>Subtotal</span>
                    <span className="font-bold">${pricing.subtotal.toLocaleString('en-US')}</span>
                  </div>
                  {pricing.discountAmount > 0 && (
                    <div className="flex justify-between text-amber-600">
                      <span>Discount ({discountPercent || 0}%)</span>
                      <span className="font-bold">-${pricing.discountAmount.toLocaleString('en-US')}</span>
                    </div>
                  )}
                  <div className="border-t border-slate-200 pt-2 flex justify-between text-slate-800">
                    <span className="font-bold text-sm">Total</span>
                    <span className="font-black text-sm text-blue-700">${pricing.total.toLocaleString('en-US')}</span>
                  </div>
                </div>

                <div className="pt-1">
                  <p className="text-[10px] text-slate-400 font-semibold mb-1.5">Status after Save:</p>
                  {requiresApproval ? (
                    <Badge variant="warning" size="sm" className="font-bold text-[10px] uppercase w-full justify-center py-1">
                      Pending Manager Approval
                    </Badge>
                  ) : (
                    <Badge variant="default" size="sm" className="font-bold text-[10px] uppercase w-full justify-center py-1 bg-slate-100 text-slate-600">
                      Draft
                    </Badge>
                  )}
                </div>

                {/* E3 / E4 error banners */}
                {e3Error && (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                    <p className="text-[10px] font-bold text-red-600 flex items-start gap-1.5">
                      <XCircle className="size-3 mt-0.5 shrink-0" />
                      <span>[E3] Room Unavailable: {e3Error}</span>
                    </p>
                  </div>
                )}
                {e4Error && (
                  <div className="rounded-lg border border-orange-200 bg-orange-50 p-3">
                    <p className="text-[10px] font-bold text-orange-600 flex items-start gap-1.5">
                      <ShieldAlert className="size-3 mt-0.5 shrink-0" />
                      <span>[E4] Authority Exceeded: {e4Error}</span>
                    </p>
                  </div>
                )}

                <div className="pt-1 space-y-2">
                  <Button
                    type="submit"
                    variant="primary"
                    className="w-full text-xs font-bold"
                    isLoading={isSubmitting}
                    leftIcon={<CheckCircle2 className="size-3.5" />}
                  >
                    {requiresApproval ? `Submit v${nextVersion} for Approval` : `Save Version ${nextVersion}`}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    className="w-full text-xs text-slate-500"
                    onClick={() => router.push(ROUTE_PATHS.quotations)}
                  >
                    Cancel
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </form>
    </div>
  );
}
