"use client";

import React, { useState, useMemo, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FileSpreadsheet, Search, CheckCircle2, Calendar, Plus, Send, GitBranch, MessageSquare, Sparkles, Building2, Archive, TimerOff, ChevronDown, ChevronUp, ListFilter, Bell, BedDouble, X } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { StatusPill } from "@/components/ui/status-pill";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import { DensityMenu } from "@/components/ui/list-toolbar";
import {
  ColumnPicker,
  ExportMenu,
  RefreshButton,
  useTableControls,
} from "@/components/ui/table-controls";

/** Sortable fields the local comparator implements. */
type SortField = "total" | "validUntil";

const QUOTATION_EXPORT_HEADERS = [
  "Quote no", "Client", "Deal", "Amount (VND)", "Valid until", "Status",
];

function quotationExportRow(q: Quotation): (string | number | null | undefined)[] {
  return [q.quoteNo, q.contactName, q.dealName, q.amount, q.expiryDate, q.status];
}
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { SendQuotationModal } from "@/features/quotation/components/SendQuotationModal";
import { QuotationDetailDrawer } from "@/features/quotation/components/QuotationDetailDrawer";
import { RecordResponseModal } from "@/features/quotation/components/RecordResponseModal";
import { ConvertToBookingModal } from "@/features/quotation/components/ConvertToBookingModal";
import { ExpireCloseModal } from "@/features/quotation/components/ExpireCloseModal";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { CreateReminderModal } from "@/features/reminder/components/CreateReminderModal";
import { QuotationActionMenu, type QuotationMenuAction } from "@/features/quotation/components/QuotationActionMenu";
import { RoomConfirmationPanel } from "@/features/room_request/components/RoomConfirmationPanel";
import type { Quotation } from "@/services/quotation_service";
export type { Quotation } from "@/services/quotation_service";
import { useQuotations, useExpireOverdue, useSubmitQuotation } from "@/features/quotation/hooks/use_quotations";
import { useAuthStore } from "@/stores/auth_store";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import { pageMeta } from "@/services/api_client";

// "rejected" stays active, not done — Revise is still the primary action on it
// (see getPrimaryAction below), so filing it under "Done" alongside truly terminal
// statuses would hide a quotation the staff still needs to act on.
const ACTIVE_STATUSES: Quotation["status"][] = [
  "draft", "pending_approval", "approved", "sent", "accepted", "interested",
  "pending_revision", "rejected", "pending_customer_response",
  "reservation_pending", "reservation_rejected"
];
const DONE_STATUSES: Quotation["status"][] = ["converted", "closed", "expired", "accepted_by_customer", "booking_request"];

type ClosureLog = {
  id: string;
  quotationId: string;
  quoteNo: string;
  contactName: string;
  action: "expired" | "closed";
  reason: string;
  closedAt: string;
  closedBy: string;
  previousStatus: string;
};

export function QuotationListScreen() {
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 10;

  const [sortField, setSortField] = useState<"total" | "validUntil" | null>(null);
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState<"active" | "done">("active");
  const [statusFilter, setStatusFilter] = useState<string>("");

  const params = useMemo(() => {
    const backendStatuses = (activeTab === "active" ? ACTIVE_STATUSES : DONE_STATUSES).map(s => s.toUpperCase());
    const backendStatus = statusFilter ? statusFilter.toUpperCase() : undefined;
    return {
      page: currentPage - 1,
      size: pageSize,
      search: search || undefined,
      status: backendStatus,
      statuses: backendStatuses,
      sortBy: sortField === "total" ? "totalAmount" : (sortField || "validUntil"),
      sortDir: sortDir,
    };
  }, [activeTab, statusFilter, search, currentPage, sortField, sortDir]);

  const { data: pageResult, isLoading, isFetching, refetch } = useQuotations(params);

  // Keep parallel counts queries with size: 1 to fetch counts efficiently
  const { data: activePageResult } = useQuotations({
    statuses: ACTIVE_STATUSES.map(s => s.toUpperCase()),
    page: 0,
    size: 1
  });
  const { data: donePageResult } = useQuotations({
    statuses: DONE_STATUSES.map(s => s.toUpperCase()),
    page: 0,
    size: 1
  });

  const activeCount = useMemo(() => pageMeta(activePageResult).totalElements, [activePageResult]);
  const doneCount = useMemo(() => pageMeta(donePageResult).totalElements, [donePageResult]);

  const serverQuotes = useMemo(() => pageResult?.content ?? [], [pageResult]);
  const meta = useMemo(() => pageMeta(pageResult), [pageResult]);

  const { user } = useAuthStore();
  const router = useRouter();
  const { highlightedId, setRowRef } = useHighlightRow();
  const expireOverdue = useExpireOverdue();
  const submitQuotation = useSubmitQuotation();
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [localStatusMap, setLocalStatusMap] = useState<Record<string, Quotation["status"]>>({});
  
  const quotes = useMemo(
    () => serverQuotes.map(q => ({
      ...q,
      status: (q.id in localStatusMap ? localStatusMap[q.id] : q.status) as Quotation["status"],
    })),
    [serverQuotes, localStatusMap]
  );

  const [closureLogs, setClosureLogs] = useState<ClosureLog[]>([]);
  const [sendTarget, setSendTarget] = useState<Quotation | null>(null);
  const [detailTarget, setDetailTarget] = useState<Quotation | null>(null);
  const [responseTarget, setResponseTarget] = useState<Quotation | null>(null);
  const [convertTarget, setConvertTarget] = useState<Quotation | null>(null);
  const [closeTarget, setCloseTarget] = useState<Quotation | null>(null);
  const [autoExpireResult, setAutoExpireResult] = useState<number | null>(null);
  const [showClosureLog, setShowClosureLog] = useState(false);
  const [reminderTarget, setReminderTarget] = useState<Quotation | null>(null);
  const [roomTarget, setRoomTarget] = useState<Quotation | null>(null);
  const [openActionMenuId, setOpenActionMenuId] = useState<string | null>(null);

  const filterPills = useMemo(() => {
    if (activeTab === "active") {
      return [
        { value: "", label: "All" },
        { value: "draft", label: "Draft" },
        { value: "pending_approval", label: "Pending Approval" },
        { value: "approved", label: "Approved" },
        { value: "sent", label: "Sent" },
        { value: "accepted", label: "Accepted" },
        { value: "rejected", label: "Rejected" },
        { value: "reservation_pending", label: "Awaiting Reservation" },
      ];
    } else {
      return [
        { value: "", label: "All" },
        { value: "converted", label: "Converted" },
        { value: "booking_request", label: "Booking Requested" },
        { value: "closed", label: "Closed" },
        { value: "expired", label: "Expired" },
      ];
    }
  }, [activeTab]);

  const handleSort = (field: SortField, dir: "asc" | "desc") => {
    setSortField(field);
    setSortDir(dir);
    setCurrentPage(1);
  };

  const handleTabChange = (tab: "active" | "done") => {
    setActiveTab(tab);
    setStatusFilter("");
    setCurrentPage(1);
  };

  // Reset to page 1 whenever the filtered set changes shape
  useEffect(() => {
    setCurrentPage(1);
  }, [search, statusFilter]);

  /**
   * Column set — Blueprint §10.7.
   *
   * `total` and `validUntil` are the only sortable columns because they are the
   * only two the local comparator implements; marking the rest sortable would
   * render an affordance that does nothing when clicked.
   */
  const quotationColumns: ColumnDef<Quotation>[] = useMemo(() => [
    {
      id: "quoteNo",
      header: "Quote Reference",
      sticky: "left",
      cell: (q) => (
        <span className="flex items-center gap-1.5 text-xs font-bold text-primary">
          <FileSpreadsheet className="size-3.5 text-muted-foreground" />
          {q.quoteNo}
        </span>
      ),
    },
    {
      id: "client",
      header: "Client Name",
      className: "text-xs font-semibold",
      cell: (q) => q.contactName,
    },
    {
      id: "deal",
      header: "Linked Deal",
      minWidth: "lg",
      className: "text-xs text-muted-foreground",
      cell: (q) => (
        <span className="max-w-[180px] truncate block text-xs text-muted-foreground" title={q.dealName}>
          {q.dealName}
        </span>
      ),
    },
    {
      id: "rooms",
      header: "Rooms",
      minWidth: "md",
      cell: (q) => {
        if (q.roomLines && q.roomLines.length > 0) {
          const mainLine = q.roomLines[0];
          const extraCount = q.roomLines.length - 1;
          const allBreakdown = q.roomLines.map((l) => `${l.roomType} × ${l.numberOfRooms}`).join(", ");
          return (
            <div
              className="flex items-center gap-1.5 text-xs max-w-[220px] overflow-hidden whitespace-nowrap"
              title={allBreakdown}
            >
              <span className="font-medium text-foreground truncate min-w-0">
                {mainLine.roomType} × {mainLine.numberOfRooms}
              </span>
              {extraCount > 0 && (
                <span className="shrink-0 rounded-md bg-brand-500/10 px-1.5 py-0.5 text-[10px] font-bold text-brand-600 dark:bg-brand-500/20 dark:text-brand-400 cursor-help">
                  +{extraCount} more
                </span>
              )}
            </div>
          );
        }
        return (
          <span
            className="text-xs text-muted-foreground truncate max-w-[200px] block"
            title={q.roomType ? `${q.roomType} (${q.numberOfRooms ?? 1})` : "—"}
          >
            {q.roomType ? `${q.roomType} (${q.numberOfRooms ?? 1})` : "—"}
          </span>
        );
      },
    },
    {
      id: "total",
      header: "Total",
      numeric: true,
      sortable: true,
      className: "font-bold",
      cell: (q) => `${q.amount.toLocaleString("vi-VN")} ₫`,
    },
    {
      id: "validUntil",
      header: "Valid Until",
      sortable: true,
      minWidth: "lg",
      cell: (q) => (
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
          <Calendar className="size-3" />
          {q.expiryDate}
        </span>
      ),
    },
    {
      id: "status",
      header: "Status",
      // Canonical quotation binding (Blueprint §2.7): danger is reserved for
      // REJECTED — an expired quote is inert, not a failure.
      cell: (q) => <StatusPill size="sm" domain="quotation" value={q.status} />,
    },
    {
      id: "sla",
      header: "SLA",
      minWidth: "xl",
      cell: (q) => <SlaStatusBadge entityId={q.id} entityType="QUOTATION" />,
    },
    {
      id: "actions",
      header: "",
      sticky: "right",
      cell: (q) => {
        const primary = getPrimaryAction(q);
        return (
          <div
            className="flex items-center justify-end gap-1.5"
            onClick={(e) => e.stopPropagation()}
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {primary && (
              <Button
                variant={primary.tone === "danger" ? "danger" : "primary"}
                size="xs"
                isLoading={primary.key === "submit" && submittingId === q.id}
                onClick={(e) => {
                  e.stopPropagation();
                  primary.onClick();
                }}
                leftIcon={<primary.Icon className="size-3" />}
                className="whitespace-nowrap"
              >
                {primary.label}
              </Button>
            )}
            <QuotationActionMenu
              actions={getMenuActions(q)}
              isOpen={openActionMenuId === q.id}
              onToggle={() => setOpenActionMenuId((cur) => (cur === q.id ? null : q.id))}
              onClose={() => setOpenActionMenuId(null)}
            />
          </div>
        );
      },
    },
    // Cells close over handlers and per-row state that change every render;
    // rebuilding the column list each pass is correct and cheap for 8 columns.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [submittingId, openActionMenuId]);

  const controls = useTableControls<Quotation>("quotations", quotationColumns, {
    defaultSortBy: "validUntil",
  });

  const handleSent = (_quotationId: string) => {
    setLocalStatusMap(prev => ({ ...prev, [_quotationId]: "sent" }));
    setSendTarget(null);
  };

  const handleResponseRecorded = (_quotationId: string, newStatus: Quotation["status"]) => {
    setLocalStatusMap(prev => ({ ...prev, [_quotationId]: newStatus }));
    setResponseTarget(null);
  };

  const handleConverted = (_quotationId: string, _bookingNo: string) => {
    setLocalStatusMap(prev => ({ ...prev, [_quotationId]: "converted" }));
    setConvertTarget(null);
  };

  const handleSubmitDraft = async (q: Quotation) => {
    setSubmittingId(q.id);
    try {
      const result = await submitQuotation.mutateAsync({
        id: q.id,
        payload: {
          submittedByName: user?.name ?? user?.email ?? "Staff",
          submittedByRole: user?.roles?.[0] ?? "SALES_STAFF",
        },
      });
      const newStatus = result.data?.status as Quotation["status"];
      if (newStatus) {
        setLocalStatusMap(prev => ({ ...prev, [q.id]: newStatus }));
      }
    } catch {
      // silent — server error will surface via list refetch
    } finally {
      setSubmittingId(null);
    }
  };

  const handleClosed = (_quotationId: string, reason: string) => {
    const target = quotes.find(q => q.id === _quotationId);
    if (target) {
      setClosureLogs(prev => [{
        id: `CL-${_quotationId}`,
        quotationId: _quotationId,
        quoteNo: target.quoteNo,
        contactName: target.contactName,
        action: "closed",
        reason,
        closedAt: new Date().toISOString().split("T")[0],
        closedBy: user?.name ?? user?.email ?? "Staff",
        previousStatus: target.status,
      }, ...prev]);
    }
    setLocalStatusMap(prev => ({ ...prev, [_quotationId]: "closed" }));
    setCloseTarget(null);
  };

  const runAutoExpire = async () => {
    try {
      const res = await expireOverdue.mutateAsync({
        expiredByName: user?.name ?? user?.email ?? "System (Auto)",
        expiredByRole: user?.roles?.[0] ?? "SALES_STAFF",
      });
      const { expiredCount = 0, expiredIds = [] } = res.data ?? {};
      if (expiredCount > 0) {
        const today = new Date().toISOString().split("T")[0];
        setClosureLogs(prev => [
          ...expiredIds.map((id: string) => {
            const q = quotes.find(qt => qt.id === id);
            return {
              id: `CL-auto-${id}`,
              quotationId: id,
              quoteNo: q?.quoteNo ?? "—",
              contactName: q?.contactName ?? "—",
              action: "expired" as const,
              reason: "Validity period exceeded — auto-expired by system",
              closedAt: today,
              closedBy: user?.name ?? "System (Auto)",
              previousStatus: q?.status ?? "unknown",
            };
          }),
          ...prev,
        ]);
        const updates: Record<string, Quotation["status"]> = {};
        expiredIds.forEach((id: string) => { updates[id] = "expired"; });
        setLocalStatusMap(prev => ({ ...prev, ...updates }));
      }
      setAutoExpireResult(expiredCount);
    } catch {
      setAutoExpireResult(0);
    }
    setTimeout(() => setAutoExpireResult(null), 4000);
  };

  const statusBadgeVariant = (status: Quotation["status"]) => {
    if (status === "accepted" || status === "approved" || status === "converted" || status === "booking_request") return "success";
    if (status === "sent") return "primary";
    if (status === "expired" || status === "rejected" || status === "closed" || status === "reservation_rejected") return "danger";
    if (status === "pending_approval") return "warning";
    if (status === "pending_revision" || status === "interested" || status === "reservation_pending") return "info";
    return "default";
  };

  const statusLabel = (status: Quotation["status"]) => {
    if (status === "pending_approval") return "Pending Approval";
    if (status === "pending_revision") return "Needs Revision";
    if (status === "interested") return "Interested";
    if (status === "converted") return "Converted";
    if (status === "closed") return "Closed";
    if (status === "reservation_pending") return "Awaiting Reservation";
    if (status === "reservation_rejected") return "Reservation Rejected";
    if (status === "booking_request") return "Booking Requested";
    return status;
  };

  // One clear primary action per row (the single next step) plus an overflow
  // menu for everything else (Revise / Close / Remind) — the Status column
  // already shows the badge, so the actions cell no longer repeats it as text.
  const getPrimaryAction = (q: Quotation): QuotationMenuAction | null => {
    switch (q.status) {
      case "approved":
        return { key: "send", label: "Send to Customer", Icon: Send, onClick: () => setSendTarget(q), tone: "primary" };
      case "sent":
        return { key: "respond", label: "Record Response", Icon: MessageSquare, onClick: () => setResponseTarget(q), tone: "primary" };
      case "accepted":
        return { key: "convert", label: "Convert to Booking", Icon: Building2, onClick: () => setConvertTarget(q), tone: "primary" };
      case "rejected":
      case "reservation_rejected":
      case "pending_revision":
        return { key: "revise", label: "Revise", Icon: GitBranch, onClick: () => router.push(ROUTE_PATHS.quotationRevise(q.id)), tone: "primary" };
      case "draft":
        return { key: "submit", label: "Submit", Icon: CheckCircle2, onClick: () => handleSubmitDraft(q), tone: "primary" };
      case "interested":
        return { key: "update-response", label: "Update Response", Icon: Sparkles, onClick: () => setResponseTarget(q), tone: "primary" };
      default:
        return null;
    }
  };

  const getMenuActions = (q: Quotation): QuotationMenuAction[] => {
    const actions: QuotationMenuAction[] = [];
    // Revise is already the primary action for rejected/pending_revision — only
    // list it here for statuses where it's a secondary option.
    if (["sent", "draft", "interested", "pending_customer_response"].includes(q.status)) {
      actions.push({ key: "revise", label: "Revise", Icon: GitBranch, onClick: () => router.push(ROUTE_PATHS.quotationRevise(q.id)) });
    }
    if (!["converted", "expired", "closed", "accepted_by_customer", "booking_request"].includes(q.status)) {
      actions.push({ key: "close", label: "Close", Icon: Archive, onClick: () => setCloseTarget(q), tone: "danger" });
    }
    // Sending and converting are both gated on a confirmed room, so let the rep ask as
    // early as they like rather than only from inside those modals.
    if (!["converted", "expired", "closed", "accepted_by_customer", "booking_request"].includes(q.status)) {
      actions.push({ key: "rooms", label: "Room Confirmation", Icon: BedDouble, onClick: () => setRoomTarget(q), tone: "primary" });
    }
    actions.push({ key: "remind", label: "Add Reminder", Icon: Bell, onClick: () => setReminderTarget(q) });
    return actions;
  };

  return (
    <div className="space-y-6">
      <PageHeader
        {...PAGE_META.quotations}
        actions={
          <>
            <Button
              variant="secondary"
              onClick={runAutoExpire}
              isLoading={expireOverdue.isPending}
              leftIcon={<TimerOff className="size-4" />}
            >
              Auto-Expire Overdue
            </Button>
            <Link href={ROUTE_PATHS.quotationCreate}>
              <Button variant="primary" leftIcon={<Plus className="size-4" />}>
                New Quotation
              </Button>
            </Link>
          </>
        }
      />

      {/* Auto-expire result banner */}
      {autoExpireResult !== null && (
        <div className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-semibold border ${
          autoExpireResult > 0
            ? "bg-amber-50 border-amber-200 text-amber-700"
            : "bg-slate-50 border-slate-200 text-slate-500"
        }`}>
          <TimerOff className="size-3.5 shrink-0" />
          {autoExpireResult > 0
            ? `${autoExpireResult} overdue quotation${autoExpireResult !== 1 ? "s" : ""} marked as Expired. Linked reminders resolved.`
            : "No overdue quotations found — all validity periods are current."}
        </div>
      )}

      {/* Status tabs */}
      <div className="flex items-center gap-1 border-b border-slate-200 bg-white rounded-t-xl px-4 pt-3 -mb-6 shadow-sm">
        <button
          type="button"
          onClick={() => handleTabChange("active")}
          className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold border-b-2 transition -mb-px ${
            activeTab === "active"
              ? "border-primary text-blue-700"
              : "border-transparent text-slate-500 hover:text-slate-700"
          }`}
        >
          In Progress
          <span className={`px-1.5 py-0.5 rounded-full text-[9px] font-bold ${
            activeTab === "active" ? "bg-blue-100 text-blue-600" : "bg-slate-100 text-slate-500"
          }`}>
            {activeCount}
          </span>
        </button>
        <button
          type="button"
          onClick={() => handleTabChange("done")}
          className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold border-b-2 transition -mb-px ${
            activeTab === "done"
              ? "border-slate-600 text-slate-700"
              : "border-transparent text-slate-500 hover:text-slate-700"
          }`}
        >
          Completed
          <span className={`px-1.5 py-0.5 rounded-full text-[9px] font-bold ${
            activeTab === "done" ? "bg-slate-200 text-slate-600" : "bg-slate-100 text-slate-400"
          }`}>
            {doneCount}
          </span>
        </button>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white rounded-t-none">
        <CardContent className="py-3 px-4">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search quote reference #, client name..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>
            <div className="flex items-center gap-1.5 flex-wrap">
              {filterPills.map((pill) => {
                const isActive = statusFilter === pill.value;
                return (
                  <button
                    key={pill.value}
                    type="button"
                    onClick={() => setStatusFilter(pill.value)}
                    className={`px-3 py-1 rounded-full text-[11px] font-semibold border transition-all duration-150 cursor-pointer ${
                      isActive
                        ? "bg-primary text-white border-primary shadow-xs"
                        : "bg-slate-50 dark:bg-zinc-900 text-slate-500 dark:text-zinc-400 border-slate-200 dark:border-zinc-800 hover:bg-slate-100 dark:hover:bg-zinc-800 hover:text-slate-700 dark:hover:text-zinc-200"
                    }`}
                  >
                    {pill.label}
                  </button>
                );
              })}
            </div>
            {(search || statusFilter) && (
              <button
                type="button"
                onClick={() => { setSearch(""); setStatusFilter(""); }}
                className="text-[10px] text-slate-400 hover:text-slate-600 underline"
              >
                Clear filters
              </button>
            )}

            {/* §2.6 control cluster */}
            <div className="ml-auto flex items-center gap-2">
              <RefreshButton onRefresh={() => refetch()} isRefreshing={isFetching} />
              <ColumnPicker
                columns={quotationColumns}
                hiddenIds={controls.hiddenColumnIds}
                onChange={controls.setHiddenColumnIds}
                requiredIds={["quoteNo", "actions"]}
              />
              <ExportMenu
                filename={`quotations-${new Date().toISOString().slice(0, 10)}`}
                headers={QUOTATION_EXPORT_HEADERS}
                rows={quotes.map(quotationExportRow)}
              />
              <DensityMenu value={controls.density} onChange={controls.setDensity} />
            </div>
          </div>
        </CardContent>
      </Card>

      <DataTable
        label="Quotations"
        rows={quotes}
        columns={controls.visibleColumns}
        rowId={(q) => q.id}
        isLoading={isLoading}
        density={controls.density}
        sortBy={sortField ?? undefined}
        sortDir={sortDir}
        onSortChange={(columnId, dir) => {
          // Only Total and Valid Until have a comparator; the rest of the header
          // stays inert rather than offering a sort that does nothing.
          if (columnId === "total" || columnId === "validUntil") {
            handleSort(columnId, dir);
          }
        }}
        highlightId={highlightedId}
        rowRef={setRowRef}
        onRowClick={(q) => setDetailTarget(q)}
        selectedIds={controls.selectedIds}
        onSelectionChange={controls.setSelectedIds}
        bulkActions={
          <ExportMenu
            filename={`quotations-selected-${new Date().toISOString().slice(0, 10)}`}
            headers={QUOTATION_EXPORT_HEADERS}
            rows={quotes.filter((q) => controls.selectedIds.has(q.id)).map(quotationExportRow)}
          />
        }
        isFiltered={!!search || !!statusFilter}
        onClearFilters={() => { setSearch(""); setStatusFilter(""); }}
        emptyTitle={activeTab === "active" ? "No active quotations" : "No completed quotations"}
        emptyMessage="Quotations you create from a deal will appear here."
        footer={
          <TablePagination
            page={currentPage - 1}
            pageSize={pageSize}
            totalElements={meta.totalElements}
            totalPages={meta.totalPages}
            onPageChange={(p) => setCurrentPage(p + 1)}
          />
        }
      />

      {/* Itemized Multi-Room Detailed View Drawer */}
      <QuotationDetailDrawer
        quote={detailTarget}
        onClose={() => setDetailTarget(null)}
        onSend={(q) => setSendTarget(q)}
        onConvertToBooking={(q) => setConvertTarget(q)}
        onRevise={(q) => router.push(ROUTE_PATHS.quotationRevise(q.id))}
        showResendButton={true}
      />

      {/* UC-14.4: Send Quotation Modal */}
      {sendTarget && (
        <SendQuotationModal
          quote={sendTarget}
          onClose={() => setSendTarget(null)}
          onSent={handleSent}
        />
      )}

      {/* UC-14.6: Record Customer Response Modal */}
      {responseTarget && (
        <RecordResponseModal
          quote={responseTarget}
          onClose={() => setResponseTarget(null)}
          onRecorded={handleResponseRecorded}
        />
      )}

      {/* UC-14.7: Convert to Booking Modal */}
      {convertTarget && (
        <ConvertToBookingModal
          quote={convertTarget}
          onClose={() => setConvertTarget(null)}
          onConverted={handleConverted}
        />
      )}

      {/* UC-14.8: Expire / Close Modal */}
      {closeTarget && (
        <ExpireCloseModal
          quote={closeTarget}
          onClose={() => setCloseTarget(null)}
          onClosed={handleClosed}
        />
      )}

      {/* UC-16.1: Create Reminder pre-filled for this quotation */}
      {reminderTarget && (
        <CreateReminderModal
          defaultRelatedEntity="QUOTATION"
          defaultRelatedId={reminderTarget.id}
          onClose={() => setReminderTarget(null)}
        />
      )}

      {/* Ask the Reservation team about rooms, from any live status — Send and Convert are
          both gated on their answer, so waiting until those modals open is too late. */}
      {roomTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl">
            <div className="mb-3 flex items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold text-slate-800">Room Confirmation</h2>
                <p className="mt-0.5 text-xs text-slate-500">
                  {roomTarget.quoteNo} · {roomTarget.roomType ?? "—"} ·{" "}
                  {roomTarget.checkInDate ?? "—"} → {roomTarget.checkOutDate ?? "—"}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setRoomTarget(null)}
                className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X className="size-4" />
              </button>
            </div>
            <RoomConfirmationPanel quote={roomTarget} />
          </div>
        </div>
      )}

      {/* UC-14.8: Closure & Expiry Audit Log */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardHeader>
          <button
            type="button"
            onClick={() => setShowClosureLog((v) => !v)}
            className="flex items-center justify-between w-full"
          >
            <CardTitle className="text-sm font-bold text-slate-700 flex items-center gap-2">
              <Archive className="size-4 text-slate-400" />
              Closure &amp; Expiry Audit Log
              <span className="text-[10px] font-normal text-slate-400">
                ({closureLogs.length} entr{closureLogs.length !== 1 ? "ies" : "y"})
              </span>
            </CardTitle>
            {showClosureLog ? (
              <ChevronUp className="size-4 text-slate-400" />
            ) : (
              <ChevronDown className="size-4 text-slate-400" />
            )}
          </button>
        </CardHeader>
        {showClosureLog && (
          <CardContent className="pt-0">
            {closureLogs.length === 0 ? (
              <p className="text-xs text-slate-400 italic py-2">No closures or expirations recorded yet.</p>
            ) : (
              <Table>
                <TableHeader className="bg-slate-50">
                  <TableRow hoverable={false}>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Quote</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Client</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Action</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Prev. Status</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Date</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">By</TableHead>
                    <TableHead className="text-[10px] font-semibold text-slate-500">Reason</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[...closureLogs].reverse().map((log) => (
                    <TableRow key={log.id} className="border-b border-slate-100">
                      <TableCell className="py-2 text-xs font-bold text-blue-600">{log.quoteNo}</TableCell>
                      <TableCell className="py-2 text-xs text-slate-700">{log.contactName}</TableCell>
                      <TableCell className="py-2">
                        <Badge
                          variant={log.action === "expired" ? "warning" : "default"}
                          size="sm"
                          className="font-bold text-[9px] uppercase"
                        >
                          {log.action}
                        </Badge>
                      </TableCell>
                      <TableCell className="py-2 text-xs text-slate-500 capitalize">{log.previousStatus.replace("_", " ")}</TableCell>
                      <TableCell className="py-2 text-xs text-slate-500">{log.closedAt}</TableCell>
                      <TableCell className="py-2 text-xs text-slate-500">{log.closedBy}</TableCell>
                      <TableCell className="py-2 text-xs text-slate-400 max-w-[160px] truncate">{log.reason}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        )}
      </Card>
    </div>
  );
}
