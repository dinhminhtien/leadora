"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  Search,
  Loader2,
  Save,
  Inbox,
  Clock3,
  CheckCircle2,
  AlertTriangle,
  ConciergeBell,
} from "lucide-react";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { useTableControls } from "@/components/ui/table-controls";
import { Card, CardContent } from "@/components/ui/Card";
import { StatusPill } from "@/components/ui/status-pill";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  useArrivalHandovers,
  useArrivalHandoverDetail,
  useArrivalHandoverSummary,
  useUpdateReadiness,
} from "@/features/front_office_handover/hooks/use_arrival_handovers";
import {
  READINESS_TRANSITIONS,
  isBookingActive,
  type ArrivalHandover,
  type ReadinessStatus,
} from "@/services/arrival_handover_service";
import { HandoverDetailDrawer } from "@/features/front_office_handover/components/HandoverDetailDrawer";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import { useAuthStore } from "@/stores/auth_store";
import { hasFullAccess } from "@/shared/auth/access";
import { userService, type UserSummary } from "@/services/user_service";

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
  const user = useAuthStore((s) => s.user);
  // UC-22.1 step 5 — only a supervisor filters by an arbitrary colleague. For Front Office Staff
  // the server decides visibility, so the control is hidden rather than shown-and-ignored.
  const isSupervisor = hasFullAccess(user);

  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [readinessFilter, setReadinessFilter] = useState("");
  const [arrivalDate, setArrivalDate] = useState("");
  const [assignedFoUserId, setAssignedFoUserId] = useState("");
  // On by default: a front desk is a shift rota, so opening the screen has to show every arrival
  // the desk is responsible for, not just the rows assigned to whoever happens to be logged in.
  // Scoping to your own queue is the deliberate narrowing, which is what the checkbox is for.
  const [deskWide, setDeskWide] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [foStaff, setFoStaff] = useState<UserSummary[]>([]);

  // Front Office roster for the assignee filter. Supervisors only, so nothing is fetched for the
  // Front Office Staff who make up most of this screen's traffic.
  useEffect(() => {
    if (!isSupervisor) return;
    let cancelled = false;
    // Asks for the Front Office team only. This used to fetch every user in the company and keep
    // the five it wanted, which handed the whole staff directory to the browser for a dropdown.
    userService
      .getSummariesByRole("FO")
      .then((res) => {
        if (cancelled) return;
        setFoStaff(res.data ?? []);
      })
      .catch(() => {
        // A missing roster only costs the filter — the list itself is unaffected.
        if (!cancelled) setFoStaff([]);
      });
    return () => {
      cancelled = true;
    };
  }, [isSupervisor]);

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
    assignedFoUserId: assignedFoUserId || undefined,
    // Sent explicitly, never elided to `undefined` when false. Axios drops undefined params, so
    // `deskWide || undefined` silently handed the decision to the server's default — unticking the
    // box would stop meaning "my queue" the moment that default changed.
    deskWide,
    page,
    size: pageSize,
  });

  // Same filters as the list minus readinessStatus — see the service for why.
  const summaryQuery = useArrivalHandoverSummary({
    search: search || undefined,
    arrivalDate: arrivalDate || undefined,
    assignedFoUserId: assignedFoUserId || undefined,
    deskWide,
  });
  const summary = summaryQuery.data;

  const rows: ArrivalHandover[] = useMemo(
    () => listQuery.data?.data?.content ?? [],
    [listQuery.data],
  );
  // Spring Boot serialises Page as { content, page: { size, number, totalElements, totalPages } }.
  // This screen read the flat `data.totalPages`, which is absent — so it always resolved to 1 and
  // the pager below (`totalPages > 1`) never rendered at all. Same shape check the lead,
  // notification and identity screens already use.
  const pageData = listQuery.data?.data;
  const pageMeta = pageData?.page && typeof pageData.page === "object" ? pageData.page : null;
  const totalPages = pageMeta ? pageMeta.totalPages : (pageData?.totalPages ?? 1);
  // The list's own count, not summary.total: the summary ignores search/date/readiness, so
  // preferring it made the header read "48 requests" above a table filtered down to 2.
  const totalElements = pageMeta
    ? pageMeta.totalElements
    : (pageData?.totalElements ?? rows.length);

  /** Column set — Blueprint §10.13 arrival desk. */
  const handoverColumns: ColumnDef<ArrivalHandover>[] = useMemo(() => [
    {
      id: "bookingCode",
      header: "Booking code",
      sticky: "left",
      className: "font-semibold",
      cell: (h) => h.bookingCode || "—",
    },
    {
      id: "guest",
      header: "Guest",
      cell: (h) => h.customerName || "—",
    },
    {
      id: "arrival",
      header: "Arrival date",
      minWidth: "md",
      cell: (h) => fmtDate(h.checkInDate),
    },
    {
      id: "room",
      header: "Room / Service",
      minWidth: "lg",
      className: "max-w-[180px] truncate",
      cell: (h) => <span title={h.roomSummary}>{h.roomSummary || "—"}</span>,
    },
    {
      id: "requests",
      header: "Special requests",
      minWidth: "xl",
      className: "max-w-[180px] truncate text-muted-foreground",
      cell: (h) => (
        <span title={h.specialRequests}>
          {h.specialRequests?.trim() ? h.specialRequests : "—"}
        </span>
      ),
    },
    {
      id: "assignedFo",
      header: "Assigned FO",
      minWidth: "lg",
      cell: (h) =>
        h.assignedFoName ?? (
          // Only mandatory on submit, so legacy rows have none. "Unassigned" in
          // warning tone, because it is something somebody needs to fix.
          <span className="text-warning">Unassigned</span>
        ),
    },
    {
      id: "ready",
      header: "Ready",
      sticky: "right",
      cell: (h) => <StatusPill size="sm" domain="readiness" value={h.readinessStatus} />,
    },
  ], []);

  const controls = useTableControls<ArrivalHandover>("fo-handovers", handoverColumns);

  // Clicking a KPI card filters the list by that readiness.
  const filterBy = (readiness: string) => {
    setReadinessFilter((cur) => (cur === readiness ? "" : readiness));
    setPage(0);
  };

  return (
    <div className="space-y-5">
      <PageHeader
        {...PAGE_META.frontOfficeHandover}
        actions={
          <div className="flex items-center gap-2.5">
          {/* Suppressed on error: "0 requests" next to a failure message reads as a fact. */}
          {!listQuery.isError && (
            <span className="text-xs font-semibold text-slate-500">{totalElements} requests</span>
          )}
          <Pill text="Front Office" className="bg-blue-100 text-blue-800" />
          </div>
        }
      />

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
          {isSupervisor && (
            <div className="lg:w-52">
              <Select
                aria-label="Assigned Front Office staff"
                value={assignedFoUserId}
                onChange={(e) => {
                  setAssignedFoUserId(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All FO staff</option>
                {foStaff.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.fullName}
                  </option>
                ))}
              </Select>
            </div>
          )}
          {/* A front desk is a shift rota: when the assignee is off duty, whoever is on duty still
              has to prepare the arrival. On by default for that reason; untick to narrow the list
              to your own queue. Pointless for a supervisor, who is never scoped in the first place. */}
          {!isSupervisor && (
          <label className="flex shrink-0 items-center gap-2 text-xs font-medium text-slate-600">
            <input
              type="checkbox"
              checked={deskWide}
              onChange={(e) => {
                setDeskWide(e.target.checked);
                setPage(0);
              }}
              className="size-3.5 rounded border-slate-300"
            />
            Whole desk
          </label>
          )}
        </CardContent>
      </Card>

      {/* List (UC-22.1).
          A failed request is not an empty desk: DataTable renders the error
          state, not the empty state, when `error` is set — the old hand-rolled
          body had to special-case that and told a Front Office user "no arrivals
          today" whenever the backend 403'd. */}
      <DataTable
        label="Arrival handovers"
        rows={rows}
        columns={controls.visibleColumns}
        rowId={(h) => h.handoverId}
        isLoading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        onRetry={isForbidden(listQuery.error) ? undefined : () => listQuery.refetch()}
        density={controls.density}
        sortBy={controls.sortBy}
        sortDir={controls.sortDir}
        onSortChange={controls.onSortChange}
        highlightId={highlightedId}
        rowRef={setRowRef}
        onRowClick={(h) => setSelectedId(h.handoverId)}
        isFiltered={!!readinessFilter || !!search || !!arrivalDate}
        emptyTitle="No arrival requests yet"
        emptyMessage="Handovers sent by Sales or Reservations after a booking is confirmed appear here to prepare for arrival."
        footer={
          <TablePagination
            page={page}
            pageSize={pageSize}
            totalElements={totalElements}
            totalPages={totalPages}
            onPageChange={setPage}
            onPageSizeChange={(s) => {
              setPageSize(s);
              setPage(0);
            }}
            pageSizeOptions={[10, 20, 50]}
          />
        }
      />

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
const READINESS_LABELS: Record<ReadinessStatus, string> = {
  PENDING_REVIEW: "Pending review",
  REVIEWED: "Reviewed",
  READY_FOR_ARRIVAL: "Ready for arrival",
  NEED_CLARIFICATION: "Needs clarification",
};

const READINESS_ORDER: ReadinessStatus[] = [
  "PENDING_REVIEW",
  "REVIEWED",
  "READY_FOR_ARRIVAL",
  "NEED_CLARIFICATION",
];

function ReadinessForm({ id, detail }: { id: string; detail: ArrivalHandover }) {
  const updateReadiness = useUpdateReadiness();

  const current = (detail.readinessStatus as ReadinessStatus) ?? "PENDING_REVIEW";
  const [readiness, setReadiness] = useState<ReadinessStatus | "">(
    (detail.readinessStatus as ReadinessStatus) ?? "",
  );
  const [note, setNote] = useState(detail.clarificationNote ?? "");
  const [localError, setLocalError] = useState<string | null>(null);

  // BR-44 — a cancelled / no-show / checked-out booking freezes readiness entirely.
  //
  // Not dead code, despite the detail endpoint now 404ing those bookings: this guards the CACHED
  // copy. React Query keeps the last successful payload when a refetch fails, so a drawer left open
  // across a cancellation keeps rendering the booking as it was. Without this the form would stay
  // enabled and the user would find out by submitting into a 422.
  const bookingActive = isBookingActive(detail.bookingStatus);
  // POST-4 — from NEED_CLARIFICATION the only move is to amend the note; Sales/Reservation must
  // re-submit before readiness can be confirmed. The server enforces this; we mirror it so the
  // dropdown never offers a move that would be refused.
  const allowed = READINESS_TRANSITIONS[current] ?? [];
  const waitingOnSales = current === "NEED_CLARIFICATION";

  const needsClarification = readiness === "NEED_CLARIFICATION";
  const dirty =
    !!readiness &&
    (readiness !== detail.readinessStatus ||
      (needsClarification && note.trim() !== (detail.clarificationNote ?? "")));

  const handleSave = () => {
    setLocalError(null);
    if (!readiness || !dirty || !bookingActive) return;
    if (needsClarification && !note.trim()) {
      // Same sentence the server sends for E7.2, so the user reads one message whether the
      // client caught it or the request did.
      setLocalError("Clarification note is required.");
      return;
    }
    // `mutate`, not `mutateAsync`: an awaited rejection here was an unhandled promise rejection,
    // and the 422 the server sends (E7.2 / E7.3 / POST-4) carries the sentence the user needs.
    updateReadiness.mutate(
      {
        id,
        readinessStatus: readiness,
        clarificationNote: needsClarification ? note.trim() : undefined,
      },
      {
        onError: (err) =>
          setLocalError(serverMessage(err, "Update failed. Please try again.")),
      },
    );
  };

  return (
    <section className="space-y-2 rounded-lg border border-brand-500/30 bg-brand-500/8 p-3">
      <p className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
        Update readiness status
      </p>

      {!bookingActive && (
        <p className="text-[11.5px] text-danger">
          This booking is {detail.bookingStatus?.toLowerCase().replace(/_/g, " ")} — its
          arrival readiness can no longer be changed.
        </p>
      )}

      <Select
        aria-label="Readiness status"
        value={readiness}
        disabled={!bookingActive}
        onChange={(e) => {
          setReadiness(e.target.value as ReadinessStatus);
          setLocalError(null);
        }}
      >
        {READINESS_ORDER.map((value) => (
          <option
            key={value}
            value={value}
            // Keep the current value selectable so the control shows where the record stands,
            // even when it is a state Front Office cannot re-enter.
            disabled={!allowed.includes(value) && value !== current}
          >
            {READINESS_LABELS[value]}
          </option>
        ))}
      </Select>

      {needsClarification && (
        <textarea
          rows={3}
          value={note}
          disabled={!bookingActive}
          onChange={(e) => {
            setNote(e.target.value);
            setLocalError(null);
          }}
          placeholder="Details for Sales/Reservations to clarify…"
          aria-label="Clarification details"
          className="w-full resize-none rounded-md border border-border bg-input px-3 py-2 text-[12.5px] text-foreground placeholder:text-muted-foreground focus-ring disabled:opacity-60"
        />
      )}

      {localError && <p className="text-[11.5px] text-danger">{localError}</p>}
      {updateReadiness.isSuccess && !dirty && !localError && (
        <p className="text-[11.5px] text-success">Updated.</p>
      )}

      <Button
        size="sm"
        className="w-full"
        disabled={!dirty || !bookingActive || updateReadiness.isPending}
        isLoading={updateReadiness.isPending}
        leftIcon={<Save className="size-3.5" />}
        onClick={handleSave}
      >
        Save status
      </Button>

      {waitingOnSales && bookingActive && (
        <p className="text-[11px] text-muted-foreground">
          Waiting on Sales/Reservations. They have to update and re-submit this handover
          before you can confirm it is ready — you can still amend the note above.
        </p>
      )}
      {needsClarification && !waitingOnSales && (
        <p className="text-[11px] text-muted-foreground">
          Choosing “Needs clarification” notifies the responsible
          Sales/Reservations staff.
        </p>
      )}
    </section>
  );
}

/** The message the API sent, falling back to something honest if the shape is unexpected. */
function serverMessage(err: unknown, fallback = "Please try again."): string {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data
    ?.message;
  return message?.trim() || fallback;
}

/**
 * Whether the request was refused rather than broken. Worth separating: a 403 will not fix itself,
 * so offering "Try again" on one is a lie, and telling the user their desk is empty is worse.
 */
function isForbidden(err: unknown): boolean {
  return (err as { response?: { status?: number } })?.response?.status === 403;
}
