"use client";

import React, { useEffect } from "react";
import { BedDouble, CheckCircle2, Clock, TriangleAlert, XCircle } from "lucide-react";

import { Badge } from "@/components/ui/Badge";
import {
  currentRoomRequest,
  isRoomConfirmationUsable,
  type RoomRequest,
} from "@/services/room_request_service";
import { useRoomRequestsByQuotation } from "@/features/room_request/hooks/use_room_requests";

/**
 * Where a quotation's rooms stand with the Reservation team. **Read-only.**
 *
 * There is no "ask" button and no "withdraw" button, because asking is no longer something a rep
 * does. The request is raised by the workflow the moment the customer accepts their quotation —
 * the point at which the sales side actually needs a real answer.
 *
 * Sales used to be able to ask at any time as well, which meant the same quotation could put two
 * questions into the Reservation inbox by two different routes. The inbox could not tell which one
 * was current, and reps re-asked questions that were already open. One route, raised automatically,
 * removes that entirely.
 *
 * Withdrawing went with it: with no way to ask again by hand, a cancelled request would strand the
 * quotation with no route to a confirmation and no way to convert. A question that no longer
 * applies is retired automatically instead — revising a quotation supersedes its request, because
 * the dates or the room type it asked about have changed.
 */

import type { RoomLineDetail } from "@/services/quotation_service";

type QuotationLike = {
  id: string;
  roomType?: string | null;
  checkInDate?: string | null;
  checkOutDate?: string | null;
  numberOfRooms?: number | null;
  roomLines?: RoomLineDetail[] | null;
};

export interface RoomConfirmationPanelProps {
  quote: QuotationLike;
  /** Called whenever the confirmation becomes usable / unusable — for wording, not gating. */
  onUsableChange?: (usable: boolean) => void;
}

function heldUntilLabel(iso: string): string {
  const until = new Date(iso);
  return until.toLocaleString(undefined, {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** How far the Reservation team has got, and whether their answer still applies. */
function describe(
  request: RoomRequest | undefined,
  quote: QuotationLike,
): { tone: "success" | "warning" | "danger" | "neutral"; title: string; detail: string } {
  if (!request) {
    return {
      tone: "neutral",
      title: "Not requested yet",
      detail:
        "The Reservation team is asked automatically as soon as the customer accepts this quotation.",
    };
  }

  const roomBreakdown = quote.roomLines && quote.roomLines.length > 0
    ? quote.roomLines.map(r => `${r.roomType ?? "Room"} × ${r.numberOfRooms ?? 1}`).join(", ")
    : `${request.quantity} × ${request.roomTypeRequested ?? quote.roomType ?? "room"}`;

  if (request.status === "PENDING") {
    return {
      tone: "warning",
      title: "Waiting on Reservation",
      detail: `Asked for ${roomBreakdown}. They are checking the hotel's system now.`,
    };
  }

  if (request.status === "REJECTED") {
    return {
      tone: "danger",
      title: "Reservation could not confirm",
      detail:
        request.reservationNote?.trim() ||
        "No rooms available for these dates. Revise the quotation with dates or a room type they can meet.",
    };
  }

  // CONFIRMED — but it may no longer describe what the quotation says.
  if (!isRoomConfirmationUsable(request, quote)) {
    const expired = request.heldUntil && new Date(request.heldUntil).getTime() < Date.now();
    return expired
      ? {
          tone: "danger",
          title: "Room hold expired",
          detail: `Reservation held the rooms until ${heldUntilLabel(request.heldUntil as string)}. Revise the quotation to get a fresh confirmation.`,
        }
      : {
          tone: "danger",
          title: "Confirmation no longer matches",
          detail:
            "The room type or dates changed after Reservation confirmed them. Revise the quotation so a fresh request is raised.",
        };
  }

  return {
    tone: "success",
    title: "Rooms confirmed",
    detail: request.heldUntil
      ? `${roomBreakdown} held until ${heldUntilLabel(request.heldUntil)}.`
      : `${roomBreakdown} confirmed by Reservation.`,
  };
}

const TONE_ICON = {
  success: <CheckCircle2 className="size-4 text-emerald-600" />,
  warning: <Clock className="size-4 text-amber-600" />,
  danger: <XCircle className="size-4 text-red-600" />,
  neutral: <TriangleAlert className="size-4 text-slate-400" />,
};

const TONE_BADGE = {
  success: "success",
  warning: "warning",
  danger: "danger",
  neutral: "default",
} as const;

export function RoomConfirmationPanel({ quote, onUsableChange }: RoomConfirmationPanelProps) {
  const { data: requests, isLoading } = useRoomRequestsByQuotation(quote.id);

  const request = currentRoomRequest(requests);
  const usable = isRoomConfirmationUsable(request, quote);
  const state = describe(request, quote);

  // Keep the parent's primary action in sync with what the backend will actually allow.
  useEffect(() => {
    onUsableChange?.(usable);
  }, [usable, onUsableChange]);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 dark:border-zinc-700 dark:bg-zinc-800/40">
      <div className="mb-2 flex items-center gap-2">
        <BedDouble className="size-4 text-slate-500" />
        <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
          Room Availability
        </p>
        {!isLoading && (
          <Badge variant={TONE_BADGE[state.tone]} className="ml-auto text-[9px] font-bold uppercase">
            {request?.status ?? "NOT REQUESTED"}
          </Badge>
        )}
      </div>

      {isLoading ? (
        <p className="text-xs text-slate-400">Checking with Reservation…</p>
      ) : (
        <>
          <div className="flex items-start gap-2">
            {TONE_ICON[state.tone]}
            <div className="min-w-0">
              <p className="text-xs font-semibold text-slate-700 dark:text-zinc-200">{state.title}</p>
              <p className="mt-0.5 text-xs text-slate-500 dark:text-zinc-400">{state.detail}</p>
              {request?.respondedByName && (
                <p className="mt-1 text-[10px] text-slate-400">
                  Answered by {request.respondedByName}
                </p>
              )}
            </div>
          </div>

          {/* Requested vs confirmed, so the rep can see the shape of the answer without
              opening the request. */}
          {request && (
            <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1 rounded border border-slate-200 bg-white p-2.5 text-[11px] dark:border-zinc-700 dark:bg-zinc-900/40">
              <dt className="text-slate-400">Requested</dt>
              <dd className="text-right font-semibold text-slate-700 dark:text-zinc-200">
                {request.quantity} × {request.roomTypeRequested ?? "—"}
              </dd>
              <dt className="text-slate-400">Stay</dt>
              <dd className="text-right font-semibold text-slate-700 dark:text-zinc-200">
                {request.checkInDate} → {request.checkOutDate}
              </dd>
              <dt className="text-slate-400">Source</dt>
              <dd className="text-right font-semibold text-slate-700 dark:text-zinc-200">
                Reservation
              </dd>
              {request.respondedAt && (
                <>
                  <dt className="text-slate-400">Answered</dt>
                  <dd className="text-right font-semibold text-slate-700 dark:text-zinc-200">
                    {new Date(request.respondedAt).toLocaleString(undefined, {
                      day: "2-digit",
                      month: "short",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </dd>
                </>
              )}
            </dl>
          )}
        </>
      )}
    </div>
  );
}
