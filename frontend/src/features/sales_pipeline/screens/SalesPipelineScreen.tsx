"use client";

import React, { useState, useMemo } from "react";
import {
  Briefcase,
  DollarSign,
  TrendingUp,
  ChevronLeft,
  ChevronRight,
  User,
  Sparkles,
  Search,
  Filter,
  CheckCircle,
  AlertCircle,
  Loader2,
  X
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { dealService } from "@/services/deal_service";
import { userService as taskUserService, type UserSummary } from "@/services/follow_up_task_service";
import { useQueryClient } from "@tanstack/react-query";

export type Deal = {
  id: string;
  title: string;
  contactName: string;
  email?: string;
  phone?: string;
  value: number;
  probability: number;
  stage: "Inquiry" | "Site Visit" | "Proposal" | "Negotiation" | "Contract" | "Confirmed";
  owner: string;
  status: "active" | "won" | "lost";
  expectedClose?: string;
  notes?: string;
  createdAt?: string;
};

const getStageStyles = (stage: Deal["stage"]) => {
  switch (stage) {
    case "Inquiry":
      return {
        border: "border-t-4 border-t-slate-400/80",
        badge: "!bg-slate-100 !text-slate-700 border !border-slate-200",
        dot: "bg-slate-400"
      };
    case "Site Visit":
      return {
        border: "border-t-4 border-t-blue-500/80",
        badge: "!bg-blue-50 !text-blue-700 border !border-blue-200/50",
        dot: "bg-blue-500"
      };
    case "Proposal":
      return {
        border: "border-t-4 border-t-amber-500/80",
        badge: "!bg-amber-50 !text-amber-700 border !border-amber-200/50",
        dot: "bg-amber-500"
      };
    case "Negotiation":
      return {
        border: "border-t-4 border-t-orange-500/80",
        badge: "!bg-orange-50 !text-orange-700 border !border-orange-200/50",
        dot: "bg-orange-500"
      };
    case "Contract":
      return {
        border: "border-t-4 border-t-indigo-500/80",
        badge: "!bg-indigo-50 !text-indigo-700 border !border-indigo-200/50",
        dot: "bg-indigo-500"
      };
    case "Confirmed":
      return {
        border: "border-t-4 border-t-emerald-500/80",
        badge: "!bg-emerald-50 !text-emerald-700 border !border-emerald-200/50",
        dot: "bg-emerald-500"
      };
    default:
      return {
        border: "border-t-4 border-t-slate-400/80",
        badge: "!bg-slate-100 !text-slate-700 border !border-slate-200",
        dot: "bg-slate-400"
      };
  }
};

export function SalesPipelineScreen() {
  const queryClient = useQueryClient();
  const [deals, setDeals] = useState<Deal[]>([]);
  const [ownerFilter, setOwnerFilter] = useState("all");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [draggedOverStage, setDraggedOverStage] = useState<Deal["stage"] | null>(null);

  // Edit Drawer States
  const [isEditDealDrawerOpen, setIsEditDealDrawerOpen] = useState(false);
  const [editingDeal, setEditingDeal] = useState<Deal | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);

  const isAlreadyClosed = useMemo(() => {
    if (!editingDeal) return false;
    const orig = deals.find(d => d.id === editingDeal.id);
    return orig ? orig.status !== "active" : false;
  }, [editingDeal, deals]);

  const showError = (msg: string) => {
    setErrorMessage(msg);
    setTimeout(() => {
      setErrorMessage(prev => (prev === msg ? null : prev));
    }, 6000);
  };

  const showSuccess = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => {
      setSuccessMessage(prev => (prev === msg ? null : prev));
    }, 4000);
  };

  const stages: Deal["stage"][] = [
    "Inquiry",
    "Site Visit",
    "Proposal",
    "Negotiation",
    "Contract",
    "Confirmed"
  ];

  // Fetch deals and users on mount
  React.useEffect(() => {
    const fetchDeals = async () => {
      try {
        const response = await dealService.getList();
        if (response && response.success && response.data) {
          setDeals(response.data as Deal[]);
        }
      } catch (err) {
        console.error("Failed to fetch deals", err);
      } finally {
        setLoading(false);
      }
    };
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
    fetchDeals();
    fetchUsers();
  }, []);

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
      status: updatedStatus
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
      owner: editingDeal.owner,
      notes: editingDeal.notes || ""
    };

    try {
      const response = await dealService.update(editingDeal.id, payload);
      if (response && response.success && response.data) {
        setDeals(prev =>
          prev.map(deal =>
            deal.id === editingDeal.id ? (response.data as Deal) : deal
          )
        );
        setIsEditDealDrawerOpen(false);
        setEditingDeal(null);
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess("Deal updated successfully!");
      } else {
        showError(response?.message || "Failed to update deal");
      }
    } catch (err: any) {
      console.error("Error updating deal", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while updating the deal.";
      showError(errMsg);
    }
  };

  // Shift deal stage helper — frontend only sends the new stage, backend decides status
  const handleShiftStage = async (dealId: string, direction: "left" | "right") => {
    const deal = deals.find(d => d.id === dealId);
    if (!deal) return;

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
      owner: deal.owner,
      notes: deal.notes || ""
    };

    try {
      const response = await dealService.update(dealId, payload);
      if (response && response.success && response.data) {
        // Update local state with the backend-determined result
        setDeals(prev =>
          prev.map(d => (d.id === dealId ? (response.data as Deal) : d))
        );
        // Invalidate dashboard summary cache so it updates in real-time
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess(`Moved deal "${deal.title}" to ${nextStage}`);
      } else {
        showError(response?.message || "Failed to update deal stage");
      }
    } catch (err: any) {
      console.error("Error shifting deal stage", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred while shifting the deal stage.";
      showError(errMsg);
    }
  };

  // Move deal to specific stage directly — backend handles status transitions
  const handleMoveToStage = async (dealId: string, targetStage: Deal["stage"]) => {
    const deal = deals.find(d => d.id === dealId);
    if (!deal) return;

    const payload = {
      title: deal.title,
      contactName: deal.contactName,
      email: deal.email || "",
      phone: deal.phone || "",
      value: deal.value,
      stage: targetStage,
      expectedClose: deal.expectedClose || new Date().toISOString().split("T")[0],
      owner: deal.owner,
      notes: deal.notes || ""
    };

    try {
      const response = await dealService.update(dealId, payload);
      if (response && response.success && response.data) {
        setDeals(prev =>
          prev.map(d => (d.id === dealId ? (response.data as Deal) : d))
        );
        // Invalidate dashboard summary cache so it updates in real-time
        queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
        queryClient.invalidateQueries({ queryKey: ["deals-for-report"] });
        showSuccess(`Moved deal "${deal.title}" to ${targetStage}`);
      } else {
        showError(response?.message || "Failed to update deal stage");
      }
    } catch (err: any) {
      console.error("Error moving deal stage", err);
      const errMsg = err.response?.data?.message || err.message || "An error occurred.";
      showError(errMsg);
    }
  };

  // Filter deals by Owner
  const filteredDeals = useMemo(() => {
    if (ownerFilter === "all") return deals;
    return deals.filter(d => d.owner === ownerFilter);
  }, [deals, ownerFilter]);

  // Statistics
  const pipelineStats = useMemo(() => {
    const totalCount = filteredDeals.length;
    const totalValue = filteredDeals.reduce((sum, d) => sum + d.value, 0);
    // Weighted value = value * probability %
    const weightedValue = filteredDeals.reduce(
      (sum, d) => sum + d.value * (d.probability / 100),
      0
    );

    return {
      totalCount,
      totalValue,
      weightedValue
    };
  }, [filteredDeals]);

  // Group deals by stage
  const dealsByStage = useMemo(() => {
    const groups: Record<Deal["stage"], Deal[]> = {
      Inquiry: [],
      "Site Visit": [],
      Proposal: [],
      Negotiation: [],
      Contract: [],
      Confirmed: []
    };
    filteredDeals.forEach(deal => {
      if (groups[deal.stage]) {
        groups[deal.stage].push(deal);
      }
    });
    return groups;
  }, [filteredDeals]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 bg-white rounded-xl border border-slate-100 shadow-xs">
        <Loader2 className="size-8 text-blue-600 animate-spin mb-3" />
        <p className="text-xs text-slate-500 font-bold">Loading sales pipeline board...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Toast Banners */}
      {errorMessage && (
        <div className="fixed top-4 right-4 z-100 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2 animate-in fade-in slide-in-from-top duration-300">
          <AlertCircle className="size-4 shrink-0" />
          <span className="text-xs font-semibold">{errorMessage}</span>
          <button type="button" onClick={() => setErrorMessage(null)} className="ml-2 hover:text-red-900">
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {successMessage && (
        <div className="fixed top-4 right-4 z-100 bg-emerald-50 border border-emerald-200 text-emerald-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2 animate-in fade-in slide-in-from-top duration-300">
          <CheckCircle className="size-4 shrink-0 text-emerald-600" />
          <span className="text-xs font-semibold">{successMessage}</span>
          <button type="button" onClick={() => setSuccessMessage(null)} className="ml-2 hover:text-emerald-900">
            <X className="size-3.5" />
          </button>
        </div>
      )}

      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Sales Pipeline Board</h1>
          <p className="text-xs text-slate-400">Drag or shift contract deals across hotel booking sales stages</p>
        </div>

        {/* Owner filter dropdown */}
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-400">Filter Owner:</span>
          <select
            value={ownerFilter}
            onChange={e => setOwnerFilter(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 focus:outline-none"
          >
            <option value="all">All Sales Agents</option>
            <option value="John Doe">John Doe</option>
            <option value="Sarah Connor">Sarah Connor</option>
          </select>
        </div>
      </div>

      {/* Summary KPI Ribbon */}
      <div className="grid grid-cols-3 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-xs">
        <div className="text-center md:text-left md:border-r border-slate-100 last:border-0 pr-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Total Deals Count</span>
          <span className="text-lg font-bold text-slate-800 block mt-1">{pipelineStats.totalCount} active deals</span>
        </div>
        <div className="text-center md:text-left md:border-r border-slate-100 last:border-0 px-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Total Pipeline Value</span>
          <span className="text-lg font-bold text-slate-800 block mt-1">${pipelineStats.totalValue.toLocaleString('en-US')}</span>
        </div>
        <div className="text-center md:text-left px-4">
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">Weighted Revenue Forecast</span>
          <span className="text-lg font-bold text-blue-600 block mt-1">${pipelineStats.weightedValue.toLocaleString('en-US')}</span>
        </div>
      </div>

      {/* Kanban Board Grid */}
      <div className="flex lg:grid lg:grid-cols-6 gap-2 lg:gap-3 overflow-x-auto lg:overflow-x-hidden pb-4 custom-scrollbar select-none min-h-[480px]">
        {stages.map(stage => {
          const stageDeals = dealsByStage[stage] || [];
          const stageTotalVal = stageDeals.reduce((sum, d) => sum + d.value, 0);
          const styles = getStageStyles(stage);

          return (
            <div
              key={stage}
              onDragOver={(e) => {
                e.preventDefault();
              }}
              onDragEnter={() => {
                setDraggedOverStage(stage);
              }}
              onDragLeave={() => {
                setDraggedOverStage(prev => prev === stage ? null : prev);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setDraggedOverStage(null);
                const dealId = e.dataTransfer.getData("text/plain");
                if (dealId) {
                  handleMoveToStage(dealId, stage);
                }
              }}
              className={`flex-1 min-w-[200px] lg:min-w-0 lg:max-w-none rounded-xl p-2.5 lg:p-2 flex flex-col gap-2.5 lg:gap-2 border transition-all duration-200 ${draggedOverStage === stage
                  ? "bg-blue-50/50 border-blue-400 border-dashed"
                  : "bg-slate-100/60 border-slate-200/50"
                } ${styles.border}`}
            >
              {/* Stage Header */}
              <div className="px-1 py-0.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className={`size-2 rounded-full shrink-0 ${styles.dot}`} />
                    <h3 className="text-xs font-bold text-slate-700 truncate">{stage}</h3>
                  </div>
                  <Badge variant="default" className={`text-[9px] font-bold px-1.5 py-0 border-0 shrink-0 ${styles.badge}`}>
                    {stageDeals.length}
                  </Badge>
                </div>
                <div className="text-[10px] font-bold text-slate-400 mt-1 pl-3.5">
                  ${stageTotalVal.toLocaleString('en-US')}
                </div>
              </div>

              {/* Deal Cards Container */}
              <div className="flex-1 space-y-3 overflow-y-auto max-h-[440px] pr-1 custom-scrollbar">
                {stageDeals.length > 0 ? (
                  stageDeals.map(deal => (
                    <Card
                      key={deal.id}
                      draggable={deal.status === "active"}
                      onDragStart={(e) => {
                        e.dataTransfer.setData("text/plain", deal.id);
                        e.dataTransfer.effectAllowed = "move";
                      }}
                      className={`border-slate-200 bg-white hover:border-blue-400 shadow-xs hover:shadow-md transition group duration-200 ${deal.status === "active" ? "cursor-grab active:cursor-grabbing" : "opacity-80"
                        }`}
                    >
                      <CardContent className="p-2.5 lg:p-2 space-y-2">
                        {/* Title and Value */}
                        <div
                          className="cursor-pointer group-hover:text-blue-600 transition"
                          onClick={() => handleOpenEditDrawer(deal)}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <h4 className="text-xs font-bold text-slate-800 line-clamp-2 leading-snug group-hover:text-blue-600 transition">
                              {deal.title}
                            </h4>
                            {deal.status !== "active" && (
                              <Badge
                                variant={deal.status === "won" ? "success" : "danger"}
                                className="text-[8px] font-bold px-1.5 py-0 leading-none uppercase shrink-0"
                              >
                                {deal.status}
                              </Badge>
                            )}
                          </div>
                          <div className="text-xs font-black text-slate-800 mt-1">
                            ${deal.value.toLocaleString('en-US')}
                          </div>
                        </div>

                        {/* Contact Person / Company */}
                        <div
                          className="flex items-center gap-1.5 text-[10px] text-slate-500 font-medium cursor-pointer"
                          onClick={() => handleOpenEditDrawer(deal)}
                        >
                          <User className="size-3 text-slate-400" />
                          <span className="truncate">{deal.contactName}</span>
                        </div>

                        {/* Stage Slider Controller */}
                        <div className="flex items-center justify-between border-t border-slate-100 pt-2.5 mt-1">
                          {/* Left Arrow */}
                          <button
                            disabled={deal.stage === stages[0] || deal.status !== "active"}
                            onClick={() => handleShiftStage(deal.id, "left")}
                            className="p-1 rounded bg-slate-50 border border-slate-200 text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-slate-50 transition"
                            title={deal.status !== "active" ? "Closed deal cannot be moved" : "Move Stage Left"}
                          >
                            <ChevronLeft className="size-3" />
                          </button>

                          {/* Probability Indicator Badge */}
                          <span className="text-[9px] font-bold text-slate-400 px-1 bg-slate-50 rounded">
                            {deal.probability}% Win
                          </span>

                          {/* Right Arrow */}
                          <button
                            disabled={deal.stage === stages[stages.length - 1] || deal.status !== "active"}
                            onClick={() => handleShiftStage(deal.id, "right")}
                            className="p-1 rounded bg-slate-50 border border-slate-200 text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-slate-50 transition"
                            title={deal.status !== "active" ? "Closed deal cannot be moved" : "Move Stage Right"}
                          >
                            <ChevronRight className="size-3" />
                          </button>
                        </div>

                        {/* Owner / Assignee */}
                        <div className="flex items-center justify-between text-[9px] text-slate-400 pt-1">
                          <span>Owner: {deal.owner.split(" ")[0]}</span>
                          <span className="size-4.5 rounded-full bg-blue-100 text-blue-700 text-[8px] font-bold flex items-center justify-center">
                            {deal.owner.slice(0, 2).toUpperCase()}
                          </span>
                        </div>
                      </CardContent>
                    </Card>
                  ))
                ) : (
                  <div className="py-8 text-center text-slate-400 text-[10px] italic border-2 border-dashed border-slate-200 rounded-xl bg-slate-50">
                    No deals in this stage.
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
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => {
              setIsEditDealDrawerOpen(false);
              setEditingDeal(null);
            }}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Briefcase className="size-4.5 text-blue-600" />
                  Deal Details & Edit
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">View and update sales deal size, forecast close date, and pipeline stage</p>
              </div>
              <button
                onClick={() => {
                  setIsEditDealDrawerOpen(false);
                  setEditingDeal(null);
                }}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleUpdateDeal} className="flex-1 overflow-y-auto p-6 space-y-4">
              {isAlreadyClosed && (
                <div className="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-2 mb-4 animate-in fade-in duration-255">
                  <AlertCircle className="size-4 shrink-0 text-amber-600" />
                  <span>This deal is closed and cannot be modified.</span>
                </div>
              )}

              {/* Stage Tracker Stepper */}
              <div className="space-y-2 border-b border-slate-100 pb-4 mb-4">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-wider">Pipeline Stage Progression</label>
                <div className="flex items-center justify-between gap-1 mt-2">
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
                        className={`flex-1 text-center py-1.5 px-0.5 rounded text-[9px] font-bold transition-all duration-200 border ${isCurrent
                            ? "bg-blue-600 border-blue-600 text-white shadow-xs"
                            : isPast
                              ? "bg-emerald-50 border-emerald-100 text-emerald-700 hover:bg-emerald-100"
                              : "bg-slate-50 border-slate-200 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                          } ${isDisabled ? "cursor-not-allowed opacity-80" : ""}`}
                      >
                        {stg}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
                <Input
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Wedding Catering Block, Corporate Conference..."
                  value={editingDeal.title || ""}
                  onChange={e => setEditingDeal({ ...editingDeal, title: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Primary Contact Person *</label>
                <Input
                  required
                  disabled={isAlreadyClosed}
                  placeholder="e.g. Alice Jenkins"
                  value={editingDeal.contactName || ""}
                  onChange={e => setEditingDeal({ ...editingDeal, contactName: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Email Address</label>
                  <Input
                    type="email"
                    disabled={isAlreadyClosed}
                    placeholder="contact@gmail.com"
                    value={editingDeal.email || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, email: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Phone Number</label>
                  <Input
                    disabled={isAlreadyClosed}
                    placeholder="+1 555-0100"
                    value={editingDeal.phone || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, phone: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Deal Value ($)</label>
                  <Input
                    type="number"
                    disabled={isAlreadyClosed}
                    placeholder="e.g. 15000"
                    value={editingDeal.value || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, value: Number(e.target.value) })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Sales Stage</label>
                  <Select
                    value={editingDeal.stage || "Inquiry"}
                    disabled={isAlreadyClosed}
                    onChange={e => setEditingDeal({ ...editingDeal, stage: e.target.value as Deal["stage"] })}
                    className="py-1.5"
                  >
                    <option value="Inquiry">Inquiry</option>
                    <option value="Site Visit">Site Visit</option>
                    <option value="Proposal">Proposal</option>
                    <option value="Negotiation">Negotiation</option>
                    <option value="Contract">Contract</option>
                    <option value="Confirmed">Confirmed</option>
                  </Select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Probability % (0-100)</label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    disabled={isAlreadyClosed}
                    placeholder="50"
                    value={editingDeal.probability || 0}
                    onChange={e => setEditingDeal({ ...editingDeal, probability: Number(e.target.value) })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Est. Close Date</label>
                  <Input
                    type="date"
                    disabled={isAlreadyClosed}
                    value={editingDeal.expectedClose || ""}
                    onChange={e => setEditingDeal({ ...editingDeal, expectedClose: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Status</label>
                  <Select
                    value={editingDeal.status || "active"}
                    disabled={isAlreadyClosed}
                    onChange={e => {
                      const newStatus = e.target.value as Deal["status"];
                      let newStage = editingDeal.stage;
                      if (newStatus === "won") {
                        newStage = "Confirmed";
                      } else if (newStatus === "active" && editingDeal.stage === "Confirmed") {
                        newStage = "Contract";
                      }
                      setEditingDeal({ ...editingDeal, status: newStatus, stage: newStage });
                    }}
                    className="py-1.5"
                  >
                    <option value="active">Active</option>
                    <option value="won">Won</option>
                    <option value="lost">Lost</option>
                  </Select>
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Owner</label>
                  <Select
                    value={editingDeal.owner || ""}
                    disabled={isAlreadyClosed}
                    onChange={e => setEditingDeal({ ...editingDeal, owner: e.target.value })}
                    className="py-1.5"
                  >
                    <option value="">Select Deal Owner...</option>
                    {users.map(u => (
                      <option key={u.userId} value={u.fullName}>
                        {u.fullName} ({u.roleName || "Staff"})
                      </option>
                    ))}
                  </Select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Notes / Details</label>
                <textarea
                  placeholder="Describe deal requirements, guest details, notes, etc..."
                  value={editingDeal.notes || ""}
                  disabled={isAlreadyClosed}
                  onChange={e => setEditingDeal({ ...editingDeal, notes: e.target.value })}
                  className="w-full min-h-[100px] p-2 text-xs border border-slate-200 rounded-md focus:outline-none focus:border-blue-500 disabled:bg-slate-50 disabled:text-slate-400"
                />
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                {!isAlreadyClosed ? (
                  <>
                    <Button
                      type="submit"
                      variant="primary"
                      className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
                    >
                      Save Changes
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setIsEditDealDrawerOpen(false);
                        setEditingDeal(null);
                      }}
                      className="w-full border-slate-200 text-xs text-slate-600"
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
                    className="w-full bg-slate-600 hover:bg-slate-700 text-xs font-semibold"
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
