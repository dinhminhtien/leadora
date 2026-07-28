"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  Headphones,
  Search,
  Loader2,
  Save,
  Inbox,
  Clock3,
  CheckCircle2,
  AlertTriangle,
  ConciergeBell,
} from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  useArrivalHandovers,
  useArrivalHandoverDetail,
  useArrivalHandoverSummary,
  useUpdateReadiness,
} from "@/features/front_office_handover/hooks/use_arrival_handovers";
import type {
  ArrivalHandover,
  ReadinessStatus,
} from "@/services/arrival_handover_service";
import { HandoverDetailDrawer } from "@/features/front_office_handover/components/HandoverDetailDrawer";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";

const PAGE_SIZE = 10;

function Pill({ text, className }: { text: string; className: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${className}`}>
      {text}
    </span>
  );
}

const SUMMARY_COLORS: Record<string, { ring: string; chip: string; text: string }> = {
  amber: { ring: "ring-amber-300", chip: "bg-amber-100 text-amber-600", text: "text-amber-600" },
  blue: { ring: "ring-blue-300", chip: "bg-blue-100 text-blue-600", text: "text-blue-600" },
  emerald: { ring: "ring-emerald-300", chip: "bg-emerald-100 text-emerald-600", text: "text-emerald-600" },
  rose: { ring: "ring-rose-300", chip: "bg-rose-100 text-rose-600", text: "text-rose-600" },
};

function SummaryCard({
  label,
  value,
  icon,
  color,
  active,
  onClick,
}: {
  label: string;
  value?: number;
  icon: React.ReactNode;
  color: keyof typeof SUMMARY_COLORS;
  active?: boolean;
  onClick?: () => void;
}) {
  const c = SUMMARY_COLORS[color];
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-3 rounded-2xl border bg-white p-3.5 text-left shadow-sm transition hover:shadow-md ${
        active ? `border-transparent ring-2 ${c.ring}` : "border-slate-100"
      }`}
    >
      <span className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${c.chip}`}>
        {icon}
      </span>
      <span className="min-w-0">
        <span className={`block text-lg font-extrabold ${c.text}`}>{value ?? "—"}</span>
        <span className="block truncate text-[11px] font-medium text-slate-500">{label}</span>
      </span>
    </button>
  );
}

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("vi-VN");
}

export function FrontOfficeHandoverScreen() {
  const { highlightedId, setRowRef } = useHighlightRow();
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [readinessFilter, setReadinessFilter] = useState("");
  const [arrivalDate, setArrivalDate] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Debounce the free-text search a touch.
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput);
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  const listQuery = useArrivalHandovers({
    search: search || undefined,
    readinessStatus: readinessFilter || undefined,
    arrivalDate: arrivalDate || undefined,
    page,
    size: PAGE_SIZE,
  });

  const summaryQuery = useArrivalHandoverSummary();
  const summary = summaryQuery.data;

  const rows: ArrivalHandover[] = useMemo(
    () => listQuery.data?.data?.content ?? [],
    [listQuery.data],
  );
  const totalPages = listQuery.data?.data?.totalPages ?? 1;
  const totalElements = summary?.total ?? listQuery.data?.data?.totalElements ?? rows.length;

  // Clicking a KPI card filters the list by that readiness.
  const filterBy = (readiness: string) => {
    setReadinessFilter((cur) => (cur === readiness ? "" : readiness));
    setPage(0);
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-slate-800">
            <Headphones className="size-5 text-blue-600" />
            Arrival Handover (Front Office)
          </h1>
          <p className="text-xs text-slate-400">
            Handovers sent to the Front Office to prepare for guest arrival — view details and update readiness.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="text-xs font-semibold text-slate-500">{totalElements} requests</span>
          <Pill text="Front Office" className="bg-blue-100 text-blue-800" />
        </div>
      </div>

      {/* KPI cards — customer arrival requests by readiness */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SummaryCard
          label="Pending review"
          value={summary?.pendingReview}
          icon={<Inbox className="size-4" />}
          color="amber"
          active={readinessFilter === "PENDING_REVIEW"}
          onClick={() => filterBy("PENDING_REVIEW")}
        />
        <SummaryCard
          label="Reviewed"
          value={summary?.reviewed}
          icon={<Clock3 className="size-4" />}
          color="blue"
          active={readinessFilter === "REVIEWED"}
          onClick={() => filterBy("REVIEWED")}
        />
        <SummaryCard
          label="Ready for arrival"
          value={summary?.readyForArrival}
          icon={<CheckCircle2 className="size-4" />}
          color="emerald"
          active={readinessFilter === "READY_FOR_ARRIVAL"}
          onClick={() => filterBy("READY_FOR_ARRIVAL")}
        />
        <SummaryCard
          label="Needs clarification"
          value={summary?.needClarification}
          icon={<AlertTriangle className="size-4" />}
          color="rose"
          active={readinessFilter === "NEED_CLARIFICATION"}
          onClick={() => filterBy("NEED_CLARIFICATION")}
        />
      </div>

      {/* Toolbar */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="flex flex-col gap-3 p-3 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search by booking code or guest name…"
              className="pl-9"
            />
          </div>
          <div className="lg:w-44">
            <Input
              type="date"
              value={arrivalDate}
              onChange={(e) => {
                setArrivalDate(e.target.value);
                setPage(0);
              }}
              title="Filter by arrival date"
            />
          </div>
          <div className="lg:w-56">
            <Select
              value={readinessFilter}
              onChange={(e) => {
                setReadinessFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All readiness statuses</option>
              <option value="PENDING_REVIEW">Pending review</option>
              <option value="REVIEWED">Reviewed</option>
              <option value="READY_FOR_ARRIVAL">Ready for arrival</option>
              <option value="NEED_CLARIFICATION">Needs clarification</option>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* List (UC-22.1) */}
      <Card className="border-slate-100 bg-white shadow-sm">
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Booking code</TableHead>
                <TableHead>Guest</TableHead>
                <TableHead>Arrival date</TableHead>
                <TableHead>Room / Service</TableHead>
                <TableHead>Special requests</TableHead>
                <TableHead>Ready</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {listQuery.isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-xs text-slate-400">
                    <Loader2 className="mx-auto mb-1 size-4 animate-spin" /> Loading…
                  </TableCell>
                </TableRow>
              )}
              {!listQuery.isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-12">
                    <div className="flex flex-col items-center justify-center gap-2 text-center">
                      <span className="flex size-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
                        <ConciergeBell className="size-6" />
                      </span>
                      <p className="text-sm font-semibold text-slate-600">
                        {readinessFilter || search || arrivalDate
                          ? "No requests match the filters"
                          : "No arrival requests yet"}
                      </p>
                      <p className="max-w-xs text-xs text-slate-400">
                        Handovers sent by Sales/Reservations after a booking is confirmed appear here for the Front Office to prepare for arrival.
                      </p>
                    </div>
                  </TableCell>
                </TableRow>
              )}
              {rows.map((h) => (
                <TableRow
                  key={h.handoverId}
                  ref={setRowRef(h.handoverId)}
                  className={`cursor-pointer hover:bg-slate-50 ${
                    highlightedId === h.handoverId ? "bg-amber-50 ring-2 ring-inset ring-amber-400" : ""
                  }`}
                  onClick={() => setSelectedId(h.handoverId)}
                >
                  <TableCell className="font-semibold text-slate-700">
                    {h.bookingCode || "—"}
                  </TableCell>
                  <TableCell>{h.customerName || "—"}</TableCell>
                  <TableCell>{fmtDate(h.checkInDate)}</TableCell>
                  <TableCell className="max-w-[180px] truncate" title={h.roomSummary}>
                    {h.roomSummary || "—"}
                  </TableCell>
                  <TableCell className="max-w-[180px] truncate text-slate-500" title={h.specialRequests}>
                    {h.specialRequests?.trim() ? h.specialRequests : "—"}
                  </TableCell>
                  <TableCell>
                    <StatusPill size="sm" domain="readiness" value={h.readinessStatus} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-xs">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <span className="text-slate-500">
            Trang {page + 1}/{totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
          </Button>
        </div>
      )}

      {/* Detail slide-over (UC-22.2 + UC-22.3) */}
      {selectedId && (
        <HandoverDetailPanel id={selectedId} onClose={() => setSelectedId(null)} />
      )}
    </div>
  );
}

/* ───────────────────── Detail + readiness update ───────────────────── */

function HandoverDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const detailQuery = useArrivalHandoverDetail(id);
  const detail = detailQuery.data?.data;

  return (
    <HandoverDetailDrawer
      handover={detail ?? null}
      onOpenChange={(open) => !open && onClose()}
    >
      {detailQuery.isLoading ? (
        <p className="flex items-center gap-2 text-[12px] text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> Loading…
        </p>
      ) : !detail ? (
        <p className="text-[12px] text-danger">Could not load handover details.</p>
      ) : (
        // Keyed on the record so the form re-seeds from server state when a
        // different arrival is opened, instead of syncing through an effect.
        <ReadinessForm key={detail.handoverId} id={id} detail={detail} />
      )}
    </HandoverDetailDrawer>
  );
}

/**
 * UC-22.3 — the only field Front Office may write (BR-27). Everything else in
 * the drawer is read-only, which is why this is the sole form on the surface.
 */
function ReadinessForm({ id, detail }: { id: string; detail: ArrivalHandover }) {
  const updateReadiness = useUpdateReadiness();

  const [readiness, setReadiness] = useState<ReadinessStatus | "">(
    (detail.readinessStatus as ReadinessStatus) ?? "",
  );
  const [note, setNote] = useState(detail.clarificationNote ?? "");
  const [localError, setLocalError] = useState<string | null>(null);

  const needsClarification = readiness === "NEED_CLARIFICATION";
  const dirty =
    !!readiness &&
    (readiness !== detail.readinessStatus ||
      (needsClarification && note.trim() !== (detail.clarificationNote ?? "")));

  const handleSave = async () => {
    setLocalError(null);
    if (!readiness || !dirty) return;
    if (needsClarification && !note.trim()) {
      setLocalError("Please enter the clarification details.");
      return;
    }
    await updateReadiness.mutateAsync({
      id,
      readinessStatus: readiness,
      clarificationNote: needsClarification ? note.trim() : undefined,
    });
  };

  return (
    <section className="space-y-2 rounded-lg border border-brand-500/30 bg-brand-500/8 p-3">
      <p className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
        Update readiness status
      </p>

      <Select
        aria-label="Readiness status"
        value={readiness}
        onChange={(e) => {
          setReadiness(e.target.value as ReadinessStatus);
          setLocalError(null);
        }}
      >
        <option value="PENDING_REVIEW" disabled>
          Pending review
        </option>
        <option value="REVIEWED">Reviewed</option>
        <option value="READY_FOR_ARRIVAL">Ready for arrival</option>
        <option value="NEED_CLARIFICATION">Needs clarification</option>
      </Select>

      {needsClarification && (
        <textarea
          rows={3}
          value={note}
          onChange={(e) => {
            setNote(e.target.value);
            setLocalError(null);
          }}
          placeholder="Details for Sales/Reservations to clarify…"
          aria-label="Clarification details"
          className="w-full resize-none rounded-md border border-border bg-input px-3 py-2 text-[12.5px] text-foreground placeholder:text-muted-foreground focus-ring"
        />
      )}

      {(localError || updateReadiness.isError) && (
        <p className="text-[11.5px] text-danger">
          {localError || "Update failed. Please try again."}
        </p>
      )}
      {updateReadiness.isSuccess && !dirty && !localError && (
        <p className="text-[11.5px] text-success">Updated.</p>
      )}

      <Button
        size="sm"
        className="w-full"
        disabled={!dirty || updateReadiness.isPending}
        isLoading={updateReadiness.isPending}
        leftIcon={<Save className="size-3.5" />}
        onClick={handleSave}
      >
        Save status
      </Button>

      {needsClarification && (
        <p className="text-[11px] text-muted-foreground">
          Choosing “Needs clarification” notifies the responsible
          Sales/Reservations staff.
        </p>
      )}
    </section>
  );
}
