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
import { dealService } from "@/services/deal_service";
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

export function SalesPipelineScreen() {
  const queryClient = useQueryClient();
  const [deals, setDeals] = useState<Deal[]>([]);
  const [ownerFilter, setOwnerFilter] = useState("all");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

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

  // Fetch deals on mount
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
    fetchDeals();
  }, []);

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
      <div className="flex gap-4 overflow-x-auto pb-4 custom-scrollbar select-none min-h-[480px]">
        {stages.map(stage => {
          const stageDeals = dealsByStage[stage] || [];
          const stageTotalVal = stageDeals.reduce((sum, d) => sum + d.value, 0);

          return (
            <div
              key={stage}
              className="flex-1 min-w-[240px] max-w-[280px] bg-slate-100/60 rounded-xl p-3 flex flex-col gap-3 border border-slate-200/50"
            >
              {/* Stage Header */}
              <div className="px-1 py-0.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold text-slate-700 truncate">{stage}</h3>
                  <Badge variant="primary" className="text-[9px] font-bold px-1.5 py-0">
                    {stageDeals.length}
                  </Badge>
                </div>
                <div className="text-[10px] font-bold text-slate-400 mt-1">
                  ${stageTotalVal.toLocaleString('en-US')}
                </div>
              </div>

              {/* Deal Cards Container */}
              <div className="flex-1 space-y-3 overflow-y-auto max-h-[440px] pr-1 custom-scrollbar">
                {stageDeals.length > 0 ? (
                  stageDeals.map(deal => (
                    <Card
                      key={deal.id}
                      className="border-slate-200 bg-white hover:border-blue-400 shadow-xs hover:shadow-md transition group duration-200"
                    >
                      <CardContent className="p-3 space-y-2.5">
                        {/* Title and Value */}
                        <div>
                          <div className="flex items-start justify-between gap-2">
                            <h4 className="text-xs font-bold text-slate-800 line-clamp-2 leading-snug">
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
                        <div className="flex items-center gap-1.5 text-[10px] text-slate-500 font-medium">
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
    </div>
  );
}
