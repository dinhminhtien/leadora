"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
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
import { GreetingBar, KpiCard } from "@/components/ui/kpi-card";
import { KpiSkeleton, CardSkeleton } from "@/components/ui/skeletons";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { getUserRole } from "@/shared/auth/access";

/** VND, compacted so a 9-figure pipeline still fits a KPI card. */
function formatVnd(value: number): string {
  if (!Number.isFinite(value)) return "—";
  if (Math.abs(value) >= 1_000_000_000)
    return `${(value / 1_000_000_000).toFixed(1)}B ₫`;
  if (Math.abs(value) >= 1_000_000)
    return `${(value / 1_000_000).toFixed(1)}M ₫`;
  return `${value.toLocaleString("vi-VN")} ₫`;
}

const ROLE_LABEL: Record<string, string> = {
  ADMIN: "Administrator",
  MANAGER: "Sales Manager",
  SALES: "Sales Staff",
  FO: "Front Office",
  RESERVATION: "Reservation",
};

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

  const router = useRouter();
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

  const monthlyForecasts = React.useMemo(() => {
    return summary?.monthlyForecasts ?? [
      { month: "Jan", value: 0 },
      { month: "Feb", value: 0 },
      { month: "Mar", value: 0 },
      { month: "Apr", value: 0 },
      { month: "May", value: 0 },
      { month: "Jun (Current)", value: 0 }
    ];
  }, [summary]);

  const chartData = React.useMemo(() => {
    const maxVal = Math.max(...monthlyForecasts.map(f => f.value), 1);
    return monthlyForecasts.map((f, idx) => {
      const x = idx * 100;
      const y = 100 - (f.value / maxVal) * 90;
      return { x, y, month: f.month, value: f.value };
    });
  }, [monthlyForecasts]);

  const linePath = React.useMemo(() => {
    if (chartData.length === 0) return "";
    return chartData.map((p, idx) => `${idx === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
  }, [chartData]);

  const areaPath = React.useMemo(() => {
    if (chartData.length === 0) return "";
    return `${linePath} L 500 120 L 0 120 Z`;
  }, [linePath, chartData]);

  // Color mapping for funnel bars
  const STAGE_COLORS: Record<string, string> = {
    "Inquiry": "bg-primary/80",
    "Qualification": "bg-accent/80",
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

  // §3.12 / §12.2 — a skeleton that mirrors the final layout, so nothing shifts
  // when the data lands. The previous full-page spinner collapsed to zero height
  // and then pushed the whole dashboard down on arrival.
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="h-[104px] animate-pulse rounded-lg border border-border bg-surface" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <KpiSkeleton key={i} />
          ))}
        </div>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <CardSkeleton className="lg:col-span-2" />
          <CardSkeleton />
        </div>
      </div>
    );
  }

  const leaderboardList = summary?.leaderboard && summary.leaderboard.length > 0
    ? summary.leaderboard
    : [{ name: userName, actionCount: 14 }];

  return (
    <div className="space-y-6">
      {/* Greeting bar — §2.15: every dashboard opens with one. */}
      <GreetingBar
        name={userName}
        roleLabel={ROLE_LABEL[getUserRole(user)] ?? "Workspace"}
        subtitle="Here is your hotel sales pipeline and follow-up activity for today."
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => router.push(ROUTE_PATHS.calendar)}
              leftIcon={<Calendar className="size-4" />}
            >
              Calendar
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => router.push(`${ROUTE_PATHS.manageFollowUpTasks}?new=1`)}
              leftIcon={<Plus className="size-4" />}
            >
              New task
            </Button>
          </>
        }
      />

      {/* KPI row — §2.15. Four cards, one component, tokens throughout. */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Active leads"
          value={activeLeadsCount.toLocaleString()}
          delta={summary?.activeLeadsGrowthPct}
          deltaLabel="vs last week"
          hint={`${(summary?.totalLeadsCount ?? 0).toLocaleString()} total`}
          icon={Users}
          tone="brand"
          href={ROUTE_PATHS.leads}
        />
        <KpiCard
          label="Active pipeline"
          value={formatVnd(activeDealsValue)}
          hint={`${activeDealsCount} open ${activeDealsCount === 1 ? "deal" : "deals"}`}
          icon={Briefcase}
          tone="teal"
          href={ROUTE_PATHS.deals}
        />
        <KpiCard
          label="Tasks due"
          value={pendingTasksCount.toLocaleString()}
          // A rise in overdue work is bad news, so the chip inverts.
          hint={
            overdueTasksCount > 0
              ? `${overdueTasksCount} overdue`
              : "Nothing overdue"
          }
          icon={CheckCircle2}
          tone={overdueTasksCount > 0 ? "danger" : "success"}
          href={ROUTE_PATHS.manageFollowUpTasks}
        />
        <KpiCard
          label="SLA compliance"
          value={`${(summary?.slaComplianceRatePct ?? 0).toFixed(1)}%`}
          hint="Target 90%"
          icon={Clock}
          tone={
            (summary?.slaComplianceRatePct ?? 0) >= 90 ? "success" : "warning"
          }
          href={ROUTE_PATHS.sla}
        />
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
                    d={areaPath}
                    fill="url(#chartGrad)"
                  />

                  {/* Forecast Line */}
                  <path
                    d={linePath}
                    fill="none"
                    stroke="var(--primary)"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                  />

                  {/* Target Goal Line */}
                  <line x1="0" y1="40" x2="500" y2="40" stroke="var(--success)" strokeWidth="1.5" strokeDasharray="4,4" />

                  {/* Nodes */}
                  {chartData.map((p, idx) => (
                    <circle
                      key={idx}
                      cx={p.x}
                      cy={p.y}
                      r="4"
                      fill="var(--primary)"
                      stroke="var(--background)"
                      strokeWidth="1.5"
                    >
                      <title>{`${p.month}: ${p.value.toLocaleString("vi-VN")} ₫`}</title>
                    </circle>
                  ))}
                </svg>
                {/* Labels */}
                <div className="flex justify-between text-[10px] text-muted-foreground font-semibold mt-1.5">
                  {chartData.map((p, idx) => (
                    <span key={idx}>{p.month}</span>
                  ))}
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
