"use client";

import React, { useState, useMemo } from "react";
import {
  Search,
  Plus,
  Filter,
  Briefcase,
  DollarSign,
  User,
  TrendingUp,
  Percent,
  X,
  Calendar,
  CheckCircle2,
  AlertCircle
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { mockDb, type Deal } from "@/shared/mock/mockData";

export function DealListScreen() {
  const [deals, setDeals] = useState<Deal[]>(mockDb.deals);
  const [searchTerm, setSearchTerm] = useState("");
  const [stageFilter, setStageFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("active");
  const [isNewDealDrawerOpen, setIsNewDealDrawerOpen] = useState(false);

  // Form State for new deal
  const [newDeal, setNewDeal] = useState({
    title: "",
    contactName: "",
    email: "",
    phone: "",
    stage: "Inquiry" as Deal["stage"],
    value: "",
    probability: "50",
    owner: "John Doe",
    expectedClose: ""
  });

  // Filter Logic
  const filteredDeals = useMemo(() => {
    return deals.filter(deal => {
      const matchesSearch =
        deal.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        deal.contactName.toLowerCase().includes(searchTerm.toLowerCase());
      
      const matchesStage = stageFilter === "all" || deal.stage === stageFilter;
      const matchesStatus = statusFilter === "all" || deal.status === statusFilter;

      return matchesSearch && matchesStage && matchesStatus;
    });
  }, [deals, searchTerm, stageFilter, statusFilter]);

  // Statistics
  const stats = useMemo(() => {
    const activeDeals = deals.filter(d => d.status === "active");
    const activeVal = activeDeals.reduce((sum, d) => sum + d.value, 0);
    const wonDeals = deals.filter(d => d.status === "won");
    const wonVal = wonDeals.reduce((sum, d) => sum + d.value, 0);
    const totalWins = wonDeals.length;
    const totalClosed = deals.filter(d => d.status !== "active").length;
    const winRate = totalClosed > 0 ? (totalWins / totalClosed) * 100 : 0;

    return {
      activeCount: activeDeals.length,
      activeValue: activeVal,
      wonValue: wonVal,
      winRate: winRate
    };
  }, [deals]);

  // Handle Close Status change (Won/Lost)
  const handleUpdateStatus = (dealId: string, newStatus: Deal["status"]) => {
    setDeals(prev =>
      prev.map(deal =>
        deal.id === dealId
          ? {
              ...deal,
              status: newStatus,
              probability: newStatus === "won" ? 100 : 0,
              stage: newStatus === "won" ? "Confirmed" : deal.stage
            }
          : deal
      )
    );
  };

  // Form Submit
  const handleCreateDeal = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDeal.title || !newDeal.contactName) {
      alert("Please enter both Deal title and Primary Contact name.");
      return;
    }

    const created: Deal = {
      id: `D-${200 + deals.length + 1}`,
      title: newDeal.title,
      contactName: newDeal.contactName,
      email: newDeal.email || "n/a",
      phone: newDeal.phone || "n/a",
      stage: newDeal.stage,
      value: Number(newDeal.value) || 0,
      probability: Number(newDeal.probability) || 50,
      owner: newDeal.owner,
      expectedClose: newDeal.expectedClose || new Date().toISOString().split("T")[0],
      status: "active",
      createdAt: new Date().toISOString().split("T")[0]
    };

    setDeals([created, ...deals]);
    setIsNewDealDrawerOpen(false);
    // Reset Form
    setNewDeal({
      title: "",
      contactName: "",
      email: "",
      phone: "",
      stage: "Inquiry",
      value: "",
      probability: "50",
      owner: "John Doe",
      expectedClose: ""
    });
  };

  return (
    <div className="space-y-6">
      {/* Header and Quick stats */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Deals register</h1>
          <p className="text-xs text-slate-400">Manage contract sizes, closing forecast probabilities, and bookings</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsNewDealDrawerOpen(true)}
            className="gap-1 bg-blue-600 hover:bg-blue-700 font-semibold text-xs text-white"
          >
            <Plus className="size-3.5" />
            <span>New Deal</span>
          </Button>
        </div>
      </div>

      {/* Stats Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Active Deals</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.activeCount} Deals</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Pipeline Value</p>
          <p className="text-lg font-bold text-slate-800 mt-1">${stats.activeValue.toLocaleString()}</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Won Revenue</p>
          <p className="text-lg font-bold text-slate-800 mt-1">${stats.wonValue.toLocaleString()}</p>
        </div>
        <div className="px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Win Ratio (Closed)</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.winRate.toFixed(1)}%</p>
        </div>
      </div>

      {/* Filters bar */}
      <Card className="border-slate-100 shadow-sm">
        <CardContent className="py-3 px-4">
          <div className="flex flex-col md:flex-row items-center gap-3">
            {/* Search */}
            <div className="relative w-full md:w-72">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search deal name, guest..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>
            
            {/* Stage Selector */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Stage:</span>
              <Select value={stageFilter} onChange={e => setStageFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All Stages</option>
                <option value="Inquiry">Inquiry</option>
                <option value="Site Visit">Site Visit</option>
                <option value="Proposal">Proposal</option>
                <option value="Negotiation">Negotiation</option>
                <option value="Contract">Contract</option>
                <option value="Confirmed">Confirmed</option>
              </Select>
            </div>

            {/* Status Selector */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Status:</span>
              <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All (Active & Closed)</option>
                <option value="active">Active Only</option>
                <option value="won">Won Only</option>
                <option value="lost">Lost Only</option>
              </Select>
            </div>

            {/* Active Count indicator */}
            <div className="md:ml-auto text-xs text-slate-400">
              Showing <strong className="text-slate-700">{filteredDeals.length}</strong> of {deals.length} entries
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Deals list table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Deal Title</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Primary Guest</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Sales Stage</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Probability</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Deal Value</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Close Date</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Owner</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Quick Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredDeals.length > 0 ? (
              filteredDeals.map(deal => (
                <TableRow key={deal.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 font-bold text-slate-800 text-xs">
                    {deal.title}
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="text-xs text-slate-700 font-semibold">{deal.contactName}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">{deal.email}</div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant="info" className="font-bold text-[10px]">
                      {deal.stage}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <div className="inline-flex items-center gap-1 text-xs text-slate-700 font-bold">
                      <Percent className="size-3 text-slate-400" />
                      {deal.probability}%
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    ${deal.value.toLocaleString()}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-medium">
                    <div className="flex items-center gap-1">
                      <Calendar className="size-3 text-slate-400" />
                      {deal.expectedClose}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="text-xs text-slate-700 font-medium">{deal.owner}</div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={
                        deal.status === "won"
                          ? "success"
                          : deal.status === "active"
                          ? "primary"
                          : "danger"
                      }
                      size="sm"
                      className="font-bold text-[10px] uppercase"
                    >
                      {deal.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {deal.status === "active" ? (
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => handleUpdateStatus(deal.id, "won")}
                          className="px-2 py-1 bg-emerald-50 text-emerald-700 rounded text-[10px] font-bold hover:bg-emerald-100 transition"
                        >
                          Won
                        </button>
                        <button
                          onClick={() => handleUpdateStatus(deal.id, "lost")}
                          className="px-2 py-1 bg-red-50 text-red-700 rounded text-[10px] font-bold hover:bg-red-100 transition"
                        >
                          Lost
                        </button>
                      </div>
                    ) : (
                      <span className="text-[10px] text-slate-400 italic font-semibold">Closed</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={9} className="py-8 text-center text-slate-400 text-xs">
                  No deals match your current search and filter settings.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Slide-over Drawer for adding Deal */}
      {isNewDealDrawerOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => setIsNewDealDrawerOpen(false)}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Briefcase className="size-4.5 text-blue-600" />
                  Add New Sales Deal
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">Move qualified leads to deal workflow to forecast booking sales</p>
              </div>
              <button
                onClick={() => setIsNewDealDrawerOpen(false)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreateDeal} className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Deal Title *</label>
                <Input
                  required
                  placeholder="e.g. Wedding Catering Block, Corporate Conference..."
                  value={newDeal.title}
                  onChange={e => setNewDeal({ ...newDeal, title: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Primary Contact Person *</label>
                <Input
                  required
                  placeholder="e.g. Alice Jenkins"
                  value={newDeal.contactName}
                  onChange={e => setNewDeal({ ...newDeal, contactName: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Email Address</label>
                  <Input
                    type="email"
                    placeholder="contact@gmail.com"
                    value={newDeal.email}
                    onChange={e => setNewDeal({ ...newDeal, email: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Phone Number</label>
                  <Input
                    placeholder="+1 555-0100"
                    value={newDeal.phone}
                    onChange={e => setNewDeal({ ...newDeal, phone: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Deal Value ($)</label>
                  <Input
                    type="number"
                    placeholder="e.g. 15000"
                    value={newDeal.value}
                    onChange={e => setNewDeal({ ...newDeal, value: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Initial Sales Stage</label>
                  <Select
                    value={newDeal.stage}
                    onChange={e => setNewDeal({ ...newDeal, stage: e.target.value as any })}
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
                    placeholder="50"
                    value={newDeal.probability}
                    onChange={e => setNewDeal({ ...newDeal, probability: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Est. Close Date</label>
                  <Input
                    type="date"
                    value={newDeal.expectedClose}
                    onChange={e => setNewDeal({ ...newDeal, expectedClose: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
                >
                  Create active Deal
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsNewDealDrawerOpen(false)}
                  className="w-full border-slate-200 text-xs text-slate-600"
                >
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        </>
      )}
    </div>
  );
}
