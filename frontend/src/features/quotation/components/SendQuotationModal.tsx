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
import { useSendQuotation, useQuotationEligibility } from "@/features/quotation/hooks/use_quotations";
import { Portal } from "@/components/ui/Portal";
import { RoomConfirmationPanel } from "@/features/room_request/components/RoomConfirmationPanel";
import { apiErrorMessage } from "@/services/api_error";

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

/**
 * Mirrors `EmailContactPolicy.EMAIL` on the backend, so an address this form accepts is not
 * then rejected by the server for a different reason.
 *
 * Delivery itself is never simulated here. This used to fake a failure for any address
 * containing ".fail" and a WhatsApp number ending "0000000", and report success for everything
 * else without asking the provider — so a genuinely undeliverable address showed the success
 * screen, and the magic strings were live in production.
 */
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

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
  const safeCheckInDate = escapeHtml(quote.checkInDate ?? "—");
  const safeCheckOutDate = escapeHtml(quote.checkOutDate ?? "—");
  const rawPaymentPolicy = quote.paymentPolicy ? (PAYMENT_LABELS[quote.paymentPolicy] ?? quote.paymentPolicy) : "—";
  const safePaymentPolicy = escapeHtml(rawPaymentPolicy);
  const safeExpiryDate = escapeHtml(quote.expiryDate ?? "");

  const roomLines = quote.roomLines && quote.roomLines.length > 0
    ? quote.roomLines
    : [
        {
          roomType: quote.roomType ?? "Standard Room",
          numberOfRooms: quote.numberOfRooms ?? 1,
          pricePerNight: quote.pricePerNight ?? quote.amount,
        },
      ];

  const roomTableRows = roomLines
    .map(
      (r) =>
        `<tr>
          <td style="padding:8px 4px;font-weight:600;">${escapeHtml(r.roomType ?? "Room")}</td>
          <td style="padding:8px 4px;text-align:center;">${r.numberOfRooms ?? 1}</td>
          <td style="padding:8px 4px;text-align:right;">${(r.pricePerNight ?? 0).toLocaleString("vi-VN")} ₫</td>
        </tr>`
    )
    .join("");

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
    table { width: 100%; border-collapse: collapse; margin-top: 6px; }
    th { font-size: 11px; text-transform: uppercase; color: #64748b; border-bottom: 2px solid #e2e8f0; padding: 6px 4px; text-align: left; }
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
    <span class="lbl">Check-in</span><span class="val">${safeCheckInDate}</span>
    <span class="lbl">Check-out</span><span class="val">${safeCheckOutDate}</span>
    <span class="lbl">Payment Policy</span><span class="val">${safePaymentPolicy}</span>
  </div>

  <h2>Itemized Room Breakdown</h2>
  <table>
    <thead>
      <tr>
        <th>Room Type</th>
        <th style="text-align:center;">Quantity</th>
        <th style="text-align:right;">Rate / Night</th>
      </tr>
    </thead>
    <tbody>
      ${roomTableRows}
    </tbody>
  </table>

  <h2>Pricing</h2>
  <table>
    <tr><td>Subtotal</td><td align="right">${(quote.subtotal ?? quote.amount).toLocaleString('vi-VN')} ₫</td></tr>
    <tr><td>Discount (${quote.discountPercent ?? 0}%)</td><td align="right">-${(quote.discountAmount ?? 0).toLocaleString('vi-VN')} ₫</td></tr>
    <tr class="total"><td><strong>Total Amount</strong></td><td align="right"><strong>${quote.amount.toLocaleString('vi-VN')} ₫</strong></td></tr>
  </table>
  <p style="font-size:11px;color:#94a3b8;margin-top:8px">This quotation is valid until <strong>${safeExpiryDate}</strong>. Please confirm your booking before the expiry date.</p>

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
  // Room confirmation is a condition on the quotation, not a precondition for sending it:
  // the panel below shows the Reservation team's answer so the rep can decide, and Send
  // stays available either way.
  const [roomConfirmed, setRoomConfirmed] = useState(false);

  // The server's own verdict on this quotation, so the button carries the true reason rather
  // than the user discovering it by clicking. Re-checked server-side on submit regardless.
  const { data: eligibility } = useQuotationEligibility(quote.id);

  const clearErrors = () => {
    setE3Error("");
    setE4Error("");
  };

  /**
   * Why Send cannot be used right now, or null when it can.
   *
   * Two sources, in order: the quotation's own state (status, customer), which only the server
   * knows, and the contact details in this form, which the user is editing and the server has
   * not seen. The form's copy of the address takes precedence over the customer record, so a
   * rep who types a valid address for a customer that has none is not blocked by a verdict
   * about the record.
   */
  const blockedReason: string | null = (() => {
    if (eligibility && !eligibility.send.allowed) {
      return eligibility.send.reason ?? "This quotation cannot be sent in its current state.";
    }
    if (method === "email") {
      if (!recipientEmail.trim()) {
        return "Cannot send by email: a recipient email address is required. Enter one above, or add it to the customer record.";
      }
      if (!EMAIL_REGEX.test(recipientEmail.trim())) {
        return "Cannot send by email: the recipient's email address is not valid. Correct it above and try again.";
      }
    }
    if (method === "whatsapp" && !recipientPhone.trim()) {
      return "Cannot send by WhatsApp/SMS: a recipient phone number is required. Enter one above, or add it to the customer record.";
    }
    if (!recipientName.trim()) {
      return "Cannot send: a recipient name is required.";
    }
    return null;
  })();

  const handleSend = async () => {
    clearErrors();
    // Belt and braces: the button is disabled while `blockedReason` is set, so this only
    // catches a state that changed between render and click.
    if (blockedReason) {
      setE3Error(blockedReason);
      return;
    }

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

    // POST to backend. Delivery is the provider's answer, not a guess made here: the request
    // only returns once the email has actually been handed over, and a rejected recipient or
    // an unreachable provider comes back as EMAIL_DELIVERY_FAILED with the reason.
    try {
      await sendQuotation.mutateAsync({
        id: quote.id,
        payload: {
          sendMethod: method.toUpperCase() as "EMAIL" | "WHATSAPP" | "PDF",
          recipientName,
          recipientEmail: recipientEmail || undefined,
          recipientPhone: recipientPhone || undefined,
          personalMessage: personalMessage || undefined,
        },
      });
    } catch (err) {
      setIsSending(false);
      // Surface the backend's own reason — the room gate returns distinct codes
      // (ROOM_NOT_REQUESTED, ROOM_PENDING_CONFIRMATION, ROOM_REJECTED, …) whose
      // messages tell the rep exactly what to do next.
      setE4Error(apiErrorMessage(err));
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
        `💰 Total: ${quote.amount.toLocaleString("vi-VN")} ₫`,
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
      <Portal>
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
      </Portal>
    );
  }

  return (
    <Portal>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-2xl max-h-[calc(100vh-2rem)] flex flex-col overflow-hidden bg-white rounded-xl shadow-2xl">

        {/* Header */}
        <div className="shrink-0 z-10 flex items-center justify-between px-5 py-4 border-b border-slate-100 bg-white rounded-t-xl">
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

        <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-5">
          {/* Quotation Summary */}
          <section className="rounded-lg border border-blue-100 bg-blue-50/30 p-4 space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Quotation Details &amp; Room Breakdown</p>
              <div className="flex items-center gap-2">
                <Badge variant="success" size="sm" className="text-[9px] font-bold uppercase">Approved</Badge>
                <span className="text-[10px] text-slate-400">Version {version}</span>
              </div>
            </div>

            <div className="flex flex-wrap gap-x-5 gap-y-1.5 text-xs">
              <span><span className="text-slate-400">Customer:</span> <strong className="text-slate-700">{quote.contactName}</strong></span>
              <span><span className="text-slate-400">Dates:</span> <strong className="text-slate-700">{quote.checkInDate ?? "—"} → {quote.checkOutDate ?? "—"}</strong></span>
              <span><span className="text-slate-400">Total:</span> <strong className="text-blue-700">{quote.amount.toLocaleString('vi-VN')} ₫</strong></span>
              <span><span className="text-slate-400">Discount:</span> <strong className="text-amber-600">{quote.discountPercent ?? 0}%</strong></span>
              <span><span className="text-slate-400">Valid until:</span> <strong className="text-slate-700">{quote.expiryDate}</strong></span>
            </div>

            {/* Itemized Room Details Table */}
            <div className="rounded-md border border-slate-200 bg-white overflow-hidden text-xs">
              <div className="bg-slate-50 px-3 py-1.5 border-b border-slate-200 text-[10px] font-bold text-slate-500 flex justify-between uppercase">
                <span>Room Type &amp; Rate</span>
                <span>Qty × Duration</span>
              </div>
              <div className="divide-y divide-slate-100">
                {quote.roomLines && quote.roomLines.length > 0 ? (
                  quote.roomLines.map((line, idx) => (
                    <div key={idx} className="px-3 py-2 flex items-center justify-between text-xs">
                      <div>
                        <span className="font-semibold text-slate-800">{line.roomType}</span>
                        {line.pricePerNight && (
                          <span className="text-[10px] text-slate-400 block">
                            {line.pricePerNight.toLocaleString('vi-VN')} ₫ / night
                          </span>
                        )}
                      </div>
                      <div className="text-right">
                        <span className="font-bold text-slate-700">{line.numberOfRooms ?? 1} room(s) × {line.nights ?? 1} night(s)</span>
                        {line.lineTotal && (
                          <span className="text-[11px] font-bold text-blue-600 block">
                            {line.lineTotal.toLocaleString('vi-VN')} ₫
                          </span>
                        )}
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="px-3 py-2 flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-800">{quote.roomType ?? "Standard Room"}</span>
                    <span className="font-bold text-slate-700">{quote.numberOfRooms ?? 1} room(s)</span>
                  </div>
                )}
              </div>
            </div>
          </section>

          {/* Sending is a promise of a room to the customer, so the Reservation team must
              have confirmed it first. This panel shows that state and can raise the request. */}
          <RoomConfirmationPanel quote={quote} onUsableChange={setRoomConfirmed} />

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
                  phoneOnly
                  value={recipientPhone}
                  onChange={(e) => { setRecipientPhone(e.target.value); setE3Error(""); }}
                  placeholder="e.g. 09xxxxxxxx"
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
                  className="max-w-45"
                />
              </div>
            )}
          </section>

        </div>

        {/* Action Footer */}
        <div className="shrink-0 mt-auto border-t border-slate-100 bg-white px-5 py-4 z-10">
          {/* The reason lives next to the button, not behind a click: an action the user can
              see but not use has to say why on the same screen. */}
          {blockedReason && (
            <p className="mb-2.5 flex items-start gap-1.5 text-[11px] font-medium text-amber-700">
              <AlertTriangle className="size-3.5 shrink-0 text-amber-500" />
              <span>{blockedReason}</span>
            </p>
          )}
          <div className="flex items-center justify-end gap-2">
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
              disabled={!!blockedReason}
              title={
                blockedReason ??
                (roomConfirmed
                  ? undefined
                  : "The Reservation team has not confirmed these rooms yet — you can still send.")
              }
              leftIcon={!isSending ? <Send className="size-3.5" /> : undefined}
              className="flex-1 text-xs font-bold"
            >
              {isSending ? "Sending…" : `Send via ${METHOD_CONFIG[method].label}`}
            </Button>
          </div>
        </div>
      </div>
    </div>
    </Portal>
  );
}
