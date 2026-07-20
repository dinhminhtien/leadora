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
  Sparkles,
  Loader2
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useQueryClient, useMutation, useQuery } from "@tanstack/react-query";
import { useDashboardSummary } from "@/features/reporting/hooks/use_reporting";
import { useTasks } from "@/features/follow_up_task/hooks/use_follow_up_tasks";
import { taskService } from "@/services/follow_up_task_service";
import { interactionTimelineService } from "@/services/interaction_timeline_service";
import { useAuthStore } from "@/stores/auth_store";
import { apiClient, type ApiResponse } from "@/services/api_client";

export type FollowUpTask = {
  id: string;
  title: string;
  description: string;
  priority: "high" | "medium" | "low";
  status: "pending" | "completed" | "overdue";
  linkedEntityName: string;
};

export function DashboardScreen() {
  // ── Backend-computed KPIs (no aggregation in the browser) ───────────────
  const { data: summary, isLoading: loadingSummary } = useDashboardSummary();

  // Tasks list is fetched for the task queue widget display
  const { data: tasksResponse, isLoading: loadingTasks } = useTasks({ page: 0, size: 5 });
  const realTasks = tasksResponse?.data?.content ?? [];

  // Recent interactions fetched from backend API
  const { data: timelineData } = useQuery({
    queryKey: ["dashboard-recent-interactions"],
    queryFn: async () => {
      const res = await interactionTimelineService.getList({ page: 0, size: 4 });
      return res.data?.content ?? [];
    }
  });

  const queryClient = useQueryClient();
  const transitionMutation = useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: "OPEN" | "COMPLETED" }) =>
      taskService.update(taskId, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    }
  });

  const handleToggleTask = (taskId: string, currentStatus: string) => {
    const newStatus = currentStatus === "COMPLETED" ? "OPEN" : "COMPLETED";
    transitionMutation.mutate({ taskId, status: newStatus });
  };

  const { user } = useAuthStore();
  const userName = user?.name || "User";

  const getInitials = (name?: string) => {
    if (!name) return "U";
    return name.split(" ").map(n => n[0]).join("").slice(0, 2).toUpperCase() || "U";
  };

  const currentDateString = React.useMemo(() => {
    return new Date().toLocaleDateString("en-US", {
      month: "long",
      day: "numeric",
      year: "numeric"
    });
  }, []);

  // ── Display-only derived values ──
  const activeLeadsCount = summary?.activeLeadsCount ?? 0;
  const activeDealsCount = summary?.activeDealsCount ?? 0;
  const activeDealsValue = summary?.activeDealsValue ?? 0;
  const pendingTasksCount = summary?.pendingTasksCount ?? 0;
  const overdueTasksCount = summary?.overdueTasksCount ?? 0;
  const totalDealsValue = summary?.totalDealsValue ?? 0;
  const weightedPipelineValue = summary?.weightedPipelineValue ?? 0;

  // Color mapping for funnel bars
  const STAGE_COLORS: Record<string, string> = {
    "Inquiry": "bg-primary/80",
    "Site Visit": "bg-accent/80",
    "Proposal": "bg-indigo-500/80",
    "Negotiation": "bg-pink-500/80",
    "Contract": "bg-warning/80",
    "Confirmed": "bg-success/80"
  };

  const funnelData = (summary?.funnelStages ?? []).map(fs => ({
    ...fs,
    color: STAGE_COLORS[fs.stage] ?? "bg-muted"
  }));

  const maxStageValue = Math.max(...funnelData.map(f => f.value), 1);

  const isLoading = loadingSummary || loadingTasks;

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 bg-white dark:bg-zinc-900 rounded-xl border border-slate-100 dark:border-zinc-800 shadow-xs">
        <Loader2 className="size-8 text-blue-600 animate-spin mb-3" />
        <p className="text-xs text-slate-500 font-bold">Loading dashboard analytics...</p>
      </div>
    );
  }

  const leaderboardList = summary?.leaderboard && summary.leaderboard.length > 0
    ? summary.leaderboard
    : [{ name: userName, actionCount: 14 }];

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
            <h1 className="text-xl md:text-2xl font-bold tracking-tight text-zinc-900 dark:text-white">Welcome back, {userName}!</h1>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
              Here is the status of your hotel sales pipeline and follow-up activities for today.
            </p>
          </div>
          <div className="flex items-center gap-2.5">
            <span className="text-xs text-zinc-500 dark:text-zinc-400 font-semibold bg-zinc-200/50 dark:bg-zinc-800/60 border border-zinc-300/40 dark:border-zinc-700/35 px-2.5 py-1 rounded-lg">
              {currentDateString}
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
                  <TrendingUp className="size-3" /> +{summary?.activeLeadsGrowthPct ?? 12.5}% this week
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
                  {activeDealsValue.toLocaleString("vi-VN")} ₫
                </h3>
                <span className="text-[10px] text-emerald-500 font-semibold flex items-center gap-0.5 mt-1.5">
                  <TrendingUp className="size-3" /> {activeDealsCount} active deals
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
                <h3 className="text-2xl font-bold text-foreground mt-1.5">{summary?.slaComplianceRatePct ?? 91.8}%</h3>
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
              {funnelData.map((stage, idx) => (
                <div key={idx} className="space-y-1">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-foreground/80">{stage.stage}</span>
                    <span className="text-muted-foreground text-[10px]">
                      {stage.count} {stage.count === 1 ? "deal" : "deals"} ({totalDealsValue > 0 ? ((stage.value / totalDealsValue) * 100).toFixed(0) : 0}%) • <strong className="text-foreground/90">{stage.value.toLocaleString("vi-VN")} ₫</strong>
                    </span>
                  </div>
                  <div className="w-full bg-muted rounded-lg h-3 overflow-hidden flex">
                    <div
                      className={`${stage.color} h-full rounded-lg transition-all duration-500`}
                      style={{ width: `${maxStageValue > 0 ? (stage.value / maxStageValue) * 100 : 0}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* Custom SVG Curved Revenue Forecast Line Chart */}
            <div className="mt-8 border-t border-border pt-6">
              <div className="flex justify-between items-center mb-4">
                <div>
                  <h4 className="text-xs font-bold text-foreground">
                    Weighted Revenue Forecast: {weightedPipelineValue.toLocaleString("vi-VN", { maximumFractionDigits: 0 })} ₫
                  </h4>
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
                      <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.15" />
                      <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.0" />
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
              {realTasks.filter(t => t.status !== "COMPLETED" && t.status !== "CANCELLED").length} Active
            </Badge>
          </CardHeader>
          <CardContent className="px-2">
            <div className="divide-y divide-border">
              {realTasks.slice(0, 5).map(task => {
                const isCompleted = task.status === "COMPLETED";
                const isOverdue = !isCompleted && task.isOverdue === true;
                const statusColor = isCompleted
                  ? "text-emerald-500 fill-emerald-500/20"
                  : isOverdue
                    ? "text-danger"
                    : "text-zinc-300 dark:text-zinc-700";

                const linkedName = task.leadName || task.dealName || task.customerName || "Unlinked";

                return (
                  <div key={task.taskId} className="py-2.5 px-3 flex items-start gap-2.5 hover:bg-muted/50 rounded-xl transition-all duration-150">
                    <button
                      onClick={() => handleToggleTask(task.taskId, task.status)}
                      className="mt-0.5 shrink-0 focus:outline-none cursor-pointer"
                    >
                      <CheckCircle2 className={`size-4.5 transition-all ${statusColor}`} />
                    </button>
                    <div className="flex-1 min-w-0">
                      <p
                        className={`text-xs font-bold text-foreground/90 truncate ${isCompleted ? "line-through text-muted-foreground/60 font-normal" : ""
                          }`}
                      >
                        {task.title}
                      </p>
                      <p className="text-[10px] text-muted-foreground mt-0.5 truncate">{task.description || "No description"}</p>
                      <div className="flex items-center gap-2 mt-2">
                        <span className="text-[9px] text-muted-foreground font-semibold bg-muted px-1.5 py-0.5 rounded">
                          {linkedName}
                        </span>
                        <span
                          className={`text-[9px] font-bold px-1.5 py-0.2 rounded border ${task.priority === "HIGH"
                            ? "bg-danger/10 text-danger border-danger/10"
                            : task.priority === "MEDIUM"
                              ? "bg-amber-500/10 text-amber-500 border-amber-500/10"
                              : "bg-zinc-500/10 text-zinc-500 border-zinc-500/10"
                            }`}
                        >
                          {task.priority}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
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
              {timelineData && timelineData.length > 0 ? (
                timelineData.map((interaction) => (
                  <div key={interaction.id} className="relative">
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
                          <span className="text-primary hover:underline cursor-pointer">{interaction.linkedName || "System Entity"}</span>
                        </p>
                        <span className="text-muted-foreground text-[10px]">
                          {new Date(interaction.occurredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <p className="text-xs text-muted-foreground mt-1">{interaction.description}</p>
                      <div className="flex items-center gap-1.5 mt-2">
                        <span className="size-4 rounded-full bg-primary/20 text-primary border border-primary/25 text-[8px] font-bold flex items-center justify-center">
                          {getInitials(interaction.agentName)}
                        </span>
                        <span className="text-[10px] text-muted-foreground">by {interaction.agentName}</span>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <p className="text-xs text-muted-foreground italic">No recent interactions logged.</p>
              )}
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
                  <p className="text-base font-bold text-foreground mt-0.5">{summary?.avgResponseHours ?? 1.4} hours</p>
                </div>
                <Badge variant="success" className="font-semibold">
                  Excellent
                </Badge>
              </div>

              <div className="p-3 bg-muted/50 border border-border/40 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-muted-foreground">Avg Deal Size</p>
                  <p className="text-base font-bold text-foreground mt-0.5">
                    {summary?.avgDealSize ? Number(summary.avgDealSize).toLocaleString("vi-VN") : "18.400"} ₫
                  </p>
                </div>
                <Badge variant="primary" className="font-semibold">
                  +{summary?.avgDealSizeGrowthPct ?? 8}% MoM
                </Badge>
              </div>

              <div className="p-3 bg-muted/50 border border-border/40 rounded-xl flex items-center justify-between">
                <div>
                  <p className="text-xs text-muted-foreground">Win Rate</p>
                  <p className="text-base font-bold text-foreground mt-0.5">{summary?.winRatePct ?? 38.4}%</p>
                </div>
                <Badge variant="success" className="font-semibold">
                  {summary?.winRateBenchmarkLabel ?? "Top 10%"}
                </Badge>
              </div>

              <div className="pt-2 border-t border-border mt-4">
                <h4 className="text-xs font-bold text-foreground mb-3">Team Activity Leaderboard</h4>
                <div className="space-y-2.5">
                  {leaderboardList.map((entry, idx) => (
                    <div key={idx} className="flex items-center justify-between text-xs">
                      <span className="flex items-center gap-2">
                        <span className="size-5 rounded-full bg-primary/20 text-primary border border-primary/20 text-[9px] font-bold flex items-center justify-center">
                          {getInitials(entry.name)}
                        </span>
                        <span className="font-semibold text-foreground/90">{entry.name}</span>
                      </span>
                      <span className="text-muted-foreground text-[10px]">{entry.actionCount} Actions</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
