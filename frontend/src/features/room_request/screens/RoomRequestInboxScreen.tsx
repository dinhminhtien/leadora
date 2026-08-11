"use client";

import React, { useState, useMemo } from "react";
import { CalendarDays, CheckCircle2, Clock, Send, XCircle } from "lucide-react";


import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Input } from "@/components/ui/Input";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";
import { OwnerCell } from "@/components/ui/row-actions";
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

/** Rows per page — mirrors the server page size requested below. */
const PAGE_SIZE = 10;

const ROOM_REQUEST_EXPORT_HEADERS = [
  "Quotation", "Customer", "Room type", "Check-in", "Check-out", "Rooms",
  "Requested by", "Status", "Held until", "Reservation note",
];

function roomRequestExportRow(r: RoomRequest): (string | number | null | undefined)[] {
  return [
    r.quoteNo, r.customerName ?? "", r.roomTypeRequested ?? "",
    r.checkInDate, r.checkOutDate, r.quantity,
    r.requestedByName ?? "", r.status, r.heldUntil ?? "", r.reservationNote ?? "",
  ];
}

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
  const totalElements =
    (typeof data?.page === "object" ? data.page.totalElements : data?.totalElements) ?? rows.length;

  /**
   * Column set — Blueprint §10.8.
   *
   * The Answer column keeps Confirm/Reject as real buttons rather than folding
   * them into the overflow menu: this inbox exists to be triaged fast, and
   * BR-24 makes every pending row a hard block on Sales until it is answered.
   */
  const roomRequestColumns: ColumnDef<RoomRequest>[] = useMemo(() => [
    {
      id: "quotation",
      header: "Quotation",
      sticky: "left",
      className: "whitespace-nowrap text-xs font-semibold",
      cell: (r) => r.quoteNo,
    },
    { id: "customer", header: "Customer", className: "text-xs", cell: (r) => r.customerName ?? "—" },
    {
      id: "room",
      header: "Room",
      minWidth: "md",
      className: "text-xs",
      cell: (r) => r.roomTypeRequested ?? "—",
    },
    {
      id: "dates",
      header: "Dates",
      minWidth: "lg",
      cell: (r) => (
        <span className="inline-flex items-center gap-1 whitespace-nowrap text-xs">
          <CalendarDays className="size-3 text-muted-foreground" />
          {formatDate(r.checkInDate)} → {formatDate(r.checkOutDate)}
        </span>
      ),
    },
    {
      id: "quantity",
      header: "Rooms",
      numeric: true,
      className: "font-semibold",
      cell: (r) => r.quantity,
    },
    {
      id: "askedBy",
      header: "Asked by",
      minWidth: "xl",
      cell: (r) => <OwnerCell name={r.requestedByName} />,
    },
    {
      id: "status",
      header: "Status",
      // Canonical room-request binding (Blueprint §2.7):
      // PENDING warning · CONFIRMED success · REJECTED danger · SUPERSEDED muted.
      cell: (r) => (
        <>
          <StatusPill size="sm" domain="roomRequest" value={r.status} />
          {r.status === "CONFIRMED" && r.heldUntil && (
            <p className="mt-1 inline-flex items-center gap-1 text-[10px] text-muted-foreground">
              <Clock className="size-3" />
              held to {new Date(r.heldUntil).toLocaleString()}
            </p>
          )}
          {r.status === "REJECTED" && r.reservationNote && (
            <p className="mt-1 max-w-50 text-[10px] text-muted-foreground">{r.reservationNote}</p>
          )}
        </>
      ),
    },
    {
      id: "answer",
      header: "Answer",
      sticky: "right",
      cell: (r) =>
        r.status === "PENDING" ? (
          <div className="flex justify-end gap-1">
            <Button size="xs" variant="primary" onClick={() => openAnswer(r, "CONFIRMED")} leftIcon={<CheckCircle2 className="size-3" />}>
              Confirm
            </Button>
            <Button size="xs" variant="secondary" onClick={() => openAnswer(r, "REJECTED")} leftIcon={<XCircle className="size-3" />}>
              Reject
            </Button>
          </div>
        ) : (
          <span className="block text-right text-[10px] text-muted-foreground">
            {r.respondedByName ? `by ${r.respondedByName}` : "—"}
          </span>
        ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const controls = useTableControls<RoomRequest>("room-requests", roomRequestColumns);

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
      <PageHeader {...PAGE_META.roomRequests} />

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

      <DataTable
        label="Room requests"
        rows={rows}
        columns={controls.visibleColumns}
        rowId={(r) => r.requestId}
        isLoading={isLoading}
        density={controls.density}
        sortBy={controls.sortBy}
        sortDir={controls.sortDir}
        onSortChange={controls.onSortChange}
        selectedIds={controls.selectedIds}
        onSelectionChange={controls.setSelectedIds}
        bulkActions={
          <ExportMenu
            filename={`room-requests-selected-${new Date().toISOString().slice(0, 10)}`}
            headers={ROOM_REQUEST_EXPORT_HEADERS}
            rows={rows.filter((r) => controls.selectedIds.has(r.requestId)).map(roomRequestExportRow)}
          />
        }
        isFiltered={status !== "all"}
        onClearFilters={() => { setStatus("all"); setPage(0); }}
        emptyTitle="Nothing waiting"
        emptyMessage="Room requests raised by Sales appear here for you to confirm or reject."
        footer={
          <TablePagination
            page={page}
            pageSize={PAGE_SIZE}
            totalElements={totalElements}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        }
      />

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
