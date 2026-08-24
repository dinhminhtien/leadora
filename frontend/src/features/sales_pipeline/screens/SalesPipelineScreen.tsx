"use client";

import React, { useState, useMemo } from "react";
import { UserSelect } from "@/components/ui/UserSelect";
import { useMyProfile } from "@/features/profile/hooks/use_profile";
import {
  Briefcase,
  DollarSign,
  TrendingUp,
  ChevronLeft,
  ChevronRight,
  User,
  Sparkles,
  Search,
  CheckCircle2,
  AlertCircle,
  Loader2,
  X,
  RefreshCw,
  Calendar,
  Layers,
  Inbox,
  ArrowRight,
  FileSpreadsheet,
  FileCheck2,
  Handshake,
  CheckCircle,
  HelpCircle,
  SlidersHorizontal,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { dealService, type PipelineDealCardResponse, type Deal } from "@/services/deal_service";
import { userService as taskUserService, type UserSummary } from "@/services/follow_up_task_service";
import { useQueryClient } from "@tanstack/react-query";
import { DealWorkflowStepper } from "@/features/deal/components/DealWorkflowStepper";

interface StageConfig {
  label: string;
  dotColor: string;
  badgeBg: string;
  badgeText: string;
  headerBorder: string;
  columnAccent: string;
  dropzoneHighlight: string;
  icon: React.ReactNode;
}

const STAGE_CONFIG: Record<Deal["stage"], StageConfig> = {
  Inquiry: {
    label: "Inquiry",
    dotColor: "bg-slate-400",
    badgeBg: "bg-slate-100 dark:bg-zinc-800",
    badgeText: "text-slate-700 dark:text-zinc-300",
    headerBorder: "border-slate-300 dark:border-zinc-700",
    columnAccent: "bg-slate-400",
    dropzoneHighlight: "bg-slate-500/10 border-slate-400",
    icon: <HelpCircle className="size-3.5 text-slate-500" />,
  },
  Qualification: {
    label: "Qualification",
    dotColor: "bg-blue-500",
    badgeBg: "bg-blue-50 dark:bg-blue-950/50",
    badgeText: "text-blue-700 dark:text-blue-300",
    headerBorder: "border-blue-300 dark:border-blue-800",
    columnAccent: "bg-blue-500",
    dropzoneHighlight: "bg-blue-500/10 border-blue-500",
    icon: <Sparkles className="size-3.5 text-blue-500" />,
  },
  Proposal: {
    label: "Proposal",
    dotColor: "bg-amber-500",
    badgeBg: "bg-amber-50 dark:bg-amber-950/50",
    badgeText: "text-amber-700 dark:text-amber-300",
    headerBorder: "border-amber-300 dark:border-amber-800",
    columnAccent: "bg-amber-500",
    dropzoneHighlight: "bg-amber-500/10 border-amber-500",
    icon: <FileSpreadsheet className="size-3.5 text-amber-500" />,
  },
  Negotiation: {
    label: "Negotiation",
    dotColor: "bg-orange-500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/50",
    badgeText: "text-orange-700 dark:text-orange-300",
    headerBorder: "border-orange-300 dark:border-orange-800",
    columnAccent: "bg-orange-500",
    dropzoneHighlight: "bg-orange-500/10 border-orange-500",
    icon: <Handshake className="size-3.5 text-orange-500" />,
  },
  Contract: {
    label: "Contract",
    dotColor: "bg-indigo-500",
    badgeBg: "bg-indigo-50 dark:bg-indigo-950/50",
    badgeText: "text-indigo-700 dark:text-indigo-300",
    headerBorder: "border-indigo-300 dark:border-indigo-800",
    columnAccent: "bg-indigo-500",
    dropzoneHighlight: "bg-indigo-500/10 border-indigo-500",
    icon: <FileCheck2 className="size-3.5 text-indigo-500" />,
  },
  Confirmed: {
    label: "Confirmed",
    dotColor: "bg-emerald-500",
    badgeBg: "bg-emerald-50 dark:bg-emerald-950/50",
    badgeText: "text-emerald-700 dark:text-emerald-300",
    headerBorder: "border-emerald-300 dark:border-emerald-800",
    columnAccent: "bg-emerald-500",
    dropzoneHighlight: "bg-emerald-500/10 border-emerald-500",
    icon: <CheckCircle2 className="size-3.5 text-emerald-500" />,
  },
};

export function SalesPipelineScreen() {
  const queryClient = useQueryClient();
  const { data: profile } = useMyProfile();
  const isManager = useMemo(() => {
    const role = (profile?.roleName || "").toUpperCase();
    return role === "MANAGER" || role === "ADMIN";
  }, [profile]);

  const [deals, setDeals] = useState<PipelineDealCardResponse[]>([]);
  const [ownerFilter, setOwnerFilter] = useState("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [draggedOverStage, setDraggedOverStage] = useState<Deal["stage"] | null>(null);
  const [draggingDealId, setDraggingDealId] = useState<string | null>(null);

  // Edit Drawer States
  const [isEditDealDrawerOpen, setIsEditDealDrawerOpen] = useState(false);
  const [editingDeal, setEditingDeal] = useState<Deal | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);

  const isAlreadyClosed = useMemo(() => {
    if (!editingDeal) return false;
    const orig = deals.find((d) => d.deal.id === editingDeal.id);
    return orig ? orig.deal.status !== "active" : false;
  }, [editingDeal, deals]);

  const showError = (msg: string) => {
    setErrorMessage(msg);
    setTimeout(() => {
      setErrorMessage((prev) => (prev === msg ? null : prev));
    }, 6000);
  };

  const showSuccess = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => {
      setSuccessMessage((prev) => (prev === msg ? null : prev));
    }, 4000);
  };

  const stages: Deal["stage"][] = [
    "Inquiry",
    "Qualification",
    "Proposal",
    "Negotiation",
    "Contract",
    "Confirmed",
  ];

  // Fetch deals helper (UC-11.2)
  const fetchDeals = async (searchVal: string, ownerVal: string, showSpinner = false) => {
    const shouldShowSpinner = showSpinner || loading;
    if (shouldShowSpinner) {
      setLoading(true);
    }
    try {
      const params: Record<string, string> = {};
      if (searchVal.trim()) {
        params.search = searchVal.trim();
      }
      if (ownerVal && ownerVal !== "all") {
        params.ownerId = ownerVal;
      }
      const response = await dealService.getPipeline(params);
      if (response && response.success && response.data) {
        setDeals(response.data as PipelineDealCardResponse[]);
      }
    } catch (err) {
      console.error("Failed to fetch deals", err);
      showError("Could not load the pipeline. Please check your connection and try again.");
    } finally {
      if (shouldShowSpinner) {
        setLoading(false);
      }
      setIsRefreshing(false);
    }
  };

  const handleManualRefresh = () => {
    setIsRefreshing(true);
    fetchDeals(searchTerm, ownerFilter, false);
  };

  // Fetch users on mount
  React.useEffect(() => {
    const fetchUsers = async () => {
      try {
        const response = await taskUserService.getAll();
        if (response && response.success && response.data) {
          setUsers(response.data);
        }
      } catch (err) {
        console.error("Failed to fetch users", err);
      }
    };
    fetchUsers();
  }, []);

  // Fetch deals when search term or owner filter changes (debounced)
  React.useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      fetchDeals(searchTerm, ownerFilter);
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [searchTerm, ownerFilter]);

  const handleOpenEditDrawer = async (deal: Deal) => {
    try {
      const response = await dealService.getById(deal.id);
      if (response && response.success && response.data) {
        setEditingDeal(response.data as Deal);
      } else {
        setEditingDeal(deal);
      }
    } catch (err) {
      console.error("Failed to fetch deal details", err);
      setEditingDeal(deal);
    }
    setIsEditDealDrawerOpen(true);
  };

  const handleStageClick = (targetStage: Deal["stage"]) => {
    if (!editingDeal) return;
    const currentIdx = stages.indexOf(editingDeal.stage);
    const targetIdx = stages.indexOf(targetStage);

    if (currentIdx === targetIdx) return;

    let updatedStatus = editingDeal.status;
    if (targetStage === "Confirmed") {
      updatedStatus = "won";
    } else if (editingDeal.stage === "Confirmed") {
      updatedStatus = "active";
    }

    const updated = {
      ...editingDeal,
      stage: targetStage,
      status: updatedStatus,
    };

    setEditingDeal(updated);
  };

  const handleUpdateDeal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingDeal || !editingDeal.title || !editingDeal.contactName) {
      showError("Please enter both Deal title and Primary Contact name.");
      return;
    }

    const payload = {
      title: editingDeal.title,
      contactName: editingDeal.contactName,
      email: editingDeal.email || "",
      phone: editingDeal.phone || "",
      value: Number(editingDeal.value) || 0,
      stage: editingDeal.stage,
      expectedClose: editingDeal.expectedClose || new Date().toISOString().split("T")[0],
      status: editingDeal.status,
      owner: editingDeal.ownerEmail || editingDeal.owner,
      notes: editingDeal.notes || "",
    };

    try {
      const response = await dealService.update(editingDeal.id, payload);
      if (response && response.success && response.data) {
        setDeals((prev) =>
          prev.map((card) =>
            card.deal.id === editingDeal.id ? { ...card, deal: response.data as Deal } : card
          )
        );
        setIsEditDealDrawerOpen(false);
        setEditingDeal(null);
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess("Deal updated successfully!");
        fetchDeals(searchTerm, ownerFilter);
      } else {
        showError(response?.message || "Failed to update deal");
      }
    } catch (err: any) {
      console.error("Error updating deal", err);
      const errMsg =
        err.response?.data?.message || err.message || "An error occurred while updating the deal.";
      showError(errMsg);
    }
  };

  // Shift deal stage helper — frontend only sends the new stage, backend decides status
  const handleShiftStage = async (dealId: string, direction: "left" | "right") => {
    const card = deals.find((c) => c.deal.id === dealId);
    if (!card) return;
    const deal = card.deal;

    const currentIdx = stages.indexOf(deal.stage);
    const nextIdx = currentIdx + (direction === "right" ? 1 : -1);
    if (nextIdx < 0 || nextIdx >= stages.length) return;

    const nextStage = stages[nextIdx];

    const payload = {
      title: deal.title,
      contactName: deal.contactName,
      email: deal.email || "",
      phone: deal.phone || "",
      value: deal.value,
      stage: nextStage,
      expectedClose: deal.expectedClose || new Date().toISOString().split("T")[0],
      owner: deal.ownerEmail || deal.owner,
      notes: deal.notes || "",
    };

    try {
      const response = await dealService.update(dealId, payload);
      if (response && response.success && response.data) {
        setDeals((prev) =>
          prev.map((c) => (c.deal.id === dealId ? { ...c, deal: response.data as Deal } : c))
        );
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess(`Moved "${deal.title}" to ${nextStage}`);
        fetchDeals(searchTerm, ownerFilter);
      } else {
        showError(response?.message || "Failed to update deal stage");
      }
    } catch (err: any) {
      console.error("Error shifting deal stage", err);
      const errMsg =
        err.response?.data?.message || err.message || "An error occurred while shifting the deal stage.";
      showError(errMsg);
    }
  };

  // Move deal to specific stage directly via drag and drop
  const handleMoveToStage = async (dealId: string, targetStage: Deal["stage"]) => {
    const card = deals.find((c) => c.deal.id === dealId);
    if (!card) return;
    const deal = card.deal;

    if (deal.stage === targetStage) return;

    const payload = {
      title: deal.title,
      contactName: deal.contactName,
      email: deal.email || "",
      phone: deal.phone || "",
      value: deal.value,
      stage: targetStage,
      expectedClose: deal.expectedClose || new Date().toISOString().split("T")[0],
      owner: deal.ownerEmail || deal.owner,
      notes: deal.notes || "",
    };

    try {
      const response = await dealService.update(dealId, payload);
      if (response && response.success && response.data) {
        setDeals((prev) =>
          prev.map((c) => (c.deal.id === dealId ? { ...c, deal: response.data as Deal } : c))
        );
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess(`Moved "${deal.title}" to ${targetStage}`);
        fetchDeals(searchTerm, ownerFilter);
      } else {
        showError(response?.message || "Failed to update deal stage");
      }
    } catch (err: any) {
      console.error("Error moving deal stage", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred.";
      showError(errMsg);
    }
  };

  // Statistics
  const pipelineStats = useMemo(() => {
    const totalCount = deals.length;
    const totalValue = deals.reduce((sum, c) => sum + (c.deal.value || 0), 0);
    const weightedValue = deals.reduce(
      (sum, c) => sum + (c.deal.value || 0) * ((c.deal.probability || 0) / 100),
      0
    );
    const avgDealSize = totalCount > 0 ? Math.round(totalValue / totalCount) : 0;

    return {
      totalCount,
      totalValue,
      weightedValue,
      avgDealSize,
    };
  }, [deals]);

  // Group deals by stage
  const dealsByStage = useMemo(() => {
    const groups: Record<Deal["stage"], PipelineDealCardResponse[]> = {
      Inquiry: [],
      Qualification: [],
      Proposal: [],
      Negotiation: [],
      Contract: [],
      Confirmed: [],
    };
    deals.forEach((card) => {
      if (card.deal && groups[card.deal.stage]) {
        groups[card.deal.stage].push(card);
      }
    });
    return groups;
  }, [deals]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-28 bg-white dark:bg-zinc-900 rounded-2xl border border-slate-200/80 dark:border-zinc-800 shadow-xs">
        <div className="relative flex items-center justify-center size-14 rounded-2xl bg-blue-50 dark:bg-blue-950/50 mb-4">
          <Loader2 className="size-7 text-blue-600 animate-spin" />
        </div>
        <h4 className="text-sm font-bold text-slate-800 dark:text-zinc-100">Loading sales pipeline</h4>
        <p className="text-xs text-slate-500 dark:text-zinc-400 mt-1">Fetching live deals, stages, and workflow statuses...</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Toast Banners */}
      {errorMessage && (
        <div className="fixed top-4 right-4 z-50 bg-red-50 dark:bg-red-950/80 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 px-4 py-3 rounded-xl shadow-xl flex items-center gap-2.5 animate-in fade-in slide-in-from-top duration-300 backdrop-blur-md">
          <AlertCircle className="size-4 shrink-0 text-red-600 dark:text-red-400" />
          <span className="text-xs font-semibold">{errorMessage}</span>
          <button
            type="button"
            onClick={() => setErrorMessage(null)}
            className="ml-2 hover:text-red-900 dark:hover:text-red-100 transition"
          >
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {successMessage && (
        <div className="fixed top-4 right-4 z-50 bg-emerald-50 dark:bg-emerald-950/80 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200 px-4 py-3 rounded-xl shadow-xl flex items-center gap-2.5 animate-in fade-in slide-in-from-top duration-300 backdrop-blur-md">
          <CheckCircle className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
          <span className="text-xs font-semibold">{successMessage}</span>
          <button
            type="button"
            onClick={() => setSuccessMessage(null)}
            className="ml-2 hover:text-emerald-900 dark:hover:text-emerald-100 transition"
          >
            <X className="size-3.5" />
          </button>
        </div>
      )}

      <PageHeader {...PAGE_META.salesPipeline} />

      {/* KPI Metric Strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
        <Card className="border-slate-200/80 dark:border-zinc-800 shadow-xs bg-white dark:bg-zinc-900 hover:shadow-md transition-shadow">
          <CardContent className="p-4 flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-500 dark:text-zinc-400 uppercase tracking-wider block">
                Active Deals
              </span>
              <div className="text-xl font-black text-slate-800 dark:text-zinc-100 mt-0.5">
                {pipelineStats.totalCount} <span className="text-xs font-normal text-slate-400">deals</span>
              </div>
              <span className="text-[10px] text-slate-400 font-medium block mt-0.5">In active pipeline</span>
            </div>
            <div className="size-10 rounded-xl bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0">
              <Briefcase className="size-5" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200/80 dark:border-zinc-800 shadow-xs bg-white dark:bg-zinc-900 hover:shadow-md transition-shadow">
          <CardContent className="p-4 flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-500 dark:text-zinc-400 uppercase tracking-wider block">
                Pipeline Value
              </span>
              <div className="text-xl font-black text-slate-800 dark:text-zinc-100 mt-0.5">
                {pipelineStats.totalValue.toLocaleString("vi-VN")} <span className="text-xs font-bold text-slate-400">₫</span>
              </div>
              <span className="text-[10px] text-slate-400 font-medium block mt-0.5">Total opportunity gross</span>
            </div>
            <div className="size-10 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0">
              <DollarSign className="size-5" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200/80 dark:border-zinc-800 shadow-xs bg-white dark:bg-zinc-900 hover:shadow-md transition-shadow">
          <CardContent className="p-4 flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-500 dark:text-zinc-400 uppercase tracking-wider block">
                Weighted Forecast
              </span>
              <div className="text-xl font-black text-blue-600 dark:text-blue-400 mt-0.5">
                {pipelineStats.weightedValue.toLocaleString("vi-VN")} <span className="text-xs font-bold text-blue-400">₫</span>
              </div>
              <span className="text-[10px] text-slate-400 font-medium block mt-0.5">Probability adjusted</span>
            </div>
            <div className="size-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0">
              <TrendingUp className="size-5" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200/80 dark:border-zinc-800 shadow-xs bg-white dark:bg-zinc-900 hover:shadow-md transition-shadow">
          <CardContent className="p-4 flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-500 dark:text-zinc-400 uppercase tracking-wider block">
                Avg. Deal Size
              </span>
              <div className="text-xl font-black text-slate-800 dark:text-zinc-100 mt-0.5">
                {pipelineStats.avgDealSize.toLocaleString("vi-VN")} <span className="text-xs font-bold text-slate-400">₫</span>
              </div>
              <span className="text-[10px] text-slate-400 font-medium block mt-0.5">Per opportunity</span>
            </div>
            <div className="size-10 rounded-xl bg-amber-50 dark:bg-amber-950/60 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0">
              <Layers className="size-5" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Control Bar: Search, Owner Filter & Refresh */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 bg-white dark:bg-zinc-900 p-3 rounded-xl border border-slate-200/80 dark:border-zinc-800 shadow-xs">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="size-4 text-slate-400 dark:text-zinc-500 ml-1" />
          <span className="text-xs font-bold text-slate-700 dark:text-zinc-300">Board Filter & Views</span>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center gap-2.5 w-full sm:w-auto">
          {/* Search bar */}
          <div className="relative w-full sm:w-64">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400 dark:text-zinc-500" />
            <input
              type="text"
              placeholder="Search deal name, contact..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-8 py-1.5 rounded-lg border border-slate-200 dark:border-zinc-700 bg-slate-50 dark:bg-zinc-800/80 text-xs text-slate-800 dark:text-zinc-100 placeholder:text-slate-400 dark:placeholder:text-zinc-500 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10 focus:bg-white dark:focus:bg-zinc-900 transition"
            />
            {searchTerm && (
              <button
                type="button"
                onClick={() => setSearchTerm("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-zinc-200"
              >
                <X className="size-3.5" />
              </button>
            )}
          </div>

          {/* Owner filter dropdown */}
          <div className="flex items-center gap-1.5">
            <select
              value={ownerFilter}
              onChange={(e) => setOwnerFilter(e.target.value)}
              className="rounded-lg border border-slate-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-zinc-200 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10 transition w-full sm:w-auto"
            >
              <option value="all">All Sales Agents</option>
              {users.map((u) => (
                <option key={u.userId} value={u.userId}>
                  {u.fullName}
                </option>
              ))}
            </select>
          </div>

          {/* Refresh Button */}
          <Button
            variant="outline"
            size="sm"
            onClick={handleManualRefresh}
            disabled={isRefreshing}
            className="flex items-center gap-1.5 text-xs font-semibold border-slate-200 dark:border-zinc-700 text-slate-700 dark:text-zinc-300 hover:bg-slate-50 dark:hover:bg-zinc-800"
            title="Refresh Board"
          >
            <RefreshCw className={`size-3.5 ${isRefreshing ? "animate-spin text-blue-600" : ""}`} />
            <span className="hidden md:inline">Refresh</span>
          </Button>
        </div>
      </div>

      {/* Kanban Board Grid */}
      <div className="flex lg:grid lg:grid-cols-6 gap-3 overflow-x-auto lg:overflow-x-visible pb-4 custom-scrollbar select-none min-h-[640px]">
        {stages.map((stage) => {
          const stageDeals = dealsByStage[stage] || [];
          const stageTotalVal = stageDeals.reduce((sum, c) => sum + (c.deal.value || 0), 0);
          const config = STAGE_CONFIG[stage];
          const isDraggedOver = draggedOverStage === stage;

          return (
            <div
              key={stage}
              onDragOver={(e) => {
                e.preventDefault();
              }}
              onDragEnter={() => {
                setDraggedOverStage(stage);
              }}
              onDragLeave={(e) => {
                // Only clear when leaving column boundary
                if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                  setDraggedOverStage((prev) => (prev === stage ? null : prev));
                }
              }}
              onDrop={(e) => {
                e.preventDefault();
                setDraggedOverStage(null);
                setDraggingDealId(null);
                const dealId = e.dataTransfer.getData("text/plain");
                if (dealId) {
                  handleMoveToStage(dealId, stage);
                }
              }}
              className={`flex-1 min-w-[270px] lg:min-w-0 rounded-2xl p-2.5 flex flex-col border transition-all duration-200 h-[620px] max-h-[calc(100vh-14rem)] ${
                isDraggedOver
                  ? "bg-blue-500/5 dark:bg-blue-500/10 border-blue-500 border-2 border-dashed shadow-lg scale-[1.01]"
                  : "bg-slate-100/75 dark:bg-zinc-900/60 border-slate-200/80 dark:border-zinc-800"
              }`}
            >
              {/* Stage Header */}
              <div className="shrink-0 px-2 pt-1.5 pb-2.5 mb-2.5 border-b border-slate-200/80 dark:border-zinc-800">
                <div className="flex items-center justify-between gap-1.5">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className={`size-2.5 rounded-full shrink-0 ${config.dotColor}`} />
                    <h3 className="text-xs font-bold text-slate-800 dark:text-zinc-100 truncate">
                      {stage}
                    </h3>
                  </div>
                  <span
                    className={`text-[10px] font-black px-2 py-0.5 rounded-full border border-slate-200/60 dark:border-zinc-700 shrink-0 ${config.badgeBg} ${config.badgeText}`}
                  >
                    {stageDeals.length}
                  </span>
                </div>
                <div className="flex items-center justify-between text-[11px] font-bold text-slate-500 dark:text-zinc-400 mt-1 pl-4">
                  <span>{stageTotalVal.toLocaleString("vi-VN")} ₫</span>
                  <span className="text-[9.5px] font-medium text-slate-400 dark:text-zinc-500">
                    {stageDeals.length > 0
                      ? `${Math.round((stageTotalVal / (pipelineStats.totalValue || 1)) * 100)}% of total`
                      : "0%"}
                  </span>
                </div>
              </div>

              {/* Deal Cards Container */}
              <div className="flex-1 min-h-0 overflow-y-auto space-y-2.5 pr-1 custom-scrollbar">
                {stageDeals.length > 0 ? (
                  stageDeals.map((card) => {
                    const deal = card.deal;
                    const isDragging = draggingDealId === deal.id;

                    // Compute probability theme
                    const prob = deal.probability || 0;
                    const probColor =
                      prob >= 75
                        ? "bg-emerald-500"
                        : prob >= 40
                        ? "bg-blue-500"
                        : "bg-amber-500";

                    return (
                      <div
                        key={deal.id}
                        draggable={deal.status === "active"}
                        onDragStart={(e) => {
                          e.dataTransfer.setData("text/plain", deal.id);
                          e.dataTransfer.effectAllowed = "move";
                          setDraggingDealId(deal.id);
                        }}
                        onDragEnd={() => {
                          setDraggingDealId(null);
                        }}
                        onClick={() => handleOpenEditDrawer(deal)}
                        className={`group relative rounded-xl border border-slate-200/90 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-3.5 shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md hover:border-blue-400 dark:hover:border-blue-500 cursor-pointer ${
                          deal.status === "active"
                            ? "cursor-grab active:cursor-grabbing"
                            : "opacity-85"
                        } ${isDragging ? "opacity-30 scale-95 border-blue-500" : ""}`}
                      >
                        <div className="space-y-2">
                          {/* Top: Deal Code / Status & Quick Stage Shift Buttons on hover */}
                          <div className="flex items-center justify-between gap-1.5">
                            <div className="flex items-center gap-1.5 min-w-0">
                              <span className="font-mono text-[9.5px] font-bold text-slate-500 dark:text-zinc-400 bg-slate-100 dark:bg-zinc-800 px-1.5 py-0.5 rounded">
                                {deal.id.startsWith("D-") ? deal.id : `D-${deal.id.slice(0, 6)}`}
                              </span>
                              {deal.status !== "active" && (
                                <Badge
                                  variant={deal.status === "won" ? "success" : "danger"}
                                  className="text-[8px] font-black px-1.5 py-0 uppercase"
                                >
                                  {deal.status}
                                </Badge>
                              )}
                            </div>

                            {/* Quick stage shift arrows (fade in on card hover) */}
                            {deal.status === "active" && (
                              <div className="flex items-center opacity-0 group-hover:opacity-100 transition-opacity">
                                <button
                                  type="button"
                                  disabled={deal.stage === stages[0]}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleShiftStage(deal.id, "left");
                                  }}
                                  className="p-0.5 rounded hover:bg-slate-100 dark:hover:bg-zinc-800 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 disabled:opacity-20 transition"
                                  title="Move to previous stage"
                                >
                                  <ChevronLeft className="size-3.5" />
                                </button>
                                <button
                                  type="button"
                                  disabled={deal.stage === stages[stages.length - 1]}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleShiftStage(deal.id, "right");
                                  }}
                                  className="p-0.5 rounded hover:bg-slate-100 dark:hover:bg-zinc-800 text-slate-400 hover:text-slate-700 dark:hover:text-zinc-200 disabled:opacity-20 transition"
                                  title="Move to next stage"
                                >
                                  <ChevronRight className="size-3.5" />
                                </button>
                              </div>
                            )}
                          </div>

                          {/* Deal Title */}
                          <h4 className="text-xs font-bold text-slate-900 dark:text-zinc-100 leading-snug line-clamp-2 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                            {deal.title}
                          </h4>

                          {/* Contact Person */}
                          <div className="flex items-center gap-1.5 text-[11px] text-slate-500 dark:text-zinc-400 font-medium">
                            <User className="size-3 text-slate-400 dark:text-zinc-500 shrink-0" />
                            <span className="truncate">{deal.contactName}</span>
                          </div>

                          {/* Bottom Row: Value & Owner / Probability */}
                          <div className="flex items-center justify-between pt-2 mt-1 border-t border-slate-100 dark:border-zinc-800/80">
                            {/* Value */}
                            <div className="text-xs font-black text-slate-900 dark:text-zinc-100 tabular-nums">
                              {deal.value.toLocaleString("vi-VN")} <span className="text-[10px] font-normal text-slate-400">₫</span>
                            </div>

                            {/* Probability & Owner Avatar */}
                            <div className="flex items-center gap-1.5">
                              {deal.probability !== undefined && deal.probability > 0 && (
                                <span
                                  className={`text-[9.5px] font-bold px-1.5 py-0.5 rounded-full ${
                                    deal.probability >= 70
                                      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300"
                                      : deal.probability >= 40
                                      ? "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300"
                                      : "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300"
                                  }`}
                                  title={`Win Probability: ${deal.probability}%`}
                                >
                                  {deal.probability}%
                                </span>
                              )}

                              {/* Owner Initials Avatar */}
                              <span
                                className="size-5.5 rounded-full bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800 text-[9px] font-black flex items-center justify-center shrink-0 shadow-2xs"
                                title={`Owner: ${deal.owner || "Unassigned"}`}
                              >
                                {(deal.owner || "U")
                                  .split(" ")
                                  .map((n) => n[0])
                                  .join("")
                                  .slice(0, 2)
                                  .toUpperCase()}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div className="py-12 px-3 text-center border-2 border-dashed border-slate-200 dark:border-zinc-800 rounded-xl bg-slate-50/50 dark:bg-zinc-900/30 flex flex-col items-center justify-center gap-1.5 text-slate-400 dark:text-zinc-500">
                    <Inbox className="size-5 opacity-40" />
                    <span className="text-[11px] font-semibold">No deals in {stage}</span>
                    <span className="text-[9.5px] opacity-70">Drag deals here to advance</span>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Slide-over Drawer for editing/viewing Deal Detail */}
      {isEditDealDrawerOpen && editingDeal && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => {
              setIsEditDealDrawerOpen(false);
              setEditingDeal(null);
            }}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white dark:bg-zinc-900 shadow-2xl border-l border-slate-200 dark:border-zinc-800 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-zinc-800 bg-slate-50/70 dark:bg-zinc-900/90">
              <div>
                <div className="flex items-center gap-2">
                  <div className="size-7 rounded-lg bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400 flex items-center justify-center shrink-0">
                    <Briefcase className="size-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-800 dark:text-zinc-100">
                      Deal Details & Workflow
                    </h3>
                    <span className="font-mono text-[10px] text-slate-400 dark:text-zinc-500">
                      {editingDeal.id.startsWith("D-") ? editingDeal.id : `D-${editingDeal.id.slice(0, 6)}`}
                    </span>
                  </div>
                </div>
              </div>
              <button
                onClick={() => {
                  setIsEditDealDrawerOpen(false);
                  setEditingDeal(null);
                }}
                className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 dark:hover:bg-zinc-800 hover:text-slate-600 dark:hover:text-zinc-200 transition"
              >
                <X className="size-4" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleUpdateDeal} className="flex-1 overflow-y-auto p-6 space-y-4">
              {isAlreadyClosed && (
                <div className="bg-amber-50 dark:bg-amber-950/60 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-200 px-4 py-2.5 rounded-xl text-xs font-semibold flex items-center gap-2 mb-2 animate-in fade-in duration-200">
                  <AlertCircle className="size-4 shrink-0 text-amber-600" />
                  <span>This deal is closed and cannot be modified.</span>
                </div>
              )}

              {/* Deal Workflow Stepper Progress Indicator */}
              <DealWorkflowStepper
                dealId={editingDeal.id}
                onSyncSuccess={async () => {
                  try {
                    const response = await dealService.getById(editingDeal.id);
                    if (response && response.success && response.data) {
                      setEditingDeal(response.data as Deal);
                    }
                  } catch (err) {
                    console.error("Failed to reload deal details after sync", err);
                  }
                  fetchDeals(searchTerm, ownerFilter);
                  showSuccess("Deal pipeline stage synchronized!");
                }}
              />

              {/* Stage Tracker Stepper */}
              <div className="space-y-2 border-b border-slate-100 dark:border-zinc-800 pb-4 mb-2">
                <label className="text-xs font-bold text-slate-600 dark:text-zinc-400 uppercase tracking-wider">
                  Pipeline Stage Progression
                </label>
                <div className="grid grid-cols-3 gap-1.5 mt-2">
                  {stages.map((stg, idx) => {
                    const isCurrent = editingDeal.stage === stg;
                    const isPast = stages.indexOf(editingDeal.stage) > idx;
                    const isDisabled = isAlreadyClosed;
                    return (
                      <button
                        key={stg}
                        type="button"
                        onClick={() => !isDisabled && handleStageClick(stg)}
                        disabled={isDisabled}
                        className={`text-center py-2 px-1 rounded-lg text-[10px] font-bold transition-all duration-200 border ${
                          isCurrent
                            ? "bg-blue-600 border-blue-600 text-white shadow-sm"
                            : isPast
                            ? "bg-emerald-50 dark:bg-emerald-950/50 border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-100"
                            : "bg-slate-50 dark:bg-zinc-800/60 border-slate-200 dark:border-zinc-700 text-slate-500 dark:text-zinc-400 hover:bg-slate-100 hover:text-slate-700"
                        } ${isDisabled ? "cursor-not-allowed opacity-80" : ""}`}
                      >
                        {stg}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Deal Title *</label>
                <Input
                  maxLength={50}
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Wedding Catering Block, Corporate Conference..."
                  value={editingDeal.title || ""}
                  onChange={(e) => setEditingDeal({ ...editingDeal, title: e.target.value })}
                  className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Primary Contact Person *</label>
                <Input
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Alice Jenkins"
                  value={editingDeal.contactName || ""}
                  onChange={(e) => setEditingDeal({ ...editingDeal, contactName: e.target.value })}
                  className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Email Address</label>
                  <Input
                    type="email"
                    disabled={isAlreadyClosed}
                    placeholder="contact@gmail.com"
                    value={editingDeal.email || ""}
                    onChange={(e) => setEditingDeal({ ...editingDeal, email: e.target.value })}
                    className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Phone Number</label>
                  <Input
                    phoneOnly
                    disabled={isAlreadyClosed}
                    placeholder="e.g. 09xxxxxxxx"
                    value={editingDeal.phone || ""}
                    onChange={(e) => setEditingDeal({ ...editingDeal, phone: e.target.value })}
                    className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Deal Value (VND)</label>
                  <Input
                    type="number"
                    disabled={isAlreadyClosed}
                    placeholder="e.g. 15000000"
                    value={editingDeal.value || ""}
                    onChange={(e) => setEditingDeal({ ...editingDeal, value: Number(e.target.value) })}
                    className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Sales Stage</label>
                  <Select
                    value={editingDeal.stage || "Inquiry"}
                    disabled={isAlreadyClosed}
                    onChange={(e) =>
                      setEditingDeal({ ...editingDeal, stage: e.target.value as Deal["stage"] })
                    }
                    className="py-1.5 focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  >
                    <option value="Inquiry">Inquiry</option>
                    <option value="Qualification">Qualification</option>
                    <option value="Proposal">Proposal</option>
                    <option value="Negotiation">Negotiation</option>
                    <option value="Contract">Contract</option>
                    <option value="Confirmed">Confirmed</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Probability % (0-100)</label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    disabled={isAlreadyClosed}
                    placeholder="50"
                    value={editingDeal.probability || 0}
                    onChange={(e) =>
                      setEditingDeal({ ...editingDeal, probability: Number(e.target.value) })
                    }
                    className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Target Close Date</label>
                  <Input
                    type="date"
                    min={new Date().toISOString().split("T")[0]}
                    disabled={isAlreadyClosed}
                    value={editingDeal.expectedClose || ""}
                    onChange={(e) =>
                      setEditingDeal({ ...editingDeal, expectedClose: e.target.value })
                    }
                    className="py-1.5 text-xs focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Status</label>
                  <Select
                    value={editingDeal.status || "active"}
                    disabled={isAlreadyClosed}
                    onChange={(e) => {
                      const newStatus = e.target.value as Deal["status"];
                      let newStage = editingDeal.stage;
                      if (newStatus === "won") {
                        newStage = "Confirmed";
                      } else if (newStatus === "active" && editingDeal.stage === "Confirmed") {
                        newStage = "Contract";
                      }
                      setEditingDeal({ ...editingDeal, status: newStatus, stage: newStage });
                    }}
                    className="py-1.5 focus:border-blue-500 focus:ring-1 focus:ring-blue-500/20 focus:bg-white"
                  >
                    <option value="active">Active</option>
                    <option value="won">Won</option>
                    <option value="lost">Lost</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Owner</label>
                  <UserSelect
                    users={users}
                    value={editingDeal.ownerEmail || ""}
                    disabled={isAlreadyClosed || !isManager}
                    onChange={(email, fullName) =>
                      setEditingDeal({
                        ...editingDeal,
                        ownerEmail: email,
                        owner: fullName,
                      })
                    }
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-700 dark:text-zinc-300">Notes / Details</label>
                <textarea
                  placeholder="Describe deal requirements, guest details, notes, etc..."
                  value={editingDeal.notes || ""}
                  disabled={isAlreadyClosed}
                  onChange={(e) => setEditingDeal({ ...editingDeal, notes: e.target.value })}
                  className="w-full min-h-24 p-2.5 text-xs border border-slate-200 dark:border-zinc-700 rounded-lg bg-white dark:bg-zinc-800 text-slate-800 dark:text-zinc-100 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10 disabled:bg-slate-50 dark:disabled:bg-zinc-900 disabled:text-slate-400 transition"
                />
              </div>

              <div className="pt-3 flex gap-3 border-t border-slate-100 dark:border-zinc-800">
                {!isAlreadyClosed ? (
                  <>
                    <Button
                      type="submit"
                      variant="primary"
                      className="w-full text-xs py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold"
                    >
                      Save Changes
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => {
                        setIsEditDealDrawerOpen(false);
                        setEditingDeal(null);
                      }}
                      className="w-full text-xs py-2 border border-slate-200 dark:border-zinc-700"
                    >
                      Cancel
                    </Button>
                  </>
                ) : (
                  <Button
                    type="button"
                    variant="primary"
                    onClick={() => {
                      setIsEditDealDrawerOpen(false);
                      setEditingDeal(null);
                    }}
                    className="w-full text-xs py-2 bg-slate-600 hover:bg-slate-700 text-white font-semibold"
                  >
                    Close Details
                  </Button>
                )}
              </div>
            </form>
          </div>
        </>
      )}
    </div>
  );
}

