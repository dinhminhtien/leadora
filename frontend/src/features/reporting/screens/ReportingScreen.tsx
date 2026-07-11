"use client";

import React, { useState } from "react";
import { FileText, Download, Printer, AlertCircle, CheckCircle2, ClipboardList, Loader2, TrendingUp, GitBranch, ReceiptText, ShieldCheck } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import type { Quotation } from "@/services/quotation_service";
import { useQuotationsForReport, useSaveReportLog } from "@/features/reporting/hooks/use_reporting";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import { SalesPerformanceTab } from "@/features/reporting/components/SalesPerformanceTab";
import { TaskPerformanceTab } from "@/features/reporting/components/TaskPerformanceTab";
import { PipelineProgressionTab } from "@/features/reporting/components/PipelineProgressionTab";
import { QuotationOutcomeTab } from "@/features/reporting/components/QuotationOutcomeTab";
import { SlaComplianceTab } from "@/features/reporting/components/SlaComplianceTab";

export interface ReportLog {
  id: string;
  generatedBy: string;
  role: string;
  generatedAt: string;
  filters: {
    dateFrom: string;
    dateTo: string;
    roomType?: string;
    discountThreshold: number;
  };
  resultCount: number;
}

// ── Discount Report Tab ──────────────────────────────────────────────────────

interface ReportFilters {
  dateFrom: string;
  dateTo: string;
  roomType: string;
  discountThreshold: string;
}

const ROOM_TYPE_OPTIONS = ["", "Deluxe Suite", "Superior Room", "Standard Queen", "Executive Suite", "Ocean View Room", "Banquet Hall", "Grand Ballroom Suite"];

function DiscountReportTab() {
  const today = new Date().toISOString().split("T")[0];
  const firstOfMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
    .toISOString()
    .split("T")[0];

  const [filters, setFilters] = useState<ReportFilters>({
    dateFrom: firstOfMonth,
    dateTo: today,
    roomType: "",
    discountThreshold: "10",
  });
  const [reportData, setReportData] = useState<Quotation[] | null>(null);
  const [auditMsg, setAuditMsg] = useState("");

  const { data: quotations = [], isLoading, isError } = useQuotationsForReport();
  const saveReportLog = useSaveReportLog();
  const currentUser = useAuthStore((s) => s.user);

  const handleGenerate = async () => {
    const threshold = parseFloat(filters.discountThreshold) || 0;
    const results = quotations.filter((q) => {
      const disc = q.discountPercent ?? 0;
      const issueDate = q.validUntil ?? q.expiryDate ?? "";
      const inRange =
        (!filters.dateFrom || issueDate >= filters.dateFrom) &&
        (!filters.dateTo || issueDate <= filters.dateTo);
      const matchesRoom = !filters.roomType || q.roomType === filters.roomType;
      return disc > threshold && inRange && matchesRoom;
    });

    setReportData(results);

    // POST-3: persist audit log to backend (BR-37: actor, role, action, result, timestamp)
    const actorName = currentUser?.name ?? currentUser?.email ?? "Unknown";
    const actorRole = currentUser?.roles?.[0] ?? "UNKNOWN";
    try {
      const saved = await saveReportLog.mutateAsync({
        generatedByName: actorName,
        generatedByRole: actorRole,
        filterDateFrom: filters.dateFrom || undefined,
        filterDateTo: filters.dateTo || undefined,
        filterRoomType: filters.roomType || undefined,
        filterDiscountThreshold: threshold,
        resultCount: results.length,
        action: "GENERATE_DISCOUNT_REPORT",
        result: results.length > 0 ? "SUCCESS" : "NO_DATA",
        reason: `Discount threshold: ${threshold}%, Date range: ${filters.dateFrom} → ${filters.dateTo}`,
      });
      const ts = saved.data?.generatedAt
        ? new Date(saved.data.generatedAt).toLocaleTimeString()
        : new Date().toLocaleTimeString();
      setAuditMsg(
        `Report generated at ${ts} by ${actorName} (${actorRole}) — ${results.length} quote(s) found. Log #${saved.data?.logId?.toString().slice(0, 8).toUpperCase() ?? "?"} saved.`
      );
    } catch {
      setAuditMsg(
        `Report generated — ${results.length} quote(s) found. (Audit log could not be saved.)`
      );
    }
  };

  const handleExportCSV = () => {
    if (!reportData) return;
    const headers = ["Quote No", "Contact Name", "Room Type", "Discount %", "Subtotal", "Discount Amt", "Total", "Status", "Date Issued"];
    const rows = reportData.map((q) => {
      const sentDate = (q as { sentDate?: string }).sentDate || q.expiryDate || "";
      return [
        q.quoteNo,
        q.contactName,
        q.roomType ?? "N/A",
        `${q.discountPercent ?? 0}%`,
        `${(q.subtotal ?? 0).toFixed(0)} ₫`,
        `${(q.discountAmount ?? 0).toFixed(0)} ₫`,
        `${q.amount.toFixed(0)} ₫`,
        q.status,
        sentDate,
      ];
    });
    const csv = "﻿" + [headers, ...rows].map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `discount-report-${today}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handlePrint = () => window.print();

  const statusBadge = (status: string) => {
    const map: Record<string, "default" | "success" | "warning" | "danger" | "primary"> = {
      draft: "default",
      sent: "primary",
      accepted: "success",
      expired: "danger",
      pending_approval: "warning",
    };
    return map[status] ?? "default";
  };

  return (
    <div className="space-y-5">
      {/* Filter Form */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardHeader>
          <CardTitle className="text-sm font-bold text-slate-700">Report Filters</CardTitle>
          <CardDescription className="text-xs text-slate-400">
            Filter quotations by date range, room type, and minimum discount threshold
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Date From</label>
              <Input
                type="date"
                value={filters.dateFrom}
                onChange={(e) => setFilters((f) => ({ ...f, dateFrom: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Date To</label>
              <Input
                type="date"
                value={filters.dateTo}
                onChange={(e) => setFilters((f) => ({ ...f, dateTo: e.target.value }))}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Room Type</label>
              <Select
                value={filters.roomType}
                onChange={(e) => setFilters((f) => ({ ...f, roomType: e.target.value }))}
              >
                <option value="">All Room Types</option>
                {ROOM_TYPE_OPTIONS.filter(Boolean).map((rt) => (
                  <option key={rt} value={rt}>{rt}</option>
                ))}
              </Select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">
                Min Discount Threshold (%)
              </label>
              <Input
                type="number"
                min={0}
                max={100}
                value={filters.discountThreshold}
                onChange={(e) => setFilters((f) => ({ ...f, discountThreshold: e.target.value }))}
              />
            </div>
          </div>
          <div className="mt-4 flex items-center gap-3">
            <Button
              variant="primary"
              size="sm"
              onClick={handleGenerate}
              disabled={isLoading || saveReportLog.isPending}
              leftIcon={
                isLoading || saveReportLog.isPending
                  ? <Loader2 className="size-3.5 animate-spin" />
                  : <ClipboardList className="size-3.5" />
              }
              className="text-xs font-bold"
            >
              {isLoading ? "Loading data..." : saveReportLog.isPending ? "Saving log..." : "Generate Report"}
            </Button>
            {isError && (
              <span className="text-xs text-red-500 font-semibold flex items-center gap-1">
                <AlertCircle className="size-3.5" /> Failed to load quotations
              </span>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Audit message */}
      {auditMsg && (
        <div className="flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3">
          <CheckCircle2 className="size-4 text-emerald-600 mt-0.5 shrink-0" />
          <p className="text-xs text-emerald-700 font-semibold">{auditMsg}</p>
        </div>
      )}

      {/* Results */}
      {reportData !== null && (
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <div className="flex items-center justify-between w-full">
              <div>
                <CardTitle className="text-sm font-bold text-slate-700">
                  Discount Report Results
                  <span className="ml-2 text-xs font-normal text-slate-400">
                    ({reportData.length} quote{reportData.length !== 1 ? "s" : ""} found)
                  </span>
                </CardTitle>
                <CardDescription className="text-xs text-slate-400">
                  Quotations with discount &gt; {filters.discountThreshold}%
                </CardDescription>
              </div>
              {reportData.length > 0 && (
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleExportCSV}
                    leftIcon={<Download className="size-3.5" />}
                    className="text-xs border-slate-200 text-slate-600"
                  >
                    Export CSV
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handlePrint}
                    leftIcon={<Printer className="size-3.5" />}
                    className="text-xs text-slate-600"
                  >
                    Print
                  </Button>
                </div>
              )}
            </div>
          </CardHeader>
          <CardContent className="px-2">
            {reportData.length === 0 ? (
              <div className="py-10 text-center">
                <AlertCircle className="size-8 text-slate-300 mx-auto mb-2" />
                <p className="text-xs text-slate-400">
                  No quotations found with discount above {filters.discountThreshold}% in the selected period.
                </p>
              </div>
            ) : (
              <Table>
                <TableHeader className="bg-slate-50 border-b border-slate-100">
                  <TableRow hoverable={false}>
                    <TableHead className="text-xs font-semibold text-slate-500">Quote No</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500">Contact</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500">Room Type</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500 text-right">Discount %</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500 text-right">Subtotal</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500 text-right">Discount Amt</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500 text-right">Total</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500">Status</TableHead>
                    <TableHead className="text-xs font-semibold text-slate-500">Issued</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {reportData.map((q) => (
                    <TableRow key={q.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                      <TableCell className="py-3 px-4 text-xs font-bold text-blue-600">{q.quoteNo}</TableCell>
                      <TableCell className="py-3 px-4 text-xs font-semibold text-slate-700">{q.contactName}</TableCell>
                      <TableCell className="py-3 px-4 text-xs text-slate-500">{q.roomType ?? "—"}</TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right">
                        <span className="font-black text-amber-600">{q.discountPercent ?? 0}%</span>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right text-slate-600">
                        {(q.subtotal ?? 0).toLocaleString('vi-VN')} ₫
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right text-red-500 font-semibold">
                        -{(q.discountAmount ?? 0).toLocaleString('vi-VN')} ₫
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right font-black text-slate-800">
                        {q.amount.toLocaleString('vi-VN')} ₫
                      </TableCell>
                      <TableCell className="py-3 px-4">
                        <Badge variant={statusBadge(q.status)} size="sm" className="font-bold text-[9px] uppercase">
                          {q.status === "pending_approval" ? "Pending" : q.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-slate-400">{(q as { sentDate?: string }).sentDate || q.expiryDate || "—"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ── Main Screen ──────────────────────────────────────────────────────────────

type Tab =
  | "sales-performance"
  | "pipeline-progression"
  | "quotation-outcome"
  | "sla-compliance"
  | "task-performance"
  | "discount-report";

export function ReportingScreen() {
  const user = useAuthStore((s) => s.user);
  const role = getUserRole(user);
  // UC-23.1 (Sales Performance) is a Sales-Manager report; UC-23.2 (Task Performance) is also
  // available to Sales Staff, scoped to their own tasks (the backend applies the scope by role).
  const isManagerScope = role === "MANAGER" || role === "ADMIN";

  // Land on the first report the role can see: managers → Sales Performance, staff → Task Performance.
  const [activeTab, setActiveTab] = useState<Tab>(isManagerScope ? "sales-performance" : "task-performance");

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    ...(isManagerScope
      ? ([
          { key: "sales-performance", label: "Sales Performance", icon: <TrendingUp className="size-3.5" /> },
          { key: "pipeline-progression", label: "Pipeline Progression", icon: <GitBranch className="size-3.5" /> },
          { key: "quotation-outcome", label: "Quotation Outcome", icon: <ReceiptText className="size-3.5" /> },
          { key: "sla-compliance", label: "SLA Compliance", icon: <ShieldCheck className="size-3.5" /> },
        ] as { key: Tab; label: string; icon: React.ReactNode }[])
      : []),
    { key: "task-performance", label: "Task Performance", icon: <ClipboardList className="size-3.5" /> },
    { key: "discount-report", label: "Discount Reports", icon: <FileText className="size-3.5" /> },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Advanced Reporting & Analytics</h1>
          <p className="text-xs text-slate-400">
            Deep-dive insights into hotel sales pipeline performance and discount approvals
          </p>
        </div>
      </div>

      {/* Tab switcher */}
      <div className="flex border-b border-slate-200 gap-1">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors -mb-px ${activeTab === tab.key
                ? "border-primary text-blue-700"
                : "border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300"
              }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "sales-performance" && <SalesPerformanceTab />}
      {activeTab === "pipeline-progression" && <PipelineProgressionTab />}
      {activeTab === "quotation-outcome" && <QuotationOutcomeTab />}
      {activeTab === "sla-compliance" && <SlaComplianceTab />}
      {activeTab === "task-performance" && <TaskPerformanceTab />}
      {activeTab === "discount-report" && <DiscountReportTab />}
    </div>
  );
}
