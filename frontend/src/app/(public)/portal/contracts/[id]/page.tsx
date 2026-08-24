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
  RefreshCw,
  ExternalLink,
  ChevronRight,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/shared/components/LoadingState";
import {
  usePublicContract,
  usePublicRequestOtp,
  usePublicConfirmOtp,
} from "@/features/contract/hooks/use_contracts";

export default function PublicContractPortalPage() {
  const params = useParams();
  const searchParams = useSearchParams();

  const id = params.id as string;
  const token = searchParams.get("token") || "";

  const { data: contract, isLoading, error, refetch } = usePublicContract(id, token);
  const requestOtpMutation = usePublicRequestOtp();
  const confirmOtpMutation = usePublicConfirmOtp();

  const [agreed, setAgreed] = useState(false);
  const [step, setStep] = useState<"inspect" | "otp" | "success">("inspect");
  const [otpCode, setOtpCode] = useState("");
  const [otpError, setOtpError] = useState("");
  const [otpSuccessMsg, setOtpSuccessMsg] = useState("");

  useEffect(() => {
    if (contract?.status === "ACKNOWLEDGED" || contract?.status === "ACTIVE") {
      setStep("success");
    }
  }, [contract]);

  if (isLoading) {
    return <LoadingState label="Loading secured contract terms..." />;
  }

  if (error || !contract) {
    return (
      <Card className="max-w-md w-full border-red-100 shadow-2xl bg-white rounded-2xl overflow-hidden p-6 text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600 mb-4">
          <AlertCircle className="size-6" />
        </div>
        <CardTitle className="text-base font-bold text-slate-950 mb-2">Invalid or Expired Link</CardTitle>
        <p className="text-xs text-slate-500 leading-relaxed mb-6">
          This secure link has either expired, been superseded by a newer version, or is incorrect. Please contact your sales representative to receive a new link.
        </p>
        <Button variant="secondary" onClick={() => refetch()} className="w-full">
          Retry Connection
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
      setOtpSuccessMsg("A 6-digit verification code has been dispatched to your email address.");
      setStep("otp");
    } catch (err: any) {
      setOtpError(err?.response?.data?.message || "Failed to send verification code. Please try again.");
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const code = otpCode.trim();
    if (!/^\d{6}$/.test(code)) {
      setOtpError("Please enter a valid 6-digit verification code.");
      return;
    }
    setOtpError("");
    try {
      await confirmOtpMutation.mutateAsync({ id, token, otpCode: code });
      setStep("success");
    } catch (err: any) {
      setOtpError(err?.response?.data?.message || "Invalid or expired OTP code.");
    }
  };

  return (
    <div className="max-w-4xl w-full grid grid-cols-1 md:grid-cols-5 gap-6 p-1">
      {/* Contract Details Panel */}
      <div className="md:col-span-3 space-y-6">
        <Card className="border-slate-100 shadow-xl bg-white rounded-2xl overflow-hidden">
          <CardHeader className="bg-slate-50 border-b border-slate-100 p-5">
            <div className="flex items-center justify-between">
              <span className="inline-flex items-center gap-1.5 text-[11px] font-bold text-blue-600 uppercase tracking-wider">
                <FileText className="size-3.5" />
                Secure Portal
              </span>
              <Badge variant="primary" className="font-bold text-[9px] px-2 py-0.5 uppercase">
                {contract.status}
              </Badge>
            </div>
            <CardTitle className="text-lg font-black text-slate-950 mt-2">
              Agreement {contract.contractCode}
            </CardTitle>
            <p className="text-[11px] text-slate-400 mt-1">
              Version {contract.version} · Date generated: {new Date(contract.createdAt).toLocaleDateString()}
            </p>
          </CardHeader>
          <CardContent className="p-6 space-y-6">
            {/* Commercial terms */}
            <div className="grid grid-cols-2 gap-4">
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Customer Name</span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">{contract.customerName || "—"}</span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Billing Method</span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">
                  {contract.billingMethod.replace("_", " ")}
                </span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <Calendar className="size-3" /> Check-in
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">
                  {contract.commercialSnapshot?.checkInDate || "—"}
                </span>
              </div>
              <div className="p-3.5 bg-slate-50/70 border border-slate-100 rounded-xl">
                <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block flex items-center gap-1">
                  <Calendar className="size-3" /> Check-out
                </span>
                <span className="text-xs font-bold text-slate-900 mt-1 block">
                  {contract.commercialSnapshot?.checkOutDate || "—"}
                </span>
              </div>
            </div>

            {/* Total Amount card */}
            <div className="bg-blue-600 p-5 rounded-2xl text-white flex items-center justify-between shadow-md">
              <div>
                <span className="text-[10px] text-blue-100 font-bold uppercase tracking-wider">Total Contract Value</span>
                <span className="text-2xl font-black block mt-1">
                  {contract.commercialSnapshot?.totalAmount?.toLocaleString("vi-VN")} ₫
                </span>
              </div>
              <DollarSign className="size-8 text-blue-200/80 shrink-0" />
            </div>

            {/* Document link */}
            <div className="border border-slate-100 rounded-xl p-4 flex items-center justify-between shadow-sm">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-red-50 text-red-600 rounded-lg shrink-0">
                  <FileText className="size-5" />
                </div>
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Read Full Contract PDF</span>
                  <span className="text-[10px] text-slate-400 block mt-0.5">Official legal terms and policies</span>
                </div>
              </div>
              {contract.pdfUrl ? (
                <a
                  href={contract.pdfUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700 bg-blue-50/70 hover:bg-blue-50 px-3 py-1.5 rounded-lg transition"
                >
                  View PDF
                  <ExternalLink className="size-3.5" />
                </a>
              ) : (
                <span className="text-xs text-slate-400 italic">Processing document...</span>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Signature Portal Steps */}
      <div className="md:col-span-2 flex flex-col">
        <Card className="border-slate-100 shadow-xl bg-white rounded-2xl overflow-hidden flex flex-col flex-1">
          <CardHeader className="bg-slate-50/70 border-b border-slate-100 p-5">
            <CardTitle className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <ShieldCheck className="size-4.5 text-blue-600" />
              Contract Sign-off
            </CardTitle>
          </CardHeader>
          <CardContent className="p-5 flex-1 flex flex-col justify-between">
            {step === "inspect" && (
              <div className="space-y-6">
                <div className="text-xs text-slate-500 leading-relaxed">
                  Please review the commercial details on the left and the complete legal details inside the PDF document. Once you are ready, check the statement below to request your secure OTP sign-off code.
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
                    I confirm that I have read, understood, and accept all the terms, room counts, cancellation policies, and values outlined in the contract document.
                  </label>
                </div>
                <Button
                  variant="primary"
                  className="w-full mt-4"
                  onClick={handleRequestOtp}
                  disabled={!agreed}
                  isLoading={requestOtpMutation.isPending}
                  rightIcon={<ArrowRight className="size-4" />}
                >
                  Request OTP Verification
                </Button>
              </div>
            )}

            {step === "otp" && (
              <form onSubmit={handleVerifyOtp} className="space-y-5">
                <div className="flex items-start gap-2.5 p-3.5 bg-blue-50/60 border border-blue-100 rounded-xl text-[11px] text-blue-700 leading-normal">
                  <Mail className="size-4.5 text-blue-600 shrink-0 mt-0.5" />
                  <div>
                    {otpSuccessMsg || "A verification OTP code was sent to your registered email address."}
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
                    Sign Contract
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
                  <h4 className="text-base font-bold text-slate-900">Signed &amp; Acknowledged</h4>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed px-4">
                    The contract terms have been successfully signed and verified via OTP. The booking setup is now in progress. You may close this tab.
                  </p>
                </div>
                <div className="pt-4 border-t border-slate-100">
                  <div className="text-[10px] text-slate-400">
                    Verification Code Authenticated. Status: <span className="font-bold text-emerald-600 uppercase">{contract.status}</span>
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
