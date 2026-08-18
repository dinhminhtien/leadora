"use client";

/**
 * Reservation detail drawer — shared `RecordDetailDrawer` (§2.11).
 *
 * **Room allocation is the point of this screen.** A reservation's status is
 * meaningless without knowing whether its rooms are actually allocated, so the
 * per-line inventory status is rendered on every line rather than summarised.
 *
 * Check-in/check-out are role-and-status gated server-side, so the caller passes
 * only the transitions that are legal right now (§12.13).
 */

import * as React from "react";
import {
  ArrowRight,
  Banknote,
  BedDouble,
  CalendarDays,
  Hash,
  StickyNote,
  User,
} from "lucide-react";

import {
  RecordDetailDrawer,
  formatDetailDate,
  formatDetailDateTime,
  formatVnd,
  type DetailActionSpec,
} from "@/components/ui/record-drawer";
import { StatusPill } from "@/components/ui/status-pill";
import type { ReservationStatus } from "@/services/reservation_status_service";

export function ReservationDetailDrawer({
  reservation,
  onOpenChange,
  actions = [],
  children,
}: {
  reservation: ReservationStatus | null;
  onOpenChange: (open: boolean) => void;
  /** Only transitions legal for the current status and role. */
  actions?: DetailActionSpec[];
  /** Loading / error slot, rendered above the sections. */
  children?: React.ReactNode;
}) {
  if (!reservation) {
    return (
      <RecordDetailDrawer open={false} onOpenChange={onOpenChange} title="" sections={[]} />
    );
  }

  return (
    <RecordDetailDrawer
      open
      onOpenChange={onOpenChange}
      avatarName={reservation.guestName}
      title={reservation.guestName || reservation.reservationNo}
      subtitle={{ icon: Hash, text: reservation.reservationNo }}
      status={{ domain: "booking", value: reservation.status }}
      recordId={reservation.id}
      actions={actions}
      notice={
        // The reason a reservation was rejected or cancelled outranks every
        // other field on the record.
        reservation.statusReason
          ? { tone: "danger", text: reservation.statusReason }
          : undefined
      }
      sections={[
        {
          title: "Stay",
          rows: [
            {
              label: "Dates",
              icon: CalendarDays,
              value: (
                <span className="inline-flex items-center gap-1.5">
                  {formatDetailDate(reservation.checkInDate)}
                  <ArrowRight className="size-3 text-muted-foreground" />
                  {formatDetailDate(reservation.checkOutDate)}
                </span>
              ),
            },
            { label: "Guest", icon: User, value: reservation.guestName },
            { label: "Room type", icon: BedDouble, value: reservation.roomType },
            {
              label: "Total",
              icon: Banknote,
              value: formatVnd(reservation.totalAmount),
            },
          ],
        },
        {
          title: "Room allocation",
          content:
            reservation.details && reservation.details.length > 0 ? (
              <ul className="divide-y divide-border">
                {reservation.details.map((d) => (
                  <li
                    key={d.bookingDetailId}
                    className="flex items-start justify-between gap-3 px-3 py-2.5"
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-[12.5px] font-medium text-foreground">
                        {d.productName}
                      </span>
                      <span className="numeric block text-[11.5px] text-muted-foreground">
                        {d.roomNumber || "Unassigned"} · {d.quantity} × {d.nights}{" "}
                        night{d.nights === 1 ? "" : "s"}
                        {d.unitPrice != null &&
                          ` @ ${d.unitPrice.toLocaleString("vi-VN")} ₫`}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end gap-1">
                      <span className="text-[12.5px] text-foreground">
                        {formatVnd(d.lineTotal)}
                      </span>
                      <StatusPill size="sm" domain="inventory" value={d.inventoryStatus} />
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-3 py-2.5 text-[12.5px] text-muted-foreground">
                No rooms allocated yet.
              </p>
            ),
        },
        {
          title: "Notes",
          rows: [
            {
              label: "Special requests",
              icon: StickyNote,
              value: reservation.specialRequests,
              block: true,
            },
          ],
        },
        {
          title: "Record",
          rows: [
            { label: "Created", value: formatDetailDateTime(reservation.createdAt) },
            { label: "Last updated", value: formatDetailDateTime(reservation.updatedAt) },
          ],
        },
      ]}
    >
      {children}
    </RecordDetailDrawer>
  );
}
