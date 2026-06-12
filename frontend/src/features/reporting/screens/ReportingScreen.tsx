"use client";

import React, { useState } from "react";
import { TrendingUp, BarChart2, Calendar, FileText, ChevronRight, PieChart, Sparkles } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

export function ReportingScreen() {
  const [reportPeriod, setReportPeriod] = useState("this-quarter");

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Advanced Reporting & Analytics</h1>
          <p className="text-xs text-slate-400">Deep-dive insights into hotel sales pipeline performance and agent velocity</p>
        </div>
        
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-400">Reporting Range:</span>
          <select
            value={reportPeriod}
            onChange={e => setReportPeriod(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 focus:outline-none"
          >
            <option value="this-month">This Month</option>
            <option value="this-quarter">This Quarter (Q2)</option>
            <option value="this-year">This Year (2026)</option>
          </select>
        </div>
      </div>

      {/* Main reporting charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Deal Conversion Funnel */}
        <Card className="lg:col-span-2 border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Conversion Funnel Velocity</CardTitle>
            <CardDescription className="text-xs text-slate-400">Weighted stage transition rates and losses</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5 pt-2">
            {[
              { label: "1. Total Inquiries Created", count: 42, value: "$280,000", pct: "100%", wPct: 100, color: "bg-blue-600" },
              { label: "2. Site Visits Scheduled", count: 28, value: "$195,000", pct: "66.7%", wPct: 67, color: "bg-indigo-600" },
              { label: "3. Proposals Dispatched", count: 18, value: "$132,000", pct: "42.8%", wPct: 43, color: "bg-purple-600" },
              { label: "4. Contract Negotiation", count: 10, value: "$85,000", pct: "23.8%", wPct: 24, color: "bg-pink-600" },
              { label: "5. Confirmed Bookings", count: 6, value: "$45,000", pct: "14.2%", wPct: 14, color: "bg-emerald-600" }
            ].map((funnel, idx) => (
              <div key={idx} className="space-y-1">
                <div className="flex justify-between items-center text-xs font-semibold">
                  <span className="text-slate-700">{funnel.label}</span>
                  <span className="text-slate-400">
                    {funnel.count} count • <strong className="text-slate-800">{funnel.value}</strong> ({funnel.pct})
                  </span>
                </div>
                <div className="w-full bg-slate-100 rounded-full h-4 overflow-hidden flex">
                  <div
                    className={`${funnel.color} h-full rounded-full flex items-center justify-end px-2 text-[9px] text-white font-bold transition-all duration-500`}
                    style={{ width: `${funnel.wPct}%` }}
                  >
                    {funnel.wPct >= 15 ? funnel.pct : ""}
                  </div>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Lead Source Breakdown */}
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Lead Source Share</CardTitle>
            <CardDescription className="text-xs text-slate-400">Inquiry channels distribution</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 pt-2">
            {[
              { source: "Direct Website", share: "45%", count: 19, value: "$126,000", color: "bg-blue-500" },
              { source: "Referral Partners", share: "25%", count: 11, value: "$70,000", color: "bg-indigo-500" },
              { source: "Social Media Ads", share: "18%", count: 8, value: "$50,400", color: "bg-purple-500" },
              { source: "Cold outreach", share: "12%", count: 4, value: "$33,600", color: "bg-slate-400" }
            ].map((item, idx) => (
              <div key={idx} className="space-y-1">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-semibold text-slate-700 flex items-center gap-1.5">
                    <span className={`size-2.5 rounded-full ${item.color}`} />
                    {item.source}
                  </span>
                  <span className="text-slate-400 font-bold">{item.share}</span>
                </div>
                <div className="text-[10px] text-slate-400 pl-4">
                  {item.count} inquiries • <strong className="text-slate-700">{item.value}</strong>
                </div>
              </div>
            ))}
            
            {/* Custom visual ring */}
            <div className="border-t border-slate-100 pt-4 flex justify-center">
              <svg className="size-24" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#f1f5f9" strokeWidth="3.5" />
                {/* Direct Website (45%) */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#3b82f6" strokeWidth="3.5" strokeDasharray="45 55" strokeDashoffset="25" />
                {/* Referrals (25%) */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#6366f1" strokeWidth="3.5" strokeDasharray="25 75" strokeDashoffset="80" />
                {/* Social (18%) */}
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#a855f7" strokeWidth="3.5" strokeDasharray="18 82" strokeDashoffset="5" />
              </svg>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* SLA metrics & Agent table */}
      <Card className="border-slate-100 shadow-sm bg-white">
        <CardHeader>
          <CardTitle className="text-sm font-bold text-slate-800">Agent Performance Benchmarks</CardTitle>
          <CardDescription className="text-xs text-slate-400">Quarterly team speed, value, and closure metrics</CardDescription>
        </CardHeader>
        <CardContent className="px-2">
          <Table>
            <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
              <TableRow hoverable={false}>
                <TableHead className="font-semibold text-xs text-slate-500">Sales Agent</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Avg Lead Response Speed</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Deals Managed</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Total Booked Value</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">Won Ratio</TableHead>
                <TableHead className="font-semibold text-xs text-slate-500">SLA Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {[
                { name: "John Doe", speed: "1.4 Hours", count: 14, val: "$108,000", ratio: "42.8%", status: "compliant" },
                { name: "Sarah Connor", speed: "1.8 Hours", count: 12, val: "$87,000", ratio: "38.5%", status: "compliant" },
                { name: "Alex Mercer", speed: "3.2 Hours", count: 8, val: "$44,000", ratio: "25.0%", status: "warning" }
              ].map((agent, idx) => (
                <TableRow key={idx} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <div className="flex items-center gap-2">
                      <span className="size-5.5 rounded-full bg-blue-100 text-blue-700 text-[9px] font-bold flex items-center justify-center">
                        {agent.name.slice(0, 2).toUpperCase()}
                      </span>
                      {agent.name}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{agent.speed}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-medium">{agent.count} active</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">{agent.val}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-bold">{agent.ratio}</TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={agent.status === "compliant" ? "success" : "warning"} size="sm" className="font-bold text-[9px] uppercase">
                      {agent.status}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
