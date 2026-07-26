"use client";

import React, { useEffect, useState } from "react";
import { BedDouble, CheckCircle2, Clock, Send, TriangleAlert, XCircle } from "lucide-react";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { apiErrorMessage } from "@/services/api_error";
import {
  currentRoomRequest,
  isRoomConfirmationUsable,
  type RoomRequest,
} from "@/services/room_request_service";
import {
  useCreateRoomRequest,
  useRoomRequestsByQuotation,
} from "@/features/room_request/hooks/use_room_requests";

/**
 * The room-confirmation state of one quotation, plus the button to ask about it.
 *
 * Advisory: room confirmation is a condition recorded against a quotation, not a
 * precondition for sending or converting it — the server enforces nothing here. The rep
 * sees whether the Reservation team has confirmed the rooms and decides for themselves;
 * `onUsableChange` lets the parent adjust its wording, not block its action.
 */

type QuotationLike = {
  id: string;
  roomType?: string | null;
  checkInDate?: string | null;
  checkOutDate?: string | null;
};

export interface RoomConfirmationPanelProps {
  quote: QuotationLike;
  /** Called whenever the confirmation becomes usable / unusable — for wording, not gating. */
  onUsableChange?: (usable: boolean) => void;
  /** Rooms to ask for, when the panel has to raise a new request. */
  defaultQuantity?: number;
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
      title: "Rooms not requested yet",
      detail:
        "The Reservation team has not been asked about these rooms. Ask them so the customer gets a quotation you can honour.",
    };
  }

  if (request.status === "PENDING") {
    return {
      tone: "warning",
      title: "Waiting on Reservation",
      detail: `Asked for ${request.quantity} x ${request.roomTypeRequested ?? "room"}. They are checking the hotel system now.`,
    };
  }

  if (request.status === "REJECTED") {
    return {
      tone: "danger",
      title: "Reservation could not confirm",
      detail:
        request.reservationNote?.trim() ||
        "No rooms available for these dates. Revise the quotation and ask again.",
    };
  }

  // CONFIRMED — but it may no longer describe what the quotation says.
  if (!isRoomConfirmationUsable(request, quote)) {
    const expired = request.heldUntil && new Date(request.heldUntil).getTime() < Date.now();
    return expired
      ? {
          tone: "danger",
          title: "Room hold expired",
          detail: `Reservation held the rooms until ${heldUntilLabel(request.heldUntil as string)}. Ask again to get a fresh confirmation.`,
        }
      : {
          tone: "danger",
          title: "Confirmation no longer matches",
          detail:
            "The room type or dates changed after Reservation confirmed them. Ask again for the current details.",
        };
  }

  return {
    tone: "success",
    title: "Rooms confirmed",
    detail: request.heldUntil
      ? `${request.quantity} x ${request.roomTypeRequested} held until ${heldUntilLabel(request.heldUntil)}.`
      : `${request.quantity} x ${request.roomTypeRequested} confirmed by Reservation.`,
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

export function RoomConfirmationPanel({
  quote,
  onUsableChange,
  defaultQuantity = 1,
}: RoomConfirmationPanelProps) {
  const { data: requests, isLoading } = useRoomRequestsByQuotation(quote.id);
  const createRequest = useCreateRoomRequest();

  const [quantity, setQuantity] = useState(String(defaultQuantity));
  const [error, setError] = useState("");

  const request = currentRoomRequest(requests);
  const usable = isRoomConfirmationUsable(request, quote);
  const state = describe(request, quote);

  // Keep the parent's primary action in sync with what the backend will actually allow.
  useEffect(() => {
    onUsableChange?.(usable);
  }, [usable, onUsableChange]);

  const canAsk = !isLoading && !usable && request?.status !== "PENDING";

  const handleAsk = async () => {
    setError("");
    const parsed = Number.parseInt(quantity, 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
      setError("Enter how many rooms you need (at least 1).");
      return;
    }
    try {
      await createRequest.mutateAsync({ quotationId: quote.id, quantity: parsed });
    } catch (e) {
      // Surfaces NO_RESERVATION_STAFF and QUOTATION_DATES_MISSING in the user's words.
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 dark:border-zinc-700 dark:bg-zinc-800/40">
      <div className="mb-2 flex items-center gap-2">
        <BedDouble className="size-4 text-slate-500" />
        <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
          Room Confirmation
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

          {canAsk && (
            <div className="mt-3 flex items-end gap-2">
              <div className="w-24">
                <label className="mb-1 block text-[10px] font-semibold text-slate-500">Rooms</label>
                <Input
                  type="number"
                  min={1}
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  className="h-8 text-xs"
                />
              </div>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={handleAsk}
                disabled={createRequest.isPending}
                leftIcon={<Send className="size-3" />}
              >
                {createRequest.isPending ? "Sending…" : "Ask Reservation"}
              </Button>
            </div>
          )}

          {error && (
            <p className="mt-2 rounded border border-red-200 bg-red-50 p-2 text-xs text-red-700">
              {error}
            </p>
          )}
        </>
      )}
    </div>
  );
}
