"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  Star,
  ThumbsUp,
  ThumbsDown,
  Activity,
  Award,
  TrendingUp,
  Download,
  RefreshCw,
  ChevronLeft,
  ChevronRight
} from "lucide-react";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { KpiCard } from "@/components/ui/kpi-card";
import { toast } from "@/stores/toast_store";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import {
  sentimentAnalyticsService,
  type SentimentOverviewResponse,
  type SentimentTrendResponse,
  type TrendPoint,
  type AspectSentimentSummary,
  type AnalyzedFeedback
} from "@/services/sentiment_analytics_service";

type DatePreset = "7days" | "30days" | "month" | "custom";

const ASPECTS = [
  { key: "attitude", label: "Attitude" },
  { key: "speed", label: "Speed" },
  { key: "accuracy", label: "Accuracy" },
  { key: "facility", label: "Facility" },
  { key: "price", label: "Price" }
] as const;

export function SentimentAnalyticsDashboardScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const isManagerOrAdmin = userRole === "MANAGER" || userRole === "ADMIN";

  // Filter State
  const [preset, setPreset] = useState<DatePreset>("30days");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  // Chart Granularity
  const [granularity, setGranularity] = useState<"week" | "month">("week");
  // Chart active aspect toggle
  const [activeAspect, setActiveAspect] = useState<string>("overall");

  // Deep-dive selection
  const [selectedAspect, setSelectedAspect] = useState<string | null>(null);
  const [selectedSentiment, setSelectedSentiment] = useState<string | null>(null);

  // Loaded Data
  const [overview, setOverview] = useState<SentimentOverviewResponse | null>(null);
  const [trend, setTrend] = useState<SentimentTrendResponse | null>(null);
  const [deepDiveData, setDeepDiveData] = useState<AnalyzedFeedback[]>([]);
  const [totalDeepDiveElements, setTotalDeepDiveElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);

  const [loadingOverview, setLoadingOverview] = useState(false);
  const [loadingTrend, setLoadingTrend] = useState(false);
  const [loadingDeepDive, setLoadingDeepDive] = useState(false);

  // Initialize dates based on preset
  useEffect(() => {
    const now = new Date();
    let start = new Date();

    if (preset === "7days") {
      start.setDate(now.getDate() - 7);
    } else if (preset === "30days") {
      start.setDate(now.getDate() - 30);
    } else if (preset === "month") {
      start.setHours(0, 0, 0, 0);
      start.setDate(1);
    } else {
      return; // Keep existing custom values
    }

    setStartDate(start.toISOString().split("T")[0]);
    setEndDate(now.toISOString().split("T")[0]);
  }, [preset]);

  // Format Dates for API call
  const apiDates = useMemo(() => {
    if (!startDate || !endDate) return { start: undefined, end: undefined };
    return {
      start: new Date(startDate + "T00:00:00Z").toISOString(),
      end: new Date(endDate + "T23:59:59Z").toISOString()
    };
  }, [startDate, endDate]);

  // Load Overview Data
  const loadOverview = async () => {
    setLoadingOverview(true);
    try {
      const dates = apiDates;
      const res = await sentimentAnalyticsService.getOverview({
        startDate: dates.start,
        endDate: dates.end
      });
      if (res && res.success && res.data) {
        setOverview(res.data);
      }
    } catch (err: any) {
      console.error("Error loading overview:", err);
      toast.error("Failed to load sentiment overview.");
    } finally {
      setLoadingOverview(false);
    }
  };

  // Load Trend Data
  const loadTrend = async () => {
    setLoadingTrend(true);
    try {
      const dates = apiDates;
      const res = await sentimentAnalyticsService.getTrends({
        startDate: dates.start,
        endDate: dates.end,
        groupBy: granularity
      });
      if (res && res.success && res.data) {
        setTrend(res.data);
      }
    } catch (err: any) {
      console.error("Error loading trend:", err);
      toast.error("Failed to load trend analytics.");
    } finally {
      setLoadingTrend(false);
    }
  };

  // Load Deep Dive Feedback list
  const loadDeepDive = async () => {
    setLoadingDeepDive(true);
    try {
      const dates = apiDates;
      const res = await sentimentAnalyticsService.getDeepDive({
        aspect: selectedAspect || undefined,
        sentiment: selectedSentiment || undefined,
        startDate: dates.start,
        endDate: dates.end,
        page,
        size: pageSize
      });
      if (res && res.success && res.data) {
        setDeepDiveData(res.data.content);
        setTotalDeepDiveElements(res.data.totalElements);
      }
    } catch (err: any) {
      console.error("Error loading deep dive:", err);
      toast.error("Failed to load customer feedback list.");
    } finally {
      setLoadingDeepDive(false);
    }
  };

  // Trigger loading when dates change
  useEffect(() => {
    if (apiDates.start && apiDates.end) {
      loadOverview();
      loadTrend();
    }
  }, [apiDates]);

  // Reload deep dive list when filters/page changes
  useEffect(() => {
    if (apiDates.start && apiDates.end) {
      loadDeepDive();
    }
  }, [selectedAspect, selectedSentiment, page, apiDates]);

  // Handle reload action
  const handleRefreshAll = () => {
    loadOverview();
    loadTrend();
    loadDeepDive();
    toast.success("Sentiment dashboard refreshed.");
  };

  // Calculate Metrics from Overview
  const metrics = useMemo(() => {
    if (!overview) {
      return {
        total: 0,
        satScore: 0,
        highlightName: "—",
        highlightHint: "No positive reviews",
        painPointName: "—",
        painPointHint: "No negative reviews"
      };
    }

    const summaries = [
      { key: "Attitude", summary: overview.attitude },
      { key: "Speed", summary: overview.speed },
      { key: "Accuracy", summary: overview.accuracy },
      { key: "Facility", summary: overview.facility },
      { key: "Price", summary: overview.price }
    ];

    // Total counts (maximum total to see total reviews)
    const maxTotal = Math.max(...summaries.map(s => s.summary.total));

    // Calculate Overall Satisfaction score (% of Positive sentiments across all aspects)
    let totalPositive = 0;
    let totalAspectCounts = 0;
    summaries.forEach(s => {
      totalPositive += s.summary.positive;
      totalAspectCounts += s.summary.total;
    });
    const satScore = totalAspectCounts > 0 ? Math.round((totalPositive * 100) / totalAspectCounts) : 0;

    const activeSummaries = summaries.filter(s => s.summary.total > 0);

    // Best Aspect (Highlight)
    let highlightName = "—";
    let highlightHint = "No positive sentiment";
    if (activeSummaries.length > 0) {
      const best = [...activeSummaries].sort((a, b) => b.summary.positivePercentage - a.summary.positivePercentage)[0];
      highlightName = best.key;
      highlightHint = `${best.summary.positivePercentage}% positive reviews`;
    }

    // Worst Aspect (Pain point)
    let painPointName = "—";
    let painPointHint = "No negative sentiment";
    if (activeSummaries.length > 0) {
      const worst = [...activeSummaries].sort((a, b) => b.summary.negativePercentage - a.summary.negativePercentage)[0];
      painPointName = worst.key;
      painPointHint = `${worst.summary.negativePercentage}% negative reviews`;
    }

    return { total: maxTotal, satScore, highlightName, highlightHint, painPointName, painPointHint };
  }, [overview]);

  // Recharts horizontal aspect breakdown bar data
  const aspectChartData = useMemo(() => {
    if (!overview) return [];
    return ASPECTS.map((aspect) => {
      const data: AspectSentimentSummary = (overview as any)[aspect.key];
      return {
        name: aspect.label,
        Positive: data.positive,
        Neutral: data.neutral,
        Negative: data.negative,
        total: data.total
      };
    });
  }, [overview]);

  // Recharts trend data formatting
  const trendChartData = useMemo(() => {
    if (!trend || !trend.points) return [];
    return trend.points.map((pt: TrendPoint) => {
      let data: any = { period: pt.period };
      const aspectMap: Record<string, any> = {
        overall: pt.overall,
        attitude: pt.attitude,
        speed: pt.speed,
        accuracy: pt.accuracy,
        facility: pt.facility,
        price: pt.price
      };
      const values = aspectMap[activeAspect] || pt.overall;
      
      data.Positive = values.positive;
      data.Neutral = values.neutral;
      data.Negative = values.negative;
      return data;
    });
  }, [trend, activeAspect]);

  // Export deep dive reviews to CSV
  const handleExportCSV = () => {
    if (deepDiveData.length === 0) {
      toast.info("No data available to export.");
      return;
    }
    const headers = ["Customer Name", "Booking Code", "Rating", "Comment", "Attitude Sentiment", "Speed Sentiment", "Accuracy Sentiment", "Facility Sentiment", "Price Sentiment"];
    const rows = deepDiveData.map(f => [
      f.customerName,
      f.bookingCode,
      f.rating,
      f.comment ? f.comment.replace(/"/g, '""') : "",
      f.absaAttitudeSentiment || "N/A",
      f.absaSpeedSentiment || "N/A",
      f.absaAccuracySentiment || "N/A",
      f.absaFacilitySentiment || "N/A",
      f.absaPriceSentiment || "N/A"
    ]);

    const csvContent =
      "data:text/csv;charset=utf-8," +
      [headers.join(","), ...rows.map(e => e.map(val => `"${val}"`).join(","))].join("\n");

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `sentiment-export-${startDate}-to-${endDate}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast.success("CSV file exported successfully.");
  };

  const getAspectBadgeClass = (sentiment?: string) => {
    if (!sentiment) return "bg-slate-50 text-slate-400 border-slate-100";
    switch (sentiment.toUpperCase()) {
      case "POSITIVE":
        return "bg-emerald-50 text-emerald-700 border-emerald-200/60";
      case "NEGATIVE":
        return "bg-rose-50 text-rose-700 border-rose-200/60";
      case "NEUTRAL":
        return "bg-slate-50 text-slate-600 border-slate-200/60";
      default:
        return "bg-slate-50 text-slate-400 border-slate-100";
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900">Sentiment AI Analytics</h1>
          <p className="text-xs text-slate-500 mt-1">
            Aggregate aspect-based customer satisfaction and feedback drivers
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={handleRefreshAll}
            disabled={loadingOverview || loadingTrend}
            leftIcon={<RefreshCw className={`size-3.5 ${loadingOverview ? "animate-spin" : ""}`} />}
          >
            Refresh
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleExportCSV}
            leftIcon={<Download className="size-3.5" />}
          >
            Export CSV
          </Button>
        </div>
      </div>

      {/* Global Filter Bar */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between bg-white border border-slate-200/85 rounded-xl p-4 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mr-2">Time Range:</span>
          {(["7days", "30days", "month", "custom"] as const).map((p) => {
            const labelMap = {
              "7days": "7 Days",
              "30days": "30 Days",
              "month": "This Month",
              "custom": "Custom Range"
            };
            const active = preset === p;
            return (
              <button
                key={p}
                onClick={() => setPreset(p)}
                className={`text-xs font-semibold px-3 py-1.5 rounded-lg border transition-all duration-150 cursor-pointer ${
                  active
                    ? "bg-slate-900 text-white border-slate-900 shadow-sm"
                    : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50"
                }`}
              >
                {labelMap[p]}
              </button>
            );
          })}
        </div>

        {preset === "custom" && (
          <div className="flex items-center gap-2 animate-in fade-in duration-150">
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none focus:border-brand-500 transition"
            />
            <span className="text-xs text-slate-400 font-semibold">to</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-700 bg-white focus:outline-none focus:border-brand-500 transition"
            />
          </div>
        )}
      </div>

      {/* Top Metrics Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Total Reviews"
          value={loadingOverview ? "—" : metrics.total.toLocaleString()}
          hint="AI-classified comments"
          icon={Activity}
          tone="brand"
        />
        <KpiCard
          label="Overall Satisfaction"
          value={loadingOverview ? "—" : `${metrics.satScore}%`}
          hint="Positive sentiment ratio"
          icon={Award}
          tone="success"
        />
        <KpiCard
          label="Top Highlight"
          value={loadingOverview ? "—" : metrics.highlightName}
          hint={metrics.highlightHint}
          icon={ThumbsUp}
          tone="teal"
        />
        <KpiCard
          label="Top Hotspot"
          value={loadingOverview ? "—" : metrics.painPointName}
          hint={metrics.painPointHint}
          icon={ThumbsDown}
          tone="danger"
        />
      </div>

      {/* Main Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Chart Panel: Aspect Breakdown */}
        <Card>
          <CardHeader className="pb-2">
            <div>
              <CardTitle className="text-sm font-bold text-foreground">Aspect Sentiment Breakdown</CardTitle>
              <CardDescription className="text-xs text-muted-foreground mt-0.5">
                Sentiment distribution across aspects. Click a bar segment to filter reviews.
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            {loadingOverview && (
              <div className="h-64 flex items-center justify-center text-xs text-slate-400">
                Loading sentiment aspect breakdown...
              </div>
            )}
            {!loadingOverview && aspectChartData.length === 0 && (
              <div className="h-64 flex items-center justify-center text-xs text-slate-400">
                No aspect sentiment statistics found.
              </div>
            )}
            {!loadingOverview && aspectChartData.length > 0 && (
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    layout="vertical"
                    data={aspectChartData}
                    margin={{ top: 5, right: 10, left: -20, bottom: 5 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" horizontal={false} />
                    <XAxis type="number" stroke="#94a3b8" fontSize={9} tickLine={false} />
                    <YAxis dataKey="name" type="category" stroke="#94a3b8" fontSize={9} tickLine={false} width={80} />
                    <Tooltip
                      cursor={{ fill: "rgba(241, 245, 249, 0.4)" }}
                      contentStyle={{
                        background: "#0f172a",
                        color: "#f8fafc",
                        border: "none",
                        borderRadius: "8px",
                        fontSize: "11px",
                        boxShadow: "0 10px 15px -3px rgba(0, 0, 0, 0.1)"
                      }}
                    />
                    <Bar
                      dataKey="Positive"
                      stackId="a"
                      fill="#10b981"
                      className="cursor-pointer"
                      onClick={(data) => {
                        if (data && data.payload) {
                          setSelectedAspect(data.payload.name.toLowerCase());
                          setSelectedSentiment("Positive");
                          setPage(0);
                        }
                      }}
                    />
                    <Bar
                      dataKey="Neutral"
                      stackId="a"
                      fill="#64748b"
                      className="cursor-pointer"
                      onClick={(data) => {
                        if (data && data.payload) {
                          setSelectedAspect(data.payload.name.toLowerCase());
                          setSelectedSentiment("Neutral");
                          setPage(0);
                        }
                      }}
                    />
                    <Bar
                      dataKey="Negative"
                      stackId="a"
                      fill="#f43f5e"
                      className="cursor-pointer"
                      onClick={(data) => {
                        if (data && data.payload) {
                          setSelectedAspect(data.payload.name.toLowerCase());
                          setSelectedSentiment("Negative");
                          setPage(0);
                        }
                      }}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
            <div className="flex justify-center gap-6 text-[10px] font-semibold text-slate-500 pt-4 border-t border-slate-100 mt-4">
              <span className="flex items-center gap-1.5">
                <span className="size-2 bg-emerald-500 rounded-full"></span> Positive
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2 bg-slate-400 rounded-full"></span> Neutral
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2 bg-rose-500 rounded-full"></span> Negative
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Right Chart Panel: Time Trends */}
        <Card>
          <CardHeader className="pb-2 flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-sm font-bold text-foreground">Sentiment Trend Over Time</CardTitle>
              <CardDescription className="text-xs text-muted-foreground mt-0.5">
                Aggregated sentiment ratings progression curves
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <Select
                value={activeAspect}
                onChange={(e) => setActiveAspect(e.target.value)}
                className="w-28 text-xs py-1"
              >
                <option value="overall">Overall Rating</option>
                <option value="attitude">Attitude</option>
                <option value="speed">Speed</option>
                <option value="accuracy">Accuracy</option>
                <option value="facility">Facility</option>
                <option value="price">Price</option>
              </Select>
              <Select
                value={granularity}
                onChange={(e) => setGranularity(e.target.value as "week" | "month")}
                className="w-24 text-xs py-1"
              >
                <option value="week">Weekly</option>
                <option value="month">Monthly</option>
              </Select>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            {loadingTrend && (
              <div className="h-64 flex items-center justify-center text-xs text-slate-400">
                Loading sentiment trends...
              </div>
            )}
            {!loadingTrend && trendChartData.length === 0 && (
              <div className="h-64 flex items-center justify-center text-xs text-slate-400">
                No trend data found.
              </div>
            )}
            {!loadingTrend && trendChartData.length > 0 && (
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={trendChartData} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis dataKey="period" stroke="#94a3b8" fontSize={9} tickLine={false} />
                    <YAxis stroke="#94a3b8" fontSize={9} tickLine={false} />
                    <Tooltip
                      contentStyle={{
                        background: "#0f172a",
                        color: "#f8fafc",
                        border: "none",
                        borderRadius: "8px",
                        fontSize: "11px",
                        boxShadow: "0 10px 15px -3px rgba(0, 0, 0, 0.1)"
                      }}
                    />
                    <Legend verticalAlign="top" height={36} iconSize={8} iconType="circle" wrapperStyle={{ fontSize: 10 }} />
                    <Line type="monotone" dataKey="Positive" stroke="#10b981" strokeWidth={2.5} activeDot={{ r: 6 }} dot={{ r: 3 }} />
                    <Line type="monotone" dataKey="Neutral" stroke="#64748b" strokeWidth={2} dot={{ r: 2 }} />
                    <Line type="monotone" dataKey="Negative" stroke="#f43f5e" strokeWidth={2} dot={{ r: 2 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Deep Dive Section */}
      <Card>
        <CardHeader className="pb-3 border-b border-slate-100 flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-sm font-bold text-foreground">Deep-Dive Customer Reviews</CardTitle>
            <CardDescription className="text-xs text-muted-foreground mt-0.5 flex items-center gap-1.5 flex-wrap">
              <span>Inspect customer comments and AI tags.</span>
              {(selectedAspect || selectedSentiment) && (
                <span className="inline-flex items-center gap-1 bg-brand-50 text-brand-700 px-2.5 py-0.5 rounded-full text-xs font-semibold border border-brand-200/50">
                  Filtered: <span className="capitalize">{selectedAspect}</span> &bull; {selectedSentiment}
                  <button
                    onClick={() => {
                      setSelectedAspect(null);
                      setSelectedSentiment(null);
                      setPage(0);
                    }}
                    className="ml-1.5 text-brand-400 hover:text-brand-600 font-bold cursor-pointer"
                  >
                    &times;
                  </button>
                </span>
              )}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {loadingDeepDive ? (
            <div className="py-20 text-center text-xs text-slate-400 flex flex-col items-center justify-center gap-2">
              <RefreshCw className="size-6 animate-spin text-slate-350" />
              <span>Loading matching customer reviews...</span>
            </div>
          ) : deepDiveData.length === 0 ? (
            <div className="py-20 text-center text-xs text-slate-400 italic">
              No customer reviews found matching the selected filters.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-200/60 text-slate-500 font-semibold uppercase tracking-wider">
                    <th className="px-6 py-3 font-semibold text-[10px]">Customer / Booking</th>
                    <th className="px-6 py-3 font-semibold text-[10px]">Overall</th>
                    <th className="px-6 py-3 font-semibold text-[10px]">Aspect Details</th>
                    <th className="px-6 py-3 font-semibold text-[10px] max-w-sm">Customer Comment</th>
                    <th className="px-6 py-3 font-semibold text-[10px] text-right">AI Aspect Tagging</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-slate-700">
                  {deepDiveData.map((f) => (
                    <tr key={f.feedbackId} className="hover:bg-slate-50/40 transition">
                      <td className="px-6 py-4">
                        <div className="font-semibold text-slate-900">{f.customerName}</div>
                        <div className="text-[10px] text-slate-400 font-mono mt-0.5">
                          {f.bookingCode}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center gap-0.5 rounded bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-700 border border-amber-100">
                          <Star className="size-3 fill-amber-500 text-amber-500" />
                          {f.rating}.0
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-col gap-1 text-[10px] text-slate-500 font-medium">
                          <span>Attitude: <strong className="text-slate-800">{f.ratingAttitude ? `${f.ratingAttitude}★` : "—"}</strong></span>
                          <span>Speed: <strong className="text-slate-800">{f.ratingSpeed ? `${f.ratingSpeed}★` : "—"}</strong></span>
                          <span>Accuracy: <strong className="text-slate-800">{f.ratingAccuracy ? `${f.ratingAccuracy}★` : "—"}</strong></span>
                        </div>
                      </td>
                      <td className="px-6 py-4 max-w-xs sm:max-w-sm">
                        <div className="text-slate-800 font-normal italic truncate" title={f.comment}>
                          {f.comment ? `"${f.comment}"` : <span className="text-slate-400 italic">No comment</span>}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1 justify-end">
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium border ${getAspectBadgeClass(f.absaAttitudeSentiment)}`}>
                            Attitude: {f.absaAttitudeSentiment || "N/A"}
                          </span>
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium border ${getAspectBadgeClass(f.absaSpeedSentiment)}`}>
                            Speed: {f.absaSpeedSentiment || "N/A"}
                          </span>
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium border ${getAspectBadgeClass(f.absaAccuracySentiment)}`}>
                            Accuracy: {f.absaAccuracySentiment || "N/A"}
                          </span>
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium border ${getAspectBadgeClass(f.absaFacilitySentiment)}`}>
                            Facility: {f.absaFacilitySentiment || "N/A"}
                          </span>
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium border ${getAspectBadgeClass(f.absaPriceSentiment)}`}>
                            Price: {f.absaPriceSentiment || "N/A"}
                          </span>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Table Pagination */}
              <div className="flex items-center justify-between border-t border-slate-100 px-6 py-3 bg-slate-50/50 select-none">
                <p className="text-[11px] text-slate-400 font-medium">
                  Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalDeepDiveElements)} of {totalDeepDiveElements} reviews
                </p>
                <div className="flex items-center gap-1.5">
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                    className="p-1 size-7 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-center disabled:opacity-40"
                  >
                    <ChevronLeft className="size-4 text-slate-600" />
                  </Button>
                  <span className="text-xs font-semibold text-slate-600 px-2">
                    Page {page + 1} of {Math.ceil(totalDeepDiveElements / pageSize) || 1}
                  </span>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={(page + 1) * pageSize >= totalDeepDiveElements}
                    onClick={() => setPage(page + 1)}
                    className="p-1 size-7 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-center disabled:opacity-40"
                  >
                    <ChevronRight className="size-4 text-slate-600" />
                  </Button>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
