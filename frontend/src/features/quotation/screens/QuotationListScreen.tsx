"use client";

import React, { useState, useMemo } from "react";
import Link from "next/link";
import { FileSpreadsheet, Search, CheckCircle2, Calendar, Plus, Clock, XCircle, RotateCcw, Send, GitBranch, MessageSquare, Sparkles, Building2, Archive, TimerOff, ChevronDown, ChevronUp, ListFilter, Bell } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { SendQuotationModal } from "@/features/quotation/components/SendQuotationModal";
import { RecordResponseModal } from "@/features/quotation/components/RecordResponseModal";
import { ConvertToBookingModal } from "@/features/quotation/components/ConvertToBookingModal";
import { ExpireCloseModal } from "@/features/quotation/components/ExpireCloseModal";
import { SlaStatusBadge } from "@/features/sla/components/SlaStatusBadge";
import { CreateReminderModal } from "@/features/reminder/components/CreateReminderModal";
import type { Quotation } from "@/services/quotation_service";
export type { Quotation } from "@/services/quotation_service";
import { useQuotations, useExpireOverdue, useSubmitQuotation } from "@/features/quotation/hooks/use_quotations";
import { useAuthStore } from "@/stores/auth_store";

const ACTIVE_STATUSES: Quotation["status"][] = [
  "draft", "pending_approval", "approved", "sent", "accepted", "interested", "pending_revision",
];
const DONE_STATUSES: Quotation["status"][] = ["converted", "closed", "expired", "rejected"];

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
  const { data: serverQuotes = [], isLoading } = useQuotations();
  const { user } = useAuthStore();
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
  const [search, setSearch] = useState("");
  const [sendTarget, setSendTarget] = useState<Quotation | null>(null);
  const [responseTarget, setResponseTarget] = useState<Quotation | null>(null);
  const [convertTarget, setConvertTarget] = useState<Quotation | null>(null);
  const [closeTarget, setCloseTarget] = useState<Quotation | null>(null);
  const [autoExpireResult, setAutoExpireResult] = useState<number | null>(null);
  const [showClosureLog, setShowClosureLog] = useState(false);
  const [reminderTarget, setReminderTarget] = useState<Quotation | null>(null);
  const [activeTab, setActiveTab] = useState<"active" | "done">("active");
  const [statusFilter, setStatusFilter] = useState<string>("");

  const handleTabChange = (tab: "active" | "done") => {
    setActiveTab(tab);
    setStatusFilter("");
  };

  const activeCount = useMemo(() => quotes.filter((q) => ACTIVE_STATUSES.includes(q.status)).length, [quotes]);
  const doneCount = useMemo(() => quotes.filter((q) => DONE_STATUSES.includes(q.status)).length, [quotes]);

  const filteredQuotes = useMemo(() => {
    const tabStatuses = activeTab === "active" ? ACTIVE_STATUSES : DONE_STATUSES;
    return quotes.filter((q) => {
      const matchesTab = tabStatuses.includes(q.status);
      const matchesStatus = !statusFilter || q.status === statusFilter;
      const matchesSearch =
        q.quoteNo.toLowerCase().includes(search.toLowerCase()) ||
        q.contactName.toLowerCase().includes(search.toLowerCase()) ||
        q.dealName.toLowerCase().includes(search.toLowerCase());
      return matchesTab && matchesStatus && matchesSearch;
    });
  }, [quotes, search, activeTab, statusFilter]);

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
    if (status === "accepted" || status === "approved" || status === "converted") return "success";
    if (status === "sent") return "primary";
    if (status === "expired" || status === "rejected" || status === "closed") return "danger";
    if (status === "pending_approval") return "warning";
    if (status === "pending_revision" || status === "interested") return "info";
    return "default";
  };

  const statusLabel = (status: Quotation["status"]) => {
    if (status === "pending_approval") return "Pending Approval";
    if (status === "pending_revision") return "Needs Revision";
    if (status === "interested") return "Interested";
    if (status === "converted") return "Converted";
    if (status === "closed") return "Closed";
    return status;
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-start gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Quotes &amp; Price Proposals</h1>
          <p className="text-xs text-slate-400">
            Generate, customize, and issue lodging room block or banquet buffet quotations
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="outline"
            size="sm"
            onClick={runAutoExpire}
            isLoading={expireOverdue.isPending}
            leftIcon={<TimerOff className="size-3.5" />}
            className="text-xs border-slate-200 text-slate-600 hover:bg-slate-50"
          >
            Auto-Expire Overdue
          </Button>
          <Link href={ROUTE_PATHS.quotationCreate}>
            <Button variant="primary" size="sm" leftIcon={<Plus className="size-3.5" />} className="text-xs font-bold">
              New Quotation
            </Button>
          </Link>
        </div>
      </div>

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
              ? "border-blue-600 text-blue-700"
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
            <div className="flex items-center gap-1.5 text-slate-400">
              <ListFilter className="size-3.5" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="py-1.5 pl-2 pr-6 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-700 focus:outline-none focus:border-blue-500 focus:bg-white transition appearance-none"
              >
                <option value="">All Status</option>
                {(activeTab === "active" ? ACTIVE_STATUSES : DONE_STATUSES).map((s) => (
                  <option key={s} value={s}>
                    {statusLabel(s)}
                  </option>
                ))}
              </select>
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
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Quote Reference</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Client Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Linked Deal</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Total</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Valid Until</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">SLA</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow hoverable={false}>
                <TableCell colSpan={8} className="py-8 text-center text-slate-400 text-xs">
                  Loading quotations...
                </TableCell>
              </TableRow>
            ) : filteredQuotes.length > 0 ? (
              filteredQuotes.map((q) => (
                <TableRow key={q.id} className={`border-b border-slate-100 transition ${DONE_STATUSES.includes(q.status) ? "opacity-60 hover:opacity-100 bg-slate-50/40 hover:bg-slate-50/80" : "hover:bg-slate-50/70"}`}>
                  <TableCell className="py-3 px-4 text-xs font-bold text-blue-600">
                    <span className="flex items-center gap-1.5">
                      <FileSpreadsheet className="size-3.5 text-slate-400" />
                      {q.quoteNo}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-700">{q.contactName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{q.dealName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">
                    ${q.amount.toLocaleString('en-US')}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-semibold">
                    <span className="flex items-center gap-1">
                      <Calendar className="size-3 text-slate-400" />
                      {q.expiryDate}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={statusBadgeVariant(q.status)} size="sm" className="font-bold text-[9px] uppercase">
                      {statusLabel(q.status)}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <SlaStatusBadge entityId={q.id} entityType="QUOTATION" />
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <div className="flex flex-col items-center gap-1.5">
                    {q.status === "approved" ? (
                      // UC-14.4: Send to Customer button
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => setSendTarget(q)}
                        leftIcon={<Send className="size-3" />}
                        className="px-2.5 py-1 text-[10px] font-bold"
                      >
                        Send to Customer
                      </Button>
                    ) : q.status === "sent" ? (
                      <div className="flex justify-center items-center gap-1.5 flex-wrap">
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => setResponseTarget(q)}
                          leftIcon={<MessageSquare className="size-3" />}
                          className="px-2.5 py-1 text-[10px] font-bold"
                        >
                          Record Response
                        </Button>
                        <Link href={ROUTE_PATHS.quotationRevise(q.id)}>
                          <Button
                            variant="outline"
                            size="sm"
                            leftIcon={<GitBranch className="size-3" />}
                            className="px-2.5 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50"
                          >
                            Revise
                          </Button>
                        </Link>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCloseTarget(q)}
                          title="Close quotation"
                          className="px-1.5 py-1 text-[10px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                        >
                          <Archive className="size-3" />
                        </Button>
                      </div>
                    ) : q.status === "accepted" ? (
                      // UC-14.7: Convert to Booking
                      <Button
                        variant="success"
                        size="sm"
                        onClick={() => setConvertTarget(q)}
                        leftIcon={<Building2 className="size-3" />}
                        className="px-2.5 py-1 text-[10px] font-bold bg-emerald-600 hover:bg-emerald-700 text-white"
                      >
                        Convert to Booking
                      </Button>
                    ) : q.status === "converted" ? (
                      <span className="text-[10px] text-emerald-600 font-bold flex items-center justify-center gap-1">
                        <CheckCircle2 className="size-3.5" /> Converted
                      </span>
                    ) : q.status === "rejected" ? (
                      <div className="flex flex-col items-center gap-1.5">
                        <span className="text-[10px] text-red-500 font-bold flex items-center justify-center gap-1">
                          <XCircle className="size-3.5" /> Rejected
                        </span>
                        <div className="flex items-center gap-1">
                          <Link href={ROUTE_PATHS.quotationRevise(q.id)}>
                            <Button
                              variant="outline"
                              size="sm"
                              leftIcon={<GitBranch className="size-3" />}
                              className="px-2 py-0.5 text-[9px] border-slate-200 text-slate-500 hover:bg-slate-50"
                            >
                              Revise
                            </Button>
                          </Link>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setCloseTarget(q)}
                            title="Close quotation"
                            className="px-1.5 py-0.5 text-[9px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                          >
                            <Archive className="size-3" />
                          </Button>
                        </div>
                      </div>
                    ) : q.status === "pending_approval" ? (
                      <div className="flex justify-center items-center gap-1.5">
                        <span className="text-[10px] text-amber-600 font-bold flex items-center gap-1">
                          <Clock className="size-3.5" /> Awaiting Manager
                        </span>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCloseTarget(q)}
                          title="Close quotation"
                          className="px-1.5 py-1 text-[10px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                        >
                          <Archive className="size-3" />
                        </Button>
                      </div>
                    ) : q.status === "pending_revision" ? (
                      <div className="flex justify-center items-center gap-1.5">
                        <Link href={ROUTE_PATHS.quotationRevise(q.id)}>
                          <Button
                            variant="outline"
                            size="sm"
                            leftIcon={<RotateCcw className="size-3" />}
                            className="px-2.5 py-1 text-[10px] border-blue-200 text-blue-600 hover:bg-blue-50"
                          >
                            Revise
                          </Button>
                        </Link>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCloseTarget(q)}
                          title="Close quotation"
                          className="px-1.5 py-1 text-[10px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                        >
                          <Archive className="size-3" />
                        </Button>
                      </div>
                    ) : q.status === "draft" ? (
                      <div className="flex justify-center items-center gap-1.5 flex-wrap">
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleSubmitDraft(q)}
                          isLoading={submittingId === q.id}
                          leftIcon={<CheckCircle2 className="size-3" />}
                          className="px-2.5 py-1 text-[10px] font-bold"
                        >
                          Submit
                        </Button>
                        <Link href={ROUTE_PATHS.quotationRevise(q.id)}>
                          <Button
                            variant="outline"
                            size="sm"
                            leftIcon={<GitBranch className="size-3" />}
                            className="px-2.5 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50"
                          >
                            Revise
                          </Button>
                        </Link>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCloseTarget(q)}
                          title="Close quotation"
                          className="px-1.5 py-1 text-[10px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                        >
                          <Archive className="size-3" />
                        </Button>
                      </div>
                    ) : q.status === "interested" ? (
                      <div className="flex justify-center items-center gap-1.5 flex-wrap">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setResponseTarget(q)}
                          leftIcon={<Sparkles className="size-3" />}
                          className="px-2.5 py-1 text-[10px] border-blue-200 text-blue-600 hover:bg-blue-50 font-bold"
                        >
                          Update Response
                        </Button>
                        <Link href={ROUTE_PATHS.quotationRevise(q.id)}>
                          <Button
                            variant="outline"
                            size="sm"
                            leftIcon={<GitBranch className="size-3" />}
                            className="px-2.5 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50"
                          >
                            Revise
                          </Button>
                        </Link>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setCloseTarget(q)}
                          title="Close quotation"
                          className="px-1.5 py-1 text-[10px] border-slate-200 text-slate-400 hover:border-red-200 hover:text-red-500 hover:bg-red-50"
                        >
                          <Archive className="size-3" />
                        </Button>
                      </div>
                    ) : q.status === "expired" ? (
                      <span className="text-[10px] text-red-400 font-semibold flex items-center justify-center gap-1">
                        <TimerOff className="size-3.5" /> Expired
                      </span>
                    ) : q.status === "closed" ? (
                      <span className="text-[10px] text-slate-400 font-semibold flex items-center justify-center gap-1">
                        <Archive className="size-3.5" /> Closed
                      </span>
                    ) : (
                      <span className="text-[10px] text-slate-400 italic">—</span>
                    )}
                    <button
                      onClick={() => setReminderTarget(q)}
                      className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] text-slate-400 hover:text-blue-500 hover:bg-blue-50 transition"
                      title="Add Reminder"
                    >
                      <Bell className="size-2.5" /> Remind
                    </button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={8} className="py-8 text-center text-slate-400 text-xs">
                  {activeTab === "active"
                    ? (search || statusFilter ? "No quotations match your filters." : "No active quotations found.")
                    : (search || statusFilter ? "No quotations match your filters." : "No completed quotations found.")}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

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
