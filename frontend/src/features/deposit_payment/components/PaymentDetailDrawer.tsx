"use client";

/**
 * Payment detail drawer — shared `RecordDetailDrawer` (§2.11).
 *
 * **Two server rules are surfaced rather than discovered on failure:**
 * - marking a payment `PAID` requires a verification note (BR-29), so the
 *   "Confirm paid" action routes through the caller's form rather than firing;
 * - cancelling invalidates the VietQR link permanently, which the caller's
 *   confirm dialog states explicitly.
 *
 * A `PAID` payment offers neither action — the server refuses both — so they are
 * simply absent instead of shown-then-403'd (§12.13).
 *
 * The QR block, checkout link and cash instruction are payment-specific and live
 * here rather than in the caller, so the screen supplies only *behaviour*
 * (download, print, copy) and never layout.
 */

import * as React from "react";
import {
  AlertTriangle,
  Banknote,
  CalendarDays,
  Copy,
  CreditCard,
  Download,
  ExternalLink,
  FileText,
  Hash,
  Landmark,
  QrCode,
  StickyNote,
  User,
} from "lucide-react";

import { Button } from "@/components/ui/Button";
import {
  RecordDetailDrawer,
  formatDetailDate,
  formatDetailDateTime,
  formatVnd,
  type DetailActionSpec,
  type DetailSectionSpec,
} from "@/components/ui/record-drawer";
import type { Payment } from "@/services/deposit_payment_service";

/** `notes` doubles as the checkout URL on gateway-backed methods. */
function checkoutLink(payment: Payment): string | null {
  const notes = payment.notes?.trim();
  if (!notes || !notes.startsWith("http")) return null;
  const method = (payment.paymentMethod ?? "").toUpperCase();
  return method === "TRANSFER" || method === "CARD" ? notes : null;
}

export function PaymentDetailDrawer({
  payment,
  onOpenChange,
  actions = [],
  onDownloadQr,
  onPrintReceipt,
  onCopyLink,
  children,
}: {
  payment: Payment | null;
  onOpenChange: (open: boolean) => void;
  actions?: DetailActionSpec[];
  onDownloadQr?: (url: string, paymentId: string) => void;
  onPrintReceipt?: (payment: Payment) => void;
  onCopyLink?: (text: string) => void;
  /** Inline forms — e.g. the manual PAID verification note. */
  children?: React.ReactNode;
}) {
  // Captured once per mount rather than read during render: `Date.now()` in a
  // render body makes the render impure — two renders of the same props could
  // disagree — which React Compiler rejects. An overdue flag does not need
  // sub-session precision, and the list refetches on every mutation anyway.
  const [now] = React.useState(() => Date.now());

  if (!payment) {
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />;
  }

  const status = (payment.status ?? "").toUpperCase();
  const method = (payment.paymentMethod ?? "").toUpperCase();
  const isPaid = status === "PAID";
  const isPending = status === "PENDING";
  const link = checkoutLink(payment);

  // A due date only means something while the payment is still outstanding.
  const overdue =
    isPending &&
    !!payment.dueDate &&
    new Date(payment.dueDate).getTime() < now;

  const sections: DetailSectionSpec[] = [];

  // Bring VietQR to the very top (under actions)
  if (isPending && method === "TRANSFER" && payment.qrCodeUrl) {
    const qrUrl = payment.qrCodeUrl;
    sections.push({
      title: "VietQR",
      content: (
        <div className="flex flex-col items-center gap-3 p-4">
          <p className="flex items-center gap-1.5 text-[12px] text-muted-foreground">
            <QrCode className="size-3.5" />
            Scan with any banking app
          </p>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={qrUrl}
            alt="VietQR payment code"
            className="size-72 max-w-full rounded-md border border-border bg-white object-contain p-1"
          />
          <div className="flex w-full flex-wrap justify-center gap-2">
            {onDownloadQr && (
              <Button
                size="sm"
                variant="outline"
                leftIcon={<Download className="size-3.5" />}
                onClick={() => onDownloadQr(qrUrl, payment.paymentId)}
              >
                Download QR
              </Button>
            )}
            {onPrintReceipt && (
              <Button
                size="sm"
                variant="primary"
                leftIcon={<FileText className="size-3.5" />}
                onClick={() => onPrintReceipt(payment)}
              >
                Export QR form
              </Button>
            )}
          </div>
        </div>
      ),
    });
  }

  sections.push(
    {
      title: "Payment",
      rows: [
        { label: "Amount", icon: Banknote, value: formatVnd(payment.amount) },
        {
          label: "Type",
          value: payment.paymentType === "DEPOSIT" ? "Deposit" : "Full payment",
        },
        { label: "Method", icon: Landmark, value: payment.paymentMethod },
        {
          label: "Due date",
          icon: CalendarDays,
          value: formatDetailDate(payment.dueDate),
        },
        { label: "Paid at", value: formatDetailDateTime(payment.paidAt) },
      ],
    },
    {
      title: "Booking",
      rows: [
        { label: "Booking", value: payment.bookingCode },
        { label: "Guest", icon: User, value: payment.customerName },
        { label: "Requested by", value: payment.createdByName },
        { label: "Created", value: formatDetailDateTime(payment.createdAt) },
      ],
    },
    {
      title: "Gateway",
      rows: [
        { label: "Provider", value: payment.gatewayProvider },
        {
          label: "Transaction ref",
          value: payment.gatewayTransactionId ? (
            <span className="numeric break-all">{payment.gatewayTransactionId}</span>
          ) : null,
        },
      ],
    }
  );

  if (link) {
    sections.push({
      title: "Checkout link",
      content: (
        <div className="flex items-center gap-2 p-3">
          <input
            readOnly
            value={link}
            aria-label="Checkout link"
            className="min-w-0 flex-1 select-all truncate rounded-md border border-border bg-surface-2 px-2.5 py-1.5 text-[12px] text-foreground focus-ring"
          />
          {onCopyLink && (
            <Button
              size="icon-sm"
              variant="outline"
              aria-label="Copy checkout link"
              onClick={() => onCopyLink(link)}
            >
              <Copy className="size-3.5" />
            </Button>
          )}
          <a
            href={link}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Open checkout link"
            className="grid size-7 shrink-0 place-items-center rounded-md border border-border text-brand-600 transition-colors hover:bg-surface-2 dark:text-brand-500"
          >
            <ExternalLink className="size-3.5" />
          </a>
        </div>
      ),
    });
  }

  // On a settled payment `notes` is the audit reference, not a URL.
  if (isPaid && payment.notes) {
    sections.push({
      title: "Verification",
      rows: [
        { label: "Reference", icon: StickyNote, value: payment.notes, block: true },
      ],
    });
  }

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      avatarIcon={CreditCard}
      title={formatVnd(payment.amount)}
      subtitle={{
        icon: Hash,
        text: payment.bookingCode ?? payment.paymentId.slice(0, 8).toUpperCase(),
      }}
      status={{ domain: "payment", value: payment.status }}
      recordId={payment.paymentId}
      actions={actions}
      notice={
        overdue
          ? { tone: "warning", text: "This payment is past its due date." }
          : undefined
      }
      sections={sections}
    >
      {children}

      {/* Cash cannot be settled by the gateway — someone has to collect it and
          then record the confirmation, so the instruction leads the panel. */}
      {isPending && method === "CASH" && (
        <p className="flex gap-2 rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-[12px] text-warning">
          <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
          <span>
            Collect {formatVnd(payment.amount)} from the guest at the front desk,
            then use <strong>Confirm paid</strong> to settle this request.
          </span>
        </p>
      )}
    </RecordDetailDrawer>
  );
}
