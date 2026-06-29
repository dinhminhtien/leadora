"use client";

import React, { useState, useMemo, useEffect } from "react";
import { 
  Phone, 
  Mail, 
  Calendar, 
  FileText, 
  Search, 
  Plus, 
  Filter, 
  MessageSquare, 
  Clock, 
  Loader2, 
  X 
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { 
  interactionTimelineService, 
  type InteractionTimelineEntry 
} from "@/services/interaction_timeline_service";
import { 
  userService, 
  type UserSummary 
} from "@/services/follow_up_task_service";

export function InteractionTimelineScreen() {
  const [interactions, setInteractions] = useState<InteractionTimelineEntry[]>([]);
  const [agents, setAgents] = useState<UserSummary[]>([]);
  
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("all");
  const [agentFilter, setAgentFilter] = useState("all");
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Selected interaction detail for the side drawer
  const [selectedInteraction, setSelectedInteraction] = useState<InteractionTimelineEntry | null>(null);

  // Helper to format occurred dates
  const formatDate = (dateStr: string) => {
    if (!dateStr) return "N/A";
    try {
      const date = new Date(dateStr);
      return date.toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch (e) {
      return dateStr;
    }
  };

  // Fetch agents dropdown
  useEffect(() => {
    const fetchAgents = async () => {
      try {
        const response = await userService.getAll();
        if (response && response.success && response.data) {
          setAgents(response.data);
        }
      } catch (err) {
        console.error("Failed to load agents", err);
      }
    };
    fetchAgents();
  }, []);

  // Fetch interaction list
  const fetchInteractions = async (searchVal: string, typeVal: string, agentVal: string) => {
    setLoading(true);
    setError(null);
    try {
      const params: any = {};
      if (searchVal.trim()) {
        params.search = searchVal.trim();
      }
      if (typeVal !== "all") {
        params.type = typeVal;
      }
      if (agentVal !== "all") {
        params.agentId = agentVal;
      }

      const response = await interactionTimelineService.getList(params);
      if (response && response.success && response.data) {
        setInteractions(response.data);
      } else {
        setError(response?.message || "Failed to load interaction timeline");
      }
    } catch (err: any) {
      console.error("Error loading interactions", err);
      setError(err?.response?.data?.message || "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  };

  // Debounced timeline list fetch
  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      fetchInteractions(searchTerm, typeFilter, agentFilter);
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [searchTerm, typeFilter, agentFilter]);

  // Load single interaction details (UC-13.3)
  const handleOpenDetail = async (id: string) => {
    try {
      const response = await interactionTimelineService.getById(id);
      if (response && response.success && response.data) {
        setSelectedInteraction(response.data);
      }
    } catch (err) {
      console.error("Failed to fetch interaction details", err);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-slate-800">Interaction History</h1>
        <p className="text-xs text-slate-400">Chronological feed of guest communications, emails, and call touchpoints</p>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4 flex flex-col md:flex-row items-center gap-3">
          {/* Search bar */}
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search descriptions, guests, agents..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>

          {/* Type filter buttons */}
          <div className="flex flex-wrap gap-2">
            {[
              { id: "all", label: "All Logs" },
              { id: "call", label: "Calls" },
              { id: "email", label: "Emails" },
              { id: "meeting", label: "Meetings" },
              { id: "note", label: "Notes" }
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setTypeFilter(tab.id)}
                className={`px-3 py-1 rounded-full text-xs font-semibold border transition ${
                  typeFilter === tab.id
                    ? "bg-blue-600 text-white border-blue-600 shadow-xs"
                    : "bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Agent Filter dropdown */}
          <div className="w-full md:w-48 flex items-center gap-2">
            <Filter className="size-3.5 text-slate-400 shrink-0" />
            <select
              value={agentFilter}
              onChange={e => setAgentFilter(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            >
              <option value="all">All Agents</option>
              {agents.map(agent => (
                <option key={agent.userId} value={agent.userId}>
                  {agent.fullName}
                </option>
              ))}
            </select>
          </div>

          <div className="md:ml-auto text-xs text-slate-400">
            Total records: <strong className="text-slate-700">{interactions.length}</strong>
          </div>
        </CardContent>
      </Card>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="px-6 py-8">
          {loading ? (
            <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-2 text-xs">
              <Loader2 className="size-6 animate-spin text-blue-600" />
              <span>Fetching timeline records...</span>
            </div>
          ) : error ? (
            <div className="py-12 text-center text-red-500 text-xs font-medium">
              {error}
            </div>
          ) : interactions.length > 0 ? (
            <div className="relative border-l border-slate-200 ml-6 pl-8 space-y-6">
              {interactions.map((item) => (
                <div key={item.id} className="relative group">
                  {/* Timeline icon */}
                  <span className="absolute left-[-45px] top-0.5 flex size-8 items-center justify-center rounded-full bg-white border border-slate-200 shadow-sm transition group-hover:border-blue-500 group-hover:scale-105">
                    {item.type === "call" && <Phone className="size-4 text-blue-500" />}
                    {item.type === "email" && <Mail className="size-4 text-emerald-500" />}
                    {item.type === "meeting" && <Calendar className="size-4 text-purple-500" />}
                    {item.type === "note" && <FileText className="size-4 text-amber-500" />}
                  </span>

                  <div>
                    <div className="flex justify-between items-center text-xs">
                      <p className="font-bold text-slate-800">
                        {item.type.toUpperCase()} Logged for{" "}
                        <span className="text-blue-600 hover:underline cursor-pointer">
                          {item.linkedName}
                        </span>
                        <span className="ml-2 text-[10px] font-normal text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">
                          {item.linkedType}
                        </span>
                      </p>
                      <span className="text-slate-400 text-[10px] flex items-center gap-1 font-semibold">
                        <Clock className="size-3" />
                        {formatDate(item.occurredAt)}
                      </span>
                    </div>
                    
                    <p 
                      onClick={() => handleOpenDetail(item.id)}
                      className="text-xs text-slate-600 mt-1.5 leading-relaxed bg-slate-50 hover:bg-slate-100/70 p-3 rounded-lg border border-slate-100/50 cursor-pointer transition"
                    >
                      {item.description}
                    </p>
                    
                    <div className="flex items-center justify-between mt-2">
                      <div className="flex items-center gap-1.5">
                        <span className="size-5 rounded-full bg-blue-100 text-blue-700 text-[9px] font-bold flex items-center justify-center">
                          {item.agentName.slice(0, 2).toUpperCase()}
                        </span>
                        <span className="text-[10px] text-slate-400">
                          Logged by <strong className="text-slate-600">{item.agentName}</strong>
                        </span>
                      </div>
                      
                      <button
                        onClick={() => handleOpenDetail(item.id)}
                        className="text-[10px] text-blue-600 hover:text-blue-800 font-semibold transition"
                      >
                        View Details →
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="py-12 text-center text-slate-400 text-xs">
              No matching interactions logged. Check filters or search query.
            </div>
          )}
        </CardContent>
      </Card>

      {/* Detail Drawer (UC-13.3) */}
      {selectedInteraction && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          {/* Backdrop */}
          <div 
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity duration-300"
            onClick={() => setSelectedInteraction(null)}
          />
          <div className="absolute inset-y-0 right-0 max-w-full flex pl-10">
            <div className="w-screen max-w-md bg-white shadow-2xl border-l border-slate-100 flex flex-col">
              {/* Header */}
              <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
                <h2 className="text-sm font-semibold text-slate-800 flex items-center gap-2">
                  <MessageSquare className="size-4 text-blue-600" />
                  Interaction Detail
                </h2>
                <button 
                  onClick={() => setSelectedInteraction(null)}
                  className="p-1 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
                >
                  <X className="size-4" />
                </button>
              </div>

              {/* Content */}
              <div className="flex-1 overflow-y-auto p-6 space-y-6">
                {/* Main Info */}
                <div className="space-y-4">
                  <div className="flex items-center gap-3">
                    <span className="flex size-10 items-center justify-center rounded-full bg-slate-50 border border-slate-200">
                      {selectedInteraction.type === "call" && <Phone className="size-5 text-blue-500" />}
                      {selectedInteraction.type === "email" && <Mail className="size-5 text-emerald-500" />}
                      {selectedInteraction.type === "meeting" && <Calendar className="size-5 text-purple-500" />}
                      {selectedInteraction.type === "note" && <FileText className="size-5 text-amber-500" />}
                    </span>
                    <div>
                      <Badge className={
                        selectedInteraction.type === "call" ? "bg-blue-100 text-blue-800 hover:bg-blue-100 border-none" :
                        selectedInteraction.type === "email" ? "bg-emerald-100 text-emerald-800 hover:bg-emerald-100 border-none" :
                        selectedInteraction.type === "meeting" ? "bg-purple-100 text-purple-800 hover:bg-purple-100 border-none" :
                        "bg-amber-100 text-amber-800 hover:bg-amber-100 border-none"
                      }>
                        {selectedInteraction.type.toUpperCase()}
                      </Badge>
                      <div className="text-[10px] text-slate-400 mt-1 font-medium">
                        Logged on {formatDate(selectedInteraction.occurredAt)}
                      </div>
                    </div>
                  </div>

                  <div className="bg-slate-50 rounded-xl p-4 border border-slate-100/80">
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Description / Notes</h3>
                    <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                      {selectedInteraction.description || "No description provided."}
                    </p>
                  </div>
                </div>

                {/* Metadata Grid */}
                <div className="border-t border-slate-100 pt-6 space-y-4">
                  <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Metadata & Relationships</h3>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-1">
                      <span className="text-[10px] text-slate-400 font-semibold block">LOGGED BY</span>
                      <span className="text-xs text-slate-700 font-medium">{selectedInteraction.agentName}</span>
                    </div>
                    <div className="space-y-1">
                      <span className="text-[10px] text-slate-400 font-semibold block">CREATED AT</span>
                      <span className="text-xs text-slate-700 font-medium">{formatDate(selectedInteraction.createdAt)}</span>
                    </div>
                    <div className="space-y-1 col-span-2">
                      <span className="text-[10px] text-slate-400 font-semibold block">LINKED RECORD ({selectedInteraction.linkedType?.toUpperCase()})</span>
                      <span className="text-xs font-semibold text-blue-600">
                        {selectedInteraction.linkedName}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Footer */}
              <div className="px-6 py-4 border-t border-slate-100 flex justify-end bg-slate-50/50">
                <Button 
                  variant="outline" 
                  onClick={() => setSelectedInteraction(null)}
                  className="text-xs"
                >
                  Close Details
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
