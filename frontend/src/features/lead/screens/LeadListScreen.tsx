"use client";

import React, { useState, useMemo } from "react";
import {
  Search,
  Plus,
  Filter,
  MoreVertical,
  Handshake,
  User,
  DollarSign,
  TrendingUp,
  Mail,
  Phone,
  Briefcase,
  X,
  FileText,
  Calendar
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { mockDb } from "@/shared/mock/mockData";
import Link from "next/link";
import { useLeads, useCreateLead } from "@/features/lead/hooks/use_leads";

export function LeadListScreen() {
  const { data: leadsResponse, isLoading } = useLeads();
  const createLeadMutation = useCreateLead();

  const leads = useMemo(() => {
    const dbLeads = leadsResponse?.data ?? [];
    return dbLeads.length > 0 ? dbLeads : (mockDb.leads as any[]);
  }, [leadsResponse]);

  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [sourceFilter, setSourceFilter] = useState("all");
  const [isNewLeadDrawerOpen, setIsNewLeadDrawerOpen] = useState(false);

  // Form State for new lead
  const [newLead, setNewLead] = useState({
    name: "",
    contactName: "",
    email: "",
    phone: "",
    company: "",
    source: "Website Inquiry",
    owner: "John Doe",
    value: "",
    notes: ""
  });

  // Filter Logic
  const filteredLeads = useMemo(() => {
    return leads.filter(lead => {
      const leadName = lead.name || "";
      const leadContact = lead.contactName || "";
      const leadCompany = lead.company || "";
      const leadStatus = lead.status || "";
      const leadSource = lead.source || "";

      const matchesSearch =
        leadName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        leadContact.toLowerCase().includes(searchTerm.toLowerCase()) ||
        leadCompany.toLowerCase().includes(searchTerm.toLowerCase());
      
      const matchesStatus = statusFilter === "all" || leadStatus === statusFilter;
      const matchesSource = sourceFilter === "all" || leadSource === sourceFilter;

      return matchesSearch && matchesStatus && matchesSource;
    });
  }, [leads, searchTerm, statusFilter, sourceFilter]);

  // Statistics
  const stats = useMemo(() => {
    const active = leads.filter(l => l.status !== "lost");
    const totalVal = active.reduce((sum, l) => sum + (Number(l.value) || 0), 0);
    const qualified = leads.filter(l => l.status === "qualified").length;
    return {
      totalCount: leads.length,
      activeCount: active.length,
      totalValue: totalVal,
      qualifiedCount: qualified
    };
  }, [leads]);

  // Form Submit
  const handleCreateLead = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newLead.name || !newLead.contactName) {
      alert("Please enter both Lead name and Contact name.");
      return;
    }

    createLeadMutation.mutate({
      name: newLead.name,
      contactName: newLead.contactName,
      email: newLead.email,
      phone: newLead.phone,
      company: newLead.company,
      source: newLead.source,
      value: Number(newLead.value) || 0,
      notes: newLead.notes
    }, {
      onSuccess: () => {
        setIsNewLeadDrawerOpen(false);
        // Reset Form
        setNewLead({
          name: "",
          contactName: "",
          email: "",
          phone: "",
          company: "",
          source: "Website Inquiry",
          owner: "John Doe",
          value: "",
          notes: ""
        });
      },
      onError: (error: any) => {
        alert("Failed to create lead: " + (error?.response?.data?.message || error.message || "Unknown error"));
      }
    });
  };


  return (
    <div className="space-y-6 relative">
      {/* Header and Quick stats */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Leads Workspace</h1>
          <p className="text-xs text-slate-400">Track and qualify incoming hotel room block and banquet bookings</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsNewLeadDrawerOpen(true)}
            className="gap-1 bg-blue-600 hover:bg-blue-700 font-semibold text-xs"
          >
            <Plus className="size-3.5" />
            <span>Add Lead</span>
          </Button>
        </div>
      </div>

      {/* Stats Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 bg-white p-4 rounded-xl border border-slate-100 shadow-sm">
        <div className="border-r border-slate-100 last:border-0 pr-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Total Leads Logged</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.totalCount}</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Active Inquiries</p>
          <p className="text-lg font-bold text-slate-800 mt-1">{stats.activeCount}</p>
        </div>
        <div className="border-r border-slate-100 last:border-0 px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Total Forecast Pipeline</p>
          <p className="text-lg font-bold text-slate-800 mt-1">${stats.totalValue.toLocaleString()}</p>
        </div>
        <div className="px-4">
          <p className="text-[10px] font-semibold text-slate-400 uppercase">Qualified Rate</p>
          <p className="text-lg font-bold text-slate-800 mt-1">
            {((stats.qualifiedCount / (stats.totalCount || 1)) * 100).toFixed(0)}%
          </p>
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
                placeholder="Search lead title, name, corporate..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
              />
            </div>
            
            {/* Status Selector */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Status:</span>
              <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All Statuses</option>
                <option value="new">New</option>
                <option value="contacted">Contacted</option>
                <option value="qualified">Qualified</option>
                <option value="lost">Lost</option>
              </Select>
            </div>

            {/* Source Selector */}
            <div className="w-full md:w-44 flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400 font-bold shrink-0">Source:</span>
              <Select value={sourceFilter} onChange={e => setSourceFilter(e.target.value)} className="w-full py-1.5">
                <option value="all">All Sources</option>
                <option value="Website Inquiry">Website</option>
                <option value="Referral">Referral</option>
                <option value="Social Media">Social Media</option>
                <option value="Cold Call">Cold Call</option>
              </Select>
            </div>

            {/* Active Count indicator */}
            <div className="md:ml-auto text-xs text-slate-400">
              Showing <strong className="text-slate-700">{filteredLeads.length}</strong> of {leads.length} entries
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Grid table */}
      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Lead Opportunity</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Primary Contact</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Company</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Lead Value</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Source</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Owner</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Created</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredLeads.length > 0 ? (
              filteredLeads.map(lead => (
                <TableRow key={lead.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 font-bold text-slate-800 text-xs">
                    <Link href={`/leads/${lead.id}`} className="hover:text-blue-600 hover:underline">
                      {lead.name}
                    </Link>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="text-xs text-slate-700 font-medium">{lead.contactName}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">{lead.email}</div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium">{lead.company}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    ${(Number(lead.value) || 0).toLocaleString()}
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{lead.source}</TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="flex items-center gap-1.5 text-xs text-slate-700 font-medium">
                      <span className="size-4.5 rounded-full bg-blue-100 text-blue-700 text-[8px] font-bold flex items-center justify-center">
                        {(lead.owner || "Unassigned").slice(0, 2).toUpperCase()}
                      </span>
                      {lead.owner || "Unassigned"}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={
                        lead.status === "qualified"
                          ? "success"
                          : lead.status === "new"
                          ? "primary"
                          : lead.status === "contacted"
                          ? "warning"
                          : "danger"
                      }
                      size="sm"
                      className="font-bold text-[10px] uppercase"
                    >
                      {lead.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-400">{lead.createdAt}</TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <div className="flex justify-center items-center gap-1.5">
                      <Link href={`/leads/${lead.id}`}>
                        <Button variant="outline" size="sm" className="px-2 py-1 text-[10px] border-slate-200">
                          View
                        </Button>
                      </Link>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={9} className="py-8 text-center text-slate-400 text-xs">
                  No leads match your current search and filter settings.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Slide-over Drawer for adding Lead */}
      {isNewLeadDrawerOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-slate-900/30 backdrop-blur-xs z-40 transition-opacity"
            onClick={() => setIsNewLeadDrawerOpen(false)}
          />
          {/* Drawer Element */}
          <div className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l border-slate-200 z-50 flex flex-col animate-in slide-in-from-right duration-300">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div>
                <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <Handshake className="size-4.5 text-blue-600" />
                  Log New Sales Lead
                </h3>
                <p className="text-[10px] text-slate-400 mt-0.5">Record guest inquiries, group quotes, or banquet events</p>
              </div>
              <button
                onClick={() => setIsNewLeadDrawerOpen(false)}
                className="p-1 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              >
                <X className="size-4.5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreateLead} className="flex-1 overflow-y-auto p-6 space-y-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Lead Name / Event Title *</label>
                <Input
                  required
                  placeholder="e.g. Wedding block, Annual retreat..."
                  value={newLead.name}
                  onChange={e => setNewLead({ ...newLead, name: e.target.value })}
                  className="py-1.5 text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Guest Name *</label>
                  <Input
                    required
                    placeholder="e.g. Emily Miller"
                    value={newLead.contactName}
                    onChange={e => setNewLead({ ...newLead, contactName: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Company Name</label>
                  <Input
                    placeholder="e.g. TechCorp Inc."
                    value={newLead.company}
                    onChange={e => setNewLead({ ...newLead, company: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Email Address</label>
                  <Input
                    type="email"
                    placeholder="guest@mail.com"
                    value={newLead.email}
                    onChange={e => setNewLead({ ...newLead, email: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Phone Number</label>
                  <Input
                    placeholder="+1 555-0100"
                    value={newLead.phone}
                    onChange={e => setNewLead({ ...newLead, phone: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Est. Lead Value ($)</label>
                  <Input
                    type="number"
                    placeholder="e.g. 5000"
                    value={newLead.value}
                    onChange={e => setNewLead({ ...newLead, value: e.target.value })}
                    className="py-1.5 text-xs"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-slate-600">Source Channel</label>
                  <Select
                    value={newLead.source}
                    onChange={e => setNewLead({ ...newLead, source: e.target.value })}
                    className="py-1.5"
                  >
                    <option value="Website Inquiry">Website Inquiry</option>
                    <option value="Referral">Referral Partner</option>
                    <option value="Social Media">Social Media</option>
                    <option value="Cold Call">Cold Outreach</option>
                  </Select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-slate-600">Event Details & Guest Notes</label>
                <textarea
                  rows={4}
                  placeholder="Describe lodging quantities, catering expectations, dates and special requests..."
                  value={newLead.notes}
                  onChange={e => setNewLead({ ...newLead, notes: e.target.value })}
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                />
              </div>

              <div className="pt-4 flex gap-3 border-t border-slate-100">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full bg-blue-600 hover:bg-blue-700 text-xs font-semibold"
                >
                  Create Lead Opportunity
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsNewLeadDrawerOpen(false)}
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
