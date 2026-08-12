"use client";

/**
 * Inline allotment readout for one room line on the quotation form.
 *
 * This is where the saved round trip actually lands. The grid answers "what have we got?";
 * this answers "can I promise *this*?" without the rep leaving the form they are filling in —
 * which is the moment the question is really asked.
 *
 * It never blocks. A quotation is an offer, and the hotel may hold rooms outside our
 * allocation, so a shortfall is reported as something the Reservation team will be asked about
 * rather than as an error. Refusing to quote past the published horizon would be the worst
 * failure mode available: quota is published some weeks out, so every enquiry for a later date
 * would become unquotable.
 */

import * as React from "react";
import { CheckCircle2, HelpCircle, Loader2, TriangleAlert } from "lucide-react";

import { useStayAvailability } from "@/features/room_availability/hooks/use_room_availability";

type AllotmentHintProps = {
  productId?: string;
  checkIn?: string;
  checkOut?: string;
  quantity?: number;
};

function formatAsOf(iso?: string): string | null {
  if (!iso) return null;
  return new Date(iso).toLocaleString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
  });
}

export function AllotmentHint({ productId, checkIn, checkOut, quantity = 1 }: AllotmentHintProps) {
  const enabled = !!productId;
  const { data, isFetching, isError } = useStayAvailability({
    checkIn,
    checkOut,
    quantity,
    productId,
    enabled,
  });

  if (!enabled || !checkIn || !checkOut) {
    return null;
  }

  if (isFetching && !data) {
    return (
      <Hint tone="muted" icon={<Loader2 className="h-3.5 w-3.5 animate-spin" />}>
        Checking allotment...
      </Hint>
    );
  }

  // A lookup failure must not read as "no rooms". Staying quiet is safer than a red warning
  // the rep would take as an answer.
  if (isError || !data || data.length === 0) {
    return null;
  }

  const stay = data[0];
  const asOf = formatAsOf(stay.asOf);

  if (stay.closedDates.length > 0) {
    return (
      <Hint tone="danger" icon={<TriangleAlert className="h-3.5 w-3.5" />}>
        The hotel has closed {stay.closedDates.length === 1 ? "this date" : "these dates"} to us
        ({stay.closedDates.slice(0, 3).join(", ")}). Choose different dates or another room type.
      </Hint>
    );
  }

  if (stay.unpublishedDates.length > 0) {
    return (
      <Hint tone="warning" icon={<HelpCircle className="h-3.5 w-3.5" />}>
        Allotment not published for {stay.unpublishedDates.length}{" "}
        {stay.unpublishedDates.length === 1 ? "night" : "nights"} yet — that is not the same as
        sold out. You can still quote; the Reservation team is asked automatically when you save.
      </Hint>
    );
  }

  if (!stay.bookableNow) {
    const available = stay.availableForStay ?? 0;
    return (
      <Hint tone="warning" icon={<TriangleAlert className="h-3.5 w-3.5" />}>
        Only <strong>{available}</strong> left in our allocation for {quantity} requested
        {stay.limitingDates.length > 0 ? ` (tightest on ${stay.limitingDates[0]})` : ""}. You can
        still quote — the Reservation team is asked automatically when you save.
        {asOf ? ` Updated ${asOf}.` : ""}
      </Hint>
    );
  }

  return (
    <Hint tone="ok" icon={<CheckCircle2 className="h-3.5 w-3.5" />}>
      <strong>{stay.availableForStay}</strong> left in our allocation — enough for {quantity}.
      {stay.stale ? " Not re-confirmed recently." : ""}
      {asOf ? ` Updated ${asOf}.` : ""}
    </Hint>
  );
}

function Hint({
  tone,
  icon,
  children,
}: {
  tone: "ok" | "warning" | "danger" | "muted";
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  const tones: Record<typeof tone, string> = {
    ok: "text-emerald-700",
    warning: "text-amber-700",
    danger: "text-red-700",
    muted: "text-slate-500",
  };
  return (
    <p className={`mt-1 flex items-start gap-1.5 text-[12px] leading-[16px] ${tones[tone]}`}>
      <span className="mt-0.5 shrink-0">{icon}</span>
      <span>{children}</span>
    </p>
  );
}
