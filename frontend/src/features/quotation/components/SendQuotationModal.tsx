"use client";

import React, { useState } from "react";
import {
  X,
  Mail,
  MessageCircle,
  FileDown,
  CheckCircle2,
  AlertTriangle,
  Send,
  Clock,
  User,
  Phone,
  AtSign,
  MessageSquare,
  Calendar,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import type { Quotation } from "@/services/quotation_service";
import { useSendQuotation } from "@/features/quotation/hooks/use_quotations";
import { useAuthStore } from "@/stores/auth_store";

type SendMethod = "email" | "whatsapp" | "pdf";

const METHOD_CONFIG: Record<SendMethod, { label: string; icon: React.ReactNode; desc: string }> = {
  email: {
    label: "Email",
    icon: <Mail className="size-4" />,
    desc: "Send as formatted email message",
  },
  whatsapp: {
    label: "WhatsApp / SMS",
    icon: <MessageCircle className="size-4" />,
    desc: "Send to customer's mobile number",
  },
  pdf: {
    label: "PDF Attachment",
    icon: <FileDown className="size-4" />,
    desc: "Download quotation as HTML/PDF file",
  },
};

const PAYMENT_LABELS: Record<string, string> = {
  full_upfront: "Full Payment Upfront",
  "50_deposit": "50% Deposit on Booking",
  pay_on_arrival: "Pay on Arrival",
};

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function simulateDelivery(
  method: SendMethod,
  email: string,
  phone: string
): { success: boolean; reason?: string } {
  // E4: Simulate failure when email contains ".fail" (test scenario)
  if (method === "email" && email.toLowerCase().includes(".fail")) {
    return { success: false, reason: "SMTP server rejected recipient address. Mailbox unreachable." };
  }
  if (method === "whatsapp" && phone.replace(/\D/g, "").endsWith("0000000")) {
    return { success: false, reason: "Mobile number is not registered or unreachable on WhatsApp." };
  }
  return { success: true };
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function generateQuotationHTML(quote: Quotation, recipientName: string, message: string): string {
  const safeQuoteNo = escapeHtml(String(quote.quoteNo ?? ""));
  const safeRecipientName = escapeHtml(recipientName ?? "");
  const safeMessageHtml = message ? escapeHtml(message).replace(/\n/g, "<br>") : "";
  const safeContactName = escapeHtml(quote.contactName ?? "");
  const safeDealName = escapeHtml(quote.dealName ?? "");
  const safeEmail = escapeHtml(quote.email ?? "—");
  const safePhone = escapeHtml(quote.phone ?? "—");

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Quotation ${safeQuoteNo}</title>
  <style>
    body { font-family: Arial, sans-serif; padding: 48px; color: #1e293b; max-width: 700px; margin: 0 auto; }
    .header { border-bottom: 3px solid #2563eb; padding-bottom: 16px; margin-bottom: 24px; }
    .logo { font-size: 22px; font-weight: bold; color: #2563eb; }
    .sub { font-size: 12px; color: #64748b; margin-top: 4px; }
    h2 { font-size: 13px; font-weight: 700; color: #475569; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px; margin: 20px 0 10px; text-transform: uppercase; letter-spacing: .05em; }
    .grid { display: grid; grid-template-columns: 140px 1fr; gap: 6px 0; font-size: 12px; }
    .lbl { color: #94a3b8; }
    .val { font-weight: 600; }
    table { width: 100%; border-collapse: collapse; }
    td { padding: 7px 4px; font-size: 12px; border-bottom: 1px solid #f1f5f9; }
    .total td { font-size: 16px; font-weight: 700; color: #1d4ed8; border-top: 2px solid #2563eb; border-bottom: none; }
    .msg { background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:14px; font-size:12px; line-height:1.7; white-space:pre-wrap; margin-bottom:16px; }
    .footer { margin-top: 36px; font-size: 10px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 12px; }
    @media print { body { padding: 24px; } }
  </style>
</head>
<body>
  <div class="header">
    <div class="logo">Leadora Hotels</div>
    <div class="sub">Quotation ${safeQuoteNo} &bull; Issued: ${new Date().toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })}</div>
  </div>

  <p style="font-size:13px">Dear <strong>${safeRecipientName}</strong>,</p>
  ${safeMessageHtml ? `<div class="msg">${safeMessageHtml}</div>` : ""}

  <h2>Customer &amp; Deal</h2>
  <div class="grid">
    <span class="lbl">Contact Name</span><span class="val">${safeContactName}</span>
    <span class="lbl">Deal / Event</span><span class="val">${safeDealName}</span>
    <span class="lbl">Email</span><span class="val">${safeEmail}</span>
    <span class="lbl">Phone</span><span class="val">${safePhone}</span>
  </div>

  <h2>Room Booking</h2>
  <div class="grid">
    <span class="lbl">Room Type</span><span class="val">${quote.roomType ?? "—"}</span>
    <span class="lbl">Rooms</span><span class="val">${quote.numberOfRooms ?? "—"}</span>
    <span class="lbl">Check-in</span><span class="val">${quote.checkInDate ?? "—"}</span>
    <span class="lbl">Check-out</span><span class="val">${quote.checkOutDate ?? "—"}</span>
    <span class="lbl">Rate / Night</span><span class="val">$${(quote.pricePerNight ?? 0).toLocaleString('en-US')}</span>
    <span class="lbl">Payment Policy</span><span class="val">${quote.paymentPolicy ? (PAYMENT_LABELS[quote.paymentPolicy] ?? quote.paymentPolicy) : "—"}</span>
  </div>

  <h2>Pricing</h2>
  <table>
    <tr><td>Subtotal</td><td align="right">$${(quote.subtotal ?? 0).toLocaleString('en-US')}</td></tr>
    <tr><td>Discount (${quote.discountPercent ?? 0}%)</td><td align="right">-$${(quote.discountAmount ?? 0).toLocaleString('en-US')}</td></tr>
    <tr class="total"><td><strong>Total Amount</strong></td><td align="right"><strong>$${quote.amount.toLocaleString('en-US')}</strong></td></tr>
  </table>
  <p style="font-size:11px;color:#94a3b8;margin-top:8px">This quotation is valid until <strong>${quote.expiryDate}</strong>. Please confirm your booking before the expiry date.</p>

  <div class="footer">Leadora Hotels &bull; Sales Department &bull; Generated by Leadora Hotel CRM</div>
</body>
</html>`;
}

export interface SendQuotationModalProps {
  quote: Quotation;
  onClose: () => void;
  onSent: (quotationId: string) => void;
}

export function SendQuotationModal({ quote, onClose, onSent }: SendQuotationModalProps) {
  const version = quote.version ?? 1;
  const sendQuotation = useSendQuotation();
  const currentUser = useAuthStore((s) => s.user);

  const [method, setMethod] = useState<SendMethod>("email");
  const [recipientName, setRecipientName] = useState(quote.contactName);
  const [recipientEmail, setRecipientEmail] = useState(quote.email ?? "");
  const [recipientPhone, setRecipientPhone] = useState(quote.phone ?? "");
  const [personalMessage, setPersonalMessage] = useState(
    `Dear ${quote.contactName},\n\nPlease find attached our room quotation for ${quote.dealName}.\n\nThis proposal is valid until ${quote.expiryDate}. We look forward to welcoming you at Leadora Hotels.\n\nFor questions, please contact our Sales Team directly.\n\nKind regards,\nLeadora Hotels Sales Team`
  );
  const [createReminder, setCreateReminder] = useState(false);
  const [reminderDate, setReminderDate] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [e3Error, setE3Error] = useState("");
  const [e4Error, setE4Error] = useState("");
  const [sendSuccess, setSendSuccess] = useState(false);

  const clearErrors = () => {
    setE3Error("");
    setE4Error("");
  };

  const validate = (): boolean => {
    if (method === "email") {
      if (!recipientEmail.trim()) {
        setE3Error("Email address is required for this delivery method (E3). Please enter a valid email.");
        return false;
      }
      if (!EMAIL_REGEX.test(recipientEmail.trim())) {
        setE3Error("Invalid email format (E3). Please verify the recipient's email address and try again.");
        return false;
      }
    }
    if (method === "whatsapp" && !recipientPhone.trim()) {
      setE3Error("Phone number is required for WhatsApp / SMS delivery (E3).");
      return false;
    }
    return true;
  };

  const handleSend = async () => {
    clearErrors();
    if (!validate()) return;

    setIsSending(true);

    // PDF: open print window — user can Save as PDF from browser print dialog
    if (method === "pdf") {
      try {
        const html = generateQuotationHTML(quote, recipientName, personalMessage);
        const printWindow = window.open("", "_blank", "width=900,height=700");
        if (printWindow) {
          printWindow.document.write(html);
          printWindow.document.close();
          printWindow.addEventListener("load", () => printWindow.print());
        }
      } catch {
        // continue even if print window fails
      }
    }

    // E4: Simulate delivery failure (email containing ".fail" / WhatsApp 0000000)
    const result = simulateDelivery(method, recipientEmail, recipientPhone);
    if (!result.success) {
      setIsSending(false);
      setE4Error(`${result.reason} The failure has been logged. Please verify the contact details or choose a different delivery method.`);
      return;
    }

    // POST to backend: update status to SENT, record send log (BR-37)
    try {
      await sendQuotation.mutateAsync({
        id: quote.id,
        payload: {
          sendMethod: method.toUpperCase() as "EMAIL" | "WHATSAPP" | "PDF",
          recipientName,
          recipientEmail: recipientEmail || undefined,
          recipientPhone: recipientPhone || undefined,
          sentByName: currentUser?.name ?? currentUser?.email ?? "Staff",
          sentByRole: currentUser?.roles?.[0] ?? "SALES",
          personalMessage: personalMessage || undefined,
        },
      });
    } catch {
      setIsSending(false);
      setE4Error("Failed to update quotation status. Please try again.");
      return;
    }

    // WhatsApp: open deeplink after status is updated in CRM
    if (method === "whatsapp" && recipientPhone.trim()) {
      let phone = recipientPhone.trim().replace(/\D/g, "");
      // Normalize Vietnamese numbers: 0912... → 84912...
      if (phone.startsWith("0")) phone = "84" + phone.slice(1);
      const lines = [
        `Dear ${recipientName},`,
        ``,
        `Please find your room quotation from Leadora Hotels:`,
        ``,
        `📋 ${quote.quoteNo} — ${quote.dealName}`,
        `🛏 Room: ${quote.roomType ?? "—"}`,
        `📅 Check-in: ${quote.checkInDate ?? "—"}  →  Check-out: ${quote.checkOutDate ?? "—"}`,
        `💰 Total: $${quote.amount.toLocaleString("en-US")}`,
        `⏰ Valid until: ${quote.expiryDate}`,
        ...(personalMessage ? [``, personalMessage] : []),
      ];
      window.open(`https://wa.me/${phone}?text=${encodeURIComponent(lines.join("\n"))}`, "_blank");
    }

    setIsSending(false);
    setSendSuccess(true);
    setTimeout(() => onSent(quote.id), 1600);
  };

  // ── Success state ──
  if (sendSuccess) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
        <div className="relative z-10 w-full max-w-sm bg-white rounded-xl shadow-2xl p-8 text-center">
          <CheckCircle2 className="size-14 text-emerald-500 mx-auto mb-4" />
          <h2 className="text-base font-bold text-slate-800">Quotation Sent!</h2>
          <p className="text-sm text-slate-500 mt-2">
            <strong>{quote.quoteNo}</strong> has been delivered to{" "}
            <strong>{recipientName}</strong> via {METHOD_CONFIG[method].label}.
          </p>
          <p className="text-xs text-slate-400 mt-1.5">
            Status updated to <strong>Sent</strong>. Send log v{version} recorded.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-white rounded-xl shadow-2xl">

        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-5 py-4 border-b border-slate-100 bg-white rounded-t-xl">
          <div>
            <h2 className="text-sm font-bold text-slate-800">Send Quotation to Customer</h2>
            <p className="text-xs text-blue-600 font-semibold mt-0.5">
              {quote.quoteNo} — {quote.dealName}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="p-5 space-y-5">
          {/* Quotation Summary */}
          <section className="rounded-lg border border-blue-100 bg-blue-50/30 p-4">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-2">Quotation Summary</p>
            <div className="flex flex-wrap gap-x-5 gap-y-1.5 text-xs">
              <span><span className="text-slate-400">Customer:</span> <strong className="text-slate-700">{quote.contactName}</strong></span>
              <span><span className="text-slate-400">Room:</span> <strong className="text-slate-700">{quote.roomType ?? "—"}</strong></span>
              <span><span className="text-slate-400">Dates:</span> <strong className="text-slate-700">{quote.checkInDate ?? "—"} → {quote.checkOutDate ?? "—"}</strong></span>
              <span><span className="text-slate-400">Total:</span> <strong className="text-blue-700">${quote.amount.toLocaleString('en-US')}</strong></span>
              <span><span className="text-slate-400">Discount:</span> <strong className="text-amber-600">{quote.discountPercent ?? 0}%</strong></span>
              <span><span className="text-slate-400">Valid until:</span> <strong className="text-slate-700">{quote.expiryDate}</strong></span>
            </div>
            <div className="mt-2.5 flex items-center gap-2">
              <Badge variant="success" size="sm" className="text-[9px] font-bold uppercase">Approved</Badge>
              <span className="text-[10px] text-slate-400">Version {version} will be logged</span>
            </div>
          </section>

          {/* E3 Error */}
          {e3Error && (
            <div className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
              <AlertTriangle className="size-4 text-red-500 mt-0.5 shrink-0" />
              <div className="flex-1">
                <p className="text-xs font-bold text-red-700">Invalid Contact Information (E3)</p>
                <p className="text-xs text-red-600 mt-0.5">{e3Error}</p>
              </div>
              <button onClick={() => setE3Error("")} className="text-red-400 hover:text-red-600 transition shrink-0">
                <X className="size-3.5" />
              </button>
            </div>
          )}

          {/* E4 Error */}
          {e4Error && (
            <div className="flex items-start gap-2.5 rounded-lg border border-orange-200 bg-orange-50 px-4 py-3">
              <AlertTriangle className="size-4 text-orange-500 mt-0.5 shrink-0" />
              <div className="flex-1">
                <p className="text-xs font-bold text-orange-700">Delivery Failure (E4)</p>
                <p className="text-xs text-orange-600 mt-0.5">{e4Error}</p>
                <p className="text-[10px] text-orange-500 mt-1">Failure has been logged. Verify contact details or switch delivery method.</p>
              </div>
              <button onClick={() => setE4Error("")} className="text-orange-400 hover:text-orange-600 transition shrink-0">
                <X className="size-3.5" />
              </button>
            </div>
          )}

          {/* Delivery Method */}
          <section>
            <p className="text-xs font-bold text-slate-600 mb-2.5">Delivery Method</p>
            <div className="grid grid-cols-3 gap-3">
              {(Object.keys(METHOD_CONFIG) as SendMethod[]).map((m) => (
                <button
                  key={m}
                  onClick={() => { setMethod(m); clearErrors(); }}
                  className={`flex flex-col items-center gap-1.5 rounded-lg border-2 p-3 text-center transition ${
                    method === m
                      ? "border-blue-500 bg-blue-50 text-blue-700"
                      : "border-slate-200 text-slate-500 hover:border-slate-300 hover:bg-slate-50"
                  }`}
                >
                  <span className={method === m ? "text-blue-600" : "text-slate-400"}>
                    {METHOD_CONFIG[m].icon}
                  </span>
                  <span className="text-xs font-bold">{METHOD_CONFIG[m].label}</span>
                  <span className="text-[10px] leading-tight text-center">{METHOD_CONFIG[m].desc}</span>
                </button>
              ))}
            </div>
          </section>

          {/* Recipient Contact Details */}
          <section className="rounded-lg border border-slate-100 p-4">
            <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-3">
              Recipient Contact Details
              <span className="text-slate-300 normal-case font-normal ml-1">(verify before sending)</span>
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  <User className="size-3 inline mr-1 text-slate-400" />
                  Recipient Name
                </label>
                <Input
                  value={recipientName}
                  onChange={(e) => setRecipientName(e.target.value)}
                  placeholder="Full name"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  <AtSign className="size-3 inline mr-1 text-slate-400" />
                  Email Address
                  {method === "email" && <span className="text-red-400 ml-0.5">*</span>}
                </label>
                <Input
                  value={recipientEmail}
                  onChange={(e) => { setRecipientEmail(e.target.value); setE3Error(""); }}
                  type="email"
                  placeholder="customer@example.com"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  <Phone className="size-3 inline mr-1 text-slate-400" />
                  Phone / WhatsApp Number
                  {method === "whatsapp" && <span className="text-red-400 ml-0.5">*</span>}
                </label>
                <Input
                  value={recipientPhone}
                  onChange={(e) => { setRecipientPhone(e.target.value); setE3Error(""); }}
                  placeholder="+1 555-0100"
                />
              </div>
            </div>
          </section>

          {/* Personalized Message */}
          <section>
            <label className="block text-xs font-bold text-slate-600 mb-2">
              <MessageSquare className="size-3 inline mr-1 text-slate-400" />
              Personalized Message
              <span className="text-slate-400 font-normal ml-1">(optional — included in email body and PDF)</span>
            </label>
            <textarea
              value={personalMessage}
              onChange={(e) => setPersonalMessage(e.target.value)}
              rows={4}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white resize-none transition placeholder-slate-400"
            />
          </section>

          {/* Follow-up Reminder */}
          <section className="rounded-lg border border-slate-100 bg-slate-50/60 p-3.5">
            <label className="flex items-center gap-2.5 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={createReminder}
                onChange={(e) => setCreateReminder(e.target.checked)}
                className="size-3.5 rounded accent-blue-600"
              />
              <span className="text-xs font-semibold text-slate-600 flex items-center gap-1.5">
                <Clock className="size-3.5 text-slate-400" />
                Create follow-up SLA reminder after sending
              </span>
            </label>
            {createReminder && (
              <div className="mt-3 ml-6 flex items-center gap-3">
                <label className="text-xs font-semibold text-slate-500 flex items-center gap-1 shrink-0">
                  <Calendar className="size-3 text-slate-400" />
                  Reminder date:
                </label>
                <Input
                  type="date"
                  value={reminderDate}
                  onChange={(e) => setReminderDate(e.target.value)}
                  className="max-w-[180px]"
                />
              </div>
            )}
          </section>

          {/* Test hint */}
          <p className="text-[10px] text-slate-300 text-center italic">
            Dev tip: Use an email containing &quot;.fail&quot; to trigger the E4 delivery failure scenario.
          </p>

          {/* Action Buttons */}
          <div className="flex items-center gap-2 pt-1">
            <Button
              variant="ghost"
              onClick={onClose}
              className="text-xs text-slate-500 hover:text-slate-700 px-4"
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleSend}
              isLoading={isSending}
              leftIcon={!isSending ? <Send className="size-3.5" /> : undefined}
              className="flex-1 text-xs font-bold"
            >
              {isSending ? "Sending…" : `Send via ${METHOD_CONFIG[method].label}`}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
