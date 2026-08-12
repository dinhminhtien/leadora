"use client";

import React, { useState, useEffect } from "react";
import { useSearchParams, useParams } from "next/navigation";
import {
  FileText,
  Calendar,
  DollarSign,
  ShieldCheck,
  Mail,
  CheckCircle,
  AlertCircle,
  ArrowRight,
  Clock,
  User,
  Phone,
  Tag,
  Info,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/shared/components/LoadingState";
import {
  usePublicQuotation,
  usePublicRequestQuotationOtp,
  usePublicConfirmQuotationOtp,
  usePublicRejectQuotation,
} from "@/features/quotation/hooks/use_quotations";

export default function PublicQuotationPortalPage() {
  const params = useParams();
  const searchParams = useSearchParams();

  const id = params.id as string;
  const token = searchParams.get("token") || "";

  const { data: quotation, isLoading, error, refetch } = usePublicQuotation(id, token);
  const requestOtpMutation = usePublicRequestQuotationOtp();
  const confirmOtpMutation = usePublicConfirmQuotationOtp();
  const rejectMutation = usePublicRejectQuotation();

  const [agreed, setAgreed] = useState(false);
  const [step, setStep] = useState<"inspect" | "otp" | "success" | "reject" | "rejected">("inspect");
  const [otpCode, setOtpCode] = useState("");
  const [otpError, setOtpError] = useState("");
  const [otpSuccessMsg, setOtpSuccessMsg] = useState("");
  const [rejectReason, setRejectReason] = useState("");
  const [rejectError, setRejectError] = useState("");

  useEffect(() => {
    if (
      quotation?.status === "accepted_by_customer" ||
      quotation?.status === "booking_request" ||
      quotation?.status === "converted" ||
      quotation?.status === "reservation_pending" ||
      quotation?.status === "reservation_rejected"
    ) {
      setStep("success");
    } else if (quotation?.status === "rejected") {
      setStep("rejected");
    }
  }, [quotation]);

  if (isLoading) {
    return <LoadingState label="Retrieving secure quotation details..." />;
  }

  if (error || !quotation) {
    return (
      <Card className="max-w-md w-full border-red-100 shadow-2xl bg-white rounded-2xl overflow-hidden p-6 text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600 mb-4">
          <AlertCircle className="size-6" />
        </div>
        <CardTitle className="text-base font-bold text-slate-950 mb-2">Link Invalid or Expired</CardTitle>
        <p className="text-xs text-slate-500 leading-relaxed mb-6">
          This secure link has either expired, been updated to a newer revision, or does not exist. Please check with your salesperson to receive a fresh quotation link.
        </p>
        <Button variant="secondary" onClick={() => refetch()} className="w-full">
          Reload Quotation
        </Button>
      </Card>
    );
  }

  const handleRequestOtp = async () => {
    if (!agreed) return;
    setOtpError("");
    setOtpSuccessMsg("");
    try {
      await requestOtpMutation.mutateAsync({ id, token });
      setOtpSuccessMsg("A 6-digit confirmation code has been sent to your email.");
      setStep("otp");
    } catch (err: any) {
      setOtpError(err?.response?.data?.message || "Could not dispatch verification code. Please try again.");
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otpCode || otpCode.length < 6) {
      setOtpError("Please enter a valid 6-digit verification code.");
      return;
    }
    setOtpError("");
    try {
      await confirmOtpMutation.mutateAsync({ id, token, otpCode });
      setStep("success");
    } catch (err: any) {
      setOtpError(err?.response?.data?.message || "Invalid or expired OTP code.");
    }
  };

  const handleRejectQuotation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectReason || rejectReason.trim().length === 0) {
      setRejectError("Please enter a reason for rejecting the quotation.");
      return;
    }
    setRejectError("");
    try {
      await rejectMutation.mutateAsync({ id, token, reason: rejectReason });
      setStep("rejected");
    } catch (err: any) {
      setRejectError(err?.response?.data?.message || "Could not submit rejection. Please try again.");
    }
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "—";
    try {
      return new Date(dateStr).toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch (e) {
      return dateStr;
    }
  };

  const displayStatus = (status: string) => {
    return status.replace(/_/g, " ").toUpperCase();
  };

  return (
    <div className="max-w-4xl w-full grid grid-cols-1 md:grid-cols-5 gap-6 p-1">
      {/* Quotation Details Panel */}
      <div className="md:col-span-3 space-y-6">
        <Card className="border-slate-100 shadow-xl bg-white rounded-2xl overflow-hidden">
          <CardHeader className="bg-slate-50 border-b border-slate-100 p-5">
            <div className="flex items-center justify-between">
              <span className="inline-flex items-center gap-1.5 text-[11px] font-bold text-blue-600 uppercase tracking-wider">
                <FileText className="size-3.5" />
                Customer Portal
              </span>
              <Badge variant="primary" className="font-bold text-[9px] px-2 py-0.5 uppercase bg-blue-50 text-blue-700 border border-blue-100">
                {displayStatus(quotation.status)}
              </Badge>
            </div>
            <CardTitle className="text-lg font-black text-slate-950 mt-2">
              Quotation {quotation.quoteNo}
            </CardTitle>
            <div className="flex flex-wrap items-center gap-3 text-[11px] text-slate-400 mt-1">
              <span>Version {quotation.version || 1}</span>
              <span>·</span>
              <span className="flex items-center gap-1">
                <Clock className="size-3" /> Valid until: {formatDate(quotation.validUntil)}
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-6 space-y-6">
            {/* Customer Details */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <User className="size-3" /> Customer Name
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">{quotation.contactName || "—"}</span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <Mail className="size-3" /> Email Address
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block select-all">{quotation.email || "—"}</span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <Calendar className="size-3" /> Check-in Date
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">
                  {formatDate(quotation.checkInDate)}
                </span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <Calendar className="size-3" /> Check-out Date
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">
                  {formatDate(quotation.checkOutDate)}
                </span>
              </div>
            </div>

            {/* Selected Accommodations */}
            <div className="space-y-3">
              <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">
                Selected Accommodations
              </span>
              <div className="border border-slate-100 rounded-xl overflow-hidden divide-y divide-slate-100">
                {quotation.roomLines && quotation.roomLines.length > 0 ? (
                  quotation.roomLines.map((line, idx) => (
                    <div key={idx} className="p-3.5 bg-slate-50/30 flex items-center justify-between text-xs gap-4">
                      <div>
                        <span className="font-bold text-slate-800 block">{line.roomType}</span>
                        <span className="text-slate-400 text-[10px] mt-0.5 block">
                          {line.numberOfRooms} Room{line.numberOfRooms > 1 ? "s" : ""} · {line.nights || quotation.nights || 0} Night{(line.nights || quotation.nights || 0) > 1 ? "s" : ""}
                        </span>
                      </div>
                      <div className="text-right shrink-0">
                        <span className="font-medium text-slate-500 block">
                          {line.pricePerNight ? `${line.pricePerNight.toLocaleString("vi-VN")} ₫ / night` : ""}
                        </span>
                        <span className="font-bold text-slate-950 block mt-0.5">
                          {line.lineTotal ? `${line.lineTotal.toLocaleString("vi-VN")} ₫` : ""}
                        </span>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="p-3.5 bg-slate-50/30 flex items-center justify-between text-xs gap-4">
                    <div>
                      <span className="font-bold text-slate-800 block">{quotation.roomType || "Room Option"}</span>
                      <span className="text-slate-400 text-[10px] mt-0.5 block">
                        {quotation.numberOfRooms || 0} Room(s) · {quotation.nights || 0} Night(s)
                      </span>
                    </div>
                    <div className="text-right shrink-0">
                      <span className="font-medium text-slate-500 block">
                        {quotation.pricePerNight ? `${quotation.pricePerNight.toLocaleString("vi-VN")} ₫ / night` : ""}
                      </span>
                      <span className="font-bold text-slate-950 block mt-0.5">
                        {quotation.totalAmount ? `${quotation.totalAmount.toLocaleString("vi-VN")} ₫` : ""}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Pricing Summary */}
            <div className="bg-slate-50 p-4 rounded-xl border border-slate-100 space-y-2.5 text-xs">
              <div className="flex justify-between text-slate-500">
                <span>Subtotal</span>
                <span>{quotation.subtotal?.toLocaleString("vi-VN")} ₫</span>
              </div>
              {quotation.discountPercent && quotation.discountPercent > 0 ? (
                <div className="flex justify-between text-emerald-600 font-medium">
                  <span className="flex items-center gap-1">
                    <Tag className="size-3.5" />
                    Discount ({quotation.discountPercent}%)
                  </span>
                  <span>-{quotation.discountAmount?.toLocaleString("vi-VN")} ₫</span>
                </div>
              ) : null}
              <div className="pt-2.5 border-t border-slate-200/80 flex justify-between items-center text-sm font-black text-slate-900">
                <span>Total Amount</span>
                <span className="text-base text-blue-600">
                  {quotation.totalAmount?.toLocaleString("vi-VN")} ₫
                </span>
              </div>
            </div>

            {/* Payment & Cancellation Policies */}
            {quotation.paymentPolicy && (
              <div className="p-4 bg-amber-50/50 border border-amber-100/70 rounded-xl text-xs space-y-1">
                <span className="font-bold text-amber-900 block flex items-center gap-1">
                  <Info className="size-3.5 text-amber-700" />
                  Policy &amp; Terms
                </span>
                <p className="text-amber-800/85 leading-relaxed">{quotation.paymentPolicy}</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Signature Portal Steps */}
      <div className="md:col-span-2 flex flex-col">
        <Card className="border-slate-100 shadow-xl bg-white rounded-2xl overflow-hidden flex flex-col flex-1">
          <CardHeader className="bg-slate-50/70 border-b border-slate-100 p-5">
            <CardTitle className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <ShieldCheck className="size-4.5 text-blue-600" />
              Acceptance Verification
            </CardTitle>
          </CardHeader>
          <CardContent className="p-5 flex-1 flex flex-col justify-between">
            {step === "inspect" && (
              <div className="space-y-6">
                <div className="text-xs text-slate-500 leading-relaxed">
                  Please review the accommodation offer and terms on the left. Once you are satisfied with this quote, tick the checkbox below to request your secure confirmation OTP code.
                </div>
                <div className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl border border-slate-100">
                  <input
                    type="checkbox"
                    id="agree-checkbox"
                    checked={agreed}
                    onChange={(e) => setAgreed(e.target.checked)}
                    className="mt-0.5 size-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                  />
                  <label htmlFor="agree-checkbox" className="text-[11px] font-medium text-slate-600 leading-normal cursor-pointer select-none">
                    I confirm that I have reviewed the check-in dates, rates, and values, and I agree to proceed with booking this quotation.
                  </label>
                </div>
                <div className="flex flex-col gap-2 mt-4">
                  <Button
                    variant="primary"
                    className="w-full"
                    onClick={handleRequestOtp}
                    disabled={!agreed}
                    isLoading={requestOtpMutation.isPending}
                    rightIcon={<ArrowRight className="size-4" />}
                  >
                    Accept Quotation
                  </Button>
                  <Button
                    variant="outline"
                    className="w-full border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700 mt-1"
                    onClick={() => {
                      setStep("reject");
                      setRejectError("");
                      setRejectReason("");
                    }}
                  >
                    Reject Offer
                  </Button>
                </div>
              </div>
            )}

            {step === "otp" && (
              <form onSubmit={handleVerifyOtp} className="space-y-5">
                <div className="flex items-start gap-2.5 p-3.5 bg-blue-50/60 border border-blue-100 rounded-xl text-[11px] text-blue-700 leading-normal">
                  <Mail className="size-4.5 text-blue-600 shrink-0 mt-0.5" />
                  <div>
                    {otpSuccessMsg || "A verification OTP has been dispatched to your email address."}
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">Verification OTP Code</label>
                  <input
                    type="text"
                    maxLength={6}
                    placeholder="Enter 6-digit OTP"
                    value={otpCode}
                    onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))}
                    className="w-full text-center tracking-widest text-lg font-black py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition bg-slate-50/50"
                  />
                </div>

                {otpError && (
                  <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 p-3 rounded-lg border border-red-100">
                    <AlertCircle className="size-4 shrink-0" />
                    <span>{otpError}</span>
                  </div>
                )}

                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    className="flex-1"
                    type="button"
                    onClick={() => setStep("inspect")}
                  >
                    Back
                  </Button>
                  <Button
                    variant="primary"
                    className="flex-1 bg-emerald-600 hover:bg-emerald-700"
                    type="submit"
                    isLoading={confirmOtpMutation.isPending}
                  >
                    Verify &amp; Accept
                  </Button>
                </div>
              </form>
            )}

            {step === "success" && (
              <div className="text-center py-6 space-y-4">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 shadow-inner">
                  <CheckCircle className="size-8" />
                </div>
                <div>
                  <h4 className="text-base font-bold text-slate-900">Quotation Accepted</h4>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed px-4">
                    The quotation terms have been successfully accepted and verified via OTP. The booking setup is now in progress. You may close this tab.
                  </p>
                </div>
                <div className="pt-4 border-t border-slate-100">
                  <div className="text-[10px] text-slate-400">
                    Verification Code Authenticated. Status: <span className="font-bold text-emerald-600 uppercase">{quotation.status}</span>
                  </div>
                </div>
              </div>
            )}

            {step === "reject" && (
              <form onSubmit={handleRejectQuotation} className="space-y-5">
                <div className="flex items-start gap-2.5 p-3.5 bg-red-50/60 border border-red-100 rounded-xl text-[11px] text-red-700 leading-normal">
                  <AlertCircle className="size-4.5 text-red-600 shrink-0 mt-0.5" />
                  <div>
                    Please tell us why you are rejecting this offer. Your feedback helps us improve our service and offer you a better deal.
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">Reason for Rejection</label>
                  <textarea
                    rows={4}
                    placeholder="E.g., Price is too high, dates have changed, or selected another option..."
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    className="w-full text-xs p-3 rounded-xl border border-slate-200 focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 transition bg-slate-50/50 resize-none"
                    required
                  />
                </div>

                {rejectError && (
                  <div className="flex items-center gap-2 text-xs text-red-600 bg-red-50 p-3 rounded-lg border border-red-100">
                    <AlertCircle className="size-4 shrink-0" />
                    <span>{rejectError}</span>
                  </div>
                )}

                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    className="flex-1"
                    type="button"
                    onClick={() => setStep("inspect")}
                  >
                    Back
                  </Button>
                  <Button
                    variant="primary"
                    className="flex-1 bg-red-600 hover:bg-red-700 text-white border-none"
                    type="submit"
                    isLoading={rejectMutation.isPending}
                  >
                    Submit Rejection
                  </Button>
                </div>
              </form>
            )}

            {step === "rejected" && (
              <div className="text-center py-6 space-y-4">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-red-600 shadow-inner">
                  <AlertCircle className="size-8" />
                </div>
                <div>
                  <h4 className="text-base font-bold text-slate-900">Quotation Rejected</h4>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed px-4">
                    You have rejected this quotation offer. We appreciate your feedback and our sales representative will review it to see if we can provide a revised proposal.
                  </p>
                </div>
                {quotation.notes && (
                  <div className="p-3 bg-slate-50 border border-slate-100 rounded-xl text-left text-[11px] text-slate-600">
                    <span className="font-bold block text-slate-800 mb-1">Your comments:</span>
                    {quotation.notes}
                  </div>
                )}
                <div className="pt-4 border-t border-slate-100">
                  <div className="text-[10px] text-slate-400">
                    Quotation status: <span className="font-bold text-red-600 uppercase">{quotation.status}</span>
                  </div>
                </div>
              </div>
            )}

            {/* Footer lock indicator */}
            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-center gap-1.5 text-[10px] text-slate-400">
              <ShieldCheck className="size-3.5 text-emerald-500" />
              Secured with AES-256 OTP authentication
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
