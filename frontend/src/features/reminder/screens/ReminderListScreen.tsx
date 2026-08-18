"use client";

import React, { useState, useMemo } from "react";
import {
  Bell, Clock, AlertTriangle, Plus, Filter, Search,
  FileSpreadsheet, Calendar, LayoutList, ChevronLeft, ChevronRight,
  Users, Building2, CreditCard, ChevronUp, ChevronDown, ArrowUpDown, Pencil,
} from "lucide-react";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { ExportMenu, useTableControls } from "@/components/ui/table-controls";
import { OwnerCell } from "@/components/ui/row-actions";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Badge } from "@/components/ui/Badge";
import { CreateReminderModal } from "@/features/reminder/components/CreateReminderModal";
import { UpdateReminderModal } from "@/features/reminder/components/UpdateReminderModal";
import { ReminderDetailDrawer } from "@/features/reminder/components/ReminderDetailDrawer";
import { useReminders } from "@/features/reminder/hooks/use_reminders";
import { useUsers } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { useAuthStore } from "@/stores/auth_store";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import type { Reminder, ReminderStatus } from "@/services/reminder_service";

// ─── Constants ────────────────────────────────────────────────────────────────

const PRIORITY_VARIANT: Record<string, "danger" | "warning" | "default"> = {
  HIGH: "danger", MEDIUM: "warning", LOW: "default",
};

const STATUS_VARIANT: Record<ReminderStatus, "danger" | "warning" | "success" | "default"> = {
  PENDING: "warning", OVERDUE: "danger", DONE: "success", CANCELLED: "default",
};

const REMINDER_EXPORT_HEADERS = [
  "Title", "Description", "Due", "Priority", "Status", "Linked to", "Assigned to", "Created by",
];

function reminderExportRow(r: Reminder): (string | number | null | undefined)[] {
  return [
    r.title, r.description ?? "", r.remindAt, r.priority, r.status,
    r.relatedEntity, r.assignedUserName ?? "", r.createdByName ?? "",
  ];
}

const ENTITY_ICON: Record<string, React.ReactNode> = {
  QUOTATION: <FileSpreadsheet className="size-3 text-blue-400" />,
  LEAD:      <Users className="size-3 text-emerald-400" />,
  BOOKING:   <Building2 className="size-3 text-violet-400" />,
  DEPOSIT:   <CreditCard className="size-3 text-amber-400" />,
};

const PRIORITY_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
// "Active" = still needs attention (pending or overdue); "Done" = past, no more action needed.
const DONE_REMINDER_STATUSES: ReminderStatus[] = ["DONE", "CANCELLED"];
const WEEKDAYS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];
const MONTHS   = ["January","February","March","April","May","June","July","August","September","October","November","December"];
const PAD      = (n: number) => String(n).padStart(2, "0");
const TODAY_STR = new Date().toISOString().slice(0, 10);

// ─── Helpers ──────────────────────────────────────────────────────────────────

function isOverdue(r: Reminder): boolean {
  return r.status === "PENDING" && new Date(r.remindAt) < new Date();
}

function formatRemindAt(iso: string): string {
  return new Date(iso).toLocaleString("en-GB", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function matchesDateFilter(r: Reminder, dateFilter: string, calDay: string | null): boolean {
  if (calDay) return r.remindAt.slice(0, 10) === calDay;
  if (!dateFilter) return true;
  const d = new Date(r.remindAt);
  const now = new Date();
  if (dateFilter === "today") return d.toDateString() === now.toDateString();
  if (dateFilter === "week") {
    const s = new Date(now); s.setDate(now.getDate() - now.getDay()); s.setHours(0, 0, 0, 0);
    const e = new Date(s);   e.setDate(s.getDate() + 6);              e.setHours(23, 59, 59, 999);
    return d >= s && d <= e;
  }
  if (dateFilter === "month") return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
  return true;
}

function applySort(list: Reminder[], sortField: "due" | "priority", sortDir: "asc" | "desc"): Reminder[] {
  const dirMul = sortDir === "asc" ? 1 : -1;
  return [...list].sort((a, b) => {
    if (sortField === "priority") {
      return ((PRIORITY_ORDER[a.priority] ?? 1) - (PRIORITY_ORDER[b.priority] ?? 1)) * dirMul;
    }
    return (new Date(a.remindAt).getTime() - new Date(b.remindAt).getTime()) * dirMul;
  });
}

// ─── Calendar Component ───────────────────────────────────────────────────────

function CalendarView({ reminders, selectedDay, onDayClick }: {
  reminders: Reminder[];
  selectedDay: string | null;
  onDayClick: (dateStr: string) => void;
}) {
  const now = new Date();
  const [year, setYear]   = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());

  const prevMonth = () => month === 0 ? (setYear(y => y - 1), setMonth(11)) : setMonth(m => m - 1);
  const nextMonth = () => month === 11 ? (setYear(y => y + 1), setMonth(0)) : setMonth(m => m + 1);

  const daysInMonth  = new Date(year, month + 1, 0).getDate();
  const firstWeekday = new Date(year, month, 1).getDay();

  const byDate = useMemo(() => {
    const map: Record<string, Reminder[]> = {};
    reminders.forEach(r => { const k = r.remindAt.slice(0, 10); (map[k] ??= []).push(r); });
    return map;
  }, [reminders]);

  const cells: (number | null)[] = [
    ...Array<null>(firstWeekday).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  return (
    <Card className="border-slate-100 shadow-sm bg-white">
      <CardContent className="p-4">
        {/* Month navigation */}
        <div className="flex items-center justify-between mb-3">
          <button onClick={prevMonth} className="p-1 rounded hover:bg-slate-100 text-slate-500 transition">
            <ChevronLeft className="size-4" />
          </button>
          <span className="text-sm font-bold text-slate-700">{MONTHS[month]} {year}</span>
          <button onClick={nextMonth} className="p-1 rounded hover:bg-slate-100 text-slate-500 transition">
            <ChevronRight className="size-4" />
          </button>
        </div>

        {/* Weekday headers */}
        <div className="grid grid-cols-7 mb-1">
          {WEEKDAYS.map(d => (
            <div key={d} className="text-center text-[9px] font-bold text-slate-400 py-1">{d}</div>
          ))}
        </div>

        {/* Day grid */}
        <div className="grid grid-cols-7 gap-0.5">
          {cells.map((day, idx) => {
            if (!day) return <div key={`e-${idx}`} />;
            const dateStr   = `${year}-${PAD(month + 1)}-${PAD(day)}`;
            const items     = byDate[dateStr] ?? [];
            const hasOver   = items.some(r => isOverdue(r) || r.status === "OVERDUE");
            const hasPend   = items.some(r => r.status === "PENDING" && !isOverdue(r));
            const hasDone   = items.some(r => r.status === "DONE");
            const isSelected = selectedDay === dateStr;
            const isToday    = dateStr === TODAY_STR;

            return (
              <button
                key={dateStr}
                onClick={() => onDayClick(isSelected ? "" : dateStr)}
                className={`rounded-lg p-1.5 flex flex-col items-center gap-0.5 min-h-[44px] transition ${
                  isSelected ? "bg-blue-100 ring-1 ring-blue-300" :
                  isToday    ? "bg-slate-100" : "hover:bg-slate-50"
                }`}
              >
                <span className={`text-[10px] font-bold leading-none ${isToday ? "text-blue-600" : "text-slate-700"}`}>
                  {day}
                </span>
                {items.length > 0 && (
                  <>
                    <div className="flex gap-0.5">
                      {hasOver && <span className="size-1.5 rounded-full bg-red-500" />}
                      {hasPend && <span className="size-1.5 rounded-full bg-amber-400" />}
                      {hasDone && <span className="size-1.5 rounded-full bg-emerald-400" />}
                    </div>
                    <span className="text-[8px] text-slate-400 leading-none">{items.length}</span>
                  </>
                )}
              </button>
            );
          })}
        </div>

        {/* Legend */}
        <div className="flex items-center gap-3 mt-3 pt-2 border-t border-slate-100">
          {[
            { color: "bg-red-500",    label: "Overdue" },
            { color: "bg-amber-400",  label: "Pending" },
            { color: "bg-emerald-400",label: "Done" },
          ].map(({ color, label }) => (
            <span key={label} className="flex items-center gap-1 text-[9px] text-slate-400">
              <span className={`size-1.5 rounded-full ${color}`} /> {label}
            </span>
          ))}
          {selectedDay && (
            <button
              onClick={() => onDayClick("")}
              className="ml-auto text-[9px] text-blue-500 hover:underline"
            >
              Clear day filter
            </button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export function ReminderListScreen() {
  const { highlightedId, setRowRef } = useHighlightRow();
  const { user, isLoading: isAuthLoading } = useAuthStore();
  const isManager = user?.roles?.includes("MANAGER") ?? false;

  // Manager can filter by specific user; staff always sees own reminders
  const [filterUserId, setFilterUserId] = useState<string>("");
  const queryUserId = isManager ? (filterUserId || undefined) : user?.id;
  const fetchAll    = isManager && !filterUserId;

  // Search — keyword match on title / description, applied client-side (see `displayed`)
  // so it doesn't shrink `allReminders`, which the stats below depend on staying unfiltered.
  const [searchQuery, setSearchQuery] = useState("");

  // Only enable query once auth is resolved (user object is available)
  const authReady = !isAuthLoading && !!user;
  const { data: allReminders = [], isLoading } = useReminders(
    authReady ? queryUserId : undefined,
    undefined,
    authReady ? fetchAll : false,
  );
  const { data: usersRes } = useUsers();
  const teamUsers = usersRes?.data ?? [];

  // Tabs — Active (still needs attention) vs Completed (past, no action needed)
  const [listTab, setListTab] = useState<"active" | "done">("active");

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [dateFilter, setDateFilter]     = useState<string>("");
  const [entityFilter, setEntityFilter] = useState<string>("");

  // Column sort — Due / Priority, toggled by clicking the table header.
  const [sortField, setSortField] = useState<"due" | "priority">("due");
  const [sortDir, setSortDir]     = useState<"asc" | "desc">("asc");

  const handleSort = (field: "due" | "priority") => {
    if (sortField === field) {
      setSortDir(prev => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  };

  // View & calendar
  const [viewMode, setViewMode]       = useState<"list" | "calendar">("list");
  const [calendarDay, setCalendarDay] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [updateTarget, setUpdateTarget] = useState<Reminder | null>(null);
  // Row click opens the shared detail surface; Edit from there opens the modal
  // below, so the existing update flow is preserved rather than replaced.
  const [detailReminder, setDetailReminder] = useState<Reminder | null>(null);

  const isDoneReminder = (r: Reminder) => DONE_REMINDER_STATUSES.includes(r.status);

  /**
   * Column set — Blueprint §10.16.
   *
   * The leading flag column is intentionally header-less: it carries a single
   * overdue warning glyph, and a header over one icon adds noise without
   * telling the reader anything the icon does not.
   */
  const reminderColumns: ColumnDef<Reminder>[] = useMemo(() => [
    {
      id: "flag",
      header: "",
      width: "w-8",
      cell: (r) =>
        isOverdue(r) || r.status === "OVERDUE" ? (
          <AlertTriangle className="size-3.5 text-danger" aria-label="Overdue" />
        ) : null,
    },
    {
      id: "title",
      header: "Title",
      sticky: "left",
      cell: (r) => (
        <>
          <div className="max-w-[220px] truncate text-xs font-bold text-foreground">{r.title}</div>
          {r.description && (
            <div className="mt-0.5 max-w-[220px] truncate text-[10px] text-muted-foreground">
              {r.description}
            </div>
          )}
        </>
      ),
    },
    {
      id: "due",
      header: "Due",
      sortable: true,
      cell: (r) => {
        const overdue = isOverdue(r) || r.status === "OVERDUE";
        return (
          <span className={`flex items-center gap-1 whitespace-nowrap text-xs font-semibold ${overdue ? "text-danger" : "text-muted-foreground"}`}>
            <Clock className="size-3 shrink-0" />
            {formatRemindAt(r.remindAt)}
          </span>
        );
      },
    },
    {
      id: "priority",
      header: "Priority",
      sortable: true,
      cell: (r) => (
        <Badge variant={PRIORITY_VARIANT[r.priority] ?? "default"} size="sm" className="text-[9px] font-bold uppercase">
          {r.priority}
        </Badge>
      ),
    },
    {
      id: "status",
      header: "Status",
      cell: (r) => {
        const overdue = isOverdue(r) || r.status === "OVERDUE";
        return (
          <Badge
            variant={overdue ? "danger" : STATUS_VARIANT[r.status] ?? "default"}
            size="sm"
            className="text-[9px] font-bold uppercase"
          >
            {overdue && r.status !== "OVERDUE" ? "OVERDUE" : r.status}
          </Badge>
        );
      },
    },
    {
      id: "linkedTo",
      header: "Linked To",
      minWidth: "md",
      cell: (r) => (
        <span className="flex items-center gap-1 whitespace-nowrap text-[10px] font-semibold text-muted-foreground">
          {ENTITY_ICON[r.relatedEntity] ?? <Bell className="size-3" />}
          {r.relatedEntity}
        </span>
      ),
    },
    {
      id: "assigned",
      header: "Assigned",
      minWidth: "lg",
      cell: (r) => (
        <>
          <OwnerCell name={r.assignedUserName} />
          {r.createdByName && r.createdByName !== r.assignedUserName && (
            <div className="mt-0.5 text-[9px] text-muted-foreground">by {r.createdByName}</div>
          )}
        </>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const controls = useTableControls<Reminder>("reminders", reminderColumns, {
    defaultSortBy: "due",
  });

  // Stats (from full unfiltered data)
  const pendingCount = allReminders.filter(r => r.status === "PENDING" && !isOverdue(r)).length;
  const overdueCount = allReminders.filter(r => isOverdue(r) || r.status === "OVERDUE").length;
  const doneCount    = allReminders.filter(r => isDoneReminder(r)).length;
  const activeCount  = allReminders.filter(r => !isDoneReminder(r)).length;

  const handleTabChange = (tab: "active" | "done") => {
    setListTab(tab);
    setStatusFilter("");
  };

  const goToStatus = (status: string, tab: "active" | "done") => {
    setListTab(tab);
    setStatusFilter(status);
  };

  // Apply tab + client-side filters + sort
  const displayed = useMemo(() => {
    let list = allReminders.filter(r => (listTab === "done" ? isDoneReminder(r) : !isDoneReminder(r)));
    list = list.filter(r => {
      if (statusFilter === "OVERDUE") return isOverdue(r) || r.status === "OVERDUE";
      if (statusFilter)               return r.status === statusFilter;
      return true;
    });
    if (entityFilter) list = list.filter(r => r.relatedEntity === entityFilter);
    list = list.filter(r => matchesDateFilter(r, dateFilter, calendarDay));
    if (searchQuery.trim()) {
      const keyword = searchQuery.trim().toLowerCase();
      list = list.filter(r =>
        r.title.toLowerCase().includes(keyword) ||
        (r.description ?? "").toLowerCase().includes(keyword));
    }
    return applySort(list, sortField, sortDir);
  }, [allReminders, listTab, statusFilter, entityFilter, dateFilter, calendarDay, searchQuery, sortField, sortDir]);

  const hasFilters = !!(statusFilter || dateFilter || entityFilter || calendarDay || filterUserId || searchQuery);

  const clearFilters = () => {
    setStatusFilter(""); setDateFilter(""); setEntityFilter("");
    setCalendarDay(null); setFilterUserId(""); setSearchQuery("");
  };

  const handleCalendarDayClick = (dateStr: string) => {
    setCalendarDay(dateStr || null);
    setDateFilter("");
    if (dateStr) setViewMode("list");
  };

  return (
    <div className="space-y-6">

      <PageHeader
        {...PAGE_META.reminders}
        subtitle={
          isManager
            ? "Time-based nudges across the whole team, so no follow-up slips past its due date."
            : PAGE_META.reminders.subtitle
        }
        actions={
          <div className="flex items-center gap-2 shrink-0">
          {/* List / Calendar toggle */}
          <div className="flex rounded-lg border border-slate-200 overflow-hidden">
            <button
              onClick={() => setViewMode("list")}
              className={`flex items-center gap-1 px-2.5 py-1.5 text-xs transition ${
                viewMode === "list"
                  ? "bg-primary text-white"
                  : "bg-white text-slate-500 hover:bg-slate-50"
              }`}
            >
              <LayoutList className="size-3" /> List
            </button>
            <button
              onClick={() => setViewMode("calendar")}
              className={`flex items-center gap-1 px-2.5 py-1.5 text-xs transition ${
                viewMode === "calendar"
                  ? "bg-primary text-white"
                  : "bg-white text-slate-500 hover:bg-slate-50"
              }`}
            >
              <Calendar className="size-3" /> Calendar
            </button>
          </div>

          <Button
            variant="primary"
            size="sm"
            onClick={() => setShowCreate(true)}
            leftIcon={<Plus className="size-3.5" />}
            className="text-xs font-bold"
          >
            New Reminder
          </Button>
          </div>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Pending",  count: pendingCount, color: "text-amber-600",   bg: "bg-amber-50 border-amber-100",     onClick: () => goToStatus("PENDING", "active") },
          { label: "Overdue",  count: overdueCount, color: "text-red-600",     bg: "bg-red-50 border-red-100",         onClick: () => goToStatus("OVERDUE", "active") },
          { label: "Done",     count: doneCount,    color: "text-emerald-600", bg: "bg-emerald-50 border-emerald-100", onClick: () => goToStatus("DONE", "done") },
        ].map(({ label, count, color, bg, onClick }) => (
          <button
            key={label}
            onClick={onClick}
            className={`rounded-xl border px-4 py-3 text-left transition hover:shadow-sm ${bg}`}
          >
            <p className={`text-xl font-bold ${color}`}>{count}</p>
            <p className="text-[10px] font-semibold text-slate-500 mt-0.5">{label}</p>
          </button>
        ))}
      </div>

      {/* Active / Completed tabs */}
      <div className="flex items-center gap-1 border-b border-slate-200">
        <button
          type="button"
          onClick={() => handleTabChange("active")}
          className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold border-b-2 transition -mb-px ${
            listTab === "active"
              ? "border-primary text-blue-700"
              : "border-transparent text-slate-500 hover:text-slate-700"
          }`}
        >
          Active
          <span className={`px-1.5 py-0.5 rounded-full text-[9px] font-bold ${
            listTab === "active" ? "bg-blue-100 text-blue-600" : "bg-slate-100 text-slate-500"
          }`}>
            {activeCount}
          </span>
        </button>
        <button
          type="button"
          onClick={() => handleTabChange("done")}
          className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold border-b-2 transition -mb-px ${
            listTab === "done"
              ? "border-slate-600 text-slate-700"
              : "border-transparent text-slate-500 hover:text-slate-700"
          }`}
        >
          Completed
          <span className={`px-1.5 py-0.5 rounded-full text-[9px] font-bold ${
            listTab === "done" ? "bg-slate-200 text-slate-600" : "bg-slate-100 text-slate-400"
          }`}>
            {doneCount}
          </span>
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search title or description..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white pl-8 pr-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition w-56"
          />
        </div>

        <Filter className="size-3.5 text-slate-400 shrink-0" />

        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
          <option value="">All statuses</option>
          {listTab === "active" ? (
            <>
              <option value="PENDING">Pending</option>
              <option value="OVERDUE">Overdue</option>
            </>
          ) : (
            <>
              <option value="DONE">Done</option>
              <option value="CANCELLED">Cancelled</option>
            </>
          )}
        </select>

        <select value={dateFilter} onChange={e => { setDateFilter(e.target.value); setCalendarDay(null); }}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
          <option value="">All time</option>
          <option value="today">Today</option>
          <option value="week">This week</option>
          <option value="month">This month</option>
        </select>

        <select value={entityFilter} onChange={e => setEntityFilter(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
          <option value="">All entities</option>
          <option value="QUOTATION">Quotation</option>
          <option value="LEAD">Lead</option>
          <option value="BOOKING">Booking</option>
          <option value="DEPOSIT">Deposit</option>
        </select>

        {/* Manager-only: filter by team member */}
        {isManager && (
          <select value={filterUserId} onChange={e => setFilterUserId(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
            <option value="">All staff</option>
            {teamUsers.map(u => (
              <option key={u.userId} value={u.userId}>{u.fullName}</option>
            ))}
          </select>
        )}

        {calendarDay && (
          <span className="flex items-center gap-1 rounded-full bg-blue-50 border border-blue-200 px-2 py-0.5 text-[10px] text-blue-600 font-semibold">
            <Calendar className="size-3" /> {calendarDay}
            <button onClick={() => setCalendarDay(null)} className="ml-0.5 hover:text-blue-800">×</button>
          </span>
        )}

        {hasFilters && (
          <button onClick={clearFilters} className="text-[10px] text-slate-400 hover:text-slate-600 underline">
            Clear all
          </button>
        )}

        <span className="ml-auto text-[10px] text-slate-400 font-semibold">
          {displayed.length} reminder{displayed.length !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Calendar view */}
      {viewMode === "calendar" && (
        <CalendarView
          reminders={allReminders}
          selectedDay={calendarDay}
          onDayClick={handleCalendarDayClick}
        />
      )}

      {/* List view */}
      {viewMode === "list" && (
        <DataTable
          label="Reminders"
          rows={displayed}
          columns={controls.visibleColumns}
          rowId={(r) => r.reminderId}
          isLoading={isLoading || isAuthLoading}
          density={controls.density}
          sortBy={sortField ?? undefined}
          sortDir={sortDir}
          onSortChange={(columnId, dir) => {
            // Only Due and Priority have comparators.
            if (columnId === "due" || columnId === "priority") {
              setSortField(columnId);
              setSortDir(dir);
            }
          }}
          highlightId={highlightedId}
          rowRef={setRowRef}
          onRowClick={(r) => setDetailReminder(r)}
          selectedIds={controls.selectedIds}
          onSelectionChange={controls.setSelectedIds}
          bulkActions={
            <ExportMenu
              filename={`reminders-selected-${new Date().toISOString().slice(0, 10)}`}
              headers={REMINDER_EXPORT_HEADERS}
              rows={displayed.filter((r) => controls.selectedIds.has(r.reminderId)).map(reminderExportRow)}
            />
          }
          isFiltered={hasFilters}
          onClearFilters={clearFilters}
          emptyTitle="No reminders found"
          emptyMessage="Reminders you set on a lead, deal or booking show up here."
          emptyAction={{ label: "New Reminder", onClick: () => setShowCreate(true) }}
        />
      )}

      {showCreate && <CreateReminderModal onClose={() => setShowCreate(false)} />}
      {updateTarget && <UpdateReminderModal reminder={updateTarget} onClose={() => setUpdateTarget(null)} />}

      {/* Shared detail surface — same behaviour as every other module. */}
      <ReminderDetailDrawer
        reminder={detailReminder}
        onOpenChange={(open) => !open && setDetailReminder(null)}
        actions={
          detailReminder && !["DONE", "CANCELLED"].includes((detailReminder.status ?? "").toUpperCase())
            ? [
                {
                  label: "Edit",
                  icon: Pencil,
                  onClick: () => {
                    const target = detailReminder;
                    setDetailReminder(null);
                    setUpdateTarget(target);
                  },
                },
              ]
            : []
        }
      />
    </div>
  );
}
