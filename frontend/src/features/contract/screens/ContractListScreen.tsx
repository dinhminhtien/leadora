"use client";

import React, { useState, useMemo, useEffect } from "react";
import { useConfirm } from "@/components/ui/confirm-dialog";
import {
  FileText,
  Search,
  CheckCircle2,
  Calendar,
  Send,
  Building2,
  Archive,
  ChevronDown,
  ChevronUp,
  ListFilter,
  CreditCard,
  Ban,
  Copy,
  ExternalLink,
  RefreshCw,
  X,
  FileSpreadsheet,
} from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Portal } from "@/components/ui/Portal";
import { DataTable, TablePagination, type ColumnDef } from "@/components/ui/data-table";
import {
  ColumnPicker,
  ExportMenu,
  RefreshButton,
  useTableControls,
} from "@/components/ui/table-controls";
import { DensityMenu } from "@/components/ui/list-toolbar";
import {
  useContracts,
  useUpdateContractBillingMethod,
  useSendContract,
  useCancelContract,
  useResendContract,
  useRegenerateContract,
} from "@/features/contract/hooks/use_contracts";
import { type Contract, type BillingMethod } from "@/services/contract_service";
import { useAuthStore } from "@/stores/auth_store";
import { useHighlightRow } from "@/shared/hooks/use_highlight_row";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { toast } from "@/stores/toast_store";

export function ContractListScreen() {
  const { data: serverContracts = [], isLoading, isFetching, refetch } = useContracts();
  const { user } = useAuthStore();
  const { highlightedId, setRowRef } = useHighlightRow();

  const updateBillingMethodMutation = useUpdateContractBillingMethod();
  const sendContractMutation = useSendContract();
  const cancelContractMutation = useCancelContract();
  const resendContractMutation = useResendContract();
  const regenerateContractMutation = useRegenerateContract();

  const { confirm, confirmElement } = useConfirm();

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [billingFilter, setBillingFilter] = useState("");
  const [detailTarget, setDetailTarget] = useState<Contract | null>(null);
  const [billingTarget, setBillingTarget] = useState<Contract | null>(null);
  const [selectedBillingMethod, setSelectedBillingMethod] = useState<BillingMethod>("BANK_TRANSFER");
  const [actionMenuOpenId, setActionMenuOpenId] = useState<string | null>(null);

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Sorting
  const [sortField, setSortField] = useState<"createdAt" | "code" | null>("createdAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  // Keep detail target updated if server state changes
  const activeDetail = useMemo(() => {
    if (!detailTarget) return null;
    return serverContracts.find((c) => c.id === detailTarget.id) || detailTarget;
  }, [serverContracts, detailTarget]);

  const filteredContracts = useMemo(() => {
    return serverContracts.filter((c) => {
      const matchesStatus = !statusFilter || c.status === statusFilter;
      const matchesBilling = !billingFilter || c.billingMethod === billingFilter;
      const matchesSearch =
        c.contractCode.toLowerCase().includes(search.toLowerCase()) ||
        (c.customerName || "").toLowerCase().includes(search.toLowerCase()) ||
        (c.dealName || "").toLowerCase().includes(search.toLowerCase());
      return matchesStatus && matchesBilling && matchesSearch;
    });
  }, [serverContracts, search, statusFilter, billingFilter]);

  useEffect(() => {
    setCurrentPage(1);
  }, [search, statusFilter, billingFilter]);

  const sortedContracts = useMemo(() => {
    if (!sortField) return filteredContracts;
    const dirMul = sortDir === "asc" ? 1 : -1;
    return [...filteredContracts].sort((a, b) => {
      if (sortField === "createdAt") {
        return (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()) * dirMul;
      }
      return a.contractCode.localeCompare(b.contractCode) * dirMul;
    });
  }, [filteredContracts, sortField, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sortedContracts.length / pageSize));
  const paginatedContracts = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sortedContracts.slice(start, start + pageSize);
  }, [sortedContracts, currentPage]);

  const handleSendContract = async (id: string) => {
    try {
      await sendContractMutation.mutateAsync(id);
      setActionMenuOpenId(null);
    } catch (err) {
      console.error("Failed to send contract", err);
    }
  };

  const handleCancelContract = async (id: string) => {
    const { ok } = await confirm({
      title: "Cancel this contract?",
      description: "The contract will be marked as CANCELLED. The quotation will remain active and a new contract can be generated if needed.",
      severity: "danger",
      confirmLabel: "Yes, Cancel Contract",
    });
    if (!ok) return;
    try {
      await cancelContractMutation.mutateAsync(id);
      setActionMenuOpenId(null);
    } catch (err) {
      console.error("Failed to cancel contract", err);
    }
  };

  const handleResendContract = async (id: string) => {
    const { ok } = await confirm({
      title: "Resend contract email?",
      description: "A fresh secure link will be generated and emailed to the customer. The previous link will be invalidated immediately.",
      severity: "warning",
      confirmLabel: "Yes, Resend",
    });
    if (!ok) return;
    try {
      await resendContractMutation.mutateAsync(id);
    } catch (err) {
      console.error("Failed to resend contract", err);
    }
  };

  const handleRegenerateContract = async (id: string) => {
    const { ok } = await confirm({
      title: "Generate a new contract?",
      description: "A fresh DRAFT contract (next version) will be created for the same quotation. You can then send it to the customer.",
      severity: "info",
      confirmLabel: "Yes, Generate",
    });
    if (!ok) return;
    try {
      await regenerateContractMutation.mutateAsync(id);
      setDetailTarget(null);
    } catch (err) {
      console.error("Failed to regenerate contract", err);
    }
  };

  const handleUpdateBillingMethod = async () => {
    if (!billingTarget) return;
    try {
      await updateBillingMethodMutation.mutateAsync({
        id: billingTarget.id,
        billingMethod: selectedBillingMethod,
      });
      setBillingTarget(null);
    } catch (err) {
      console.error("Failed to update billing method", err);
    }
  };

  const copySecurePortalLink = (contract: Contract) => {
    // Generate a simulated public token or read it if available
    const token = "CLIENT_SECURED_OTP_TOKEN"; // Fallback representation
    const link = `${window.location.origin}/portal/contracts/${contract.id}?token=${token}`;
    navigator.clipboard.writeText(link);
    toast.success("Public portal link copied to clipboard!");
  };

  const statusBadgeVariant = (status: Contract["status"]): "success" | "warning" | "danger" | "info" | "primary" | "default" => {
    switch (status) {
      case "ACTIVE":
        return "success";
      case "ACKNOWLEDGED":
        return "info";
      case "SENT":
        return "primary";
      case "DRAFT":
        return "warning";
      case "CANCELLED":
      case "EXPIRED":
        return "danger";
      case "SUPERSEDED":
        return "default";
      default:
        return "default";
    }
  };

  const contractColumns: ColumnDef<Contract>[] = useMemo(() => [
    {
      id: "code",
      header: "Contract Code",
      sticky: "left",
      cell: (c) => (
        <span className="flex items-center gap-1.5 text-xs font-bold text-blue-600">
          <FileText className="size-3.5 text-slate-400" />
          {c.contractCode}
        </span>
      ),
    },
    {
      id: "client",
      header: "Customer",
      className: "text-xs font-semibold text-slate-800",
      cell: (c) => c.customerName || "—",
    },
    {
      id: "deal",
      header: "Linked Deal",
      className: "text-xs text-slate-500",
      cell: (c) => c.dealName || "—",
    },
    {
      id: "customerType",
      header: "Type Snapshot",
      cell: (c) => (
        <Badge variant="default" size="sm" className="font-bold text-[10px]">
          {c.customerTypeSnapshot}
        </Badge>
      ),
    },
    {
      id: "billingMethod",
      header: "Billing Method",
      className: "text-xs font-medium text-slate-600",
      cell: (c) => c.billingMethod.replace("_", " "),
    },
    {
      id: "version",
      header: "Version",
      className: "text-center text-xs font-medium text-slate-500",
      cell: (c) => `v${c.version}`,
    },
    {
      id: "status",
      header: "Status",
      cell: (c) => (
        <Badge variant={statusBadgeVariant(c.status)} size="sm" className="font-bold text-[10px]">
          {c.status}
        </Badge>
      ),
    },
    {
      id: "actions",
      header: "",
      sticky: "right",
      cell: (c) => (
        <div
          className="flex items-center justify-end gap-1.5"
          onClick={(e) => e.stopPropagation()}
        >
          {c.status === "DRAFT" && (
            <Button
              variant="secondary"
              size="xs"
              leftIcon={<CreditCard className="size-3" />}
              onClick={() => {
                setBillingTarget(c);
                setSelectedBillingMethod(c.billingMethod);
              }}
            >
              Billing
            </Button>
          )}

          {c.status === "DRAFT" && (
            <Button
              variant="primary"
              size="xs"
              isLoading={sendContractMutation.isPending}
              leftIcon={<Send className="size-3" />}
              onClick={() => handleSendContract(c.id)}
            >
              Send
            </Button>
          )}

          {["CANCELLED", "EXPIRED"].includes(c.status) && (
            <Button
              variant="secondary"
              size="xs"
              isLoading={regenerateContractMutation.isPending}
              leftIcon={<FileSpreadsheet className="size-3" />}
              onClick={() => handleRegenerateContract(c.id)}
            >
              New Contract
            </Button>
          )}

          {["DRAFT", "SENT", "ACKNOWLEDGED"].includes(c.status) && (
            <Button
              variant="danger"
              size="xs"
              leftIcon={<Ban className="size-3" />}
              onClick={() => handleCancelContract(c.id)}
            >
              Cancel
            </Button>
          )}

          {c.status === "SENT" && (
            <Button
              variant="secondary"
              size="xs"
              isLoading={resendContractMutation.isPending}
              leftIcon={<RefreshCw className="size-3" />}
              onClick={() => handleResendContract(c.id)}
            >
              Resend
            </Button>
          )}

          {c.status === "SENT" && (
            <Button
              variant="secondary"
              size="xs"
              leftIcon={<Copy className="size-3" />}
              onClick={() => copySecurePortalLink(c)}
            >
              Portal Link
            </Button>
          )}
        </div>
      ),
    },
  ], [sendContractMutation.isPending]);

  const controls = useTableControls<Contract>("contracts", contractColumns, {
    defaultSortBy: "createdAt",
  });

  return (
    <div className="space-y-6">
      <PageHeader
        {...PAGE_META.contracts}
        actions={
          <Button
            variant="secondary"
            onClick={() => refetch()}
            isLoading={isFetching}
            leftIcon={<RefreshCw className="size-4" />}
          >
            Refresh
          </Button>
        }
      />

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search contract code, client name, deal..."
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
                <option value="">All Statuses</option>
                <option value="DRAFT">DRAFT</option>
                <option value="SENT">SENT</option>
                <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="CANCELLED">CANCELLED</option>
                <option value="SUPERSEDED">SUPERSEDED</option>
                <option value="EXPIRED">EXPIRED</option>
              </select>
            </div>

            <div className="flex items-center gap-1.5 text-slate-400">
              <select
                value={billingFilter}
                onChange={(e) => setBillingFilter(e.target.value)}
                className="py-1.5 pl-2 pr-6 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-700 focus:outline-none focus:border-blue-500 focus:bg-white transition appearance-none"
              >
                <option value="">All Billing Methods</option>
                <option value="CREDIT_CARD">Credit Card</option>
                <option value="BANK_TRANSFER">Bank Transfer</option>
                <option value="CASH">Cash</option>
                <option value="DIRECT_BILLING">Direct Billing</option>
              </select>
            </div>

            {(search || statusFilter || billingFilter) && (
              <button
                type="button"
                onClick={() => {
                  setSearch("");
                  setStatusFilter("");
                  setBillingFilter("");
                }}
                className="text-[10px] text-slate-400 hover:text-slate-600 underline"
              >
                Clear filters
              </button>
            )}

            <div className="ml-auto flex items-center gap-2">
              <RefreshButton onRefresh={() => refetch()} isRefreshing={isFetching} />
              <ColumnPicker
                columns={contractColumns}
                hiddenIds={controls.hiddenColumnIds}
                onChange={controls.setHiddenColumnIds}
                requiredIds={["code", "actions"]}
              />
              <DensityMenu value={controls.density} onChange={controls.setDensity} />
            </div>
          </div>
        </CardContent>
      </Card>

      <DataTable
        label="Contracts"
        rows={paginatedContracts}
        columns={controls.visibleColumns}
        rowId={(c) => c.id}
        isLoading={isLoading}
        density={controls.density}
        sortBy={sortField ?? undefined}
        sortDir={sortDir}
        onSortChange={(columnId) => {
          if (columnId === "code" || columnId === "createdAt") {
            const nextDir = sortField === columnId && sortDir === "asc" ? "desc" : "asc";
            setSortField(columnId as any);
            setSortDir(nextDir);
          }
        }}
        highlightId={highlightedId}
        onRowClick={(c) => setDetailTarget(c)}
        bulkActions={null}
        emptyTitle="No contracts found"
        emptyMessage="Contracts are generated automatically when a customer quotation response is tracked as ACCEPTED."
        footer={
          <TablePagination
            page={currentPage - 1}
            pageSize={pageSize}
            totalElements={filteredContracts.length}
            totalPages={totalPages}
            onPageChange={(p) => setCurrentPage(p + 1)}
            onPageSizeChange={(s) => {
              setPageSize(s);
              setCurrentPage(1);
            }}
            pageSizeOptions={[10, 20, 50]}
          />
        }
      />

      {/* Contract Detail Drawer (Aligned with Deal Drawer pattern) */}
      {activeDetail && (
        <Portal>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => setDetailTarget(null)}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-xl bg-white dark:bg-zinc-900 shadow-2xl border-l border-slate-200 dark:border-zinc-800 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/50">
              <div>
                <h3 className="text-sm font-bold text-slate-800 dark:text-zinc-100 flex items-center gap-2">
                  <FileText className="size-4.5 text-[#185FA5]" />
                  Contract Details & Terms
                </h3>
                <p className="text-[10px] text-slate-400 dark:text-zinc-500 mt-0.5">
                  Agreement {activeDetail.contractCode} · Version {activeDetail.version} · Created {new Date(activeDetail.createdAt).toLocaleDateString()}
                </p>
              </div>
              <button
                onClick={() => setDetailTarget(null)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 dark:hover:bg-zinc-800 hover:text-slate-600 dark:hover:text-zinc-200 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Status Section */}
              <div className="bg-slate-50 dark:bg-zinc-800/50 p-4 rounded-xl flex items-center justify-between border border-slate-100 dark:border-zinc-800">
                <div>
                  <span className="text-[10px] text-slate-400 dark:text-zinc-500 font-semibold block uppercase tracking-wider">Current Status</span>
                  <span className="mt-1 block">
                    <Badge variant={statusBadgeVariant(activeDetail.status)} className="font-bold px-2 py-0.5 text-xs">
                      {activeDetail.status}
                    </Badge>
                  </span>
                </div>
                <div className="text-right">
                  <span className="text-[10px] text-slate-400 dark:text-zinc-500 font-semibold block uppercase tracking-wider">Billing Method</span>
                  <span className="text-sm font-bold text-slate-800 dark:text-zinc-100 mt-1 block">
                    {activeDetail.billingMethod.replace("_", " ")}
                  </span>
                </div>
              </div>

              {/* Commercial terms snapshot */}
              <div className="space-y-3">
                <h3 className="text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Commercial Terms Snapshot</h3>
                <div className="border border-slate-100 dark:border-zinc-800 rounded-xl overflow-hidden shadow-xs bg-white dark:bg-zinc-900">
                  <table className="min-w-full divide-y divide-slate-100 dark:divide-zinc-800 text-xs">
                    <tbody className="divide-y divide-slate-100 dark:divide-zinc-800">
                      <tr>
                        <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50 w-1/3">Client Name</td>
                        <td className="px-4 py-3 font-medium text-slate-800 dark:text-zinc-200">{activeDetail.customerName || "—"}</td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50">Customer Type Snapshot</td>
                        <td className="px-4 py-3 font-medium text-slate-800 dark:text-zinc-200">{activeDetail.customerTypeSnapshot}</td>
                      </tr>
                      {activeDetail.contactName && (
                        <tr>
                          <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50">Primary Contact</td>
                          <td className="px-4 py-3 font-medium text-slate-800 dark:text-zinc-200">{activeDetail.contactName}</td>
                        </tr>
                      )}
                      <tr>
                        <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50">Check-in Date</td>
                        <td className="px-4 py-3 font-medium text-slate-800 dark:text-zinc-200">
                          {activeDetail.commercialSnapshot?.checkInDate || "—"}
                        </td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50">Check-out Date</td>
                        <td className="px-4 py-3 font-medium text-slate-800 dark:text-zinc-200">
                          {activeDetail.commercialSnapshot?.checkOutDate || "—"}
                        </td>
                      </tr>
                      <tr>
                        <td className="px-4 py-3 font-semibold text-slate-500 dark:text-zinc-400 bg-slate-50 dark:bg-zinc-800/50">Total Amount</td>
                        <td className="px-4 py-3 font-bold text-slate-900 dark:text-zinc-100">
                          {activeDetail.commercialSnapshot?.totalAmount?.toLocaleString("vi-VN")} ₫
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Document / PDF Status */}
              <div className="space-y-3">
                <h3 className="text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Legal Document (PDF)</h3>
                <div className="border border-slate-100 dark:border-zinc-800 rounded-xl p-4 flex items-center justify-between shadow-xs bg-white dark:bg-zinc-900">
                  <div className="flex items-center gap-3">
                    <div className="p-2.5 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 rounded-lg">
                      <FileText className="size-6" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-slate-800 dark:text-zinc-200 block">Contract Document File</span>
                      <span className="text-[10px] text-slate-400 dark:text-zinc-500 block mt-0.5">
                        PDF Status: <span className="font-semibold text-slate-600 dark:text-zinc-300">{activeDetail.pdfStatus}</span>
                      </span>
                    </div>
                  </div>
                  {activeDetail.pdfUrl ? (
                    <a
                      href={activeDetail.pdfUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="flex items-center gap-1.5 text-xs font-bold text-blue-600 dark:text-blue-400 hover:text-blue-700"
                    >
                      View Document
                      <ExternalLink className="size-3.5" />
                    </a>
                  ) : (
                    <span className="text-xs text-slate-400 italic">No PDF generated yet.</span>
                  )}
                </div>
              </div>

              {/* Cancelled / Expired — regeneration callout */}
              {["CANCELLED", "EXPIRED"].includes(activeDetail.status) && (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Contract Recovery</h3>
                  <div className="border border-red-100 dark:border-red-900/50 bg-red-50/60 dark:bg-red-950/20 rounded-xl p-4 space-y-3">
                    <p className="text-xs text-red-700 dark:text-red-300 leading-relaxed">
                      This contract is <span className="font-bold">{activeDetail.status}</span>. The quotation is still active and can have a new contract generated. This will create a fresh <span className="font-bold">DRAFT v{(activeDetail.version ?? 0) + 1}</span> linked to the same quotation.
                    </p>
                    <Button
                      variant="primary"
                      size="sm"
                      isLoading={regenerateContractMutation.isPending}
                      leftIcon={<FileSpreadsheet className="size-3.5" />}
                      onClick={() => handleRegenerateContract(activeDetail.id)}
                    >
                      Generate New Contract
                    </Button>
                  </div>
                </div>
              )}

              {/* Public Secure OTP Link */}
              {activeDetail.status === "SENT" && (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-slate-700 dark:text-zinc-300 uppercase tracking-wider">Public Client Verification</h3>
                  <div className="border border-slate-100 dark:border-zinc-800 bg-blue-50/50 dark:bg-blue-950/20 rounded-xl p-4 space-y-3">
                    <p className="text-xs text-slate-600 dark:text-zinc-300 leading-relaxed">
                      Provide this secure confirmation link to the customer. They can click the link, inspect the contract details, and acknowledge the terms securely with an OTP code sent directly to their email.
                    </p>
                    <div className="flex gap-2 flex-wrap">
                      <Button
                        variant="secondary"
                        size="sm"
                        leftIcon={<Copy className="size-3.5" />}
                        className="bg-white dark:bg-zinc-800 hover:bg-slate-50 dark:hover:bg-zinc-700 border border-slate-200 dark:border-zinc-700 text-slate-700 dark:text-zinc-200"
                        onClick={() => copySecurePortalLink(activeDetail)}
                      >
                        Copy Secured Link
                      </Button>
                      <Button
                        variant="primary"
                        size="sm"
                        isLoading={resendContractMutation.isPending}
                        leftIcon={<RefreshCw className="size-3.5" />}
                        onClick={() => handleResendContract(activeDetail.id)}
                      >
                        Resend Email
                      </Button>
                      <a
                        href={`${window.location.origin}/portal/contracts/${activeDetail.id}?token=CLIENT_SECURED_OTP_TOKEN`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-zinc-200 shadow-xs transition hover:bg-slate-50 dark:hover:bg-zinc-700"
                      >
                        Open Portal
                        <ExternalLink className="ml-1.5 size-3.5" />
                      </a>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Drawer Footer */}
            <div className="flex justify-end gap-2 p-4 border-t border-slate-100 dark:border-zinc-800 bg-slate-50/50 dark:bg-zinc-800/50">
              <Button variant="secondary" size="sm" onClick={() => setDetailTarget(null)}>
                Close
              </Button>
            </div>
          </div>
        </Portal>
      )}

      {/* Edit Billing Method Modal (Styled to match Deal Modal) */}
      {billingTarget && (
        <Portal>
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4 animate-in fade-in duration-200"
            onClick={() => setBillingTarget(null)}
          >
            <div
              className="w-full max-w-md rounded-2xl bg-white dark:bg-zinc-900 p-6 shadow-2xl border border-slate-100 dark:border-zinc-800 animate-in zoom-in-95 duration-200"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-zinc-800 mb-4">
                <h3 className="text-sm font-bold text-slate-800 dark:text-zinc-100 flex items-center gap-2">
                  <CreditCard className="size-4.5 text-[#185FA5]" />
                  Update Billing Method
                </h3>
                <button
                  onClick={() => setBillingTarget(null)}
                  className="p-1 rounded-full text-slate-400 hover:bg-slate-100 dark:hover:bg-zinc-800 hover:text-slate-600 transition"
                >
                  <X className="size-4" />
                </button>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="text-[11px] font-semibold text-slate-500 dark:text-zinc-400 uppercase block mb-1.5">
                    Select Payment / Billing Method
                  </label>
                  <select
                    value={selectedBillingMethod}
                    onChange={(e) => setSelectedBillingMethod(e.target.value as BillingMethod)}
                    className="w-full py-2 px-3 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800 text-xs text-slate-800 dark:text-zinc-100 focus:outline-none focus:border-[#185FA5] focus:bg-white dark:focus:bg-zinc-900 transition"
                  >
                    <option value="BANK_TRANSFER">Bank Transfer</option>
                    <option value="CREDIT_CARD">Credit Card</option>
                    <option value="CASH">Cash</option>
                    <option value="DIRECT_BILLING">Direct Billing</option>
                  </select>
                </div>
                <div className="flex justify-end gap-2 pt-3 border-t border-slate-100 dark:border-zinc-800">
                  <Button variant="secondary" size="sm" onClick={() => setBillingTarget(null)}>
                    Cancel
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    isLoading={updateBillingMethodMutation.isPending}
                    onClick={handleUpdateBillingMethod}
                  >
                    Save Changes
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </Portal>
      )}

      {confirmElement}
    </div>
  );
}
