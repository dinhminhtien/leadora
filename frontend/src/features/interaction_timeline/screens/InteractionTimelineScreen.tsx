"use client";

import React, { useState, useEffect } from "react";
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
  X,
  Edit2,
  AlertCircle,
  Building2,
  User,
  Briefcase
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import {
  interactionTimelineService,
  type InteractionTimelineEntry,
  type CreateInteractionTimelinePayload,
  type UpdateInteractionTimelinePayload,
  type InteractionAuditLog
} from "@/services/interaction_timeline_service";
import {
  userService,
  type UserSummary
} from "@/services/follow_up_task_service";
import { leadService } from "@/services/lead_service";
import { customerProfileService } from "@/services/customer_profile_service";
import { dealService } from "@/services/deal_service";
import { toast } from "@/stores/toast_store";

export function InteractionTimelineScreen() {
  const [interactions, setInteractions] = useState<InteractionTimelineEntry[]>([]);
  const [agents, setAgents] = useState<UserSummary[]>([]);

  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("all");
  const [agentFilter, setAgentFilter] = useState("all");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Detail Drawer state
  const [selectedInteraction, setSelectedInteraction] = useState<InteractionTimelineEntry | null>(null);
  const [isEditingDetail, setIsEditingDetail] = useState(false);
  const [editType, setEditType] = useState<"call" | "email" | "meeting" | "note">("call");
  const [editDescription, setEditDescription] = useState("");
  const [editOccurredAt, setEditOccurredAt] = useState("");
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Audit state
  const [detailTab, setDetailTab] = useState<"details" | "audit">("details");
  const [auditLogs, setAuditLogs] = useState<InteractionAuditLog[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  // Create Drawer state
  const [showCreateDrawer, setShowCreateDrawer] = useState(false);
  const [formType, setFormType] = useState<"call" | "email" | "meeting" | "note">("call");
  const [formDescription, setFormDescription] = useState("");
  const [formOccurredAt, setFormOccurredAt] = useState("");
  const [searchEntityType, setSearchEntityType] = useState<"lead" | "customer" | "deal">("lead");
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [selectedLead, setSelectedLead] = useState<any | null>(null);
  const [selectedCustomer, setSelectedCustomer] = useState<any | null>(null);
  const [selectedDeal, setSelectedDeal] = useState<any | null>(null);
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

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

  // Convert UTC ISO to local datetime-local value
  const toLocalDateTimeLocal = (isoString: string) => {
    if (!isoString) return "";
    try {
      const d = new Date(isoString);
      const offset = d.getTimezoneOffset() * 60000;
      return new Date(d.getTime() - offset).toISOString().slice(0, 16);
    } catch (e) {
      return "";
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
      // This screen shows the full recent history with no pager UI, so request
      // a generous page — the backend list is paged (default size 20) and would
      // otherwise silently cap the timeline.
      const params: any = { page: 0, size: 200 };
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
        setInteractions(response.data.content ?? []);
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

  // Autocomplete search for target records
  useEffect(() => {
    if (searchQuery.trim().length < 1) {
      setSearchResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setSearchLoading(true);
      try {
        if (searchEntityType === "lead") {
          const res = await leadService.getList({ search: searchQuery, size: 8 });
          if (res && res.success && res.data) {
            setSearchResults(res.data.content || []);
          }
        } else if (searchEntityType === "customer") {
          const res = await customerProfileService.getList({ search: searchQuery, size: 8 });
          if (res && res.success && res.data) {
            setSearchResults(res.data || []);
          }
        } else if (searchEntityType === "deal") {
          const res = await dealService.getList();
          if (res && res.success && res.data) {
            const filtered = res.data.filter((d: any) =>
              d.title?.toLowerCase().includes(searchQuery.toLowerCase())
            );
            setSearchResults(filtered.slice(0, 8));
          }
        }
      } catch (err) {
        console.error("Search failed", err);
      } finally {
        setSearchLoading(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery, searchEntityType]);

  // Load single interaction details (UC-13.3)
  const handleOpenDetail = async (id: string) => {
    try {
      const response = await interactionTimelineService.getById(id);
      if (response && response.success && response.data) {
        setSelectedInteraction(response.data);
        setIsEditingDetail(false);
        setDetailTab("details");
        setEditError(null);
        // Prep edit fields
        setEditType(response.data.type);
        setEditDescription(response.data.description);
        setEditOccurredAt(toLocalDateTimeLocal(response.data.occurredAt));
      }
    } catch (err) {
      console.error("Failed to fetch interaction details", err);
    }
  };

  // Fetch audit logs when tab is selected
  const fetchAuditLogs = async (id: string) => {
    setAuditLoading(true);
    setAuditError(null);
    try {
      const res = await interactionTimelineService.getAuditLogs(id);
      if (res && res.success && res.data) {
        setAuditLogs(res.data);
      } else {
        setAuditError(res.message || "Failed to load audit logs.");
      }
    } catch (err: any) {
      console.error("Failed to load audit logs", err);
      setAuditError(err?.response?.data?.message || "An unexpected error occurred.");
    } finally {
      setAuditLoading(false);
    }
  };

  useEffect(() => {
    if (selectedInteraction && detailTab === "audit") {
      fetchAuditLogs(selectedInteraction.id);
    }
  }, [selectedInteraction, detailTab]);

  // Open creation drawer
  const handleOpenCreateDrawer = () => {
    setFormType("call");
    setFormDescription("");
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60000;
    setFormOccurredAt(new Date(now.getTime() - offset).toISOString().slice(0, 16));

    setSelectedLead(null);
    setSelectedCustomer(null);
    setSelectedDeal(null);
    setSearchQuery("");
    setSearchResults([]);
    setCreateError(null);
    setShowCreateDrawer(true);
  };

  // Submit new log
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    if (!selectedLead && !selectedCustomer && !selectedDeal) {
      setCreateError("Please select a Lead, Customer, or Deal to link to this interaction.");
      return;
    }

    if (!formDescription.trim()) {
      setCreateError("Description / Notes are required.");
      return;
    }

    setCreateLoading(true);
    try {
      const payload: CreateInteractionTimelinePayload = {
        type: formType,
        description: formDescription.trim(),
        occurredAt: new Date(formOccurredAt).toISOString(),
        leadId: selectedLead?.leadId || undefined,
        customerId: selectedCustomer?.id || undefined,
        dealId: selectedDeal?.id || undefined,
      };

      const res = await interactionTimelineService.create(payload);
      if (res && res.success) {
        toast.success("Interaction logged successfully.");
        setShowCreateDrawer(false);
        fetchInteractions(searchTerm, typeFilter, agentFilter);
      } else {
        setCreateError(res.message || "Failed to log interaction.");
      }
    } catch (err: any) {
      console.error("Failed to create log", err);
      setCreateError(err?.response?.data?.message || "An unexpected error occurred.");
    } finally {
      setCreateLoading(false);
    }
  };

  // Submit edit log
  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInteraction) return;
    setEditError(null);

    if (!editDescription.trim()) {
      setEditError("Description / Notes are required.");
      return;
    }

    setEditLoading(true);
    try {
      const payload: UpdateInteractionTimelinePayload = {
        type: editType,
        description: editDescription.trim(),
        occurredAt: new Date(editOccurredAt).toISOString()
      };

      const res = await interactionTimelineService.update(selectedInteraction.id, payload);
      if (res && res.success) {
        toast.success("Interaction updated successfully.");
        setIsEditingDetail(false);
        handleOpenDetail(selectedInteraction.id);
        fetchInteractions(searchTerm, typeFilter, agentFilter);
      } else {
        setEditError(res.message || "Failed to update interaction.");
      }
    } catch (err: any) {
      console.error("Failed to update log", err);
      setEditError(err?.response?.data?.message || "An unexpected error occurred.");
    } finally {
      setEditLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-foreground flex items-center gap-2">
            <MessageSquare className="size-5 text-[#185FA5]" />
            Interaction History
          </h1>
          <p className="text-[10px] text-muted-foreground mt-0.5">Chronological feed of guest communications, emails, and call touchpoints</p>
        </div>
        <Button
          id="btn-log-interaction"
          variant="primary"
          size="sm"
          onClick={handleOpenCreateDrawer}
          leftIcon={<Plus className="size-4" />}
        >
          Log Interaction
        </Button>
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
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
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
                className={`px-3 py-1 rounded-full text-xs font-semibold border transition ${typeFilter === tab.id
                  ? "bg-[#185FA5] text-[#E6F1FB] border-[#0C447C] shadow-xs"
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
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
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
              <Loader2 className="size-6 animate-spin text-[#185FA5]" />
              <span>Fetching timeline records...</span>
            </div>
          ) : error ? (
            <div className="py-12 text-center text-red-500 text-xs font-medium">
              {error}
            </div>
          ) : interactions.length > 0 ? (
            <div className="relative border-l border-slate-200 ml-6 pl-8 space-y-6">
              {interactions.map((item) => (
                <div key={item.id} className="relative group animate-in fade-in duration-200">
                  {/* Timeline icon */}
                  <span className={`absolute left-[-45px] top-0.5 flex size-8 items-center justify-center rounded-full bg-white border border-slate-200 shadow-sm transition group-hover:scale-105 ${item.type === "call" ? "group-hover:border-green-500 group-hover:bg-green-50/50" :
                    item.type === "email" ? "group-hover:border-blue-500 group-hover:bg-blue-50/50" :
                      item.type === "meeting" ? "group-hover:border-purple-500 group-hover:bg-purple-50/50" :
                        "group-hover:border-amber-500 group-hover:bg-amber-50/50"
                    }`}>
                    {item.type === "call" && <Phone className="size-4 text-green-600" />}
                    {item.type === "email" && <Mail className="size-4 text-blue-600" />}
                    {item.type === "meeting" && <Calendar className="size-4 text-purple-600" />}
                    {item.type === "note" && <FileText className="size-4 text-amber-600" />}
                  </span>

                  <div>
                    <div className="flex justify-between items-center text-xs">
                      <div className="font-bold text-slate-800 flex items-center gap-1.5 flex-wrap">
                        <span className="capitalize">{item.type}</span> Logged for{" "}
                        <span className="text-[#185FA5] hover:text-[#0C447C] hover:underline cursor-pointer" onClick={() => handleOpenDetail(item.id)}>
                          {item.linkedName}
                        </span>
                        <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full border capitalize ${item.linkedType === "lead" ? "bg-[#E6F1FB] text-[#0C447C] border-[#85B7EB]" :
                          item.linkedType === "customer" ? "bg-[#EAF3DE] text-[#3B6D11] border-[#C0DD97]" :
                            "bg-[#FAEEDA] text-[#854F0B] border-[#FAC775]"
                          }`}>
                          {item.linkedType}
                        </span>
                      </div>
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
                        <span className="size-5 rounded-full bg-[#E6F1FB] text-[#0C447C] text-[9px] font-bold flex items-center justify-center">
                          {item.agentName.slice(0, 2).toUpperCase()}
                        </span>
                        <span className="text-[10px] text-slate-400">
                          Logged by <strong className="text-slate-600">{item.agentName}</strong>
                        </span>
                      </div>

                      <button
                        onClick={() => handleOpenDetail(item.id)}
                        className="text-[10px] text-[#185FA5] hover:text-[#0C447C] font-semibold transition"
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

      {/* Log Interaction Drawer */}
      {showCreateDrawer && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          <div
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity duration-300"
            onClick={() => setShowCreateDrawer(false)}
          />
          <div className="absolute inset-y-0 right-0 max-w-full flex pl-10">
            <div className="w-screen max-w-md bg-white shadow-2xl border-l border-slate-100 flex flex-col animate-in slide-in-from-right duration-300">

              {/* Header */}
              <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
                <h2 className="text-sm font-semibold text-slate-800 flex items-center gap-2">
                  <Plus className="size-4 text-[#185FA5]" />
                  Log New Interaction
                </h2>
                <button
                  onClick={() => setShowCreateDrawer(false)}
                  className="p-1 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
                >
                  <X className="size-4" />
                </button>
              </div>

              {/* Form */}
              <form onSubmit={handleCreateSubmit} className="flex-1 flex flex-col justify-between overflow-hidden">
                <div className="flex-1 overflow-y-auto p-6 space-y-5">

                  {/* Interaction Type Selection */}
                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-slate-600 block">Interaction Type *</label>
                    <div className="grid grid-cols-4 gap-2">
                      {[
                        { id: "call", label: "Call", icon: Phone, color: "text-green-700 border-green-200 bg-green-50/50", ringColor: "ring-green-400" },
                        { id: "email", label: "Email", icon: Mail, color: "text-blue-700 border-blue-200 bg-blue-50/50", ringColor: "ring-blue-400" },
                        { id: "meeting", label: "Meeting", icon: Calendar, color: "text-purple-700 border-purple-200 bg-purple-50/50", ringColor: "ring-purple-400" },
                        { id: "note", label: "Note", icon: FileText, color: "text-amber-700 border-amber-200 bg-amber-50/50", ringColor: "ring-amber-400" }
                      ].map(option => {
                        const Icon = option.icon;
                        const isSelected = formType === option.id;
                        return (
                          <button
                            key={option.id}
                            type="button"
                            onClick={() => setFormType(option.id as any)}
                            className={`flex flex-col items-center justify-center p-2 rounded-xl border text-xs transition duration-200 ${isSelected
                              ? `${option.color} ring-1 ${option.ringColor} font-bold scale-[1.03]`
                              : "border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-500"
                              }`}
                          >
                            <Icon className="size-4 mb-1" />
                            {option.label}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* Occurred At Date Picker */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-600 block">Date & Time *</label>
                    <input
                      type="datetime-local"
                      required
                      value={formOccurredAt}
                      onChange={e => setFormOccurredAt(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
                    />
                  </div>

                  {/* Association Selection */}
                  <div className="space-y-3 border border-slate-200 rounded-xl p-3 bg-slate-50/30">
                    <span className="flex items-center gap-1.5 text-[10px] font-bold text-slate-500 uppercase tracking-wide">
                      <Building2 className="size-3.5" />
                      Link to Record *
                    </span>

                    {/* Radio-like Tabs */}
                    <div className="flex rounded-lg border border-slate-200 overflow-hidden text-[10px] font-semibold bg-white">
                      {[
                        { id: "lead", label: "Lead" },
                        { id: "customer", label: "Customer" },
                        { id: "deal", label: "Deal" }
                      ].map(tab => (
                        <button
                          key={tab.id}
                          type="button"
                          onClick={() => {
                            setSearchEntityType(tab.id as any);
                            setSearchQuery("");
                            setSearchResults([]);
                          }}
                          className={`flex-1 px-3 py-1.5 text-center transition ${searchEntityType === tab.id
                            ? "bg-[#185FA5] text-white font-bold"
                            : "text-slate-500 hover:bg-slate-50"
                            }`}
                        >
                          {tab.label}
                        </button>
                      ))}
                    </div>

                    {/* Display Selected Entity if any */}
                    {(selectedLead || selectedCustomer || selectedDeal) ? (
                      <div className="p-3 rounded-lg border border-[#85B7EB] bg-[#E6F1FB] flex items-center justify-between text-xs animate-in fade-in duration-200">
                        <div className="flex items-center gap-2">
                          {selectedLead && <User className="size-4 text-[#185FA5]" />}
                          {selectedCustomer && <Building2 className="size-4 text-[#185FA5]" />}
                          {selectedDeal && <Briefcase className="size-4 text-[#185FA5]" />}
                          <div>
                            <p className="font-semibold text-[#0C447C]">
                              {selectedLead?.fullName || selectedCustomer?.name || selectedDeal?.title}
                            </p>
                            <p className="text-[10px] text-[#185FA5]">
                              {selectedLead && `Lead · ${selectedLead.phone || selectedLead.email || ""}`}
                              {selectedCustomer && `Customer · ${selectedCustomer.phone || selectedCustomer.email || ""}`}
                              {selectedDeal && `Deal · ${selectedDeal.status || ""}`}
                            </p>
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedLead(null);
                            setSelectedCustomer(null);
                            setSelectedDeal(null);
                            setSearchQuery("");
                          }}
                          className="p-1 rounded text-[#185FA5] hover:text-[#A32D2D] hover:bg-white/50 transition"
                        >
                          <X className="size-3.5" />
                        </button>
                      </div>
                    ) : (
                      /* Autocomplete Input Search */
                      <div className="relative">
                        <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
                        <input
                          type="text"
                          placeholder={`Search ${searchEntityType} by name...`}
                          value={searchQuery}
                          onChange={e => {
                            setSearchQuery(e.target.value);
                          }}
                          className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-white text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 transition"
                        />

                        {/* Search Results Dropdown */}
                        {searchQuery.trim().length > 0 && (
                          <div className="absolute left-0 right-0 mt-1 max-h-48 overflow-y-auto bg-white border border-slate-200 rounded-lg shadow-lg z-20 divide-y divide-slate-100">
                            {searchLoading ? (
                              <div className="p-3 text-center text-xs text-slate-400 flex items-center justify-center gap-1.5">
                                <Loader2 className="size-3.5 animate-spin text-[#185FA5]" />
                                Searching...
                              </div>
                            ) : searchResults.length > 0 ? (
                              searchResults.map(item => (
                                <button
                                  key={item.leadId || item.id || item.dealId}
                                  type="button"
                                  onClick={() => {
                                    if (searchEntityType === "lead") setSelectedLead(item);
                                    if (searchEntityType === "customer") setSelectedCustomer(item);
                                    if (searchEntityType === "deal") setSelectedDeal(item);
                                    setSearchQuery("");
                                  }}
                                  className="w-full text-left px-3 py-2 hover:bg-[#E6F1FB] flex flex-col transition"
                                >
                                  <span className="text-xs font-semibold text-slate-700 hover:text-[#0C447C]">
                                    {item.fullName || item.name || item.title}
                                  </span>
                                  <span className="text-[10px] text-slate-400 truncate">
                                    {item.email || item.phone || item.companyName || item.company || item.status}
                                  </span>
                                </button>
                              ))
                            ) : (
                              <div className="p-3 text-center text-xs text-slate-400">
                                No records found.
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Notes / Description */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-600 block">Description / Notes *</label>
                    <textarea
                      rows={4}
                      required
                      value={formDescription}
                      onChange={e => setFormDescription(e.target.value)}
                      placeholder="Summarize the core details of the phone call, meeting discussion, email outcome, or general notes..."
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition resize-none leading-relaxed"
                    />
                  </div>

                  {createError && (
                    <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-600 flex items-start gap-1.5 animate-in fade-in duration-200">
                      <AlertCircle className="size-4 shrink-0 mt-0.5" />
                      <span>{createError}</span>
                    </div>
                  )}

                </div>

                {/* Footer buttons */}
                <div className="px-6 py-4 border-t border-slate-100 flex gap-3 bg-slate-50/50">
                  <Button
                    type="submit"
                    variant="primary"
                    disabled={createLoading || (!selectedLead && !selectedCustomer && !selectedDeal)}
                    className="flex-1 text-xs py-2"
                  >
                    {createLoading ? (
                      <span className="flex items-center justify-center gap-1.5">
                        <Loader2 className="size-3.5 animate-spin" /> Logging...
                      </span>
                    ) : "Save Log"}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => setShowCreateDrawer(false)}
                    className="flex-1 text-xs py-2"
                  >
                    Cancel
                  </Button>
                </div>
              </form>

            </div>
          </div>
        </div>
      )}

      {/* Detail Drawer (UC-13.3) */}
      {selectedInteraction && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity duration-300"
            onClick={() => setSelectedInteraction(null)}
          />
          <div className="absolute inset-y-0 right-0 max-w-full flex pl-10">
            <div className="w-screen max-w-md bg-white shadow-2xl border-l border-slate-100 flex flex-col animate-in slide-in-from-right duration-300">

              {/* Header */}
              <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
                <h2 className="text-sm font-semibold text-slate-800 flex items-center gap-2">
                  <MessageSquare className="size-4 text-[#185FA5]" />
                  {isEditingDetail ? "Edit Interaction Log" : "Interaction Detail"}
                </h2>
                <div className="flex items-center gap-1.5">
                  {!isEditingDetail && (
                    <button
                      onClick={() => setIsEditingDetail(true)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-semibold text-slate-600 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100 transition duration-150"
                      title="Edit this log entry"
                    >
                      <Edit2 className="size-3" />
                      Edit
                    </button>
                  )}
                  <button
                    onClick={() => setSelectedInteraction(null)}
                    className="p-1 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
                  >
                    <X className="size-4" />
                  </button>
                </div>
              </div>

              {/* Tabs */}
              {!isEditingDetail && (
                <div className="flex border-b border-slate-100 text-xs font-semibold bg-slate-50/20">
                  <button
                    onClick={() => setDetailTab("details")}
                    className={`flex-1 py-3 text-center border-b-2 transition ${detailTab === "details"
                      ? "border-[#185FA5] text-[#185FA5] font-bold"
                      : "border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                      }`}
                  >
                    Details
                  </button>
                  <button
                    onClick={() => setDetailTab("audit")}
                    className={`flex-1 py-3 text-center border-b-2 transition ${detailTab === "audit"
                      ? "border-[#185FA5] text-[#185FA5] font-bold"
                      : "border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                      }`}
                  >
                    Audit History
                  </button>
                </div>
              )}

              {isEditingDetail ? (
                /* Edit Form Mode */
                <form onSubmit={handleEditSubmit} className="flex-1 flex flex-col justify-between overflow-hidden">
                  <div className="flex-1 overflow-y-auto p-6 space-y-5">

                    {/* Interaction Type */}
                    <div className="space-y-2">
                      <label className="text-xs font-semibold text-slate-600 block">Interaction Type *</label>
                      <div className="grid grid-cols-4 gap-2">
                        {[
                          { id: "call", label: "Call", icon: Phone, color: "text-green-700 border-green-200 bg-green-50/50", ringColor: "ring-green-400" },
                          { id: "email", label: "Email", icon: Mail, color: "text-blue-700 border-blue-200 bg-blue-50/50", ringColor: "ring-blue-400" },
                          { id: "meeting", label: "Meeting", icon: Calendar, color: "text-purple-700 border-purple-200 bg-purple-50/50", ringColor: "ring-purple-400" },
                          { id: "note", label: "Note", icon: FileText, color: "text-amber-700 border-amber-200 bg-amber-50/50", ringColor: "ring-amber-400" }
                        ].map(option => {
                          const Icon = option.icon;
                          const isSelected = editType === option.id;
                          return (
                            <button
                              key={option.id}
                              type="button"
                              onClick={() => setEditType(option.id as any)}
                              className={`flex flex-col items-center justify-center p-2 rounded-xl border text-xs transition duration-200 ${isSelected
                                ? `${option.color} ring-1 ${option.ringColor} font-bold scale-[1.03]`
                                : "border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-500"
                                }`}
                            >
                              <Icon className="size-4 mb-1" />
                              {option.label}
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    {/* Occurred At Date Picker */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-semibold text-slate-600 block">Date & Time *</label>
                      <input
                        type="datetime-local"
                        required
                        value={editOccurredAt}
                        onChange={e => setEditOccurredAt(e.target.value)}
                        className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition"
                      />
                    </div>

                    {/* Notes / Description */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-semibold text-slate-600 block">Description / Notes *</label>
                      <textarea
                        rows={6}
                        required
                        value={editDescription}
                        onChange={e => setEditDescription(e.target.value)}
                        className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-800 focus:outline-none focus:border-[#185FA5] focus:ring-1 focus:ring-[#185FA5]/20 focus:bg-white transition resize-none leading-relaxed"
                      />
                    </div>

                    {editError && (
                      <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-600 flex items-start gap-1.5">
                        <AlertCircle className="size-4 shrink-0 mt-0.5" />
                        <span>{editError}</span>
                      </div>
                    )}
                  </div>

                  {/* Footer buttons */}
                  <div className="px-6 py-4 border-t border-slate-100 flex gap-3 bg-slate-50/50">
                    <Button
                      type="submit"
                      variant="primary"
                      disabled={editLoading}
                      className="flex-1 text-xs py-2"
                    >
                      {editLoading ? (
                        <span className="flex items-center justify-center gap-1.5">
                          <Loader2 className="size-3.5 animate-spin" /> Saving...
                        </span>
                      ) : "Save Changes"}
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => setIsEditingDetail(false)}
                      className="flex-1 text-xs py-2"
                    >
                      Cancel
                    </Button>
                  </div>
                </form>
              ) : detailTab === "details" ? (
                /* View Mode Details */
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                  {/* Main Info */}
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <span className="flex size-10 items-center justify-center rounded-full bg-slate-50 border border-slate-200">
                        {selectedInteraction.type === "call" && <Phone className="size-5 text-green-600" />}
                        {selectedInteraction.type === "email" && <Mail className="size-5 text-blue-600" />}
                        {selectedInteraction.type === "meeting" && <Calendar className="size-5 text-purple-600" />}
                        {selectedInteraction.type === "note" && <FileText className="size-5 text-amber-600" />}
                      </span>
                      <div>
                        <Badge className={
                          selectedInteraction.type === "call" ? "bg-green-100 text-green-800 hover:bg-green-100 border-none font-bold" :
                            selectedInteraction.type === "email" ? "bg-blue-100 text-blue-800 hover:bg-blue-100 border-none font-bold" :
                              selectedInteraction.type === "meeting" ? "bg-purple-100 text-purple-800 hover:bg-purple-100 border-none font-bold" :
                                "bg-amber-100 text-amber-800 hover:bg-amber-100 border-none font-bold"
                        }>
                          {selectedInteraction.type.toUpperCase()}
                        </Badge>
                        <div className="text-[10px] text-slate-400 mt-1 font-semibold">
                          Logged on {formatDate(selectedInteraction.occurredAt)}
                        </div>
                      </div>
                    </div>

                    <div className="bg-slate-50 rounded-xl p-4 border border-slate-100/80">
                      <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Description / Notes</h3>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {selectedInteraction.description || "No description provided."}
                      </p>
                    </div>
                  </div>

                  {/* Metadata Grid */}
                  <div className="border-t border-slate-100 pt-6 space-y-4">
                    <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">Metadata & Relationships</h3>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-1">
                        <span className="text-[10px] text-slate-400 font-bold block">LOGGED BY</span>
                        <span className="text-xs text-slate-700 font-medium">{selectedInteraction.agentName}</span>
                      </div>
                      <div className="space-y-1">
                        <span className="text-[10px] text-slate-400 font-bold block">CREATED AT</span>
                        <span className="text-xs text-slate-700 font-medium">{formatDate(selectedInteraction.createdAt)}</span>
                      </div>
                      <div className="space-y-1 col-span-2">
                        <span className="text-[10px] text-slate-400 font-bold block">LINKED RECORD ({selectedInteraction.linkedType?.toUpperCase()})</span>
                        <span className="text-xs font-semibold text-[#185FA5]">
                          {selectedInteraction.linkedName}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                /* View Mode Audit History */
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                  {auditLoading ? (
                    <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-2 text-xs">
                      <Loader2 className="size-5 animate-spin text-[#185FA5]" />
                      <span>Loading audit history...</span>
                    </div>
                  ) : auditError ? (
                    <div className="py-12 text-center text-red-500 text-xs font-medium">
                      {auditError}
                    </div>
                  ) : auditLogs.length > 0 ? (
                    <div className="relative border-l border-slate-200 ml-4 pl-6 space-y-5">
                      {auditLogs.map((log) => (
                        <div key={log.auditId} className="relative group text-xs">
                          {/* Timeline dot */}
                          <span className={`absolute left-[-31px] top-1 flex size-4 items-center justify-center rounded-full border bg-white ${log.action === "CREATED" ? "border-green-500" : "border-[#185FA5]"
                            }`}>
                            <span className={`size-1.5 rounded-full ${log.action === "CREATED" ? "bg-green-500" : "bg-[#185FA5]"
                              }`} />
                          </span>

                          <div className="space-y-1">
                            <div className="flex justify-between items-center">
                              <span className="font-bold text-slate-700">
                                {log.action === "CREATED" ? "Created Interaction" : "Updated Field"}
                              </span>
                              <span className="text-[10px] text-slate-400 font-medium">
                                {formatDate(log.timestamp)}
                              </span>
                            </div>

                            <div className="text-[10px] text-slate-400">
                              By <strong className="text-slate-600">{log.changedByName}</strong> ({log.changedByRole || "N/A"})
                            </div>

                            {log.action === "CREATED" ? (
                              <p className="mt-1 text-[11px] text-slate-600 leading-relaxed bg-slate-50 p-2 rounded border border-slate-100">
                                {log.newValue}
                              </p>
                            ) : (
                              <div className="mt-2 space-y-1 bg-slate-50 p-2.5 rounded border border-slate-100">
                                <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wide">
                                  {log.fieldName === "interactionType" ? "Interaction Type" :
                                    log.fieldName === "description" ? "Description / Notes" :
                                      log.fieldName === "occurredAt" ? "Interaction Date" :
                                        log.fieldName === "lead" ? "Linked Lead" :
                                          log.fieldName === "customer" ? "Linked Customer" :
                                            log.fieldName === "deal" ? "Linked Deal" :
                                              log.fieldName}
                                </div>
                                <div className="grid grid-cols-1 gap-1 text-[11px]">
                                  {log.oldValue && (
                                    <div className="text-red-600 line-through bg-red-50 px-1.5 py-0.5 rounded leading-relaxed wrap-break-word">
                                      {log.oldValue}
                                    </div>
                                  )}
                                  <div className="text-green-700 bg-green-50 px-1.5 py-0.5 rounded font-medium leading-relaxed wrap-break-word">
                                    {log.newValue || "(none)"}
                                  </div>
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="py-12 text-center text-slate-400 text-xs">
                      No audit history found.
                    </div>
                  )}
                </div>
              )}

              {/* View Mode Footer */}
              {!isEditingDetail && (
                <div className="px-6 py-4 border-t border-slate-100 flex justify-end bg-slate-50/50">
                  <Button
                    variant="ghost"
                    onClick={() => setSelectedInteraction(null)}
                    className="text-xs py-1.5 px-4"
                  >
                    Close Details
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

