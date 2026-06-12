"use client";

import React, { useState } from "react";
import {
  Handshake,
  User,
  Mail,
  Phone,
  Building2,
  DollarSign,
  TrendingUp,
  ChevronLeft,
  Calendar,
  FileText,
  Clock,
  MessageSquare,
  Plus,
  ArrowRight,
  Sparkles
} from "lucide-react";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { mockDb, type Lead, type InteractionTimeline } from "@/shared/mock/mockData";

type LeadDetailScreenProps = {
  leadId: string;
};

export function LeadDetailScreen({ leadId }: LeadDetailScreenProps) {
  // Find lead
  const leadData = mockDb.leads.find(l => l.id === leadId) || mockDb.leads[0];
  const [lead, setLead] = useState<Lead>(leadData);
  const [interactions, setInteractions] = useState<InteractionTimeline[]>(
    mockDb.interactions.filter(i => i.linkedName === lead.name || i.linkedName.includes("Wedding") || i.linkedName.includes("TechCorp"))
  );

  // Quick Interaction Log state
  const [logType, setLogType] = useState<"call" | "email" | "meeting" | "note">("call");
  const [logNotes, setLogNotes] = useState("");

  const handleLogInteraction = (e: React.FormEvent) => {
    e.preventDefault();
    if (!logNotes) return;

    const newInteraction: InteractionTimeline = {
      id: `I-${Date.now()}`,
      type: logType,
      date: new Date().toISOString().slice(0, 16).replace("T", " "),
      description: logNotes,
      agentName: "John Doe",
      linkedName: lead.name
    };

    setInteractions([newInteraction, ...interactions]);
    setLogNotes("");
  };

  const handleConvert = () => {
    setLead(prev => ({ ...prev, status: "qualified" }));
    alert(`Success! Lead converted to an Active Deal. Status updated to 'Qualified'.`);
  };

  return (
    <div className="space-y-6">
      {/* Breadcrumbs and Actions */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div className="flex items-center gap-2">
          <Link
            href="/leads"
            className="p-1 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition"
          >
            <ChevronLeft className="size-5" />
          </Link>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded font-bold">
                {lead.id}
              </span>
              <span className="text-xs text-slate-400">Back to Leads</span>
            </div>
            <h1 className="text-lg font-bold text-slate-800 mt-0.5">{lead.name}</h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {lead.status !== "qualified" ? (
            <Button
              onClick={handleConvert}
              variant="success"
              size="sm"
              className="gap-1 bg-emerald-600 hover:bg-emerald-700 font-semibold text-xs text-white"
            >
              <TrendingUp className="size-4" />
              Convert to Deal
            </Button>
          ) : (
            <Badge variant="success" className="font-bold text-xs uppercase px-3 py-1 bg-emerald-100 text-emerald-800">
              ✓ Qualified Active Deal
            </Badge>
          )}
        </div>
      </div>

      {/* Detail Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Columns - Details */}
        <div className="lg:col-span-1 space-y-6">
          {/* Card Info */}
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="border-b border-slate-100 pb-4">
              <CardTitle className="text-xs font-bold text-slate-800 uppercase tracking-wider">Opportunity Profile</CardTitle>
              <CardDescription className="text-[10px] text-slate-400">General qualification criteria</CardDescription>
            </CardHeader>
            <CardContent className="pt-4 space-y-4">
              {/* Value */}
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase block">Estimated Contract Value</span>
                <span className="text-xl font-black text-slate-800 flex items-center gap-0.5 mt-0.5">
                  <DollarSign className="size-4 text-slate-400" />
                  {lead.value.toLocaleString()}
                </span>
              </div>

              {/* Status & Owner */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-[10px] font-bold text-slate-400 uppercase block">Status</span>
                  <div className="mt-1">
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
                    >
                      {lead.status.toUpperCase()}
                    </Badge>
                  </div>
                </div>
                <div>
                  <span className="text-[10px] font-bold text-slate-400 uppercase block">Sales Owner</span>
                  <span className="text-xs text-slate-700 font-medium block mt-1">{lead.owner}</span>
                </div>
              </div>

              {/* Source Channel */}
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase block">Source Channel</span>
                <span className="text-xs text-slate-700 font-semibold block mt-1">{lead.source}</span>
              </div>

              {/* Created */}
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase block">Created Date</span>
                <span className="text-xs text-slate-700 block mt-1">{lead.createdAt}</span>
              </div>
            </CardContent>
          </Card>

          {/* Contact Details */}
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="border-b border-slate-100 pb-4">
              <CardTitle className="text-xs font-bold text-slate-800 uppercase tracking-wider">Guest Contacts</CardTitle>
              <CardDescription className="text-[10px] text-slate-400">Primary communication details</CardDescription>
            </CardHeader>
            <CardContent className="pt-4 space-y-3.5">
              <div className="flex items-start gap-2.5">
                <User className="size-4.5 text-slate-400 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 block font-semibold">Contact Person</span>
                  <span className="text-xs text-slate-700 font-bold">{lead.contactName}</span>
                </div>
              </div>

              {lead.company && (
                <div className="flex items-start gap-2.5">
                  <Building2 className="size-4.5 text-slate-400 mt-0.5" />
                  <div>
                    <span className="text-[10px] text-slate-400 block font-semibold">Company</span>
                    <span className="text-xs text-slate-700 font-semibold">{lead.company}</span>
                  </div>
                </div>
              )}

              <div className="flex items-start gap-2.5">
                <Mail className="size-4.5 text-slate-400 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 block font-semibold">Email</span>
                  <a href={`mailto:${lead.email}`} className="text-xs text-blue-600 hover:underline">
                    {lead.email}
                  </a>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Phone className="size-4.5 text-slate-400 mt-0.5" />
                <div>
                  <span className="text-[10px] text-slate-400 block font-semibold">Phone</span>
                  <a href={`tel:${lead.phone}`} className="text-xs text-slate-700 font-semibold">
                    {lead.phone}
                  </a>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Additional Notes */}
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="pb-2">
              <CardTitle className="text-xs font-bold text-slate-800 uppercase tracking-wider">Inquiry Notes</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">
                {lead.notes || "No special requests logged for this opportunity yet."}
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Right Columns - Activity Logging & Feed */}
        <div className="lg:col-span-2 space-y-6">
          {/* Quick logger */}
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-bold text-slate-800 flex items-center gap-1.5">
                <Sparkles className="size-4 text-blue-500 animate-pulse" />
                Quick Log Activity
              </CardTitle>
              <CardDescription className="text-xs text-slate-400">Record calls, emails, or notes directly</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleLogInteraction} className="space-y-3">
                {/* Type buttons */}
                <div className="flex gap-2">
                  {[
                    { type: "call", label: "Log Call" },
                    { type: "email", label: "Log Email" },
                    { type: "meeting", label: "Log Meeting" },
                    { type: "note", label: "Quick Note" }
                  ].map(tab => (
                    <button
                      key={tab.type}
                      type="button"
                      onClick={() => setLogType(tab.type as any)}
                      className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition ${
                        logType === tab.type
                          ? "bg-blue-600 text-white border-blue-600 shadow-xs"
                          : "bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100"
                      }`}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>

                {/* Input text */}
                <div className="space-y-1">
                  <textarea
                    required
                    rows={3}
                    placeholder={`Write summary notes regarding this ${logType}...`}
                    value={logNotes}
                    onChange={e => setLogNotes(e.target.value)}
                    className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
                  />
                </div>

                <div className="flex justify-end">
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    className="bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white px-4 py-2"
                  >
                    Post Interaction
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          {/* Activity Timeline list */}
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader className="pb-3 border-b border-slate-100">
              <CardTitle className="text-sm font-bold text-slate-800">Timeline & Communications</CardTitle>
            </CardHeader>
            <CardContent className="px-2 pt-4">
              {interactions.length > 0 ? (
                <div className="relative border-l border-slate-200 ml-5 pl-6 space-y-6">
                  {interactions.map(item => (
                    <div key={item.id} className="relative">
                      {/* Timeline icon */}
                      <span className="absolute -left-9.5 top-0.5 flex size-7 items-center justify-center rounded-full bg-white border border-slate-200 shadow-sm">
                        {item.type === "call" && <Phone className="size-3.5 text-blue-500" />}
                        {item.type === "email" && <Mail className="size-3.5 text-emerald-500" />}
                        {item.type === "meeting" && <Calendar className="size-3.5 text-purple-500" />}
                        {item.type === "note" && <FileText className="size-3.5 text-amber-500" />}
                      </span>

                      <div>
                        <div className="flex justify-between items-center text-xs">
                          <p className="font-bold text-slate-700">
                            {item.type.toUpperCase()} Logged
                          </p>
                          <span className="text-slate-400 text-[10px] flex items-center gap-1">
                            <Clock className="size-3" /> {item.date}
                          </span>
                        </div>
                        <p className="text-xs text-slate-600 mt-1.5 leading-relaxed">{item.description}</p>
                        <div className="flex items-center gap-1.5 mt-2.5">
                          <span className="size-4.5 rounded-full bg-slate-100 text-slate-600 text-[8px] font-bold flex items-center justify-center">
                            {item.agentName.slice(0, 2).toUpperCase()}
                          </span>
                          <span className="text-[10px] text-slate-400">Agent: {item.agentName}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="py-8 text-center text-slate-400 text-xs">
                  No interactions logged for this lead yet. Use the log box above to post your first activity.
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
