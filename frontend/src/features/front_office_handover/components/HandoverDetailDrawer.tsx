"use client";

/**
 * Arrival / operational handover detail drawer — shared `RecordDetailDrawer`
 * (§2.11).
 *
 * **BR-27 is enforced structurally.** Front Office may set arrival readiness and
 * nothing else: they cannot edit the booking, quotation or deal behind it. This
 * drawer therefore renders the handover's information read-only and exposes
 * *only* readiness actions, supplied by the caller. There is no code path here
 * that could write a booking field, which is a stronger guarantee than
 * disabling a control.
 */

import * as React from "react";
import {
  BedDouble,
  CalendarDays,
  CreditCard,
  MessageSquareWarning,
  Sparkles,
  StickyNote,
  User,
} from "lucide-react";

import {
  RecordDetailDrawer,
  formatDetailDate,
  formatDetailDateTime,
  type DetailActionSpec,
} from "@/components/ui/record-drawer";
import { StatusPill } from "@/components/ui/status-pill";
import type { ArrivalHandover } from "@/services/arrival_handover_service";

export function HandoverDetailDrawer({
  handover,
  onOpenChange,
  actions = [],
  children,
}: {
  handover: ArrivalHandover | null;
  onOpenChange: (open: boolean) => void;
  /** Readiness transitions only — see BR-27 in the file header. */
  actions?: DetailActionSpec[];
  /** Readiness form, rendered above the read-only sections. */
  children?: React.ReactNode;
}) {
  if (!handover) {
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />;
  }

  const needsClarification =
    (handover.readinessStatus ?? "").toUpperCase() === "NEED_CLARIFICATION";

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      avatarIcon={BedDouble}
      title={handover.customerName || handover.bookingCode || "Arrival"}
      subtitle={{ icon: BedDouble, text: handover.bookingCode ?? "—" }}
      status={{ domain: "readiness", value: handover.readinessStatus }}
      badges={
        handover.status ? (
          <StatusPill size="sm" domain="handover" value={handover.status} />
        ) : undefined
      }
      recordId={handover.handoverId}
      actions={actions}
      notice={
        // A clarification request is a question aimed at Sales/Reservation — it
        // is the reason the arrival is blocked, so it leads.
        needsClarification && handover.clarificationNote
          ? { tone: "danger", text: handover.clarificationNote }
          : undefined
      }
      sections={[
        {
          title: "Arrival",
          rows: [
            {
              label: "Check-in",
              icon: CalendarDays,
              value: formatDetailDate(handover.checkInDate),
            },
            { label: "Check-out", value: formatDetailDate(handover.checkOutDate) },
            { label: "Guest", icon: User, value: handover.customerName },
            { label: "Phone", value: handover.customerPhone },
          ],
        },
        {
          title: "Rooms",
          content:
            handover.rooms && handover.rooms.length > 0 ? (
              <ul className="divide-y divide-border">
                {handover.rooms.map((r, i) => (
                  <li
                    key={i}
                    className="flex items-center justify-between gap-3 px-3 py-2.5 text-[12.5px]"
                  >
                    <span className="min-w-0 truncate text-foreground">
                      {r.productName ?? "Room"}
                      {r.roomNumber ? ` · ${r.roomNumber}` : ""}
                    </span>
                    {r.quantity != null && (
                      <span className="numeric shrink-0 text-muted-foreground">
                        ×{r.quantity}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-3 py-2.5 text-[12.5px] text-muted-foreground">
                {handover.roomSummary || "—"}
              </p>
            ),
        },
        {
          title: "Handover notes",
          rows: [
            {
              label: "Special requests",
              icon: StickyNote,
              value: handover.specialRequests,
              block: true,
            },
            {
              label: "Room preferences",
              icon: BedDouble,
              value: handover.roomPreferences,
              block: true,
            },
            {
              label: "VIP notes",
              icon: Sparkles,
              value: handover.vipNotes,
              block: true,
            },
            {
              label: "Operational notes",
              icon: MessageSquareWarning,
              value: handover.operationalNotes,
              block: true,
            },
          ],
        },
        {
          title: "Record",
          rows: [
            {
              label: "Payment ref",
              icon: CreditCard,
              value: handover.paymentReference,
            },
            { label: "Submitted", value: formatDetailDateTime(handover.submittedAt) },
            {
              label: "Acknowledged",
              value: formatDetailDateTime(handover.acknowledgedAt),
            },
            { label: "Last updated by", value: handover.updatedByName },
          ],
        },
      ]}
    >
      {children}
    </RecordDetailDrawer>
  );
}
