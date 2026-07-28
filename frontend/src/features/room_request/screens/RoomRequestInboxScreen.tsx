"use client";

import React, { useState } from "react";
import { BedDouble, CalendarDays, CheckCircle2, Clock, Send, XCircle } from "lucide-react";


import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { apiErrorMessage } from "@/services/api_error";
import type { RoomRequest, RoomRequestStatus } from "@/services/room_request_service";
import {
  useRespondRoomRequest,
  useRoomRequestInbox,
} from "@/features/room_request/hooks/use_room_requests";

/**
 * The Reservation team's queue: rooms Sales is waiting on.
 *
 * This CRM owns no room inventory — the answer comes from the hotel's real PMS, which the
 * Reservation team reads outside this system. This screen only records what they say, and
 * for how long they will hold it.
 */


const STATUS_FILTERS = ["PENDING", "CONFIRMED", "REJECTED", "all"] as const;

function formatDate(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

/** `datetime-local` needs `YYYY-MM-DDTHH:mm` in local time, not an ISO instant. */
function defaultHoldUntil(): string {
  const tomorrowEvening = new Date();
  tomorrowEvening.setDate(tomorrowEvening.getDate() + 1);
  tomorrowEvening.setHours(18, 0, 0, 0);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${tomorrowEvening.getFullYear()}-${pad(tomorrowEvening.getMonth() + 1)}-${pad(
    tomorrowEvening.getDate(),
  )}T${pad(tomorrowEvening.getHours())}:${pad(tomorrowEvening.getMinutes())}`;
}

export function RoomRequestInboxScreen() {
  const [status, setStatus] = useState<string>("PENDING");
  const [page, setPage] = useState(0);
  const { data, isLoading } = useRoomRequestInbox({ status, page, size: 10 });
  const respond = useRespondRoomRequest();

  const [answering, setAnswering] = useState<RoomRequest | null>(null);
  const [decision, setDecision] = useState<"CONFIRMED" | "REJECTED">("CONFIRMED");
  const [note, setNote] = useState("");
  const [heldUntil, setHeldUntil] = useState(defaultHoldUntil());
  const [error, setError] = useState("");

  const rows = data?.content ?? [];
  const totalPages = (typeof data?.page === "object" ? data.page.totalPages : data?.totalPages) ?? 1;

  const openAnswer = (request: RoomRequest, initial: "CONFIRMED" | "REJECTED") => {
    setAnswering(request);
    setDecision(initial);
    setNote("");
    setHeldUntil(defaultHoldUntil());
    setError("");
  };

  const submit = async () => {
    if (!answering) return;
    setError("");

    // The backend requires this so Sales has something to tell the customer.
    if (decision === "REJECTED" && !note.trim()) {
      setError("A note is required when rooms cannot be confirmed, so Sales can inform the customer.");
      return;
    }

    try {
      await respond.mutateAsync({
        id: answering.requestId,
        payload: {
          decision,
          reservationNote: note.trim() || undefined,
          // Only meaningful on a confirmation; converted from local time to an instant.
          heldUntil:
            decision === "CONFIRMED" && heldUntil ? new Date(heldUntil).toISOString() : undefined,
        },
      });
      setAnswering(null);
    } catch (e) {
      // Two Reservation staff answering at once: the loser gets a clean conflict here.
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="p-6">
      <div className="mb-5">
        <h1 className="flex items-center gap-2 text-lg font-bold text-foreground">
          <BedDouble className="size-5 text-slate-500" />
          Room Requests
        </h1>
        <p className="mt-1 text-xs text-slate-400 dark:text-zinc-400">
          Sales cannot send a quotation or create a booking until you confirm the rooms. Check the
          hotel system, then answer here — including how long you can hold them.
        </p>
      </div>

      <div className="mb-3 flex items-center gap-2">
        {STATUS_FILTERS.map((s) => (
          <Button
            key={s}
            size="sm"
            variant={status === s ? "primary" : "secondary"}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
          >
            {s === "all" ? "All" : s}
          </Button>
        ))}
      </div>

      {isLoading ? (
        <p className="text-xs text-slate-400">Loading requests…</p>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 p-8 text-center dark:border-zinc-700">
          <CheckCircle2 className="mx-auto mb-2 size-6 text-emerald-500" />
          <p className="text-sm font-semibold text-slate-600 dark:text-zinc-300">Nothing waiting</p>
          <p className="mt-1 text-xs text-slate-400">No room requests with this status.</p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Quotation</TableHead>
              <TableHead>Customer</TableHead>
              <TableHead>Room</TableHead>
              <TableHead>Dates</TableHead>
              <TableHead>Rooms</TableHead>
              <TableHead>Asked by</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Answer</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.requestId}>
                <TableCell className="whitespace-nowrap text-xs font-semibold">{r.quoteNo}</TableCell>
                <TableCell className="text-xs">{r.customerName ?? "—"}</TableCell>
                <TableCell className="text-xs">{r.roomTypeRequested ?? "—"}</TableCell>
                <TableCell className="whitespace-nowrap text-xs">
                  <span className="inline-flex items-center gap-1">
                    <CalendarDays className="size-3 text-slate-400" />
                    {formatDate(r.checkInDate)} → {formatDate(r.checkOutDate)}
                  </span>
                </TableCell>
                <TableCell className="text-xs font-semibold">{r.quantity}</TableCell>
                <TableCell className="text-xs">{r.requestedByName ?? "—"}</TableCell>
                <TableCell>
                  {/* Canonical room-request binding (Blueprint §2.7):
                      PENDING warning · CONFIRMED success · REJECTED danger ·
                      SUPERSEDED muted. */}
                  <StatusPill size="sm" domain="roomRequest" value={r.status} />
                  {r.status === "CONFIRMED" && r.heldUntil && (
                    <p className="mt-1 inline-flex items-center gap-1 text-[10px] text-slate-400">
                      <Clock className="size-3" />
                      held to {new Date(r.heldUntil).toLocaleString()}
                    </p>
                  )}
                  {r.status === "REJECTED" && r.reservationNote && (
                    <p className="mt-1 max-w-50 text-[10px] text-slate-400">{r.reservationNote}</p>
                  )}
                </TableCell>
                <TableCell>
                  {r.status === "PENDING" ? (
                    <div className="flex gap-1">
                      <Button
                        size="sm"
                        variant="primary"
                        onClick={() => openAnswer(r, "CONFIRMED")}
                        leftIcon={<CheckCircle2 className="size-3" />}
                      >
                        Confirm
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => openAnswer(r, "REJECTED")}
                        leftIcon={<XCircle className="size-3" />}
                      >
                        Reject
                      </Button>
                    </div>
                  ) : (
                    <span className="text-[10px] text-slate-400">
                      {r.respondedByName ? `by ${r.respondedByName}` : "—"}
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {totalPages > 1 && (
        <div className="mt-3 flex items-center justify-end gap-2">
          <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <span className="text-xs text-slate-400">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            size="sm"
            variant="secondary"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next
          </Button>
        </div>
      )}

      {answering && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl dark:bg-zinc-900">
            <h2 className="text-sm font-bold text-slate-800 dark:text-zinc-100">
              {decision === "CONFIRMED" ? "Confirm rooms" : "Reject request"}
            </h2>
            <p className="mt-1 text-xs text-slate-500 dark:text-zinc-400">
              {answering.quantity} x {answering.roomTypeRequested ?? "room"} ·{" "}
              {formatDate(answering.checkInDate)} → {formatDate(answering.checkOutDate)}
            </p>

            <div className="mt-4 flex gap-2">
              <Button
                size="sm"
                variant={decision === "CONFIRMED" ? "primary" : "secondary"}
                onClick={() => setDecision("CONFIRMED")}
              >
                Confirm
              </Button>
              <Button
                size="sm"
                variant={decision === "REJECTED" ? "primary" : "secondary"}
                onClick={() => setDecision("REJECTED")}
              >
                Reject
              </Button>
            </div>

            {decision === "CONFIRMED" && (
              <div className="mt-4">
                <label className="mb-1 block text-[10px] font-semibold text-slate-500">
                  Hold the rooms until
                </label>
                <Input
                  type="datetime-local"
                  value={heldUntil}
                  onChange={(e) => setHeldUntil(e.target.value)}
                  className="text-xs"
                />
                <p className="mt-1 text-[10px] text-slate-400">
                  Your commitment — the actual hold stays in the hotel system. Sales can send the
                  quotation until this passes. Leave empty for no deadline.
                </p>
              </div>
            )}

            <div className="mt-4">
              <label className="mb-1 block text-[10px] font-semibold text-slate-500">
                Note for Sales {decision === "REJECTED" && <span className="text-red-500">*</span>}
              </label>
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={3}
                placeholder={
                  decision === "CONFIRMED"
                    ? "e.g. 5 Deluxe left, sea view not guaranteed"
                    : "e.g. Fully booked those nights — a Suite is available instead"
                }
                className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 transition focus:border-blue-500 focus:bg-white focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              />
            </div>

            {error && (
              <p className="mt-3 rounded border border-red-200 bg-red-50 p-2 text-xs text-red-700">
                {error}
              </p>
            )}

            <div className="mt-5 flex gap-2">
              <Button variant="ghost" onClick={() => setAnswering(null)} className="text-xs">
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={submit}
                isLoading={respond.isPending}
                leftIcon={!respond.isPending ? <Send className="size-3" /> : undefined}
                className="flex-1 text-xs font-bold"
              >
                Send answer to Sales
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
