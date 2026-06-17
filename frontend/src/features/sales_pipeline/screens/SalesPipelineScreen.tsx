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
  AlertCircle
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
export type Deal = {
  id: string;
  title: string;
  contactName: string;
  value: number;
  probability: number;
  stage: "Inquiry" | "Site Visit" | "Proposal" | "Negotiation" | "Contract" | "Confirmed";
  owner: string;
  status: "active" | "won" | "lost";
};

export function SalesPipelineScreen() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [ownerFilter, setOwnerFilter] = useState("all");

  const stages: Deal["stage"][] = [
    "Inquiry",
    "Site Visit",
    "Proposal",
    "Negotiation",
    "Contract",
    "Confirmed"
  ];

  // Shift deal stage helper
  const handleShiftStage = (dealId: string, direction: "left" | "right") => {
    setDeals(prev =>
      prev.map(deal => {
        if (deal.id !== dealId) return deal;
        const currentIdx = stages.indexOf(deal.stage);
        let nextIdx = currentIdx + (direction === "right" ? 1 : -1);
        if (nextIdx < 0) nextIdx = 0;
        if (nextIdx >= stages.length) nextIdx = stages.length - 1;
        return { ...deal, stage: stages[nextIdx] };
      })
    );
  };

  // Move deal to specific stage directly
  const handleMoveToStage = (dealId: string, targetStage: Deal["stage"]) => {
    setDeals(prev =>
      prev.map(deal =>
        deal.id === dealId ? { ...deal, stage: targetStage } : deal
      )
    );
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

  return (
    <div className="space-y-6">
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
                          <h4 className="text-xs font-bold text-slate-800 line-clamp-2 leading-snug">
                            {deal.title}
                          </h4>
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
                            disabled={deal.stage === stages[0]}
                            onClick={() => handleShiftStage(deal.id, "left")}
                            className="p-1 rounded bg-slate-50 border border-slate-200 text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-slate-50 transition"
                            title="Move Stage Left"
                          >
                            <ChevronLeft className="size-3" />
                          </button>

                          {/* Probability Indicator Badge */}
                          <span className="text-[9px] font-bold text-slate-400 px-1 bg-slate-50 rounded">
                            {deal.probability}% Win
                          </span>

                          {/* Right Arrow */}
                          <button
                            disabled={deal.stage === stages[stages.length - 1]}
                            onClick={() => handleShiftStage(deal.id, "right")}
                            className="p-1 rounded bg-slate-50 border border-slate-200 text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-slate-50 transition"
                            title="Move Stage Right"
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
