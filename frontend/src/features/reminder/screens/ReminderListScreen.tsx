"use client";

import React, { useState, useMemo } from "react";
import {
  Bell, Clock, AlertTriangle, Plus, Filter,
  FileSpreadsheet, Calendar, LayoutList, ChevronLeft, ChevronRight,
  Users, Building2, CreditCard,
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { CreateReminderModal } from "@/features/reminder/components/CreateReminderModal";
import { UpdateReminderModal } from "@/features/reminder/components/UpdateReminderModal";
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

const ENTITY_ICON: Record<string, React.ReactNode> = {
  QUOTATION: <FileSpreadsheet className="size-3 text-blue-400" />,
  LEAD:      <Users className="size-3 text-emerald-400" />,
  BOOKING:   <Building2 className="size-3 text-violet-400" />,
  DEPOSIT:   <CreditCard className="size-3 text-amber-400" />,
};

const PRIORITY_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
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

function applySort(list: Reminder[], sortBy: string): Reminder[] {
  return [...list].sort((a, b) => {
    if (sortBy === "date-desc") return new Date(b.remindAt).getTime() - new Date(a.remindAt).getTime();
    if (sortBy === "priority")  return (PRIORITY_ORDER[a.priority] ?? 1) - (PRIORITY_ORDER[b.priority] ?? 1);
    return new Date(a.remindAt).getTime() - new Date(b.remindAt).getTime();
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

  // Only enable query once auth is resolved (user object is available)
  const authReady = !isAuthLoading && !!user;
  const { data: allReminders = [], isLoading } = useReminders(
    authReady ? queryUserId : undefined,
    undefined,
    authReady ? fetchAll : false,
  );
  const { data: usersRes } = useUsers();
  const teamUsers = usersRes?.data ?? [];

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [dateFilter, setDateFilter]     = useState<string>("");
  const [entityFilter, setEntityFilter] = useState<string>("");
  const [sortBy, setSortBy]             = useState<string>("date-asc");

  // View & calendar
  const [viewMode, setViewMode]       = useState<"list" | "calendar">("list");
  const [calendarDay, setCalendarDay] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [updateTarget, setUpdateTarget] = useState<Reminder | null>(null);

  // Stats (from full unfiltered data)
  const pendingCount = allReminders.filter(r => r.status === "PENDING" && !isOverdue(r)).length;
  const overdueCount = allReminders.filter(r => isOverdue(r) || r.status === "OVERDUE").length;
  const doneCount    = allReminders.filter(r => r.status === "DONE").length;

  // Apply client-side filters + sort
  const displayed = useMemo(() => {
    let list = allReminders.filter(r => {
      if (statusFilter === "OVERDUE") return isOverdue(r) || r.status === "OVERDUE";
      if (statusFilter)               return r.status === statusFilter;
      return true;
    });
    if (entityFilter) list = list.filter(r => r.relatedEntity === entityFilter);
    list = list.filter(r => matchesDateFilter(r, dateFilter, calendarDay));
    return applySort(list, sortBy);
  }, [allReminders, statusFilter, entityFilter, dateFilter, calendarDay, sortBy]);

  const hasFilters = !!(statusFilter || dateFilter || entityFilter || calendarDay || filterUserId);

  const clearFilters = () => {
    setStatusFilter(""); setDateFilter(""); setEntityFilter("");
    setCalendarDay(null); setFilterUserId("");
  };

  const handleCalendarDayClick = (dateStr: string) => {
    setCalendarDay(dateStr || null);
    setDateFilter("");
    if (dateStr) setViewMode("list");
  };

  return (
    <div className="space-y-6">

      {/* Header */}
      <div className="flex justify-between items-start gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Reminders</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            {isManager ? "All team follow-up reminders" : "Your follow-up reminders"}
          </p>
        </div>
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
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Pending",  count: pendingCount, color: "text-amber-600",   bg: "bg-amber-50 border-amber-100",     onClick: () => setStatusFilter("PENDING") },
          { label: "Overdue",  count: overdueCount, color: "text-red-600",     bg: "bg-red-50 border-red-100",         onClick: () => setStatusFilter("OVERDUE") },
          { label: "Done",     count: doneCount,    color: "text-emerald-600", bg: "bg-emerald-50 border-emerald-100", onClick: () => setStatusFilter("DONE") },
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

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2">
        <Filter className="size-3.5 text-slate-400 shrink-0" />

        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="OVERDUE">Overdue</option>
          <option value="DONE">Done</option>
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

        <select value={sortBy} onChange={e => setSortBy(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-blue-400 transition">
          <option value="date-asc">Due: Soonest first</option>
          <option value="date-desc">Due: Latest first</option>
          <option value="priority">Priority: High first</option>
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
        <Card className="border-slate-100 shadow-sm bg-white overflow-hidden">
          <CardContent className="p-0">
            <Table>
              <TableHeader className="bg-slate-50 border-b border-slate-100">
                <TableRow hoverable={false}>
                  <TableHead className="font-semibold text-xs text-slate-500 w-6" />
                  <TableHead className="font-semibold text-xs text-slate-500">Title</TableHead>
                  <TableHead className="font-semibold text-xs text-slate-500">Due</TableHead>
                  <TableHead className="font-semibold text-xs text-slate-500">Priority</TableHead>
                  <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
                  <TableHead className="font-semibold text-xs text-slate-500">Linked To</TableHead>
                  <TableHead className="font-semibold text-xs text-slate-500">Assigned</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading || isAuthLoading ? (
                  <TableRow hoverable={false}>
                    <TableCell colSpan={7} className="py-10 text-center text-xs text-slate-400">
                      Loading reminders…
                    </TableCell>
                  </TableRow>
                ) : displayed.length === 0 ? (
                  <TableRow hoverable={false}>
                    <TableCell colSpan={7} className="py-14 text-center">
                      <div className="flex flex-col items-center gap-2">
                        <Bell className="size-9 text-slate-200" />
                        <p className="text-sm font-bold text-slate-500">No reminders found</p>
                        <p className="text-[10px] text-slate-400">
                          {hasFilters
                            ? <button onClick={clearFilters} className="text-blue-500 hover:underline">Clear filters to see all reminders</button>
                            : <button onClick={() => setShowCreate(true)} className="text-blue-500 hover:underline">Create your first reminder</button>
                          }
                        </p>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  displayed.map(r => {
                    const overdue = isOverdue(r) || r.status === "OVERDUE";
                    return (
                      <TableRow
                        key={r.reminderId}
                        ref={setRowRef(r.reminderId)}
                        onClick={() => setUpdateTarget(r)}
                        className={`border-b border-slate-100 transition cursor-pointer ${
                          highlightedId === r.reminderId ? "bg-amber-50 ring-2 ring-inset ring-amber-400" :
                          overdue          ? "bg-red-50/30 hover:bg-red-50/50" :
                          r.status === "DONE" ? "opacity-55 bg-slate-50/40" :
                          "hover:bg-slate-50/60"
                        }`}
                      >
                        <TableCell className="py-3 pl-4 pr-0">
                          {overdue && <AlertTriangle className="size-3.5 text-red-400" />}
                        </TableCell>

                        <TableCell className="py-3 px-4">
                          <div className="text-xs font-bold text-slate-800 max-w-[220px] truncate">{r.title}</div>
                          {r.description && (
                            <div className="text-[10px] text-slate-400 mt-0.5 max-w-[220px] truncate">{r.description}</div>
                          )}
                        </TableCell>

                        <TableCell className="py-3 px-4 whitespace-nowrap">
                          <span className={`flex items-center gap-1 text-xs font-semibold ${overdue ? "text-red-600" : "text-slate-500"}`}>
                            <Clock className="size-3 shrink-0" />
                            {formatRemindAt(r.remindAt)}
                          </span>
                        </TableCell>

                        <TableCell className="py-3 px-4">
                          <Badge variant={PRIORITY_VARIANT[r.priority] ?? "default"} size="sm" className="text-[9px] font-bold uppercase">
                            {r.priority}
                          </Badge>
                        </TableCell>

                        <TableCell className="py-3 px-4">
                          <Badge
                            variant={overdue ? "danger" : STATUS_VARIANT[r.status] ?? "default"}
                            size="sm"
                            className="text-[9px] font-bold uppercase"
                          >
                            {overdue && r.status !== "OVERDUE" ? "OVERDUE" : r.status}
                          </Badge>
                        </TableCell>

                        <TableCell className="py-3 px-4">
                          <span className="flex items-center gap-1 text-[10px] text-slate-500 font-semibold whitespace-nowrap">
                            {ENTITY_ICON[r.relatedEntity] ?? <Bell className="size-3 text-slate-400" />}
                            {r.relatedEntity}
                          </span>
                        </TableCell>

                        <TableCell className="py-3 px-4">
                          <div className="text-xs text-slate-600 font-semibold">{r.assignedUserName ?? "—"}</div>
                          {r.createdByName && r.createdByName !== r.assignedUserName && (
                            <div className="text-[9px] text-slate-400">by {r.createdByName}</div>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {showCreate && <CreateReminderModal onClose={() => setShowCreate(false)} />}
      {updateTarget && <UpdateReminderModal reminder={updateTarget} onClose={() => setUpdateTarget(null)} />}
    </div>
  );
}
