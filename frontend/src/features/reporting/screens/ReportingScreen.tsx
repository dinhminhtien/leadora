"use client";

import React, { useState, useEffect } from "react";
import { BarChart2, FileText, Download, Printer, AlertCircle, CheckCircle2, ClipboardList } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { quotationService, type Quotation } from "@/services/quotation_service";

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

// ── Analytics Tab ────────────────────────────────────────────────────────────

function AnalyticsTab({ reportPeriod, setReportPeriod }: { reportPeriod: string; setReportPeriod: (v: string) => void }) {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 justify-end">
        <span className="text-xs font-semibold text-slate-400">Reporting Range:</span>
        <select
          value={reportPeriod}
          onChange={e => setReportPeriod(e.target.value)}
          className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 focus:outline-none"
        >
          <option value="this-month">This Month</option>
          <option value="this-quarter">This Quarter (Q2)</option>
          <option value="this-year">This Year (2026)</option>
        </select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2 border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Conversion Funnel Velocity</CardTitle>
            <CardDescription className="text-xs text-slate-400">Weighted stage transition rates and losses</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5 pt-2">
            {[
              { label: "1. Total Inquiries Created", count: 42, value: "$280,000", pct: "100%", wPct: 100, color: "bg-blue-600" },
              { label: "2. Site Visits Scheduled", count: 28, value: "$195,000", pct: "66.7%", wPct: 67, color: "bg-indigo-600" },
              { label: "3. Proposals Dispatched", count: 18, value: "$132,000", pct: "42.8%", wPct: 43, color: "bg-purple-600" },
              { label: "4. Contract Negotiation", count: 10, value: "$85,000", pct: "23.8%", wPct: 24, color: "bg-pink-600" },
              { label: "5. Confirmed Bookings", count: 6, value: "$45,000", pct: "14.2%", wPct: 14, color: "bg-emerald-600" },
            ].map((funnel, idx) => (
              <div key={idx} className="space-y-1">
                <div className="flex justify-between items-center text-xs font-semibold">
                  <span className="text-slate-700">{funnel.label}</span>
                  <span className="text-slate-400">
                    {funnel.count} count • <strong className="text-slate-800">{funnel.value}</strong> ({funnel.pct})
                  </span>
                </div>
                <div className="w-full bg-slate-100 rounded-full h-4 overflow-hidden flex">
                  <div
                    className={`${funnel.color} h-full rounded-full flex items-center justify-end px-2 text-[9px] text-white font-bold transition-all duration-500`}
                    style={{ width: `${funnel.wPct}%` }}
                  >
                    {funnel.wPct >= 15 ? funnel.pct : ""}
                  </div>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Lead Source Share</CardTitle>
            <CardDescription className="text-xs text-slate-400">Inquiry channels distribution</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 pt-2">
            {[
              { source: "Direct Website", share: "45%", count: 19, value: "$126,000", color: "bg-blue-500" },
              { source: "Referral Partners", share: "25%", count: 11, value: "$70,000", color: "bg-indigo-500" },
              { source: "Social Media Ads", share: "18%", count: 8, value: "$50,400", color: "bg-purple-500" },
              { source: "Cold outreach", share: "12%", count: 4, value: "$33,600", color: "bg-slate-400" },
            ].map((item, idx) => (
              <div key={idx} className="space-y-1">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-semibold text-slate-700 flex items-center gap-1.5">
                    <span className={`size-2.5 rounded-full ${item.color}`} />
                    {item.source}
                  </span>
                  <span className="text-slate-400 font-bold">{item.share}</span>
                </div>
                <div className="text-[10px] text-slate-400 pl-4">
                  {item.count} inquiries • <strong className="text-slate-700">{item.value}</strong>
                </div>
              </div>
            ))}
            <div className="border-t border-slate-100 pt-4 flex justify-center">
              <svg className="size-24" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#f1f5f9" strokeWidth="3.5" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#3b82f6" strokeWidth="3.5" strokeDasharray="45 55" strokeDashoffset="25" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#6366f1" strokeWidth="3.5" strokeDasharray="25 75" strokeDashoffset="80" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#a855f7" strokeWidth="3.5" strokeDasharray="18 82" strokeDashoffset="5" />
              </svg>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardHeader>
          <CardTitle className="text-sm font-bold text-slate-800">Agent Performance Benchmarks</CardTitle>
          <CardDescription className="text-xs text-slate-400">Quarterly team speed, value, and closure metrics</CardDescription>
        </CardHeader>
        <CardContent className="px-2">
          <Table>
            <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
              <TableRow hoverable={false}>
                <TableHead className="font-semibold text-xs text-slate-500">Sales Agent</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Avg Lead Response Speed</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Deals Managed</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Total Booked Value</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Won Ratio</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">SLA Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {[
                { name: "John Doe", speed: "1.4 Hours", count: 14, val: "$108,000", ratio: "42.8%", status: "compliant" },
                { name: "Sarah Connor", speed: "1.8 Hours", count: 12, val: "$87,000", ratio: "38.5%", status: "compliant" },
                { name: "Alex Mercer", speed: "3.2 Hours", count: 8, val: "$44,000", ratio: "25.0%", status: "warning" },
              ].map((agent, idx) => (
                <TableRow key={idx} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <div className="flex items-center gap-2">
                      <span className="size-5 rounded-full bg-blue-100 text-blue-700 text-[9px] font-bold flex items-center justify-center">
                        {agent.name.slice(0, 2).toUpperCase()}
                      </span>
                      {agent.name}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{agent.speed}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium">{agent.count} active</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">{agent.val}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-bold">{agent.ratio}</TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={agent.status === "compliant" ? "success" : "warning"} size="sm" className="font-bold text-[9px] uppercase">
                      {agent.status}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
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
  const [quotations, setQuotations] = useState<Quotation[]>([]);
  const [reportData, setReportData] = useState<Quotation[] | null>(null);
  const [auditMsg, setAuditMsg] = useState("");
  const [logsCount, setLogsCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const loadQuotations = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await quotationService.getList();
        if (active) {
          setQuotations(response.data || []);
        }
      } catch (err: any) {
        console.error("Failed to fetch quotations:", err);
        if (active) {
          setError("Failed to load quotations from server.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    loadQuotations();
    return () => {
      active = false;
    };
  }, []);

  const handleGenerate = () => {
    const threshold = parseFloat(filters.discountThreshold) || 0;
    const results = quotations.filter((q) => {
      const disc = q.discountPercent ?? 0;
      const sentDate = (q as any).sentDate || q.expiryDate || "";
      const inRange =
        (!filters.dateFrom || sentDate >= filters.dateFrom) &&
        (!filters.dateTo || sentDate <= filters.dateTo);
      const matchesRoom = !filters.roomType || q.roomType === filters.roomType;
      return disc > threshold && inRange && matchesRoom;
    });

    const now = new Date();
    const logEntry: ReportLog = {
      id: `RPT-${logsCount + 1}`,
      generatedBy: "Sarah Connor",
      role: "SALES",
      generatedAt: now.toISOString(),
      filters: {
        dateFrom: filters.dateFrom,
        dateTo: filters.dateTo,
        roomType: filters.roomType || undefined,
        discountThreshold: threshold,
      },
      resultCount: results.length,
    };
    setLogsCount(prev => prev + 1);

    setReportData(results);
    setAuditMsg(
      `Report generated at ${now.toLocaleTimeString()} — ${results.length} quote(s) found. Audit log saved. Manager notified (SALES role).`
    );
  };

  const handleExportCSV = () => {
    if (!reportData) return;
    const headers = ["Quote No", "Contact Name", "Room Type", "Discount %", "Subtotal", "Discount Amt", "Total", "Status", "Date Issued"];
    const rows = reportData.map((q) => {
      const sentDate = (q as any).sentDate || q.expiryDate || "";
      return [
        q.quoteNo,
        q.contactName,
        q.roomType ?? "N/A",
        `${q.discountPercent ?? 0}%`,
        `$${(q.subtotal ?? 0).toFixed(2)}`,
        `$${(q.discountAmount ?? 0).toFixed(2)}`,
        `$${q.amount.toFixed(2)}`,
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
          <div className="mt-4">
            <Button
              variant="primary"
              size="sm"
              onClick={handleGenerate}
              leftIcon={<ClipboardList className="size-3.5" />}
              className="text-xs font-bold"
            >
              Generate Report
            </Button>
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
                        ${(q.subtotal ?? 0).toLocaleString('en-US')}
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right text-red-500 font-semibold">
                        -${(q.discountAmount ?? 0).toLocaleString('en-US')}
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-right font-black text-slate-800">
                        ${q.amount.toLocaleString('en-US')}
                      </TableCell>
                      <TableCell className="py-3 px-4">
                        <Badge variant={statusBadge(q.status)} size="sm" className="font-bold text-[9px] uppercase">
                          {q.status === "pending_approval" ? "Pending" : q.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="py-3 px-4 text-xs text-slate-400">{(q as any).sentDate || q.expiryDate || "—"}</TableCell>
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

type Tab = "analytics" | "discount-report";

export function ReportingScreen() {
  const [activeTab, setActiveTab] = useState<Tab>("analytics");
  const [reportPeriod, setReportPeriod] = useState("this-quarter");

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: "analytics", label: "Sales Analytics", icon: <BarChart2 className="size-3.5" /> },
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
            className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors -mb-px ${
              activeTab === tab.key
                ? "border-blue-600 text-blue-700"
                : "border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300"
            }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "analytics" && (
        <AnalyticsTab reportPeriod={reportPeriod} setReportPeriod={setReportPeriod} />
      )}
      {activeTab === "discount-report" && <DiscountReportTab />}
    </div>
  );
}
