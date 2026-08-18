"use client";

/**
 * Booking detail drawer — shared `RecordDetailDrawer` (§2.11), same surface as
 * every other module's detail.
 *
 * **Role-laned actions (spec §4.10 / Guard C4–C5).** A booking's legal
 * transitions depend on *who* is acting: Sales may withdraw a booking but never
 * confirm one; Reservation confirms, rejects and marks no-show; Front Office
 * checks in and out. Rather than showing every button and letting the server
 * refuse, the caller passes only the actions its role may perform.
 */

import * as React from "react";
import {
  BedDouble,
  CalendarDays,
  CreditCard,
  Hotel,
  StickyNote,
  User,
} from "lucide-react";

import {
  RecordDetailDrawer,
  formatDetailDate,
  formatVnd,
  type DetailActionSpec,
} from "@/components/ui/record-drawer";
import { StatusPill } from "@/components/ui/status-pill";
import type { Booking } from "@/services/booking_confirmation_service";

/** Nights between two ISO dates, or null when either is missing/invalid. */
function nightsBetween(from?: string, to?: string): number | null {
  if (!from || !to) return null;
  const a = new Date(from).getTime();
  const b = new Date(to).getTime();
  if (Number.isNaN(a) || Number.isNaN(b) || b <= a) return null;
  return Math.round((b - a) / 86_400_000);
}

export function BookingDetailDrawer({
  booking,
  onOpenChange,
  actions = [],
}: {
  booking: Booking | null;
  onOpenChange: (open: boolean) => void;
  /** Only the transitions the current role may perform. */
  actions?: DetailActionSpec[];
}) {
  if (!booking) {
    return <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />;
  }

  const nights = nightsBetween(booking.checkInDate, booking.checkOutDate);

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      avatarIcon={Hotel}
      title={booking.customerName || booking.bookingCode}
      subtitle={{ icon: Hotel, text: booking.bookingCode }}
      status={{ domain: "booking", value: booking.status }}
      recordId={booking.bookingId}
      actions={actions}
      notice={
        // The rejection/cancellation reason is the single most useful field on a
        // refused booking, so it is promoted out of the sections.
        booking.statusReason
          ? { tone: "danger", text: booking.statusReason }
          : undefined
      }
      sections={[
        {
          title: "Stay",
          rows: [
            {
              label: "Check-in",
              icon: CalendarDays,
              value: formatDetailDate(booking.checkInDate),
            },
            {
              label: "Check-out",
              value: formatDetailDate(booking.checkOutDate),
            },
            {
              label: "Nights",
              value: nights != null ? <span className="numeric">{nights}</span> : null,
            },
          ],
        },
        {
          title: "Commercials",
          rows: [
            {
              label: "Total",
              icon: CreditCard,
              value: formatVnd(booking.totalAmount),
            },
            { label: "Guest", icon: User, value: booking.customerName },
            { label: "Owner", value: booking.assignedUserName },
          ],
        },
        // Line items only appear when the detail endpoint supplied them; the
        // list payload does not carry them, so the section is omitted rather
        // than rendered empty.
        ...(booking.details && booking.details.length > 0
          ? [
              {
                title: "Rooms & services",
                content: (
                  <ul className="divide-y divide-border">
                    {booking.details.map((d) => (
                      <li
                        key={d.bookingDetailId}
                        className="flex items-start justify-between gap-3 px-3 py-2.5"
                      >
                        <span className="min-w-0">
                          <span className="block truncate text-[12.5px] font-medium text-foreground">
                            {d.productName}
                            {d.roomNumber ? ` · ${d.roomNumber}` : ""}
                          </span>
                          <span className="numeric block text-[11.5px] text-muted-foreground">
                            {d.quantity} × {d.nights} night{d.nights === 1 ? "" : "s"}
                            {d.unitPrice != null &&
                              ` @ ${d.unitPrice.toLocaleString("vi-VN")} ₫`}
                          </span>
                        </span>
                        <span className="flex shrink-0 flex-col items-end gap-1">
                          <span className="text-[12.5px] text-foreground">
                            {formatVnd(d.lineTotal)}
                          </span>
                          <StatusPill
                            size="sm"
                            domain="inventory"
                            value={d.inventoryStatus}
                          />
                        </span>
                      </li>
                    ))}
                  </ul>
                ),
              },
            ]
          : []),
        {
          title: "Notes",
          rows: [
            {
              label: "Special requests",
              icon: StickyNote,
              value: booking.specialRequests,
              block: true,
            },
          ],
        },
        {
          title: "Record",
          rows: [
            { label: "Created", value: formatDetailDate(booking.createdAt) },
            { label: "Last updated", value: formatDetailDate(booking.updatedAt) },
            {
              label: "Quotation",
              icon: BedDouble,
              value: booking.quotationId ? (
                <span className="numeric">
                  {booking.quotationId.slice(0, 8).toUpperCase()}
                </span>
              ) : null,
            },
          ],
        },
      ]}
    />
  );
}
