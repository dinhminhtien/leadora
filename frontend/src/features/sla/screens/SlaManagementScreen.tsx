"use client";

import React, { useState } from "react";
import { Gauge, Save, Bell, AlertTriangle, ShieldCheck, RefreshCw } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

export function SlaManagementScreen() {
  const [slaConfigs, setSlaConfigs] = useState([
    { id: "response", title: "New Lead Response Speed", desc: "Max time to contact a new website lead before SLA warning triggers", value: 2, unit: "Hours", active: true },
    { id: "proposal", title: "Proposal Dispatch Deadline", desc: "Max time to send catering & room quote proposals after qualification", value: 24, unit: "Hours", active: true },
    { id: "handover", title: "Operational Transfer Turnaround", desc: "Max time to complete shift handovers once a deal is won", value: 12, unit: "Hours", active: false },
    { id: "deposit", title: "Deposit Clearance Period", desc: "Max time to verify client bank wire transfers once contract is signed", value: 48, unit: "Hours", active: true }
  ]);

  const handleToggle = (id: string) => {
    setSlaConfigs(prev =>
      prev.map(sla => (sla.id === id ? { ...sla, active: !sla.active } : sla))
    );
  };

  const handleRangeChange = (id: string, newVal: number) => {
    setSlaConfigs(prev =>
      prev.map(sla => (sla.id === id ? { ...sla, value: newVal } : sla))
    );
  };

  const handleSave = () => {
    alert("SLA limits successfully updated. Live metrics engine updated.");
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">SLA Response Rules</h1>
          <p className="text-xs text-slate-400">Configure response warning thresholds to track sales team responsiveness</p>
        </div>
        <Button
          onClick={handleSave}
          variant="primary"
          size="sm"
          className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white px-4 py-2"
        >
          <Save className="size-4" />
          Save Rules
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main configs list */}
        <div className="lg:col-span-2 space-y-4">
          {slaConfigs.map(sla => (
            <Card
              key={sla.id}
              className={`border-slate-100 hover:border-slate-200 transition bg-white shadow-xs ${
                !sla.active ? "opacity-60" : ""
              }`}
            >
              <CardContent className="p-5 flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="flex-1 space-y-1">
                  <div className="flex items-center gap-2">
                    <h3 className="text-xs font-bold text-slate-800">{sla.title}</h3>
                    <Badge variant={sla.active ? "success" : "default"} size="sm" className="font-bold text-[9px] uppercase">
                      {sla.active ? "ACTIVE ENGINE" : "DISABLED"}
                    </Badge>
                  </div>
                  <p className="text-xs text-slate-400 max-w-lg leading-relaxed">{sla.desc}</p>
                  
                  {sla.active && (
                    <div className="pt-4 flex items-center gap-4">
                      <input
                        type="range"
                        min="1"
                        max={sla.id === "deposit" ? 96 : sla.id === "proposal" ? 48 : 12}
                        value={sla.value}
                        onChange={e => handleRangeChange(sla.id, Number(e.target.value))}
                        className="w-full md:w-64 accent-blue-600 h-1.5 bg-slate-100 rounded-lg cursor-pointer"
                      />
                      <span className="text-xs font-bold text-blue-600 shrink-0">
                        {sla.value} {sla.unit}
                      </span>
                    </div>
                  )}
                </div>

                <div className="shrink-0 flex items-center">
                  <button
                    onClick={() => handleToggle(sla.id)}
                    className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                      sla.active ? "bg-blue-600" : "bg-slate-200"
                    }`}
                  >
                    <span
                      className={`pointer-events-none inline-block size-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out ${
                        sla.active ? "translate-x-5" : "translate-x-0"
                      }`}
                    />
                  </button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Info panel */}
        <div className="space-y-4">
          <Card className="border-slate-100 shadow-sm bg-white">
            <CardHeader>
              <CardTitle className="text-xs font-bold text-slate-800 uppercase tracking-wider">SLA Engine Policy</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 text-xs text-slate-500 leading-relaxed">
              <div className="flex items-start gap-2">
                <ShieldCheck className="size-4 text-emerald-500 mt-0.5 shrink-0" />
                <p>
                  <strong>Automatic Escalation:</strong> When inquiries exceed the active threshold, reminders are logged, and warning banners display in dashboard alerts.
                </p>
              </div>
              <div className="flex items-start gap-2">
                <AlertTriangle className="size-4 text-amber-500 mt-0.5 shrink-0" />
                <p>
                  <strong>Target Performance:</strong> System keeps records of agent speed. Aim for a target response rate under 2.0 hours.
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
