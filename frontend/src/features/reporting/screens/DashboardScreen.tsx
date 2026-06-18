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
  Plus,
  Sparkles
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

export type FollowUpTask = {
  id: string;
  title: string;
  description: string;
  dueDate: string;
  priority: "high" | "medium" | "low";
  status: "pending" | "completed" | "overdue";
  linkedEntityName: string;
};

export type InteractionTimeline = {
  id: string;
  type: "call" | "email" | "meeting" | "note";
  date: string;
  description: string;
  agentName: string;
  linkedName: string;
};

const initialTasks: FollowUpTask[] = [
  {
    id: "task-1",
    title: "Follow up on Corporate Group inquiry",
    description: "Call back event planner for Hilton corporate booking",
    dueDate: "Today",
    priority: "high",
    status: "pending",
    linkedEntityName: "Hilton Corporate Group",
  },
  {
    id: "task-2",
    title: "Send quotation for VIP room block",
    description: "Draft custom contract rates for wedding block (40 rooms)",
    dueDate: "Today",
    priority: "medium",
    status: "pending",
    linkedEntityName: "Victoria Wedding",
  },
  {
    id: "task-3",
    title: "Approve front desk handover log",
    description: "Review night auditor handover logs for operational deviations",
    dueDate: "Today",
    priority: "low",
    status: "completed",
    linkedEntityName: "Front Desk Shift A",
  },
  {
    id: "task-4",
    title: "Check SLA alert for VIP booking",
    description: "Booking response time exceeded 2 hours limit",
    dueDate: "Overdue",
    priority: "high",
    status: "overdue",
    linkedEntityName: "John Hammond (VIP)",
  },
];

const initialInteractions: InteractionTimeline[] = [
  {
    id: "int-1",
    type: "call",
    date: "10m ago",
    description: "Discussed corporate rates and site visit schedule. Event planner was very pleased with the proposed terms.",
    agentName: "John Doe",
    linkedName: "Hilton Corporate Group",
  },
  {
    id: "int-2",
    type: "email",
    date: "1h ago",
    description: "Sent room block contract draft with custom group discount options (15% off rack rate).",
    agentName: "John Doe",
    linkedName: "Victoria Wedding",
  },
  {
    id: "int-3",
    type: "meeting",
    date: "Yesterday",
    description: "Conducted virtual tour of the presidential suite. Client wants to proceed to proposal stage.",
    agentName: "Sarah Connor",
    linkedName: "Grand Majestic Banquet",
  },
  {
    id: "int-4",
    type: "note",
    date: "2d ago",
    description: "Guest requested high-floor room away from elevator and early check-in at 11:00 AM.",
    agentName: "John Doe",
    linkedName: "Alice Cooper",
  },
];

export function DashboardScreen() {
  const [tasks, setTasks] = useState<FollowUpTask[]>(initialTasks);
  const [interactions, setInteractions] = useState<InteractionTimeline[]>(initialInteractions);

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
  const activeLeadsCount = 12;
  const activeDealsValue = 104500;
  const pendingTasksCount = tasks.filter(t => t.status !== "completed").length;
  const overdueTasksCount = tasks.filter(t => t.status === "overdue").length;

  return (
    <div className="space-y-6">
      {/* Welcome Banner: Modern Theme-Aware Panel */}
      <div className="relative overflow-hidden bg-zinc-100 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-6 text-zinc-800 dark:text-zinc-100 shadow-xs">
        <div className="absolute top-0 right-0 w-80 h-80 rounded-full bg-primary/5 dark:bg-primary/10 blur-[80px] pointer-events-none" />
        <div className="absolute -bottom-20 -left-20 w-60 h-60 rounded-full bg-emerald-500/5 blur-[80px] pointer-events-none" />
        
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-primary/10 dark:bg-primary/20 border border-primary/20 dark:border-primary/30 text-[10px] font-bold text-primary dark:text-primary-foreground tracking-wider uppercase mb-3">
              <Sparkles className="size-3" />
              <span>Direct Sales Active</span>
            </div>
            <h1 className="text-xl md:text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">Welcome back, John!</h1>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
              Here is the status of your hotel sales pipeline and follow-up activities for today.
            </p>
          </div>
          <div className="flex items-center gap-2.5">
            <span className="text-xs text-zinc-500 dark:text-zinc-400 font-semibold bg-zinc-200/50 dark:bg-zinc-800/60 border border-zinc-300/40 dark:border-zinc-700/35 px-2.5 py-1 rounded-lg">
              June 12, 2026
            </span>
          </div>
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1 */}
        <Card className="hover:-translate-y-0.5 transition-all duration-300">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Active Leads</p>
                <h3 className="text-2xl font-bold text-foreground mt-1.5">{activeLeadsCount} Leads</h3>
                <span className="text-[10px] text-emerald-500 font-semibold flex items-center gap-0.5 mt-1.5">
                  <TrendingUp className="size-3" /> +12.5% this week
                </span>
              </div>
              <div className="p-2 bg-primary/10 text-primary dark:bg-primary/20 dark:text-primary-foreground rounded-lg animate-pulse-slow">
                <Users className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2 */}
        <Card className="hover:-translate-y-0.5 transition-all duration-300">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Active Deals Pipeline</p>
                <h3 className="text-2xl font-bold text-foreground mt-1.5">
                  ${activeDealsValue.toLocaleString('en-US')}
                </h3>
                <span className="text-[10px] text-emerald-500 font-semibold flex items-center gap-0.5 mt-1.5">
                  <TrendingUp className="size-3" /> 5 active bookings
                </span>
              </div>
              <div className="p-2 bg-emerald-500/10 text-emerald-500 dark:bg-emerald-500/20 dark:text-emerald-400 rounded-lg">
                <Briefcase className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3 */}
        <Card className="hover:-translate-y-0.5 transition-all duration-300">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Pending Activities</p>
                <h3 className="text-2xl font-bold text-foreground mt-1.5">{pendingTasksCount} Tasks</h3>
                {overdueTasksCount > 0 ? (
                  <span className="text-[10px] bg-danger/10 text-danger px-1.5 py-0.5 rounded-md font-semibold inline-block mt-1.5 border border-danger/15">
                    {overdueTasksCount} overdue
                  </span>
                ) : (
                  <span className="text-[10px] text-zinc-400 font-semibold inline-block mt-1.5">All on track</span>
                )}
              </div>
              <div className="p-2 bg-amber-500/10 text-amber-500 dark:bg-amber-500/20 dark:text-amber-400 rounded-lg">
                <AlertCircle className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* KPI 4 */}
        <Card className="hover:-translate-y-0.5 transition-all duration-300">
          <CardContent className="pt-5">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">SLA Compliance Rate</p>
                <h3 className="text-2xl font-bold text-foreground mt-1.5">91.8%</h3>
                <span className="text-[10px] text-primary font-semibold flex items-center gap-0.5 mt-1.5">
                  Target threshold 90%
                </span>
              </div>
              <div className="p-2 bg-indigo-500/10 text-indigo-500 dark:bg-indigo-500/20 dark:text-indigo-400 rounded-lg">
                <Clock className="size-5" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Charts & Visualizations Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Pipeline Stage Distribution */}
        <Card className="lg:col-span-2">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-bold text-foreground">Sales Funnel Distribution</CardTitle>
            <CardDescription className="text-xs text-muted-foreground">
              Value and count of deals distributed across the hotel sales stages
            </CardDescription>
          </CardHeader>
          <CardContent>
            {/* SVG Visual Stage Chart */}
            <div className="space-y-3.5 pt-2">
              {[
                { stage: "Inquiry", count: 1, value: 18000, color: "bg-primary/80" },
                { stage: "Site Visit", count: 1, value: 35000, color: "bg-accent/80" },
                { stage: "Proposal", count: 1, value: 12500, color: "bg-indigo-500/80" },
                { stage: "Negotiation", count: 1, value: 24000, color: "bg-pink-500/80" },
                { stage: "Contract", count: 1, value: 9500, color: "bg-warning/80" },
                { stage: "Confirmed", count: 1, value: 5500, color: "bg-success/80" }
              ].map((stage, idx) => (
                <div key={idx} className="space-y-1">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-foreground/80">{stage.stage}</span>
                    <span className="text-muted-foreground text-[10px]">
                      {stage.count} deal ({((stage.value / 104500) * 100).toFixed(0)}%) • <strong className="text-foreground/90">${stage.value.toLocaleString('en-US')}</strong>
                    </span>
                  </div>
                  <div className="w-full bg-muted rounded-lg h-3 overflow-hidden flex">
                    <div
                      className={`${stage.color} h-full rounded-lg transition-all duration-500`}
                      style={{ width: `${(stage.value / 35000) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* Custom SVG Curved Revenue Forecast Line Chart */}
            <div className="mt-8 border-t border-border pt-6">
              <div className="flex justify-between items-center mb-4">
                <div>
                  <h4 className="text-xs font-bold text-foreground">Weighted Revenue Forecast</h4>
                  <p className="text-[10px] text-muted-foreground">Projected win values based on historical deal stages</p>
                </div>
                <div className="flex gap-4 text-[10px] font-semibold text-muted-foreground">
                  <span className="flex items-center gap-1"><span className="size-2 bg-primary rounded-full"></span> Forecast</span>
                  <span className="flex items-center gap-1"><span className="size-2 border-b border-dashed border-success"></span> Target Goal</span>
                </div>
              </div>

              {/* Responsive SVG Sparkline / Area Chart */}
              <div className="h-44 w-full">
                <svg className="w-full h-full" viewBox="0 0 500 120" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.15"/>
                      <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.0"/>
                    </linearGradient>
                  </defs>
                  
                  {/* Grid Lines */}
                  <line x1="0" y1="30" x2="500" y2="30" stroke="var(--chart-grid)" strokeWidth="1" />
                  <line x1="0" y1="60" x2="500" y2="60" stroke="var(--chart-grid)" strokeWidth="1" />
                  <line x1="0" y1="90" x2="500" y2="90" stroke="var(--chart-grid)" strokeWidth="1" />
                  
                  {/* Area path */}
                  <path
                    d="M 0 100 Q 100 85, 200 45 T 400 30 L 500 10 L 500 120 L 0 120 Z"
                    fill="url(#chartGrad)"
                  />
                  
                  {/* Forecast Line */}
                  <path
                    d="M 0 100 Q 100 85, 200 45 T 400 30 L 500 10"
                    fill="none"
                    stroke="var(--primary)"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                  />

                  {/* Target Goal Line */}
                  <line x1="0" y1="40" x2="500" y2="40" stroke="var(--success)" strokeWidth="1.5" strokeDasharray="4,4" />

                  {/* Nodes */}
                  <circle cx="200" cy="45" r="4" fill="var(--primary)" stroke="var(--background)" strokeWidth="1.5" />
                  <circle cx="400" cy="30" r="4" fill="var(--primary)" stroke="var(--background)" strokeWidth="1.5" />
                  <circle cx="500" cy="10" r="4" fill="var(--primary)" stroke="var(--background)" strokeWidth="1.5" />
                </svg>
                {/* Labels */}
                <div className="flex justify-between text-[10px] text-muted-foreground font-semibold mt-1.5">
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
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-sm font-bold text-foreground">Tasks Queue</CardTitle>
              <CardDescription className="text-xs text-muted-foreground">Due today or outstanding</CardDescription>
            </div>
            <Badge variant="primary" className="text-[10px]">
              {tasks.filter(t => t.status !== "completed").length} Active
            </Badge>
          </CardHeader>
          <CardContent className="px-2">
            <div className="divide-y divide-border">
              {tasks.map(task => (
                <div key={task.id} className="py-2.5 px-3 flex items-start gap-2.5 hover:bg-muted/50 rounded-xl transition-all duration-150">
                  <button
                    onClick={() => handleToggleTask(task.id)}
                    className="mt-0.5 shrink-0 focus:outline-none cursor-pointer"
                  >
                    <CheckCircle2
                      className={`size-4.5 transition-all ${
                        task.status === "completed"
                          ? "text-emerald-500 fill-emerald-500/20"
                          : task.status === "overdue"
                          ? "text-danger"
                          : "text-zinc-300 dark:text-zinc-700"
                      }`}
                    />
                  </button>
                  <div className="flex-1 min-w-0">
                    <p
                      className={`text-xs font-bold text-foreground/90 truncate ${
                        task.status === "completed" ? "line-through text-muted-foreground/60 font-normal" : ""
                      }`}
                    >
                      {task.title}
                    </p>
                    <p className="text-[10px] text-muted-foreground mt-0.5 truncate">{task.description}</p>
                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-[9px] text-muted-foreground font-semibold bg-muted px-1.5 py-0.5 rounded">
                        {task.linkedEntityName}
                      </span>
                      <span
                        className={`text-[9px] font-bold px-1.5 py-0.2 rounded border ${
                          task.priority === "high"
                            ? "bg-danger/10 text-danger border-danger/10"
                            : task.priority === "medium"
                            ? "bg-amber-500/10 text-amber-500 border-amber-500/10"
                            : "bg-zinc-500/10 text-zinc-500 border-zinc-500/10"
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
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-sm font-bold text-foreground">Recent Sales Interactions</CardTitle>
            <CardDescription className="text-xs text-muted-foreground">
              Live updates of customer outreach and timeline updates
            </CardDescription>
          </CardHeader>
          <CardContent className="px-2">
            <div className="relative border-l border-border ml-5 pl-6 space-y-5">
              {interactions.map((interaction, idx) => (
                <div key={interaction.id} className="relative">
                  {/* Timeline icon */}
                  <span className="absolute -left-9.5 top-0.5 flex size-7 items-center justify-center rounded-full bg-background border border-border shadow-xs">
                    {interaction.type === "call" && <Phone className="size-3.5 text-primary" />}
                    {interaction.type === "email" && <Mail className="size-3.5 text-emerald-500" />}
                    {interaction.type === "meeting" && <Calendar className="size-3.5 text-indigo-500" />}
                    {interaction.type === "note" && <FileText className="size-3.5 text-amber-500" />}
                  </span>
                  
                  <div>
                    <div className="flex justify-between items-center text-xs">
                      <p className="font-bold text-foreground/90">
                        {interaction.type.toUpperCase()} Logged for{" "}
                        <span className="text-primary hover:underline cursor-pointer">{interaction.linkedName}</span>
                      </p>
                      <span className="text-muted-foreground text-[10px]">{interaction.date}</span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">{interaction.description}</p>
                    <div className="flex items-center gap-1.5 mt-2">
                      <span className="size-4 rounded-full bg-primary/20 text-primary border border-primary/25 text-[8px] font-bold flex items-center justify-center">
                        {interaction.agentName.slice(0,2).toUpperCase()}
                      </span>
                      <span className="text-[10px] text-muted-foreground">by {interaction.agentName}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Hot Leads / Quick Stats Summary Panel */}
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-bold text-foreground">Sales Conversion Metrics</CardTitle>
            <CardDescription className="text-xs text-muted-foreground">Team performance benchmarks</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="p-3 bg-muted/50 border border-border/40 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-muted-foreground">Avg response speed</p>
                  <p className="text-base font-bold text-foreground mt-0.5">1.4 hours</p>
                </div>
                <Badge variant="success" className="font-semibold">
                  Excellent
                </Badge>
              </div>

              <div className="p-3 bg-muted/50 border border-border/40 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-muted-foreground">Avg Deal Size</p>
                  <p className="text-base font-bold text-foreground mt-0.5">$18,400</p>
                </div>
                <Badge variant="primary" className="font-semibold">
                  +8% MoM
                </Badge>
              </div>

              <div className="p-3 bg-muted/50 border border-border/40 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-muted-foreground">Win Rate</p>
                  <p className="text-base font-bold text-foreground mt-0.5">38.4%</p>
                </div>
                <Badge variant="success" className="font-semibold">
                  Top 10%
                </Badge>
              </div>

              <div className="pt-2 border-t border-border mt-4">
                <h4 className="text-xs font-bold text-foreground mb-3">Team Activity Leaderboard</h4>
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2">
                      <span className="size-5 rounded-full bg-primary/20 text-primary border border-primary/20 text-[9px] font-bold flex items-center justify-center">JD</span>
                      <span className="font-semibold text-foreground/90">John Doe</span>
                    </span>
                    <span className="text-muted-foreground text-[10px]">14 Actions</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2">
                      <span className="size-5 rounded-full bg-emerald-500/20 text-emerald-500 border border-emerald-500/20 text-[9px] font-bold flex items-center justify-center">SC</span>
                      <span className="font-semibold text-foreground/90">Sarah Connor</span>
                    </span>
                    <span className="text-muted-foreground text-[10px]">12 Actions</span>
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
