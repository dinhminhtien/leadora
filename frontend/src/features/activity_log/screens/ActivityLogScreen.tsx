"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  History, Search, Calendar, RefreshCw, X, ShieldAlert, Cpu, User, Info,
  ChevronLeft, ChevronRight, List, GitFork, ArrowRight, Eye, LayoutGrid,
  AlertTriangle, Terminal, Trash2
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { timelineEventIcon } from "@/components/ui/timeline";
import { timelineEventKind } from "@/shared/design/timeline-events";
import { Badge } from "@/components/ui/Badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { type ActorType, type RecordOperation } from "@/services/activity_log_service";
import { type SystemLogEntry } from "@/services/system_log_service";
import { useActivityLogs, useActivityLogDetail, useSystemLogs, useClearSystemLogs } from "@/features/activity_log/hooks/use_activity_logs";
import { toast } from "@/stores/toast_store";

const PAGE_SIZE = 15;

const OPERATION_CONFIG: Record<RecordOperation, { label: string; variant: "success" | "warning" | "danger" }> = {
  NORMAL: { label: "Normal", variant: "success" },
  CORRECTED: { label: "Corrected", variant: "warning" },
  VOIDED: { label: "Voided", variant: "danger" },
};

function formatDateTime(isoString: string): string {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleString("en-US", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function getActionCategory(activityType: string) {
  const type = (activityType || "").toUpperCase();
  if (type === "USER_LOGGED_IN") {
    return { label: "Login Success", color: "bg-teal-50 text-teal-700 dark:bg-teal-950/20 dark:text-teal-400 border border-teal-200 dark:border-teal-900" };
  }
  if (type === "USER_LOGGED_OUT") {
    return { label: "Logout", color: "bg-zinc-100 text-zinc-650 dark:bg-zinc-900 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-800" };
  }
  if (type === "PASSWORD_CHANGED" || type === "PASSWORD_RESET_COMPLETED") {
    return { label: "Password Change", color: "bg-amber-50 text-amber-800 dark:bg-amber-950/20 dark:text-amber-400 border border-amber-300 dark:border-amber-900" };
  }
  if (type === "PASSWORD_RESET_REQUESTED") {
    return { label: "Reset Request", color: "bg-orange-50 text-orange-700 dark:bg-orange-950/20 dark:text-orange-400 border border-orange-200 dark:border-orange-900" };
  }
  if (type === "USER_ACCOUNT_CREATED") {
    return { label: "Account Created", color: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/20 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900" };
  }
  if (type === "USER_ACCOUNT_UPDATED") {
    return { label: "Account Updated", color: "bg-blue-50 text-blue-750 dark:bg-blue-950/20 dark:text-blue-400 border border-blue-200 dark:border-blue-900" };
  }
  if (type === "LOGIN_FAILED") {
    return { label: "Login Failed", color: "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400 border border-red-200 dark:border-red-900 font-medium" };
  }
  if (type === "ACCESS_DENIED_EVENT") {
    return { label: "Access Denied", color: "bg-rose-100 text-rose-800 dark:bg-rose-950/40 dark:text-rose-300 border border-rose-300 dark:border-rose-800 font-semibold" };
  }
  if (type === "INVALID_TOKEN_ACCESS") {
    return { label: "Invalid Token", color: "bg-red-100 text-red-800 dark:bg-red-950/40 dark:text-red-300 border border-red-300 dark:border-red-800 font-semibold" };
  }
  if (type === "FEEDBACK_LINK_EXPIRED") {
    return { label: "Feedback Expired", color: "bg-amber-100 text-amber-850 dark:bg-amber-950/30 dark:text-amber-300 border border-amber-300 dark:border-amber-800" };
  }

  if (type.includes("CREATE")) {
    return { label: "Create", color: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/20 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900" };
  }
  if (type.includes("STATUS_UPDATED") || type.includes("STAGE_UPDATED")) {
    return { label: "Status Transition", color: "bg-amber-50 text-amber-700 dark:bg-amber-950/20 dark:text-amber-455 border border-amber-200 dark:border-amber-900" };
  }
  if (type.includes("UPDATE")) {
    return { label: "Update", color: "bg-blue-50 text-blue-700 dark:bg-blue-950/20 dark:text-blue-400 border border-blue-200 dark:border-blue-900" };
  }
  if (type.includes("CONVERT")) {
    return { label: "Conversion", color: "bg-indigo-50 text-indigo-700 dark:bg-indigo-950/20 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-900" };
  }
  if (type.includes("SUBMIT")) {
    return { label: "Submission", color: "bg-violet-50 text-violet-700 dark:bg-violet-950/20 dark:text-violet-400 border border-violet-250 dark:border-violet-900" };
  }
  if (type.includes("APPROVE")) {
    return { label: "Approval", color: "bg-teal-50 text-teal-700 dark:bg-teal-950/20 dark:text-teal-400 border border-teal-250 dark:border-teal-900" };
  }
  if (type.includes("REJECT")) {
    return { label: "Rejection", color: "bg-rose-50 text-rose-700 dark:bg-rose-950/20 dark:text-rose-455 border border-rose-250 dark:border-rose-900" };
  }
  if (type.includes("CANCEL")) {
    return { label: "Cancellation", color: "bg-red-50 text-red-700 dark:bg-red-950/20 dark:text-red-400 border border-red-250 dark:border-red-900" };
  }
  if (type.includes("COMPLETE")) {
    return { label: "Completion", color: "bg-purple-50 text-purple-700 dark:bg-purple-950/20 dark:text-purple-400 border border-purple-250 dark:border-purple-900" };
  }
  return { label: "Other", color: "bg-zinc-50 text-zinc-700 dark:bg-zinc-950/20 dark:text-zinc-400 border border-zinc-200 dark:border-zinc-800" };
}

function getEntityStyle(entityType: string) {
  const type = (entityType || "").toUpperCase();
  switch (type) {
    case "USER":
      return {
        color: "bg-rose-50/30 text-rose-700 dark:bg-rose-950/10 dark:text-rose-400 border-rose-200 dark:border-rose-900",
        badge: "bg-rose-100/70 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300 border border-rose-200/50 dark:border-rose-800/40",
        iconBg: "bg-rose-500 text-white"
      };
    case "LEAD":
      return {
        color: "bg-emerald-50/50 text-emerald-700 dark:bg-emerald-950/10 dark:text-emerald-400 border-emerald-200 dark:border-emerald-900",
        badge: "bg-emerald-100/70 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300 border border-emerald-200/50 dark:border-emerald-800/40",
        iconBg: "bg-emerald-500 text-white"
      };
    case "DEAL":
      return {
        color: "bg-indigo-50/50 text-indigo-700 dark:bg-indigo-950/10 dark:text-indigo-400 border-indigo-200 dark:border-indigo-900",
        badge: "bg-indigo-100/70 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-300 border border-indigo-200/50 dark:border-indigo-800/40",
        iconBg: "bg-indigo-500 text-white"
      };
    case "QUOTATION":
      return {
        color: "bg-violet-50/50 text-violet-700 dark:bg-violet-950/10 dark:text-violet-400 border-violet-200 dark:border-violet-900",
        badge: "bg-violet-100/70 text-violet-800 dark:bg-violet-900/40 dark:text-violet-300 border border-violet-200/50 dark:border-violet-800/40",
        iconBg: "bg-violet-500 text-white"
      };
    case "BOOKING":
      return {
        color: "bg-teal-50/50 text-teal-700 dark:bg-teal-950/10 dark:text-teal-400 border-teal-200 dark:border-teal-900",
        badge: "bg-teal-100/70 text-teal-800 dark:bg-teal-900/40 dark:text-teal-300 border border-teal-200/50 dark:border-teal-800/40",
        iconBg: "bg-teal-500 text-white"
      };
    case "TASK":
      return {
        color: "bg-amber-50/50 text-amber-700 dark:bg-amber-950/10 dark:text-amber-400 border-amber-200 dark:border-amber-900",
        badge: "bg-amber-100/70 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 border border-amber-200/50 dark:border-amber-800/40",
        iconBg: "bg-amber-500 text-white"
      };
    case "PAYMENT":
      return {
        color: "bg-rose-50/50 text-rose-700 dark:bg-rose-950/10 dark:text-rose-450 border-rose-200 dark:border-rose-900",
        badge: "bg-rose-100/70 text-rose-800 dark:bg-rose-900/40 dark:text-rose-350 border border-rose-200/50 dark:border-rose-800/40",
        iconBg: "bg-rose-500 text-white"
      };
    default:
      return {
        color: "bg-zinc-50 text-zinc-700 dark:bg-zinc-950/20 dark:text-zinc-400 border-zinc-200 dark:border-zinc-800",
        badge: "bg-zinc-100 text-zinc-800 dark:bg-zinc-900/40 dark:text-zinc-300 border border-zinc-200/50 dark:border-zinc-800/40",
        iconBg: "bg-zinc-500 text-white"
      };
  }
}

function renderPayloadSummary(payload: Record<string, unknown> | null | undefined) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const keys = Object.keys(payload);
  if (keys.length === 0) return null;
  return (
    <div className="border border-zinc-150 dark:border-zinc-850 rounded-xl overflow-hidden text-[11px] bg-white dark:bg-zinc-950">
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="bg-zinc-50 dark:bg-zinc-900/60 border-b border-zinc-150 dark:border-zinc-850 text-zinc-500 dark:text-zinc-400">
            <th className="py-2 px-3 font-semibold uppercase tracking-wider text-[10px]">Property</th>
            <th className="py-2 px-3 font-semibold uppercase tracking-wider text-[10px]">Value</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
          {keys.map((key) => {
            const val = payload[key];
            let valStr = "";
            if (val === null || val === undefined) {
              valStr = "—";
            } else if (typeof val === "object") {
              valStr = JSON.stringify(val);
            } else {
              valStr = String(val);
            }
            return (
              <tr key={key} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-900/20 transition">
                <td className="py-2 px-3 font-semibold text-zinc-650 dark:text-zinc-400 font-mono">{key}</td>
                <td className="py-2 px-3 text-zinc-800 dark:text-zinc-200 font-mono break-all leading-relaxed">{valStr}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function ActivityLogScreen() {
  const [categoryTab, setCategoryTab] = useState<"BUSINESS" | "SECURITY" | "SYSTEM">("BUSINESS");
  const [viewMode, setViewMode] = useState<"table" | "timeline">("table");
  const [dataView, setDataView] = useState<"RAW" | "EFFECTIVE">("EFFECTIVE");
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");

  // Advanced filters
  const [actorType, setActorType] = useState<ActorType | "">("");
  const [activityType, setActivityType] = useState("");
  const [entityType, setEntityType] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [page, setPage] = useState(0);

  // Activity Log query
  const activityLogParams = useMemo(() => {
    return {
      keyword: keyword || undefined,
      actorType: actorType || undefined,
      activityType: activityType || undefined,
      entityType: entityType || undefined,
      startDate: startDate ? new Date(startDate).toISOString() : undefined,
      endDate: endDate ? new Date(endDate).toISOString() : undefined,
      view: dataView,
      category: categoryTab === "SYSTEM" ? undefined : categoryTab,
      page,
      size: PAGE_SIZE,
    };
  }, [keyword, actorType, activityType, entityType, startDate, endDate, dataView, categoryTab, page]);

  const { data: activityResponse, isLoading: isLoadingLogs } = useActivityLogs(
    categoryTab !== "SYSTEM" ? activityLogParams : undefined
  );
  const logs = activityResponse?.data?.content ?? [];
  const totalPages =
    activityResponse?.data?.totalPages ??
    (activityResponse?.data?.page && typeof activityResponse?.data?.page === "object"
      ? activityResponse.data.page.totalPages
      : 1);
  const totalElements =
    activityResponse?.data?.totalElements ??
    (activityResponse?.data?.page && typeof activityResponse?.data?.page === "object"
      ? activityResponse.data.page.totalElements
      : 0);

  // System Logs States
  const [selectedSystemLogLevel, setSelectedSystemLogLevel] = useState<string>("ALL");
  const [selectedSystemLog, setSelectedSystemLog] = useState<SystemLogEntry | null>(null);

  const systemLogParams = useMemo(() => ({
    level: selectedSystemLogLevel === "ALL" ? undefined : selectedSystemLogLevel,
    keyword: keyword || undefined,
  }), [selectedSystemLogLevel, keyword]);

  const { data: systemResponse, isLoading: isLoadingSystem } = useSystemLogs(
    systemLogParams,
    categoryTab === "SYSTEM"
  );
  const systemLogs = useMemo(() => systemResponse?.data ?? [], [systemResponse?.data]);
  const clearSystemLogsMutation = useClearSystemLogs();

  const isLoading = categoryTab === "SYSTEM" ? isLoadingSystem : isLoadingLogs;

  // Selected Activity Log for detailed Drawer
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { data: detailResponse, isLoading: isLoadingDetail } = useActivityLogDetail(selectedId || undefined);
  const selectedLog = detailResponse?.data ?? null;

  // System Logs client-side pagination
  const systemLogsPageSize = 25;
  const paginatedSystemLogs = useMemo(() => {
    const start = page * systemLogsPageSize;
    return systemLogs.slice(start, start + systemLogsPageSize);
  }, [systemLogs, page]);

  const systemLogsTotalPages = useMemo(() => {
    return Math.ceil(systemLogs.length / systemLogsPageSize) || 1;
  }, [systemLogs]);

  // Auto-bounce search input keyword
  useEffect(() => {
    const t = setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(t);
  }, [searchInput]);

  const handleClearFilters = () => {
    setSearchInput("");
    setKeyword("");
    setActorType("");
    setActivityType("");
    setEntityType("");
    setStartDate("");
    setEndDate("");
    setSelectedSystemLogLevel("ALL");
    setPage(0);
  };

  const handleViewRelation = (refId: string) => {
    setSelectedId(refId);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        {...PAGE_META.activityLogs}
        actions={
          /* View mode toggle & Raw/Effective filter */
          <div className="flex items-center gap-3">
          {/* RAW / EFFECTIVE Switcher */}
          <div className="flex items-center p-0.5 rounded-lg bg-zinc-100 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs">
            <button
              onClick={() => { setDataView("EFFECTIVE"); setPage(0); }}
              className={`px-3 py-1.5 rounded-md font-semibold transition ${dataView === "EFFECTIVE"
                  ? "bg-white dark:bg-zinc-800 text-primary shadow-xs"
                  : "text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300"
                }`}
              title="Filter out voided and corrected activities to show effective state only"
            >
              Effective State
            </button>
            <button
              onClick={() => { setDataView("RAW"); setPage(0); }}
              className={`px-3 py-1.5 rounded-md font-semibold transition ${dataView === "RAW"
                  ? "bg-white dark:bg-zinc-800 text-primary shadow-xs"
                  : "text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300"
                }`}
              title="Show all activity logs including voided, corrected, and correction actions"
            >
              Raw History
            </button>
          </div>

          {/* Table / Timeline toggle */}
          <div className="flex items-center p-0.5 rounded-lg bg-zinc-100 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 text-xs">
            <button
              onClick={() => setViewMode("table")}
              className={`p-1.5 rounded-md transition ${viewMode === "table"
                  ? "bg-white dark:bg-zinc-800 text-primary shadow-xs"
                  : "text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
                }`}
              title="Table View"
            >
              <List className="size-4" />
            </button>
            <button
              onClick={() => setViewMode("timeline")}
              className={`p-1.5 rounded-md transition ${viewMode === "timeline"
                  ? "bg-white dark:bg-zinc-800 text-primary shadow-xs"
                  : "text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
                }`}
              title="Timeline View"
            >
              <GitFork className="size-4" />
            </button>
          </div>
          </div>
        }
      />

      {/* Tabs */}
      <div className="flex border-b border-zinc-200 dark:border-zinc-800 gap-4">
        <button
          onClick={() => { setCategoryTab("BUSINESS"); setPage(0); handleClearFilters(); }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition ${
            categoryTab === "BUSINESS"
              ? "border-primary text-primary"
              : "border-transparent text-zinc-500 hover:text-zinc-750 dark:hover:text-zinc-350"
          }`}
        >
          <LayoutGrid className="size-4" />
          Business Audit Logs
        </button>
        <button
          onClick={() => { setCategoryTab("SECURITY"); setPage(0); handleClearFilters(); }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition ${
            categoryTab === "SECURITY"
              ? "border-rose-500 text-rose-605 dark:text-rose-400"
              : "border-transparent text-zinc-500 hover:text-zinc-750 dark:hover:text-zinc-350"
          }`}
        >
          <ShieldAlert className="size-4 text-rose-500" />
          Security Audit Logs
        </button>
        <button
          onClick={() => { setCategoryTab("SYSTEM"); setPage(0); handleClearFilters(); }}
          className={`flex items-center gap-2 px-4 py-3 text-sm font-semibold border-b-2 transition ${
            categoryTab === "SYSTEM"
              ? "border-amber-500 text-amber-605 dark:text-amber-400"
              : "border-transparent text-zinc-500 hover:text-zinc-750 dark:hover:text-zinc-350"
          }`}
        >
          <Terminal className="size-4 text-amber-500" />
          System Server Logs
        </button>
      </div>

      {/* Advanced Filters Panel */}
      <Card className="border-zinc-200 dark:border-zinc-800 shadow-sm bg-zinc-50/50 dark:bg-zinc-950/20">
        <CardContent className="p-4 flex flex-wrap gap-4 items-center">
          {categoryTab === "SYSTEM" ? (
            <>
              {/* Keyword Search */}
              <div className="relative flex-1 min-w-60">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-zinc-400 pointer-events-none" />
                <input
                  type="text"
                  placeholder="Search logger name, message content, exception stack trace..."
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 focus:border-primary focus:outline-none transition shadow-sm text-foreground placeholder:text-zinc-400 dark:placeholder:text-zinc-600"
                />
              </div>

              {/* Log Level Selector */}
              <select
                value={selectedSystemLogLevel}
                onChange={(e) => { setSelectedSystemLogLevel(e.target.value); setPage(0); }}
                className="px-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 text-zinc-755 dark:text-zinc-300 focus:outline-none focus:border-primary cursor-pointer shadow-sm font-semibold"
              >
                <option value="ALL">All Log Levels</option>
                <option value="ERROR" className="text-rose-600 font-semibold">ERROR</option>
                <option value="WARN" className="text-amber-605 font-semibold">WARN</option>
                <option value="INFO" className="text-blue-600 font-semibold">INFO</option>
                <option value="DEBUG" className="text-zinc-550 font-semibold">DEBUG</option>
              </select>

              {/* Clear Filters */}
              <button
                onClick={handleClearFilters}
                className="px-3 py-2 text-xs text-zinc-500 hover:text-zinc-850 dark:hover:text-zinc-200 border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-850 rounded-lg transition shadow-sm font-semibold flex items-center gap-1"
              >
                <X className="size-3.5" />
                Clear Filters
              </button>

              {/* Clear server logs */}
              <button
                onClick={async () => {
                  if (confirm("Are you sure you want to clear all in-memory server logs?")) {
                    try {
                      await clearSystemLogsMutation.mutateAsync();
                      toast.success("Server logs cleared successfully.");
                    } catch {
                      toast.error("Failed to clear server logs.");
                    }
                  }
                }}
                className="px-3 py-2 text-xs text-rose-600 hover:text-rose-700 border border-rose-200 dark:border-rose-950 bg-rose-50/50 dark:bg-rose-950/20 hover:bg-rose-50 dark:hover:bg-rose-950/40 rounded-lg transition shadow-sm font-semibold flex items-center gap-1"
              >
                <Trash2 className="size-3.5" />
                Clear Server Logs
              </button>
            </>
          ) : (
            <>
              {/* Keyword Search */}
              <div className="relative flex-1 min-w-60">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-zinc-400 pointer-events-none" />
                <input
                  type="text"
                  placeholder="Search keyword (reason, summary, actor, entity id)..."
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 focus:border-primary focus:outline-none transition shadow-sm text-foreground placeholder:text-zinc-400 dark:placeholder:text-zinc-600"
                />
              </div>

              {/* Actor Type */}
              <select
                value={actorType}
                onChange={(e) => { setActorType(e.target.value as ActorType | ""); setPage(0); }}
                className="px-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 focus:outline-none focus:border-primary cursor-pointer shadow-sm"
              >
                <option value="">All Actors</option>
                <option value="USER">User Account</option>
                <option value="SYSTEM">System/Automation</option>
              </select>

              {/* Activity Type */}
              <select
                value={activityType}
                onChange={(e) => { setActivityType(e.target.value); setPage(0); }}
                className="px-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 focus:outline-none focus:border-primary cursor-pointer shadow-sm"
              >
                <option value="">All Actions</option>
                {categoryTab === "BUSINESS" ? (
                  <>
                    <optgroup label="Lead Actions">
                      <option value="LEAD_CREATED">Lead Created</option>
                      <option value="LEAD_UPDATED">Lead Updated</option>
                      <option value="LEAD_STATUS_UPDATED">Lead Status Updated</option>
                      <option value="LEAD_CONVERTED">Lead Converted</option>
                    </optgroup>
                    <optgroup label="Deal Actions">
                      <option value="DEAL_CREATED">Deal Created</option>
                      <option value="DEAL_UPDATED">Deal Updated</option>
                      <option value="DEAL_STAGE_UPDATED">Deal Stage Updated</option>
                      <option value="DEAL_AUTO_WON">Deal Auto Won</option>
                    </optgroup>
                    <optgroup label="Quotation Actions">
                      <option value="QUOTATION_CREATED">Quotation Created</option>
                      <option value="QUOTATION_UPDATED">Quotation Updated</option>
                      <option value="QUOTATION_SUBMITTED">Quotation Submitted</option>
                      <option value="QUOTATION_APPROVED">Quotation Approved</option>
                      <option value="QUOTATION_REJECTED">Quotation Rejected</option>
                    </optgroup>
                    <optgroup label="Booking Actions">
                      <option value="BOOKING_CREATED">Booking Created</option>
                      <option value="BOOKING_UPDATED">Booking Updated</option>
                      <option value="BOOKING_CONFIRMED">Booking Confirmed</option>
                      <option value="BOOKING_CANCELLED">Booking Cancelled</option>
                    </optgroup>
                    <optgroup label="Task Actions">
                      <option value="TASK_CREATED">Task Created</option>
                      <option value="TASK_COMPLETED">Task Completed</option>
                    </optgroup>
                  </>
                ) : (
                  <>
                    <optgroup label="Security & Identity">
                      <option value="USER_LOGGED_IN">User Login Success</option>
                      <option value="USER_LOGGED_OUT">User Logout</option>
                      <option value="PASSWORD_CHANGED">Password Changed</option>
                      <option value="PASSWORD_RESET_REQUESTED">Reset Password Request</option>
                      <option value="PASSWORD_RESET_COMPLETED">Reset Password Complete</option>
                    </optgroup>
                    <optgroup label="User Lifecycle">
                      <option value="USER_ACCOUNT_CREATED">User Account Created</option>
                      <option value="USER_ACCOUNT_UPDATED">User Account Updated</option>
                    </optgroup>
                    <optgroup label="Access & Authorization Audit">
                      <option value="LOGIN_FAILED">Login Failed</option>
                      <option value="ACCESS_DENIED_EVENT">Access Denied</option>
                      <option value="INVALID_TOKEN_ACCESS">Invalid/Expired Token Attempt</option>
                      <option value="FEEDBACK_LINK_EXPIRED">Expired Feedback Link Access</option>
                    </optgroup>
                  </>
                )}
              </select>

              {/* Entity Type */}
              <select
                value={entityType}
                onChange={(e) => { setEntityType(e.target.value); setPage(0); }}
                className="px-3 py-2 text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 focus:outline-none focus:border-primary cursor-pointer shadow-sm"
              >
                <option value="">All Entities</option>
                {categoryTab === "BUSINESS" ? (
                  <>
                    <option value="LEAD">Lead</option>
                    <option value="DEAL">Deal</option>
                    <option value="QUOTATION">Quotation</option>
                    <option value="BOOKING">Booking</option>
                    <option value="TASK">Task</option>
                    <option value="PAYMENT">Payment</option>
                  </>
                ) : (
                  <option value="USER">User</option>
                )}
              </select>

              {/* Start Date */}
              <div className="flex items-center gap-1.5 text-xs text-zinc-500">
                <Calendar className="size-3.5 text-zinc-400" />
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => { setStartDate(e.target.value); setPage(0); }}
                  className="px-2 py-1.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 focus:outline-none text-zinc-700 dark:text-zinc-300 cursor-pointer shadow-sm"
                />
              </div>

              {/* End Date */}
              <div className="flex items-center gap-1.5 text-xs text-zinc-500">
                <Calendar className="size-3.5 text-zinc-400" />
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => { setEndDate(e.target.value); setPage(0); }}
                  className="px-2 py-1.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-white dark:bg-zinc-900 focus:outline-none text-zinc-700 dark:text-zinc-300 cursor-pointer shadow-sm"
                />
              </div>

              {/* Clear Filters */}
              <button
                onClick={handleClearFilters}
                className="px-3 py-2 text-xs text-zinc-500 hover:text-zinc-850 dark:hover:text-zinc-200 border border-zinc-200 dark:border-zinc-880 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-850 rounded-lg transition shadow-sm font-semibold flex items-center gap-1"
              >
                <X className="size-3.5" />
                Clear Filters
              </button>
            </>
          )}
        </CardContent>
      </Card>

      {/* Main View Area */}
      <div className="bg-white dark:bg-zinc-950 rounded-xl border border-zinc-100 dark:border-zinc-900 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-24 gap-3 text-zinc-400 dark:text-zinc-500">
            <RefreshCw className="size-6 animate-spin text-primary" />
            <p className="text-xs font-semibold">Loading logs...</p>
          </div>
        ) : categoryTab === "SYSTEM" && systemLogs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-zinc-400 dark:text-zinc-500">
            <Terminal className="size-12 mb-3 opacity-30 text-zinc-400" />
            <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">No server logs found</p>
            <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-1">Try updating your filters or search terms.</p>
          </div>
        ) : categoryTab === "SYSTEM" ? (
          <Table className="table-fixed text-left">
            <colgroup>
              <col style={{ width: "12%" }} />
              <col style={{ width: "10%" }} />
              <col style={{ width: "20%" }} />
              <col style={{ width: "48%" }} />
              <col style={{ width: "10%" }} />
            </colgroup>
            <TableHeader className="bg-zinc-50 dark:bg-zinc-900/60 border-b border-zinc-150 dark:border-zinc-900 text-zinc-500 dark:text-zinc-400">
              <TableRow hoverable={false}>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Timestamp</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Level</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Source Logger / Thread</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Log Message</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedSystemLogs.map((log, index) => {
                let badgeClass = "bg-zinc-105 text-zinc-800 border-zinc-200 dark:bg-zinc-900/50 dark:text-zinc-400 dark:border-zinc-800";
                if (log.level === "ERROR") {
                  badgeClass = "bg-rose-50 text-rose-700 border-rose-205 dark:bg-rose-950/20 dark:text-rose-455 dark:border-rose-900";
                } else if (log.level === "WARN") {
                  badgeClass = "bg-amber-55 text-amber-700 border-amber-205 dark:bg-amber-955/20 dark:text-amber-455 dark:border-amber-900";
                } else if (log.level === "INFO") {
                  badgeClass = "bg-blue-50 text-blue-700 border-blue-205 dark:bg-blue-955/20 dark:text-blue-455 dark:border-blue-900";
                } else if (log.level === "DEBUG") {
                  badgeClass = "bg-zinc-100 text-zinc-550 border-zinc-200 dark:bg-zinc-900/50 dark:text-zinc-500 dark:border-zinc-850";
                }

                const timeStr = new Date(log.timestamp).toLocaleTimeString();
                const dateStr = new Date(log.timestamp).toLocaleDateString();

                return (
                  <TableRow key={index} className="hover:bg-zinc-50 dark:hover:bg-zinc-900/40 border-b border-zinc-100 dark:border-zinc-900 transition">
                    <TableCell className="py-3 px-4 text-xs font-medium text-zinc-505 dark:text-zinc-400 whitespace-nowrap">
                      <div>{dateStr}</div>
                      <div className="text-[10px] text-zinc-400 mt-0.5">{timeStr}</div>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border inline-block ${badgeClass}`}>
                        {log.level}
                      </span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-zinc-650 dark:text-zinc-400">
                      <div className="truncate font-semibold text-zinc-705 dark:text-zinc-300" title={log.loggerName}>
                        {log.loggerName.split(".").pop()}
                      </div>
                      <div className="text-[10px] font-mono text-zinc-400 dark:text-zinc-500 truncate" title={log.threadName}>
                        [{log.threadName}]
                      </div>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-zinc-650 dark:text-zinc-400">
                      <div className="line-clamp-2 break-all font-mono text-[11px]" title={log.message}>
                        {log.message}
                      </div>
                      {log.exception && (
                        <div className="mt-1 text-[10px] text-rose-500 font-semibold flex items-center gap-1">
                          <AlertTriangle className="size-3" /> Exception stack trace available
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="py-3 px-4 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedSystemLog(log)}
                        className="py-1 px-2 border-zinc-200 dark:border-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-900 text-xs font-semibold"
                        leftIcon={<Eye className="size-3.5" />}
                      >
                        Inspect
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : logs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-zinc-400 dark:text-zinc-500">
            <History className="size-12 mb-3 opacity-30 text-zinc-400" />
            <p className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">No activity logs found</p>
            <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-1">Try updating your filters or search terms.</p>
          </div>
        ) : viewMode === "table" ? (
          /* Table View Mode */
          <Table className="table-fixed text-left">
            <colgroup>
              <col style={{ width: "13%" }} />
              <col style={{ width: "13%" }} />
              <col style={{ width: "11%" }} />
              <col style={{ width: "11%" }} />
              <col style={{ width: "14%" }} />
              <col style={{ width: "24%" }} />
              <col style={{ width: "8%" }} />
              <col style={{ width: "6%" }} />
            </colgroup>
            <TableHeader className="bg-zinc-50 dark:bg-zinc-900/60 border-b border-zinc-150 dark:border-zinc-900 text-zinc-500 dark:text-zinc-400">
              <TableRow hoverable={false}>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Timestamp</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Actor</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Entity</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Category</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Action Event</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">Summary</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider">State</TableHead>
                <TableHead className="py-3 px-4 text-[11px] font-bold uppercase tracking-wider text-right">Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {logs.map((log) => {
                const opCfg = OPERATION_CONFIG[log.recordOperation] ?? OPERATION_CONFIG.NORMAL;
                const isSystem = log.actor.type === "SYSTEM";
                const entityStyle = getEntityStyle(log.entity.type);
                const actionCat = getActionCategory(log.activityType);

                return (
                  <TableRow key={log.id} className="hover:bg-zinc-50 dark:hover:bg-zinc-900/40 border-b border-zinc-100 dark:border-zinc-900 transition">
                    <TableCell className="py-3 px-4 text-xs font-medium text-zinc-505 dark:text-zinc-400 whitespace-nowrap">
                      {formatDateTime(log.createdAt)}
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                      <div className="flex flex-col">
                        <div className="flex items-center gap-2">
                          {isSystem ? (
                            <Cpu className="size-3.5 text-zinc-400" />
                          ) : (
                            <User className="size-3.5 text-primary" />
                          )}
                          <span className="truncate" title={log.actor.fullName}>{log.actor.fullName}</span>
                        </div>
                        {!isSystem && log.actor.email && (
                          <span className="text-[10px] text-zinc-400 dark:text-zinc-550 font-mono mt-0.5 truncate block max-w-full" title={log.actor.email}>
                            {log.actor.email}
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs font-bold text-zinc-500">
                      <span className={`px-2 py-0.5 rounded text-[10px] uppercase font-bold tracking-wider ${entityStyle.badge}`}>
                        {log.entity.type}
                      </span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide border ${actionCat.color}`}>
                        {actionCat.label}
                      </span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs font-mono font-bold text-zinc-700 dark:text-zinc-300">
                      <span className="truncate block" title={log.activityType}>{log.activityType}</span>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-xs text-zinc-650 dark:text-zinc-400">
                      <span className="block truncate" title={log.summary}>{log.summary}</span>
                    </TableCell>
                    <TableCell className="py-3 px-4">
                      <Badge variant={opCfg.variant} className="font-extrabold text-[9px] uppercase tracking-wide">
                        {opCfg.label}
                      </Badge>
                    </TableCell>
                    <TableCell className="py-3 px-4 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedId(log.id)}
                        className="py-1 px-2 border-zinc-200 dark:border-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-900 text-xs font-semibold"
                        leftIcon={<Eye className="size-3.5" />}
                      >
                        Inspect
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        ) : (
          /* Timeline View Mode */
          <div className="p-6 md:p-8 space-y-6">
            <div className="relative border-l-2 border-zinc-200 dark:border-zinc-800 ml-4 md:ml-6 pl-6 md:pl-8 space-y-8">
              {logs.map((log) => {
                const isSystem = log.actor.type === "SYSTEM";
                const opCfg = OPERATION_CONFIG[log.recordOperation] ?? OPERATION_CONFIG.NORMAL;
                const style = getEntityStyle(log.entity.type);
                const actionCat = getActionCategory(log.activityType);

                return (
                  <div key={log.id} className="relative">
                    {/* Circle icon marker.
                        Every human-actor entry used to render the same generic
                        `User` glyph, so a login, an approval and a deletion were
                        indistinguishable in the icon column — the one thing that
                        column exists to convey. The icon now comes from the
                        canonical event registry (§9.8), keyed on the activity
                        type. Machine-actor rows keep `Cpu`, which is a genuine
                        actor distinction rather than an event one. */}
                    <span className={`absolute -left-8.75 md:-left-10.75 top-1.5 flex items-center justify-center rounded-full size-6 md:size-8 ring-4 ring-white dark:ring-zinc-955 ${style.iconBg}`}>
                      {isSystem ? (
                        <Cpu className="size-3 md:size-4" />
                      ) : (
                        (() => {
                          const EventIcon = timelineEventIcon(timelineEventKind(log.activityType));
                          return <EventIcon className="size-3 md:size-4" />;
                        })()
                      )}
                    </span>

                    {/* Card container */}
                    <div className={`p-4 rounded-xl border transition hover:shadow-md ${style.color}`}>
                      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-zinc-200/50 dark:border-zinc-800/40 pb-2 mb-2">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold uppercase tracking-wider">{log.activityType}</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide border ${actionCat.color}`}>
                            {actionCat.label}
                          </span>
                          <Badge variant={opCfg.variant} className="font-extrabold text-[9px] scale-90 uppercase">
                            {opCfg.label}
                          </Badge>
                        </div>
                        <span className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
                          {formatDateTime(log.createdAt)}
                        </span>
                      </div>

                      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
                        <div>
                          <p className="text-xs font-medium text-zinc-800 dark:text-zinc-200">{log.summary}</p>
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-2 text-[11px] text-zinc-500">
                            <span>Actor: <strong className="text-zinc-700 dark:text-zinc-300">{log.actor.fullName}</strong>{log.actor.email && ` <${log.actor.email}>`} {log.actor.role && `(${log.actor.role})`}</span>
                            <span>•</span>
                            <span>Entity: <strong className="text-zinc-700 dark:text-zinc-300">{log.entity.type}:{log.entity.id.slice(0, 8)}</strong></span>
                          </div>
                        </div>

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setSelectedId(log.id)}
                          className="bg-white dark:bg-zinc-900 border-zinc-200/60 dark:border-zinc-800 text-[11px] font-bold"
                          rightIcon={<ArrowRight className="size-3.5" />}
                        >
                          View Details
                        </Button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Pagination Controls */}
        {(() => {
          const activeTotalPages = categoryTab === "SYSTEM" ? systemLogsTotalPages : totalPages;
          const activeTotalElements = categoryTab === "SYSTEM" ? systemLogs.length : totalElements;
          
          if (activeTotalPages <= 1) return null;

          return (
            <div className="flex items-center justify-between px-5 py-4 border-t border-zinc-100 dark:border-zinc-900 bg-zinc-50/50 dark:bg-zinc-950/20">
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                Page <strong className="text-zinc-800 dark:text-zinc-200">{page + 1}</strong> of <strong className="text-zinc-850 dark:text-zinc-200">{activeTotalPages}</strong>
                <span className="text-zinc-400 dark:text-zinc-600 ml-2">· {activeTotalElements} entries</span>
              </p>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-lg border border-zinc-200 dark:border-zinc-800 text-zinc-500 dark:text-zinc-400 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-850 disabled:opacity-40 disabled:cursor-not-allowed transition shadow-xs"
                >
                  <ChevronLeft className="size-4" /> Prev
                </button>

                {Array.from({ length: Math.min(5, activeTotalPages) }, (_, i) => {
                  const start = Math.max(0, Math.min(page - 2, activeTotalPages - 5));
                  return start + i;
                }).map((p) => (
                  <button
                    key={p}
                    onClick={() => setPage(p)}
                    className={`size-8 text-xs font-bold rounded-lg border transition ${p === page
                        ? "bg-primary text-white border-primary shadow-sm"
                        : "border-zinc-200 dark:border-zinc-800 text-zinc-500 dark:text-zinc-400 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-850"
                      }`}
                  >
                    {p + 1}
                  </button>
                ))}

                <button
                  onClick={() => setPage((p) => Math.min(activeTotalPages - 1, p + 1))}
                  disabled={page >= activeTotalPages - 1}
                  className="flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-lg border border-zinc-200 dark:border-zinc-800 text-zinc-500 dark:text-zinc-400 bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-850 disabled:opacity-40 disabled:cursor-not-allowed transition shadow-xs"
                >
                  Next <ChevronRight className="size-4" />
                </button>
              </div>
            </div>
          );
        })()}
      </div>

      {/* Details Side Panel / Drawer */}
      {selectedId && (
        <>
          <div className="fixed inset-0 bg-zinc-950/30 backdrop-blur-xs z-40 transition-opacity" onClick={() => setSelectedId(null)} />
          <div className="fixed inset-y-0 right-0 z-50 w-full max-w-xl bg-white dark:bg-zinc-950 shadow-2xl border-l border-zinc-200 dark:border-zinc-900 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Drawer Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 dark:border-zinc-900">
              <div className="flex items-center gap-2">
                <History className="size-5 text-primary" />
                <div>
                  <h3 className="text-sm font-bold text-zinc-800 dark:text-zinc-200">Inspect Audit Entry</h3>
                  <p className="text-[10px] text-zinc-400 dark:text-zinc-500 mt-0.5">
                    Immutable cryptographically-correlated record details.
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedId(null)}
                className="p-1.5 rounded-full text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900 hover:text-zinc-600 dark:hover:text-zinc-200 transition cursor-pointer"
              >
                <X className="size-5" />
              </button>
            </div>

            {/* Drawer Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {isLoadingDetail ? (
                <div className="flex flex-col items-center justify-center py-20 gap-2 text-zinc-400">
                  <RefreshCw className="size-5 animate-spin text-primary" />
                  <p className="text-xs font-semibold">Loading details...</p>
                </div>
              ) : selectedLog ? (
                (() => {
                  const opCfg = OPERATION_CONFIG[selectedLog.recordOperation] ?? OPERATION_CONFIG.NORMAL;
                  const actionCat = getActionCategory(selectedLog.activityType);
                  const entityStyle = getEntityStyle(selectedLog.entity.type);

                  return (
                    <>
                      {/* Status Banner */}
                      {selectedLog.recordOperation !== "NORMAL" && (
                        <div className={`flex items-start gap-2.5 p-3.5 border rounded-xl text-[11px] ${selectedLog.recordOperation === "VOIDED"
                            ? "bg-rose-50 border-rose-200 dark:bg-rose-955/10 dark:border-rose-900/60 text-rose-800 dark:text-rose-400"
                            : "bg-amber-50 border-amber-200 dark:bg-amber-955/10 dark:border-amber-900/60 text-amber-800 dark:text-amber-400"
                          }`}>
                          <ShieldAlert className="size-4 shrink-0 mt-px" />
                          <div className="space-y-1">
                            <p className="font-bold">This is a {selectedLog.recordOperation.toLowerCase()} record</p>
                            <p className="leading-relaxed opacity-90">
                              {selectedLog.recordOperation === "VOIDED"
                                ? "This transaction has been voided and is no longer part of active ledger status calculations."
                                : "This transaction has been superseded by a correction event."}
                            </p>
                            {selectedLog.reason && (
                              <p className="mt-1.5 font-bold italic">Reason: &ldquo;{selectedLog.reason}&rdquo;</p>
                            )}
                          </div>
                        </div>
                      )}

                      {/* Operational Details Grid */}
                      <div className="grid grid-cols-2 gap-4 border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 bg-zinc-50/50 dark:bg-zinc-900/30">
                        <div className="space-y-1">
                          <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Record ID</span>
                          <p className="text-xs font-mono font-semibold text-zinc-700 dark:text-zinc-300 truncate" title={selectedLog.id}>
                            {selectedLog.id}
                          </p>
                        </div>
                        <div className="space-y-1">
                          <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Timestamp</span>
                          <p className="text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                            {formatDateTime(selectedLog.createdAt)}
                          </p>
                        </div>
                        <div className="space-y-1 col-span-2">
                          <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Trace correlation id (mdc)</span>
                          <p className="text-xs font-mono font-semibold text-zinc-700 dark:text-zinc-300 truncate" title={selectedLog.correlationId}>
                            {selectedLog.correlationId}
                          </p>
                        </div>
                      </div>

                      {/* Action classification */}
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                          <GitFork className="size-4 text-zinc-400" /> Action Classification
                        </h4>
                        <div className="border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 bg-white dark:bg-zinc-950 space-y-3">
                          <div className="grid grid-cols-2 gap-3 text-xs">
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-505 block">Action Category</span>
                              <span className={`px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide border inline-block ${actionCat.color}`}>
                                {actionCat.label}
                              </span>
                            </div>
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-505 block">Action Event Code</span>
                              <strong className="text-zinc-700 dark:text-zinc-300 font-mono text-[11px]">{selectedLog.activityType}</strong>
                            </div>
                            <div className="col-span-2">
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-505 block mb-1">State / Operation Mode</span>
                              <Badge variant={opCfg.variant} className="font-extrabold text-[9px] uppercase tracking-wide">
                                {opCfg.label}
                              </Badge>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Actor details */}
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                          <User className="size-4 text-zinc-400" /> Actor Context
                        </h4>
                        <div className="border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 space-y-3 bg-white dark:bg-zinc-955">
                          <div className="grid grid-cols-2 gap-3 text-xs">
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-505 block">Actor Type</span>
                              <strong className="text-zinc-700 dark:text-zinc-300">{selectedLog.actor.type}</strong>
                            </div>
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Actor Name</span>
                              <strong className="text-zinc-700 dark:text-zinc-300">{selectedLog.actor.fullName}</strong>
                            </div>
                            {selectedLog.actor.id && (
                              <div>
                                <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Actor User ID</span>
                                <span className="font-mono text-zinc-600 dark:text-zinc-400 truncate block" title={selectedLog.actor.id}>
                                  {selectedLog.actor.id}
                                </span>
                              </div>
                            )}
                            {selectedLog.actor.role && (
                              <div>
                                <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Role Snapshot</span>
                                <strong className="text-zinc-700 dark:text-zinc-300">{selectedLog.actor.role}</strong>
                              </div>
                            )}
                            {selectedLog.actor.email && (
                              <div className="col-span-2">
                                <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Actor Email</span>
                                <strong className="text-zinc-700 dark:text-zinc-300 font-mono">{selectedLog.actor.email}</strong>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Entity context */}
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                          <Info className="size-4 text-zinc-400" /> Target Entity
                        </h4>
                        <div className="border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 bg-white dark:bg-zinc-950">
                          <div className="grid grid-cols-2 gap-3 text-xs">
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Entity Type</span>
                              <span className={`px-2 py-0.5 rounded text-[10px] uppercase font-bold tracking-wider inline-block ${entityStyle.badge}`}>
                                {selectedLog.entity.type}
                              </span>
                            </div>
                            <div>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-550 block">Entity ID</span>
                              <span className="font-mono text-zinc-600 dark:text-zinc-400 truncate block" title={selectedLog.entity.id}>
                                {selectedLog.entity.id}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Referenced History Node (Self-correcting audits chain) */}
                      {selectedLog.refActivityId && (
                        <div className="space-y-2">
                          <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                            <GitFork className="size-4 text-zinc-400" /> Historical Reference Chain
                          </h4>
                          <button
                            type="button"
                            onClick={() => handleViewRelation(selectedLog.refActivityId!)}
                            className="w-full text-left border border-primary/20 dark:border-primary/10 rounded-xl p-4 bg-primary/5 hover:bg-primary/10 text-xs flex items-center justify-between transition group"
                          >
                            <div>
                              <span className="text-[10px] text-primary font-bold uppercase tracking-wider block mb-1">
                                {selectedLog.recordOperation === "CORRECTED" ? "Superseded Reference Log" : "Corrected Original Log"}
                              </span>
                              <span className="font-semibold text-zinc-800 dark:text-zinc-200 block truncate max-w-sm">
                                View details for Activity ID: {selectedLog.refActivityId.slice(0, 8)}...
                              </span>
                            </div>
                            <ArrowRight className="size-4 text-primary transition-transform group-hover:translate-x-1" />
                          </button>
                        </div>
                      )}

                      {/* Action Payload Summary */}
                      {selectedLog.payload && typeof selectedLog.payload === "object" && Object.keys(selectedLog.payload).length > 0 && (
                        <div className="space-y-2">
                          <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                            <LayoutGrid className="size-4 text-zinc-400" /> Action Payload Attributes
                          </h4>
                          {renderPayloadSummary(selectedLog.payload)}
                        </div>
                      )}

                      {/* Payload JSON */}
                      <div className="space-y-2">
                        <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                          <LayoutGrid className="size-4 text-zinc-400" /> Business Action Payload (JSONB Raw)
                        </h4>
                        {selectedLog.payload ? (
                          <pre className="bg-zinc-50 dark:bg-zinc-900 p-4 rounded-xl overflow-x-auto text-[11px] font-mono border border-zinc-150 dark:border-zinc-850 text-zinc-700 dark:text-zinc-300 max-h-64 custom-scrollbar">
                            {JSON.stringify(selectedLog.payload, null, 2)}
                          </pre>
                        ) : (
                          <div className="p-4 border border-zinc-100 dark:border-zinc-900 rounded-xl text-center text-xs text-zinc-400 bg-zinc-50/50 dark:bg-zinc-950/30">
                            No payload associated with this activity entry.
                          </div>
                        )}
                      </div>
                    </>
                  );
                })()
              ) : (
                <div className="text-center text-xs text-rose-500 py-10">
                  Failed to load log content details.
                </div>
              )}
            </div>

            {/* Drawer Footer */}
            <div className="px-6 py-4 border-t border-zinc-100 dark:border-zinc-900 bg-zinc-50 dark:bg-zinc-900/40 flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSelectedId(null)}
                className="border-zinc-200 dark:border-zinc-850 text-xs font-bold bg-white dark:bg-zinc-950"
              >
                Close Audit Inspection
              </Button>
            </div>
          </div>
        </>
      )}

      {/* System Log Details Side Panel / Drawer */}
      {selectedSystemLog && (
        <>
          <div className="fixed inset-0 bg-zinc-955/30 backdrop-blur-xs z-40 transition-opacity" onClick={() => setSelectedSystemLog(null)} />
          <div className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl bg-white dark:bg-zinc-950 shadow-2xl border-l border-zinc-200 dark:border-zinc-900 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Drawer Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 dark:border-zinc-900">
              <div className="flex items-center gap-2">
                <Terminal className="size-5 text-amber-500" />
                <div>
                  <h3 className="text-sm font-bold text-zinc-800 dark:text-zinc-200">Inspect Server Log Entry</h3>
                  <p className="text-[10px] text-zinc-400 dark:text-zinc-500 mt-0.5">
                    Real-time captured standard output and trace logs.
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedSystemLog(null)}
                className="p-1.5 rounded-full text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900 hover:text-zinc-650 dark:hover:text-zinc-200 transition cursor-pointer"
              >
                <X className="size-5" />
              </button>
            </div>

            {/* Drawer Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Log Level Banner */}
              <div className={`flex items-start gap-2.5 p-3.5 border rounded-xl text-[11px] ${
                selectedSystemLog.level === "ERROR"
                  ? "bg-rose-50 border-rose-200 dark:bg-rose-955/10 dark:border-rose-900/60 text-rose-800 dark:text-rose-400"
                  : selectedSystemLog.level === "WARN"
                  ? "bg-amber-50 border-amber-200 dark:bg-amber-955/10 dark:border-amber-900/60 text-amber-800 dark:text-amber-400"
                  : selectedSystemLog.level === "INFO"
                  ? "bg-blue-50 border-blue-200 dark:bg-blue-955/10 dark:border-blue-900/60 text-blue-800 dark:text-blue-400"
                  : "bg-zinc-50 border-zinc-200 dark:bg-zinc-900/10 dark:border-zinc-850 text-zinc-705 dark:text-zinc-400"
              }`}>
                <Terminal className="size-4 shrink-0 mt-px" />
                <div className="space-y-1">
                  <p className="font-bold">Log Event: {selectedSystemLog.level}</p>
                  <p className="leading-relaxed opacity-90 font-mono text-[11px] break-all">
                    {selectedSystemLog.message}
                  </p>
                </div>
              </div>

              {/* Log Metadata Grid */}
              <div className="grid grid-cols-2 gap-4 border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 bg-zinc-50/50 dark:bg-zinc-900/30">
                <div className="space-y-1">
                  <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Timestamp</span>
                  <p className="text-xs font-semibold text-zinc-755 dark:text-zinc-300">
                    {new Date(selectedSystemLog.timestamp).toLocaleString()}
                  </p>
                </div>
                <div className="space-y-1">
                  <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Thread Name</span>
                  <p className="text-xs font-mono font-semibold text-zinc-755 dark:text-zinc-300 truncate" title={selectedSystemLog.threadName}>
                    {selectedSystemLog.threadName}
                  </p>
                </div>
                <div className="space-y-1 col-span-2">
                  <span className="text-[10px] font-bold text-zinc-400 dark:text-zinc-500 uppercase tracking-wide">Logger Name</span>
                  <p className="text-xs font-mono font-semibold text-zinc-755 dark:text-zinc-300 truncate" title={selectedSystemLog.loggerName}>
                    {selectedSystemLog.loggerName}
                  </p>
                </div>
              </div>

              {/* Execution Context */}
              <div className="space-y-2">
                <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                  <User className="size-4 text-zinc-400" /> Execution Context
                </h4>
                <div className="border border-zinc-100 dark:border-zinc-900 rounded-xl p-4 bg-white dark:bg-zinc-950 space-y-3">
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block">Actor ID / Sub</span>
                      <strong className="text-zinc-700 dark:text-zinc-300 font-mono text-[11px] truncate block" title={selectedSystemLog.userId || "N/A"}>
                        {selectedSystemLog.userId || "SYSTEM (Non-interactive)"}
                      </strong>
                    </div>
                    <div>
                      <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block">Actor Email</span>
                      <strong className="text-zinc-700 dark:text-zinc-300 truncate block" title={selectedSystemLog.userEmail || "N/A"}>
                        {selectedSystemLog.userEmail || "N/A"}
                      </strong>
                    </div>
                    <div className="col-span-2">
                      <span className="text-[10px] text-zinc-400 dark:text-zinc-500 block">Trace Correlation ID</span>
                      <span className="font-mono text-zinc-600 dark:text-zinc-400 text-[11px] truncate block" title={selectedSystemLog.correlationId || "N/A"}>
                        {selectedSystemLog.correlationId || "N/A"}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Exception Stack Trace */}
              {selectedSystemLog.exception && (
                <div className="space-y-2">
                  <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 uppercase tracking-wide flex items-center gap-1.5">
                    <AlertTriangle className="size-4 text-rose-500" /> Exception Traceback
                  </h4>
                  <pre className="bg-rose-50/20 dark:bg-rose-955/10 p-4 rounded-xl overflow-x-auto text-[11px] font-mono border border-rose-100 dark:border-rose-900/40 text-rose-700 dark:text-rose-400 max-h-96 custom-scrollbar whitespace-pre">
                    {selectedSystemLog.exception}
                  </pre>
                </div>
              )}
            </div>

            {/* Drawer Footer */}
            <div className="px-6 py-4 border-t border-zinc-100 dark:border-zinc-900 bg-zinc-50 dark:bg-zinc-900/40 flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSelectedSystemLog(null)}
                className="border-zinc-200 dark:border-zinc-850 text-xs font-bold bg-white dark:bg-zinc-950"
              >
                Close Inspector
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
