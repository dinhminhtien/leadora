"use client";

import React, { useState } from "react";
import {
  TrendingUp,
  TrendingDown,
  Users,
  Briefcase,
  AlertCircle,
  CheckCircle2,
  Clock,
  ArrowRight,
  Phone,
  Mail,
  Calendar,
  FileText,
  UserCheck,
  ChevronRight,
  Plus
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { mockDb, type FollowUpTask } from "@/shared/mock/mockData";

export function DashboardScreen() {
  const [tasks, setTasks] = useState<FollowUpTask[]>(mockDb.tasks);

  // Toggle task status
  const handleToggleTask = (taskId: string) => {
    setTasks(prev =>
      prev.map(task =>
        task.id === taskId
          ? { ...task, status: task.status === "completed" ? "pending" : "completed" }
          : task
      )
    );
  };

  // KPI Calculations
  const activeLeadsCount = mockDb.leads.filter(l => l.status !== "lost").length;
  const activeDealsValue = mockDb.deals
    .filter(d => d.status === "active")
    .reduce((sum, d) => sum + d.value, 0);
  const pendingTasksCount = tasks.filter(t => t.status === "pending").length;
  const overdueTasksCount = tasks.filter(t => t.status === "overdue").length;

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-linear-to-r from-blue-700 to-indigo-800 rounded-2xl p-6 text-white shadow-md">
        <div>
          <h1 className="text-xl md:text-2xl font-bold tracking-tight">Welcome back, John!</h1>
          <p className="text-xs md:text-sm text-blue-100 mt-1">
            Here is the status of your hotel sales pipeline and follow-up activities for today.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="success" className="bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-medium text-xs px-2.5 py-1">
            System Online
          </Badge>
          <span className="text-xs text-blue-200">June 12, 2026</span>
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1 */}
        <Card className="border-slate-100 hover:shadow-md transition">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Leads</p>
                <h3 className="text-2xl font-bold text-slate-800 mt-1">{activeLeadsCount} Leads</h3>
                <span className="text-[10px] text-emerald-600 font-semibold flex items-center gap-0.5 mt-1.5">
                  <TrendingUp className="size-3" /> +12.5% this week
                </span>
              </div>
              <div className="p-2.5 bg-blue-50 text-blue-600 rounded-xl">
                <Users className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2 */}
        <Card className="border-slate-100 hover:shadow-md transition">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Deals Pipeline</p>
                <h3 className="text-2xl font-bold text-slate-800 mt-1">
                  ${activeDealsValue.toLocaleString()}
                </h3>
                <span className="text-[10px] text-emerald-600 font-semibold flex items-center gap-0.5 mt-1.5">
                  <TrendingUp className="size-3" /> 5 active bookings
                </span>
              </div>
              <div className="p-2.5 bg-emerald-50 text-emerald-600 rounded-xl">
                <Briefcase className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3 */}
        <Card className="border-slate-100 hover:shadow-md transition">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Pending Activities</p>
                <h3 className="text-2xl font-bold text-slate-800 mt-1">{pendingTasksCount} Tasks</h3>
                {overdueTasksCount > 0 ? (
                  <span className="text-[10px] bg-red-50 text-red-600 px-1.5 py-0.5 rounded-full font-semibold inline-block mt-1">
                    {overdueTasksCount} overdue
                  </span>
                ) : (
                  <span className="text-[10px] text-slate-400 font-semibold inline-block mt-1">All on track</span>
                )}
              </div>
              <div className="p-2.5 bg-amber-50 text-amber-600 rounded-xl">
                <AlertCircle className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 4 */}
        <Card className="border-slate-100 hover:shadow-md transition">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">SLA Compliance Rate</p>
                <h3 className="text-2xl font-bold text-slate-800 mt-1">91.8%</h3>
                <span className="text-[10px] text-blue-600 font-semibold flex items-center gap-0.5 mt-1.5">
                  Target threshold 90%
                </span>
              </div>
              <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-xl">
                <Clock className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Charts & Visualizations Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Pipeline Stage Distribution */}
        <Card className="lg:col-span-2 border-slate-100 shadow-sm bg-white">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-bold text-slate-800">Sales Funnel Distribution</CardTitle>
            <CardDescription className="text-xs text-slate-400">
              Value and count of deals distributed across the hotel sales stages
            </CardDescription>
          </CardHeader>
          <CardContent>
            {/* SVG Visual Stage Chart */}
            <div className="space-y-4 pt-2">
              {[
                { stage: "Inquiry", count: 1, value: 18000, pct: 25, color: "bg-blue-400" },
                { stage: "Site Visit", count: 1, value: 35000, pct: 50, color: "bg-indigo-400" },
                { stage: "Proposal", count: 1, value: 12500, pct: 35, color: "bg-purple-400" },
                { stage: "Negotiation", count: 1, value: 24000, pct: 45, color: "bg-pink-400" },
                { stage: "Contract", count: 1, value: 9500, pct: 15, color: "bg-amber-400" },
                { stage: "Confirmed", count: 1, value: 5500, pct: 10, color: "bg-emerald-400" }
              ].map((stage, idx) => (
                <div key={idx} className="space-y-1">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-slate-700">{stage.stage}</span>
                    <span className="text-slate-400">
                      {stage.count} deal ({((stage.value / 104500) * 100).toFixed(0)}%) • <strong className="text-slate-700">${stage.value.toLocaleString()}</strong>
                    </span>
                  </div>
                  <div className="w-full bg-slate-100 rounded-full h-3.5 overflow-hidden flex">
                    <div
                      className={`${stage.color} h-full rounded-full transition-all duration-500`}
                      style={{ width: `${(stage.value / 35000) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* Custom SVG Curved Revenue Forecast Line Chart */}
            <div className="mt-8 border-t border-slate-100 pt-6">
              <div className="flex justify-between items-center mb-4">
                <div>
                  <h4 className="text-xs font-bold text-slate-800">Weighted Revenue Forecast</h4>
                  <p className="text-[10px] text-slate-400">Projected win values based on historical deal stages</p>
                </div>
                <div className="flex gap-4 text-[10px] font-semibold">
                  <span className="flex items-center gap-1"><span className="size-2 bg-blue-600 rounded-full"></span> Forecast</span>
                  <span className="flex items-center gap-1"><span className="size-2 bg-emerald-500 rounded-full"></span> Target Goal</span>
                </div>
              </div>

              {/* Responsive SVG Sparkline / Area Chart */}
              <div className="h-44 w-full">
                <svg className="w-full h-full" viewBox="0 0 500 120" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity="0.2"/>
                      <stop offset="100%" stopColor="#2563eb" stopOpacity="0.0"/>
                    </linearGradient>
                  </defs>
                  
                  {/* Grid Lines */}
                  <line x1="0" y1="30" x2="500" y2="30" stroke="#f1f5f9" strokeWidth="1" />
                  <line x1="0" y1="60" x2="500" y2="60" stroke="#f1f5f9" strokeWidth="1" />
                  <line x1="0" y1="90" x2="500" y2="90" stroke="#f1f5f9" strokeWidth="1" />
                  
                  {/* Area path */}
                  <path
                    d="M 0 100 Q 100 85, 200 45 T 400 30 L 500 10 L 500 120 L 0 120 Z"
                    fill="url(#chartGrad)"
                  />
                  
                  {/* Forecast Line */}
                  <path
                    d="M 0 100 Q 100 85, 200 45 T 400 30 L 500 10"
                    fill="none"
                    stroke="#2563eb"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                  />

                  {/* Target Goal Line */}
                  <line x1="0" y1="40" x2="500" y2="40" stroke="#10b981" strokeWidth="1.5" strokeDasharray="4,4" />

                  {/* Nodes */}
                  <circle cx="200" cy="45" r="4" fill="#2563eb" stroke="#ffffff" strokeWidth="1.5" />
                  <circle cx="400" cy="30" r="4" fill="#2563eb" stroke="#ffffff" strokeWidth="1.5" />
                  <circle cx="500" cy="10" r="4" fill="#2563eb" stroke="#ffffff" strokeWidth="1.5" />
                </svg>
                {/* Labels */}
                <div className="flex justify-between text-[10px] text-slate-400 font-semibold mt-1">
                  <span>Jan</span>
                  <span>Feb</span>
                  <span>Mar</span>
                  <span>Apr</span>
                  <span>May</span>
                  <span>Jun (Current)</span>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Task Queue Due Today */}
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-sm font-bold text-slate-800">Tasks Queue</CardTitle>
              <CardDescription className="text-xs text-slate-400">Due today or outstanding</CardDescription>
            </div>
            <Badge variant="primary" className="text-[10px]">
              {tasks.filter(t => t.status !== "completed").length} Active
            </Badge>
          </CardHeader>
          <CardContent className="px-2">
            <div className="divide-y divide-slate-100">
              {tasks.map(task => (
                <div key={task.id} className="py-3 px-4 flex items-start gap-3 hover:bg-slate-50 rounded-xl transition">
                  <button
                    onClick={() => handleToggleTask(task.id)}
                    className="mt-0.5 shrink-0 focus:outline-none"
                  >
                    <CheckCircle2
                      className={`size-4.5 ${
                        task.status === "completed"
                          ? "text-emerald-500 fill-emerald-50"
                          : task.status === "overdue"
                          ? "text-red-400"
                          : "text-slate-300"
                      }`}
                    />
                  </button>
                  <div className="flex-1 min-w-0">
                    <p
                      className={`text-xs font-bold text-slate-700 truncate ${
                        task.status === "completed" ? "line-through text-slate-400 font-normal" : ""
                      }`}
                    >
                      {task.title}
                    </p>
                    <p className="text-[10px] text-slate-400 mt-0.5 truncate">{task.description}</p>
                    <div className="flex items-center gap-2 mt-1.5">
                      <span className="text-[9px] text-slate-500 font-medium bg-slate-100 px-1.5 py-0.5 rounded">
                        {task.linkedEntityName}
                      </span>
                      <span
                        className={`text-[9px] font-semibold px-1 rounded ${
                          task.priority === "high"
                            ? "bg-red-50 text-red-600"
                            : task.priority === "medium"
                            ? "bg-amber-50 text-amber-600"
                            : "bg-slate-100 text-slate-600"
                        }`}
                      >
                        {task.priority.toUpperCase()}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Activities & Recent Interactions Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2 border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Recent Sales Interactions</CardTitle>
            <CardDescription className="text-xs text-slate-400">
              Live updates of customer outreach and timeline updates
            </CardDescription>
          </CardHeader>
          <CardContent className="px-2">
            <div className="relative border-l border-slate-200 ml-5 pl-6 space-y-5">
              {mockDb.interactions.map((interaction, idx) => (
                <div key={interaction.id} className="relative">
                  {/* Timeline icon */}
                  <span className="absolute -left-9.5 top-0.5 flex size-7 items-center justify-center rounded-full bg-white border border-slate-200 shadow-sm">
                    {interaction.type === "call" && <Phone className="size-3.5 text-blue-500" />}
                    {interaction.type === "email" && <Mail className="size-3.5 text-emerald-500" />}
                    {interaction.type === "meeting" && <Calendar className="size-3.5 text-purple-500" />}
                    {interaction.type === "note" && <FileText className="size-3.5 text-amber-500" />}
                  </span>
                  
                  <div>
                    <div className="flex justify-between items-center text-xs">
                      <p className="font-bold text-slate-700">
                        {interaction.type.toUpperCase()} Logged for{" "}
                        <span className="text-blue-600 hover:underline cursor-pointer">{interaction.linkedName}</span>
                      </p>
                      <span className="text-slate-400 text-[10px]">{interaction.date}</span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">{interaction.description}</p>
                    <div className="flex items-center gap-1.5 mt-2">
                      <span className="size-4 rounded-full bg-slate-200 text-slate-600 text-[8px] font-bold flex items-center justify-center">
                        {interaction.agentName.slice(0,2).toUpperCase()}
                      </span>
                      <span className="text-[10px] text-slate-400">by {interaction.agentName}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Hot Leads / Quick Stats Summary Panel */}
        <Card className="border-slate-100 shadow-sm bg-white">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-slate-800">Sales Conversion Metrics</CardTitle>
            <CardDescription className="text-xs text-slate-400">Team performance benchmarks</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="p-3 bg-blue-50/50 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-slate-400">Avg response speed</p>
                  <p className="text-base font-bold text-slate-800">1.4 hours</p>
                </div>
                <Badge variant="success" className="bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Excellent
                </Badge>
              </div>

              <div className="p-3 bg-purple-50/50 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-slate-400">Avg Deal Size</p>
                  <p className="text-base font-bold text-slate-800">$18,400</p>
                </div>
                <Badge variant="primary" className="bg-blue-50 text-blue-700 border border-blue-200">
                  +8% MoM
                </Badge>
              </div>

              <div className="p-3 bg-emerald-50/50 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-slate-400">Win Rate</p>
                  <p className="text-base font-bold text-slate-800">38.4%</p>
                </div>
                <Badge variant="success" className="bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Top 10%
                </Badge>
              </div>

              <div className="pt-2">
                <h4 className="text-xs font-bold text-slate-800 mb-2">Team Activity Leaderboard</h4>
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-1.5">
                      <span className="size-5 rounded-full bg-blue-600 text-white text-[9px] font-bold flex items-center justify-center">JD</span>
                      <span className="font-semibold text-slate-700">John Doe</span>
                    </span>
                    <span className="text-slate-400">14 Actions</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-1.5">
                      <span className="size-5 rounded-full bg-emerald-600 text-white text-[9px] font-bold flex items-center justify-center">SC</span>
                      <span className="font-semibold text-slate-700">Sarah Connor</span>
                    </span>
                    <span className="text-slate-400">12 Actions</span>
                  </div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
