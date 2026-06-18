"use client";

import React, { useMemo } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useRouter } from "next/navigation";
import { ArrowLeft, AlertCircle, CheckCircle2, Calculator } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Badge } from "@/components/ui/Badge";
import { ROUTE_PATHS } from "@/app/routes/route_paths";

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
  })
  .refine(
    (data) =>
      !data.checkInDate ||
      !data.checkOutDate ||
      new Date(data.checkOutDate) > new Date(data.checkInDate),
    { message: "Check-out must be after check-in", path: ["checkOutDate"] }
  );

type FormValues = z.infer<typeof schema>;

const ROOM_TYPES = ["Deluxe Suite", "Superior Room", "Standard Queen", "Executive Suite", "Ocean View Room", "Banquet Hall", "Grand Ballroom Suite"];

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

export function CreateQuotationScreen() {
  const router = useRouter();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    // Zod v4 coerce types cause input/output mismatch with RHF resolver typing
    resolver: zodResolver(schema) as unknown as Resolver<FormValues>,
    defaultValues: {
      numberOfRooms: 1,
      pricePerNight: 0,
      discountPercent: 0,
    },
  });

  const [checkInDate, checkOutDate, numberOfRooms, pricePerNight, discountPercent] = watch([
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

  const requiresApproval = (discountPercent || 0) > 10;

  const onSubmit = (data: FormValues) => {
    const inDate = new Date(data.checkInDate);
    const outDate = new Date(data.checkOutDate);
    const nights = Math.max(
      1,
      Math.floor((outDate.getTime() - inDate.getTime()) / (1000 * 60 * 60 * 24))
    );
    const subtotal = data.pricePerNight * nights * data.numberOfRooms;
    const discountAmount = Math.round(subtotal * data.discountPercent) / 100;
    const totalAmount = subtotal - discountAmount;

    router.push(ROUTE_PATHS.quotations);
  };

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

      <div>
        <h1 className="text-xl font-bold text-slate-800">Create Room Quotation</h1>
        <p className="text-xs text-slate-400">
          Fill in the form to generate a new room price proposal. Discounts above 10% require Manager approval.
        </p>
      </div>

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
                  <Input
                    {...register("contactName")}
                    placeholder="e.g. Emily Miller"
                    error={errors.contactName?.message}
                  />
                </div>
                <div>
                  <FieldLabel required>Email Address</FieldLabel>
                  <Input
                    {...register("email")}
                    type="email"
                    placeholder="e.g. emily@example.com"
                    error={errors.email?.message}
                  />
                </div>
                <div>
                  <FieldLabel required>Phone Number</FieldLabel>
                  <Input
                    {...register("phone")}
                    placeholder="e.g. +1 555-0100"
                    error={errors.phone?.message}
                  />
                </div>
                <div>
                  <FieldLabel required>Deal / Event Name</FieldLabel>
                  <Input
                    {...register("dealName")}
                    placeholder="e.g. Miller Wedding Booking"
                    error={errors.dealName?.message}
                  />
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
                      <option key={rt} value={rt}>
                        {rt}
                      </option>
                    ))}
                  </Select>
                </div>
                <div>
                  <FieldLabel required>Check-In Date</FieldLabel>
                  <Input
                    {...register("checkInDate")}
                    type="date"
                    error={errors.checkInDate?.message}
                  />
                </div>
                <div>
                  <FieldLabel required>Check-Out Date</FieldLabel>
                  <Input
                    {...register("checkOutDate")}
                    type="date"
                    error={errors.checkOutDate?.message}
                  />
                </div>
                <div>
                  <FieldLabel required>Number of Rooms</FieldLabel>
                  <Input
                    {...register("numberOfRooms")}
                    type="number"
                    min={1}
                    placeholder="1"
                    error={errors.numberOfRooms?.message}
                  />
                </div>
                <div className="flex items-end">
                  {pricing.nights > 0 && (
                    <span className="text-xs text-slate-500 font-semibold pb-2">
                      Duration: <strong className="text-slate-800">{pricing.nights} night{pricing.nights !== 1 ? "s" : ""}</strong>
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
                  <Input
                    {...register("pricePerNight")}
                    type="number"
                    min={0}
                    step={0.01}
                    placeholder="0.00"
                    error={errors.pricePerNight?.message}
                  />
                </div>
                <div>
                  <FieldLabel>Discount (%)</FieldLabel>
                  <Input
                    {...register("discountPercent")}
                    type="number"
                    min={0}
                    max={100}
                    step={0.1}
                    placeholder="0"
                    error={errors.discountPercent?.message}
                  />
                  {requiresApproval && (
                    <p className="mt-1 text-[10px] text-amber-600 font-semibold flex items-center gap-1">
                      <AlertCircle className="size-3" />
                      Discount &gt;10% requires Manager approval
                    </p>
                  )}
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
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </Select>
                </div>
                <div>
                  <FieldLabel required>Quote Valid Until</FieldLabel>
                  <Input
                    {...register("validUntil")}
                    type="date"
                    error={errors.validUntil?.message}
                  />
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
          </div>

          {/* Right: price summary */}
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
                    <span className="font-semibold text-slate-700">
                      ${(pricePerNight || 0).toLocaleString('en-US')}
                    </span>
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
                    <span className="font-black text-sm text-blue-700">
                      ${pricing.total.toLocaleString('en-US')}
                    </span>
                  </div>
                </div>

                <div className="pt-2">
                  <p className="text-[10px] text-slate-400 font-semibold mb-1.5">Quote Status after Save:</p>
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

                <div className="pt-2 space-y-2">
                  <Button
                    type="submit"
                    variant="primary"
                    className="w-full text-xs font-bold"
                    isLoading={isSubmitting}
                    leftIcon={<CheckCircle2 className="size-3.5" />}
                  >
                    {requiresApproval ? "Submit for Approval" : "Save as Draft"}
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