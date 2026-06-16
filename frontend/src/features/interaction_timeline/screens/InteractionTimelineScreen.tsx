"use client";

import React, { useState, useMemo } from "react";
import { Phone, Mail, Calendar, FileText, Search, Plus, Filter, MessageSquare, Clock } from "lucide-react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
export type InteractionTimeline = {
  id: string;
  type: "call" | "email" | "meeting" | "note";
  date: string;
  description: string;
  agentName: string;
  linkedName: string;
};

export function InteractionTimelineScreen() {
  const [interactions, setInteractions] = useState<InteractionTimeline[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("all");

  const filteredInteractions = useMemo(() => {
    return interactions.filter(item => {
      const matchesSearch =
        item.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
        item.linkedName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        item.agentName.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesType = typeFilter === "all" || item.type === typeFilter;

      return matchesSearch && matchesType;
    });
  }, [interactions, searchTerm, typeFilter]);

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
              placeholder="Search comments, guests, sales agents..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>

          {/* Type filter buttons */}
          <div className="flex gap-2">
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

          <div className="md:ml-auto text-xs text-slate-400">
            Total records: <strong className="text-slate-700">{filteredInteractions.length}</strong>
          </div>
        </CardContent>
      </Card>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="px-6 py-8">
          {filteredInteractions.length > 0 ? (
            <div className="relative border-l border-slate-200 ml-6 pl-8 space-y-6">
              {filteredInteractions.map((item, idx) => (
                <div key={item.id} className="relative">
                  {/* Timeline icon */}
                  <span className="absolute left-[-45px] top-0.5 flex size-8 items-center justify-center rounded-full bg-white border border-slate-200 shadow-sm">
                    {item.type === "call" && <Phone className="size-4 text-blue-500" />}
                    {item.type === "email" && <Mail className="size-4 text-emerald-500" />}
                    {item.type === "meeting" && <Calendar className="size-4 text-purple-500" />}
                    {item.type === "note" && <FileText className="size-4 text-amber-500" />}
                  </span>

                  <div>
                    <div className="flex justify-between items-center text-xs">
                      <p className="font-bold text-slate-800">
                        {item.type.toUpperCase()} Logged for{" "}
                        <span className="text-blue-600 hover:underline cursor-pointer">{item.linkedName}</span>
                      </p>
                      <span className="text-slate-400 text-[10px] flex items-center gap-1 font-semibold">
                        <Clock className="size-3" />
                        {item.date}
                      </span>
                    </div>
                    <p className="text-xs text-slate-600 mt-1.5 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100/50">
                      {item.description}
                    </p>
                    <div className="flex items-center gap-1.5 mt-2">
                      <span className="size-5 rounded-full bg-blue-100 text-blue-700 text-[9px] font-bold flex items-center justify-center">
                        {item.agentName.slice(0, 2).toUpperCase()}
                      </span>
                      <span className="text-[10px] text-slate-400">Logged by <strong className="text-slate-600">{item.agentName}</strong></span>
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
    </div>
  );
}
