"use client";

import React, { useState, useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  Star,
  MessageSquare,
  TrendingUp,
  Sparkles,
  AlertTriangle,
  Download,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ThumbsUp,
  ThumbsDown,
  User,
  Flag,
  FileText,
  Mail,
  Minus
} from "lucide-react";
import {
  AreaChart,
  Area,
  ComposedChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  LabelList,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { PageHeader } from "@/components/ui/page-header";
import { PAGE_META } from "@/app/routes/page_meta";
import { toast } from "@/stores/toast_store";
import { useAuthStore } from "@/stores/auth_store";
import { getUserRole } from "@/shared/auth/access";
import {
  sentimentAnalyticsService,
  type SentimentOverviewResponse,
  type SentimentTrendResponse,
  type TrendPoint,
  type AspectSentimentSummary,
  type AnalyzedFeedback,
  type StaffSentimentPerformanceResponse
} from "@/services/sentiment_analytics_service";
import { customerFeedbackService } from "@/services/customer_feedback_service";
import { taskService, userService, type UserSummary } from "@/services/follow_up_task_service";

type DatePreset = "7days" | "30days" | "month" | "custom";

const ASPECTS = [
  { key: "attitude", label: "Attitude" },
  { key: "speed", label: "Speed" },
  { key: "accuracy", label: "Accuracy" },
  { key: "facility", label: "Facility" },
  { key: "price", label: "Price" }
] as const;

const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-[#5B51D8] text-white font-bold text-xs px-2.5 py-1.5 rounded-md shadow-lg border border-white/10 flex flex-col gap-1">
        {payload.map((pld: any) => (
          <div key={pld.dataKey} className="flex items-center gap-1.5 justify-between min-w-[70px]">
            <span className="capitalize">{pld.name || pld.dataKey}:</span>
            <span>{pld.value}</span>
          </div>
        ))}
      </div>
    );
  }
  return null;
};

interface MetricCardProps {
  value: string | number;
  label: string;
  sub?: string;
  subColor?: string;
  icon: React.ComponentType<{ className?: string }>;
}

function MetricCard({ value, label, sub, subColor = "text-slate-500", icon: Icon }: MetricCardProps) {
  return (
    <div className="bg-white rounded-xl border border-slate-200/80 p-5 shadow-sm hover:shadow transition-all duration-200">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
          {label}
        </span>
        <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-650">
          <Icon className="size-4" />
        </div>
      </div>
      <p className="text-2xl font-bold text-slate-900 tracking-tight mt-2">
        {value}
      </p>
      {sub && (
        <p className={`text-xs font-medium mt-1 ${subColor}`}>
          {sub}
        </p>
      )}
    </div>
  );
}

// Calculate dynamic overall sentiment based on aspect confidence sums
export const getOverallSentiment = (f: AnalyzedFeedback): "POSITIVE" | "NEGATIVE" | "NEUTRAL" => {
  const confList = [
    { sent: f.absaAttitudeSentiment, conf: f.absaAttitudeConfidence },
    { sent: f.absaSpeedSentiment, conf: f.absaSpeedConfidence },
    { sent: f.absaAccuracySentiment, conf: f.absaAccuracyConfidence },
    { sent: f.absaFacilitySentiment, conf: f.absaFacilityConfidence },
    { sent: f.absaPriceSentiment, conf: f.absaPriceConfidence }
  ];
  let posConfSum = 0;
  let negConfSum = 0;
  confList.forEach(item => {
    if (item.sent && item.conf) {
      const val = Number(item.conf);
      if (item.sent.toUpperCase() === "POSITIVE") {
        posConfSum += val;
      } else if (item.sent.toUpperCase() === "NEGATIVE") {
        negConfSum += val;
      }
    }
  });
  if (posConfSum > negConfSum) return "POSITIVE";
  if (negConfSum > posConfSum) return "NEGATIVE";
  return "NEUTRAL";
};

export function SentimentAnalyticsDashboardScreen() {
  const { user } = useAuthStore();
  const userRole = getUserRole(user);
  const router = useRouter();

  // Filter State
  const [preset, setPreset] = useState<DatePreset>("30days");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  // Chart active aspect toggle
  const [activeAspect, setActiveAspect] = useState<string>("overall");

  // Deep-dive selection
  const [selectedAspect, setSelectedAspect] = useState<string | null>(null);
  const [selectedSentiment, setSelectedSentiment] = useState<string | null>(null);

  // Benchmarking states
  const [selectedStaffForCompare, setSelectedStaffForCompare] = useState<string[]>([]);
  const [isCompareModalOpen, setIsCompareModalOpen] = useState(false);
  const [selectedStaffInspector, setSelectedStaffInspector] = useState<string | null>(null);
  const [staffPerformanceList, setStaffPerformanceList] = useState<StaffSentimentPerformanceResponse[]>([]);
  const [loadingStaffPerformance, setLoadingStaffPerformance] = useState(false);
  const [activeTab, setActiveTab] = useState<"overview" | "staff">("overview");
  const [showReport, setShowReport] = useState(false);

  // Loaded Data
  const [overview, setOverview] = useState<SentimentOverviewResponse | null>(null);
  const [trend, setTrend] = useState<SentimentTrendResponse | null>(null);
  const [allFeedbacks, setAllFeedbacks] = useState<AnalyzedFeedback[]>([]);
  const [deepDiveData, setDeepDiveData] = useState<AnalyzedFeedback[]>([]);
  const [totalDeepDiveElements, setTotalDeepDiveElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);

  // Detail Drawer State
  const [selectedFeedback, setSelectedFeedback] = useState<AnalyzedFeedback | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  // Task Modal Form States
  const [isTaskModalOpen, setIsTaskModalOpen] = useState(false);
  const [usersList, setUsersList] = useState<UserSummary[]>([]);
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [assignedUserId, setAssignedUserId] = useState("");
  const [assignedUserName, setAssignedUserName] = useState("");
  const [taskStartAt, setTaskStartAt] = useState("");
  const [taskEndAt, setTaskEndAt] = useState("");
  const [contactName, setContactName] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [taskPriority, setTaskPriority] = useState<"LOW" | "MEDIUM" | "HIGH" | "CRITICAL">("MEDIUM");
  const [submittingTask, setSubmittingTask] = useState(false);

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
        groupBy: "week"
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

  // Load All Feedbacks to perform aggregates
  const loadAllFeedbacks = async () => {
    try {
      const dates = apiDates;
      const res = await sentimentAnalyticsService.getDeepDive({
        startDate: dates.start,
        endDate: dates.end,
        page: 0,
        size: 500 // Retrieve sufficient data points for client-side calculations
      });
      if (res && res.success && res.data) {
        setAllFeedbacks(res.data.content);
      }
    } catch (err) {
      console.error("Error loading aggregates:", err);
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

  // Load Staff Performance metrics from API
  const loadStaffPerformance = async () => {
    setLoadingStaffPerformance(true);
    try {
      const dates = apiDates;
      const res = await sentimentAnalyticsService.getStaffPerformance({
        startDate: dates.start,
        endDate: dates.end
      });
      if (res && res.success && res.data) {
        setStaffPerformanceList(res.data);
      }
    } catch (err) {
      console.error("Error loading staff performance:", err);
      toast.error("Failed to load staff performance data.");
    } finally {
      setLoadingStaffPerformance(false);
    }
  };

  // Trigger loading when dates change
  useEffect(() => {
    if (apiDates.start && apiDates.end) {
      loadOverview();
      loadTrend();
      loadAllFeedbacks();
      loadStaffPerformance();
    }
  }, [apiDates]);

  // Reload deep dive list when filters/page changes
  useEffect(() => {
    if (apiDates.start && apiDates.end) {
      loadDeepDive();
    }
  }, [selectedAspect, selectedSentiment, page, apiDates]);

  // Load active CRM users
  const loadUsers = async () => {
    try {
      const res = await userService.getAll();
      if (res && res.success && res.data) {
        setUsersList(res.data);
      }
    } catch (err) {
      console.error("Error loading active users list:", err);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  // Handle reload action
  const handleRefreshAll = () => {
    loadOverview();
    loadTrend();
    loadAllFeedbacks();
    loadDeepDive();
    loadStaffPerformance();
    toast.success("Sentiment dashboard refreshed.");
  };

  // Calculate Operational Metrics for Hotel Managers
  const operationalMetrics = useMemo(() => {
    const total = allFeedbacks.length;
    if (total === 0) {
      return {
        totalFeedback: 0,
        netSentiment: "0%",
        netColor: "text-slate-500",
        bottleneck: "None",
        bottleneckSub: "No negative feedback",
        staffHealth: "0%",
        staffHealthColor: "text-slate-500"
      };
    }

    // 1. Customer Satisfaction Score (CSAT): Positive aspect ratio across all reviews
    let posCount = 0;
    let negCount = 0;
    allFeedbacks.forEach(f => {
      [f.absaAttitudeSentiment, f.absaSpeedSentiment, f.absaAccuracySentiment, f.absaFacilitySentiment, f.absaPriceSentiment].forEach(sent => {
        if (sent) {
          if (sent.toUpperCase() === "POSITIVE") posCount++;
          if (sent.toUpperCase() === "NEGATIVE") negCount++;
        }
      });
    });
    const totalSentiments = posCount + negCount;
    const csat = totalSentiments > 0 ? Math.round((posCount / totalSentiments) * 100) : 0;
    const netColor = csat >= 80 ? "text-emerald-600" : csat >= 60 ? "text-slate-500" : "text-rose-600";

    // 2. Primary Service Bottleneck (System Aspect with highest negative count)
    const systemAspects = [
      { label: "Price", key: "absaPriceSentiment" },
      { label: "Facility", key: "absaFacilitySentiment" },
      { label: "Speed", key: "absaSpeedSentiment" }
    ] as const;

    const systemNegatives = systemAspects.map(aspect => {
      const count = allFeedbacks.filter(f => f[aspect.key]?.toUpperCase() === "NEGATIVE").length;
      const ratio = total > 0 ? Math.round((count / total) * 100) : 0;
      return { label: aspect.label, count, ratio };
    }).sort((a, b) => b.count - a.count);

    const worstSystem = systemNegatives[0];
    const bottleneck = worstSystem.count > 0 ? `${worstSystem.label} (${worstSystem.ratio}%)` : "None";
    const bottleneckSub = worstSystem.count > 0 ? `${worstSystem.count} complaint reviews` : "No complaints recorded";

    // 3. Staff Service Health (Ratio of positive Attitude & Accuracy)
    let staffPos = 0;
    let staffTotal = 0;
    allFeedbacks.forEach(f => {
      [f.absaAttitudeSentiment, f.absaAccuracySentiment].forEach(sent => {
        if (sent) {
          staffTotal++;
          if (sent.toUpperCase() === "POSITIVE") staffPos++;
        }
      });
    });
    const staffHealthRatio = staffTotal > 0 ? Math.round((staffPos / staffTotal) * 100) : 0;
    const staffHealthColor = staffHealthRatio >= 85 ? "text-emerald-600" : staffHealthRatio >= 70 ? "text-slate-500" : "text-rose-600";

    return {
      totalFeedback: total,
      netSentiment: `${csat}%`,
      netColor,
      bottleneck,
      bottleneckSub,
      staffHealth: `${staffHealthRatio}%`,
      staffHealthColor
    };
  }, [allFeedbacks]);

  // Group feedbacks by sales staff and calculate aggregated metrics
  const staffPerformanceData = useMemo(() => {
    return staffPerformanceList || [];
  }, [staffPerformanceList]);

  // Calculate department average aspect CSAT ratios for benchmarking
  const departmentAverage = useMemo(() => {
    if (staffPerformanceData.length === 0) return { attitude: 0, speed: 0, accuracy: 0, facility: 0, price: 0 };
    let att = 0, spd = 0, acc = 0, fac = 0, prc = 0;
    staffPerformanceData.forEach(s => {
      att += s.attitudePositiveRatio;
      spd += s.speedPositiveRatio;
      acc += s.accuracyPositiveRatio;
      fac += s.facilityPositiveRatio;
      prc += s.pricePositiveRatio;
    });
    const count = staffPerformanceData.length;
    return {
      attitude: Math.round(att / count),
      speed: Math.round(spd / count),
      accuracy: Math.round(acc / count),
      facility: Math.round(fac / count),
      price: Math.round(prc / count)
    };
  }, [staffPerformanceData]);

  // Helper to display staff name as is
  const displayStaffName = (name: string) => name;

  // Compared staff objects memo
  const comparedStaffObjects = useMemo(() => {
    return staffPerformanceData.filter(s => selectedStaffForCompare.includes(s.staffId));
  }, [selectedStaffForCompare, staffPerformanceData]);

  // Radar data mapping for recharts radar chart supporting up to 3 staff members
  const radarData = useMemo(() => {
    if (comparedStaffObjects.length < 2) return [];
    return [
      {
        aspect: "Attitude",
        Average: departmentAverage.attitude,
        ...comparedStaffObjects.reduce((acc, s) => {
          acc[s.staffName] = s.attitudePositiveRatio;
          return acc;
        }, {} as Record<string, number>)
      },
      {
        aspect: "Speed",
        Average: departmentAverage.speed,
        ...comparedStaffObjects.reduce((acc, s) => {
          acc[s.staffName] = s.speedPositiveRatio;
          return acc;
        }, {} as Record<string, number>)
      },
      {
        aspect: "Accuracy",
        Average: departmentAverage.accuracy,
        ...comparedStaffObjects.reduce((acc, s) => {
          acc[s.staffName] = s.accuracyPositiveRatio;
          return acc;
        }, {} as Record<string, number>)
      },
      {
        aspect: "Facility",
        Average: departmentAverage.facility,
        ...comparedStaffObjects.reduce((acc, s) => {
          acc[s.staffName] = s.facilityPositiveRatio;
          return acc;
        }, {} as Record<string, number>)
      },
      {
        aspect: "Price",
        Average: departmentAverage.price,
        ...comparedStaffObjects.reduce((acc, s) => {
          acc[s.staffName] = s.pricePositiveRatio;
          return acc;
        }, {} as Record<string, number>)
      }
    ];
  }, [comparedStaffObjects, departmentAverage]);

  // AI Coaching Recommendations delta logic memo
  const coachingRecommendations = useMemo(() => {
    if (comparedStaffObjects.length < 2) return [];
    const recs: { title: string; suggestion: string; mentorId: string; menteeId: string; aspect: string }[] = [];

    // 1. Attitude & Communication Aspect
    const sortedAtt = [...comparedStaffObjects].sort((a, b) => b.attitudePositiveRatio - a.attitudePositiveRatio);
    const topAtt = sortedAtt[0];
    const lowerAtts = sortedAtt.slice(1).filter(s => topAtt.attitudePositiveRatio - s.attitudePositiveRatio >= 15);
    if (lowerAtts.length > 0) {
      recs.push({
        title: "Attitude & Communication Mentorship",
        suggestion: `${displayStaffName(topAtt.staffName)} (CSAT: ${topAtt.attitudePositiveRatio}%) shows strong communication skills. Suggest ${lowerAtts.map(s => displayStaffName(s.staffName)).join(" and ")} to shadow their customer consultation sessions.`,
        mentorId: topAtt.staffId,
        menteeId: lowerAtts[0].staffId,
        aspect: "Attitude"
      });
    }

    // 2. Process Efficiency & Speed Aspect
    const sortedSpd = [...comparedStaffObjects].sort((a, b) => b.speedPositiveRatio - a.speedPositiveRatio);
    const topSpd = sortedSpd[0];
    const lowerSpds = sortedSpd.slice(1).filter(s => topSpd.speedPositiveRatio - s.speedPositiveRatio >= 15);
    if (lowerSpds.length > 0) {
      recs.push({
        title: "Process Efficiency & Speed Coaching",
        suggestion: `${displayStaffName(topSpd.staffName)} (CSAT: ${topSpd.speedPositiveRatio}%) is significantly faster in contract handling and follow-ups. Suggest ${lowerSpds.map(s => displayStaffName(s.staffName)).join(" and ")} to consult them on template/work management shortcuts.`,
        mentorId: topSpd.staffId,
        menteeId: lowerSpds[0].staffId,
        aspect: "Speed"
      });
    }

    // 3. Operational Accuracy Aspect
    const sortedAcc = [...comparedStaffObjects].sort((a, b) => b.accuracyPositiveRatio - a.accuracyPositiveRatio);
    const topAcc = sortedAcc[0];
    const lowerAccs = sortedAcc.slice(1).filter(s => topAcc.accuracyPositiveRatio - s.accuracyPositiveRatio >= 15);
    if (lowerAccs.length > 0) {
      recs.push({
        title: "Operational Accuracy Mentoring",
        suggestion: `${displayStaffName(topAcc.staffName)} (CSAT: ${topAcc.accuracyPositiveRatio}%) is highly precise. Suggest ${lowerAccs.map(s => displayStaffName(s.staffName)).join(" and ")} to review their customer verification process checklists and lead peer review sessions.`,
        mentorId: topAcc.staffId,
        menteeId: lowerAccs[0].staffId,
        aspect: "Accuracy"
      });
    }

    return recs;
  }, [comparedStaffObjects]);


  // System & Facility Aspects Data for Section A BarChart
  const systemChartData = useMemo(() => {
    if (!overview) return [];
    return ASPECTS.map(a => {
      const data: AspectSentimentSummary = (overview as any)[a.key];
      return {
        name: a.label,
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

  // Positive & Negative lists for Row 3 drivers
  const positiveAspectDrivers = useMemo(() => {
    if (!overview) return [];
    return ASPECTS.map(a => {
      const s = (overview as any)[a.key] as AspectSentimentSummary;
      return { label: a.label, key: a.key, value: s.positive, percentage: s.positivePercentage };
    }).sort((a, b) => b.percentage - a.percentage);
  }, [overview]);

  const negativeAspectDrivers = useMemo(() => {
    if (!overview) return [];
    return ASPECTS.map(a => {
      const s = (overview as any)[a.key] as AspectSentimentSummary;
      return { label: a.label, key: a.key, value: s.negative, percentage: s.negativePercentage };
    }).sort((a, b) => b.percentage - a.percentage);
  }, [overview]);

  // Check if feedback contains severe negative sentiment (> 80% confidence)
  const hasSevereNegative = (f: AnalyzedFeedback): boolean => {
    const confList = [
      { sent: f.absaAttitudeSentiment, conf: f.absaAttitudeConfidence },
      { sent: f.absaSpeedSentiment, conf: f.absaSpeedConfidence },
      { sent: f.absaAccuracySentiment, conf: f.absaAccuracyConfidence },
      { sent: f.absaFacilitySentiment, conf: f.absaFacilityConfidence },
      { sent: f.absaPriceSentiment, conf: f.absaPriceConfidence }
    ];
    return confList.some(item => {
      if (item.sent?.toUpperCase() === "NEGATIVE" && item.conf) {
        const val = Number(item.conf);
        return val >= 80 || (val > 0 && val <= 1.0 && val >= 0.8);
      }
      return false;
    });
  };

  // Get Initials for Avatars
  const getInitials = (name?: string) => {
    if (!name) return "U";
    return name.split(" ").map(n => n[0]).join("").slice(0, 2).toUpperCase() || "U";
  };

  // Update Review Status handler in drawer
  const handleUpdateStatus = async (status: "REVIEWED" | "DISMISSED") => {
    if (!selectedFeedback) return;
    try {
      await customerFeedbackService.updateReviewStatus(selectedFeedback.feedbackId, status);
      toast.success(`Feedback status updated to ${status}.`);

      // Update local state lists
      const updatedList = (list: AnalyzedFeedback[]) =>
        list.map(f => f.feedbackId === selectedFeedback.feedbackId ? { ...f, reviewStatus: status } : f);

      setDeepDiveData(updatedList(deepDiveData));
      setAllFeedbacks(updatedList(allFeedbacks));
      setSelectedFeedback(prev => prev ? { ...prev, reviewStatus: status } : null);
    } catch (err) {
      console.error("Error updating feedback status:", err);
      toast.error("Failed to update feedback status.");
    }
  };

  // Create Follow-up Task shortcut handler - Opens the modal and pre-fills fields
  const handleCreateFollowUpTask = () => {
    if (!selectedFeedback) return;

    // Pre-fill Title
    setTaskTitle(`[ABSA Alert] Negative feedback resolution - Booking ${selectedFeedback.bookingCode}`);

    // Pre-fill Priority based on severe negative check
    const isSevere = hasSevereNegative(selectedFeedback);
    setTaskPriority(isSevere ? "HIGH" : "MEDIUM");

    // Pre-fill and lock Assignee
    const matchedUser = usersList.find(
      u => u.fullName.toLowerCase() === selectedFeedback.salesStaffName?.toLowerCase()
    );
    if (matchedUser) {
      setAssignedUserId(matchedUser.userId);
      setAssignedUserName(matchedUser.fullName);
    } else {
      // Fallback to current manager/admin account
      setAssignedUserId(user?.id || "");
      setAssignedUserName(user?.name || "Assigned Manager");
    }

    // Pre-fill startAt (now) and endAt (2 days later) formatted for datetime-local
    const now = new Date();
    const tzoffset = now.getTimezoneOffset() * 60000;
    const localStart = new Date(now.getTime() - tzoffset).toISOString().slice(0, 16);
    const localEnd = new Date(now.getTime() + 2 * 24 * 60 * 60 * 1000 - tzoffset).toISOString().slice(0, 16);
    setTaskStartAt(localStart);
    setTaskEndAt(localEnd);

    // Pre-fill contact details
    setContactName(selectedFeedback.customerName || "");
    setContactPhone("");

    // Pre-fill Description (ABSA analysis breakdown context in English)
    const aspects = [
      { label: "Attitude (Staff)", sentiment: selectedFeedback.absaAttitudeSentiment, conf: selectedFeedback.absaAttitudeConfidence },
      { label: "Speed & Process", sentiment: selectedFeedback.absaSpeedSentiment, conf: selectedFeedback.absaSpeedConfidence },
      { label: "Accuracy & Info", sentiment: selectedFeedback.absaAccuracySentiment, conf: selectedFeedback.absaAccuracyConfidence },
      { label: "Facility (Hotel)", sentiment: selectedFeedback.absaFacilitySentiment, conf: selectedFeedback.absaFacilityConfidence },
      { label: "Price & Value", sentiment: selectedFeedback.absaPriceSentiment, conf: selectedFeedback.absaPriceConfidence }
    ];
    const absaBreakdownText = aspects
      .map(a => `- ${a.label}: ${a.sentiment || "Neutral"} (${a.conf ? Math.round(Number(a.conf)) : 0}%)`)
      .join("\n");

    const recipientName = matchedUser ? matchedUser.fullName : (user?.name || "Team");

    setTaskDescription(
      `Dear ${recipientName},\n\nYou have been assigned to resolve customer dissatisfaction flagged by ABSA AI:\n- Customer: ${selectedFeedback.customerName}\n- Booking Code: ${selectedFeedback.bookingCode}\n- Date Submitted: ${selectedFeedback.submittedAt ? new Date(selectedFeedback.submittedAt).toLocaleString() : "N/A"}\n\nCustomer Raw Comment:\n"${selectedFeedback.comment || ""}"\n\nAI Sentiment Analysis Breakdown:\n${absaBreakdownText}\n\nRequired Action:\nPlease contact the guest or resolve service quality issues.`
    );

    setIsTaskModalOpen(true);
  };

  // Submit Task Form
  const submitTaskForm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFeedback) return;
    if (!assignedUserId) {
      toast.error("Please select an assignee.");
      return;
    }

    setSubmittingTask(true);
    try {
      // 1. Create Follow-up Task via service
      await taskService.create({
        title: taskTitle,
        description: taskDescription,
        assignedUserId: assignedUserId,
        priority: taskPriority,
        activityType: "FOLLOW_UP",
        customerId: selectedFeedback.customerId || undefined,
        startAt: taskStartAt ? new Date(taskStartAt).toISOString() : undefined,
        endAt: taskEndAt ? new Date(taskEndAt).toISOString() : undefined,
        primaryContactName: contactName || undefined,
        primaryContactPhone: contactPhone || undefined
      });

      toast.success("Follow-up task created successfully!");

      // 2. Automatically update review status to REVIEWED if it is currently PENDING
      if (selectedFeedback.reviewStatus === "PENDING") {
        await handleUpdateStatus("REVIEWED");
      }

      // 3. Reset states and close modals
      setIsTaskModalOpen(false);
      setIsDrawerOpen(false);
    } catch (err: any) {
      console.error("Error creating follow-up task:", err);
      toast.error(err.response?.data?.message || "Failed to create follow-up task.");
    } finally {
      setSubmittingTask(false);
    }
  };

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

  // Aspect Badge Class Builder
  const getAspectBadgeClass = (sentiment?: string) => {
    if (!sentiment) return "bg-slate-50 text-slate-400 border-slate-100";
    switch (sentiment.toUpperCase()) {
      case "POSITIVE":
        return "bg-emerald-50/50 text-emerald-700 border-emerald-100/80";
      case "NEGATIVE":
        return "bg-rose-50/50 text-rose-700 border-rose-100/80";
      default:
        return "bg-slate-50 text-slate-500 border-slate-100";
    }
  };

  const renderReportContent = () => {
    if (comparedStaffObjects.length < 2) return null;
    const nowStr = new Date().toLocaleDateString("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric"
    });

    return (
      <div className="space-y-4 text-slate-900 print-card" style={{ fontFamily: "Calibri, 'Segoe UI', Arial, sans-serif" }}>
        {/* Header Block */}
        <div className="flex justify-between items-end border-b-2 border-slate-800 pb-3">
          <div className="border-l-4 border-slate-800 pl-3">
            <h1 className="text-xl font-bold uppercase tracking-wider text-slate-900">Leadora CRM Analytics</h1>
            <p className="text-[10px] text-slate-500 uppercase tracking-widest mt-0.5 font-bold font-sans">Hotel Guest Experience Management Suite</p>
          </div>
          <div className="text-right">
            <h2 className="text-sm font-bold uppercase text-slate-900">Performance Comparison Report</h2>
            <p className="text-[10px] text-slate-500 font-mono mt-0.5">REF: LE-GX-{new Date().toISOString().slice(0, 10).replace(/-/g, "")}</p>
          </div>
        </div>

        {/* Metadata Details */}
        <div className="grid grid-cols-2 gap-x-8 gap-y-2 text-xs border-b border-slate-200 pb-3 font-sans">
          <div>
            <span className="font-bold text-slate-900">Date Generated:</span> {nowStr}
          </div>
          <div>
            <span className="font-bold text-slate-900">Evaluator Name:</span> {user?.name || "Hotel Manager"}
          </div>
          <div>
            <span className="font-bold text-slate-900">Evaluation Type:</span> ABSA Customer Sentiment Benchmarking
          </div>
          <div>
            <span className="font-bold text-slate-900">Department:</span> Guest Relations & Sales Team
          </div>
        </div>

        {/* Executive Summary */}
        <div className="space-y-1.5">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900 border-b border-slate-200 pb-1">1. Executive Summary</h3>
          <p className="text-[10px] text-slate-500 font-sans font-bold">
            Data Scope / Filter Range: {startDate} - {endDate}
          </p>
          <p className="text-xs text-slate-650 leading-relaxed font-normal">
            This document outlines the comparative guest satisfaction metrics and sales outcome correlations for the selected sales representatives. By combining Aspect-Based Sentiment Analysis (ABSA) confidence ratios with actual pipeline conversion rates and SLA task compliance, this report aims to identify operational strengths, training requirements, and direct peer-to-peer mentoring pathways.
          </p>
        </div>

        {/* Comparative Core Matrix Table */}
        <div className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900 border-b border-slate-200 pb-1">2. Core Business Outcomes & SLA Metrics</h3>
          <table className="w-full text-left text-xs border-collapse border border-slate-300">
            <thead>
              <tr className="bg-slate-100 border-b border-slate-300 text-[10px] font-bold text-slate-800 uppercase">
                <th className="border border-slate-300 px-3 py-1.5" style={{ width: "35%" }}>Metric Details</th>
                {comparedStaffObjects.map(s => (
                  <th key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center" style={{ width: `${65 / comparedStaffObjects.length}%` }}>{displayStaffName(s.staffName)}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-slate-700">
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold bg-slate-50/50">Overall Satisfaction (CSAT)</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center font-bold">{s.satisfactionRatio}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">Total Deals Assigned</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.totalDeals}</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">Conversion Rate</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.conversionRate}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">Revenue Won</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center font-semibold text-slate-900">
                    {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'VND', minimumFractionDigits: 0 }).format(s.totalRevenueWon || 0).replace("₫", "").trim()} VND
                  </td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">SLA Punctuality Rate</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.taskPunctualityRate}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold text-rose-700">Overdue Task Backlog</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center font-bold text-rose-600">{s.overdueTasksCount}</td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>

        {/* Aspect Satisfaction Table */}
        <div className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900 border-b border-slate-200 pb-1">3. AI Aspect CSAT Breakdown (%)</h3>
          <table className="w-full text-left text-xs border-collapse border border-slate-300">
            <thead>
              <tr className="bg-slate-100 border-b border-slate-300 text-[10px] font-bold text-slate-800 uppercase">
                <th className="border border-slate-300 px-3 py-1.5" style={{ width: "25%" }}>Aspect</th>
                <th className="border border-slate-300 px-3 py-1.5 text-center" style={{ width: "15%" }}>Dept. Average</th>
                {comparedStaffObjects.map(s => (
                  <th key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center" style={{ width: `${60 / comparedStaffObjects.length}%` }}>{displayStaffName(s.staffName)}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-slate-700">
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold bg-slate-50/50">Attitude</td>
                <td className="border border-slate-300 px-3 py-1.5 text-center text-slate-400 font-semibold">{departmentAverage.attitude}%</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.attitudePositiveRatio}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">Speed</td>
                <td className="border border-slate-300 px-3 py-1.5 text-center text-slate-400 font-semibold">{departmentAverage.speed}%</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.speedPositiveRatio}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold bg-slate-50/50">Accuracy</td>
                <td className="border border-slate-300 px-3 py-1.5 text-center text-slate-400 font-semibold">{departmentAverage.accuracy}%</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.accuracyPositiveRatio}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold">Facility</td>
                <td className="border border-slate-300 px-3 py-1.5 text-center text-slate-400 font-semibold">{departmentAverage.facility}%</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.facilityPositiveRatio}%</td>
                ))}
              </tr>
              <tr>
                <td className="border border-slate-300 px-3 py-1.5 font-semibold bg-slate-50/50">Price</td>
                <td className="border border-slate-300 px-3 py-1.5 text-center text-slate-400 font-semibold">{departmentAverage.price}%</td>
                {comparedStaffObjects.map(s => (
                  <td key={s.staffId} className="border border-slate-300 px-3 py-1.5 text-center">{s.pricePositiveRatio}%</td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>

        {/* AI Recommendations */}
        <div className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-900 border-b border-slate-200 pb-1">4. AI-Driven Coaching & Mentoring Guidelines</h3>
          {coachingRecommendations.length === 0 ? (
            <p className="text-xs text-slate-500 italic font-normal">No significant aspect deltas detected between selected representatives. Performance levels are aligned.</p>
          ) : (
            <div className="space-y-1.5">
              {coachingRecommendations.map((rec, idx) => (
                <div key={idx} className="border-l-2 border-slate-500 pl-3 py-0.5">
                  <div className="font-bold text-slate-800 text-[11px]">{rec.title}</div>
                  <div className="text-slate-650 text-xs mt-0.5 leading-normal font-normal">{rec.suggestion}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Document Footer / Audit Log (Replaces signature lines) */}
        <div className="pt-4 border-t border-slate-200 text-[9px] text-slate-500 font-sans space-y-1.5">
          <div className="flex justify-between font-bold text-slate-600">
            <div>
              <span>Verification Status:</span> System Compiled & Archived
            </div>
            <div>
              <span>Verification Ref:</span> LE-GX-{new Date().toISOString().slice(0, 10).replace(/-/g, "")}-REF
            </div>
          </div>
          <p className="leading-relaxed italic font-normal">
            Disclaimer: This document is an automatically compiled benchmarking report generated by Leadora CRM ABSA Engine. It is intended solely for internal evaluation, sales coaching reference, and department performance analysis. No formal signatures or contractual agreements are required or implied by the content of this evaluation.
          </p>
          <div className="bg-slate-50 border border-slate-200/60 p-1.5 text-[8.5px] text-slate-700 font-semibold rounded flex items-center justify-between font-sans">
            <span>Drafted Mentoring Tasks:</span>
            <span>{coachingRecommendations.length} peer-coaching task(s) drafted for execution</span>
          </div>
        </div>
      </div>
    );
  };

  return (
    <>
      <div className="space-y-6 min-h-[101vh] print:hidden" style={{ scrollbarGutter: "stable" }}>
        {/* Standard Header conforming strictly to booking-confirmation sizing */}
        <PageHeader
          {...PAGE_META.sentimentAnalytics}
          actions={
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
            </div>
          }
        />

        {/* Global Filter Bar */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between bg-white border border-slate-200/80 rounded-xl p-4 shadow-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[10px] font-bold text-slate-450 uppercase tracking-wider mr-2">Time Range:</span>
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
                  className={`text-xs font-semibold px-3.5 h-9 rounded-lg border transition-all duration-150 cursor-pointer ${active
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
                className="rounded-lg border border-slate-200 px-3 h-9 text-xs text-slate-700 bg-white focus:outline-none focus:border-brand-500 transition"
              />
              <span className="text-xs text-slate-455 font-semibold">to</span>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="rounded-lg border border-slate-200 px-3 h-9 text-xs text-slate-700 bg-white focus:outline-none focus:border-brand-500 transition"
              />
            </div>
          )}
        </div>

        {/* Tab Switcher */}
        <div className="flex border-b border-slate-200 gap-2">
          <button
            onClick={() => setActiveTab("overview")}
            className={`px-6 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${activeTab === "overview"
                ? "border-slate-900 text-slate-900"
                : "border-transparent text-slate-400 hover:text-slate-600"
              }`}
          >
            Overview & Trends
          </button>
          <button
            onClick={() => setActiveTab("staff")}
            className={`px-6 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${activeTab === "staff"
                ? "border-slate-900 text-slate-900"
                : "border-transparent text-slate-400 hover:text-slate-600"
              }`}
          >
            Staff Performance & Benchmarking
          </button>
        </div>

        {activeTab === "overview" && (
          <>
            {/* Top Metrics Grid Rebuilt for Hotel Sales Managers */}
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard
                label="Total Feedback Scanned"
                value={loadingOverview ? "—" : operationalMetrics.totalFeedback.toLocaleString()}
                sub="Processed comments & review tags"
                subColor="text-slate-500"
                icon={MessageSquare}
              />
              <MetricCard
                label="Customer Satisfaction"
                value={loadingOverview ? "—" : operationalMetrics.netSentiment}
                sub="Overall positive sentiment ratio"
                subColor={operationalMetrics.netColor}
                icon={TrendingUp}
              />
              <MetricCard
                label="Primary Service Bottleneck"
                value={loadingOverview ? "—" : operationalMetrics.bottleneck}
                sub={operationalMetrics.bottleneckSub}
                subColor="text-rose-600"
                icon={AlertTriangle}
              />
              <MetricCard
                label="Staff Service Health"
                value={loadingOverview ? "—" : operationalMetrics.staffHealth}
                sub="Positive Attitude & Accuracy tags"
                subColor={operationalMetrics.staffHealthColor}
                icon={Sparkles}
              />
            </div>

            {/* Row 2: Charts (Grid 2/3 and 1/3 layout like Manager Dashboard & Mockups) */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Left: Sentiment Trend Line Chart */}
              <Card className="lg:col-span-2 bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl">
                <CardHeader className="pb-2 flex flex-row items-center justify-between">
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground">Sentiment Trend Over Time</CardTitle>
                    <CardDescription className="text-xs text-muted-foreground mt-0.5">
                      Spline curves tracking satisfaction timeline
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
                  </div>
                </CardHeader>
                <CardContent className="pt-4">
                  {loadingTrend && (
                    <div className="h-[300px] flex items-center justify-center text-xs text-slate-400">
                      Loading satisfaction trend charts...
                    </div>
                  )}
                  {!loadingTrend && trendChartData.length === 0 && (
                    <div className="h-[300px] flex items-center justify-center text-xs text-slate-400">
                      No trend history records found.
                    </div>
                  )}
                  {!loadingTrend && trendChartData.length > 0 && (
                    <div className="h-[300px] w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <ComposedChart data={trendChartData} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}>
                          <defs>
                            <linearGradient id="colorIndigo" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#5B51D8" stopOpacity={0.25} />
                              <stop offset="95%" stopColor="#5B51D8" stopOpacity={0.0} />
                            </linearGradient>
                          </defs>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                          <XAxis dataKey="period" stroke="#94A3B8" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: '#94A3B8' }} />
                          <YAxis stroke="#94A3B8" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: '#94A3B8' }} allowDecimals={false} />
                          <Tooltip content={<CustomTooltip />} />
                          <Legend verticalAlign="top" height={36} iconSize={8} iconType="circle" wrapperStyle={{ fontSize: 10 }} />
                          <Area type="monotone" dataKey="Positive" stroke="#5B51D8" strokeWidth={3} fill="url(#colorIndigo)" dot={false} activeDot={{ r: 6, strokeWidth: 0 }} />
                          <Line type="monotone" dataKey="Neutral" stroke="#C042EC" strokeWidth={2.5} strokeDasharray="3 4" dot={false} />
                          <Line type="monotone" dataKey="Negative" stroke="#38BDF8" strokeWidth={2.5} strokeDasharray="2 4" dot={false} />
                        </ComposedChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Right: Aspect Breakdown (Stacked Area Chart) */}
              <Card className="bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl">
                <CardHeader className="pb-2">
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground">Aspect Breakdown</CardTitle>
                    <CardDescription className="text-xs text-muted-foreground mt-0.5">
                      Sentiment proportion distribution across aspects
                    </CardDescription>
                  </div>
                </CardHeader>
                <CardContent className="pt-4">
                  {loadingOverview && (
                    <div className="h-[300px] flex items-center justify-center text-xs text-slate-400">
                      Loading breakdown segments...
                    </div>
                  )}
                  {!loadingOverview && systemChartData.length === 0 && (
                    <div className="h-[300px] flex items-center justify-center text-xs text-slate-400">
                      No aspect stats recorded.
                    </div>
                  )}
                  {!loadingOverview && systemChartData.length > 0 && (
                    <div className="h-[300px] w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart
                          data={systemChartData}
                          margin={{ top: 5, right: 15, left: -20, bottom: 5 }}
                        >
                          <defs>
                            <linearGradient id="gradientPositive" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#5B51D8" stopOpacity={0.3} />
                              <stop offset="95%" stopColor="#5B51D8" stopOpacity={0.0} />
                            </linearGradient>
                            <linearGradient id="gradientNeutral" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#C042EC" stopOpacity={0.3} />
                              <stop offset="95%" stopColor="#C042EC" stopOpacity={0.0} />
                            </linearGradient>
                            <linearGradient id="gradientNegative" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor="#FF7BB0" stopOpacity={0.3} />
                              <stop offset="95%" stopColor="#FF7BB0" stopOpacity={0.0} />
                            </linearGradient>
                          </defs>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                          <XAxis dataKey="name" stroke="#94A3B8" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: '#94A3B8' }} />
                          <YAxis stroke="#94A3B8" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: '#94A3B8' }} allowDecimals={false} />
                          <Tooltip
                            contentStyle={{
                              backgroundColor: 'rgba(15, 23, 42, 0.95)',
                              borderRadius: '12px',
                              border: '1px solid rgba(255, 255, 255, 0.1)',
                              color: '#fff',
                              padding: '10px 14px',
                              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)'
                            }}
                          />
                          <Legend verticalAlign="top" height={36} iconSize={8} iconType="circle" wrapperStyle={{ fontSize: 10 }} />
                          <Area type="monotone" dataKey="Positive" stroke="#5B51D8" strokeWidth={2.5} fill="url(#gradientPositive)" fillOpacity={0.3} dot={false} activeDot={{ r: 6, strokeWidth: 0 }} />
                          <Area type="monotone" dataKey="Neutral" stroke="#C042EC" strokeWidth={2.5} fill="url(#gradientNeutral)" fillOpacity={0.3} dot={false} activeDot={{ r: 6, strokeWidth: 0 }} />
                          <Area type="monotone" dataKey="Negative" stroke="#FF7BB0" strokeWidth={2.5} fill="url(#gradientNegative)" fillOpacity={0.3} dot={false} activeDot={{ r: 6, strokeWidth: 0 }} />
                        </AreaChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Row 2.5: Service Highlights & Hotspots (Drivers) */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Service Highlights */}
              <Card className="bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl">
                <CardHeader className="pb-2">
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground">Service Highlights</CardTitle>
                    <CardDescription className="text-xs text-muted-foreground mt-0.5">
                      Key operational drivers with highest positive guest experience sentiment
                    </CardDescription>
                  </div>
                </CardHeader>
                <CardContent className="pt-4 pb-6">
                  {loadingOverview && (
                    <div className="h-[200px] flex items-center justify-center text-xs text-slate-400">
                      Loading service highlights...
                    </div>
                  )}
                  {!loadingOverview && positiveAspectDrivers.length === 0 && (
                    <div className="h-[200px] flex items-center justify-center text-xs text-slate-400">
                      No sentiment data recorded.
                    </div>
                  )}
                  {!loadingOverview && positiveAspectDrivers.length > 0 && (
                    <div className="space-y-4 pt-2">
                      {positiveAspectDrivers.map((item) => {
                        const isActive = selectedAspect === item.key && selectedSentiment === "Positive";
                        return (
                          <div
                            key={item.key}
                            onClick={() => {
                              if (isActive) {
                                setSelectedAspect(null);
                                setSelectedSentiment(null);
                              } else {
                                setSelectedAspect(item.key);
                                setSelectedSentiment("Positive");
                              }
                              setPage(0);
                            }}
                            className={`group cursor-pointer p-2 rounded-xl transition-all duration-150 ${isActive ? "bg-indigo-50 border border-indigo-100 shadow-xs" : "border border-transparent hover:bg-slate-50"
                              }`}
                          >
                            <div className="flex items-center justify-between text-xs font-semibold mb-2">
                              <span className={`text-slate-700 transition ${isActive ? "text-indigo-700 font-bold" : "group-hover:text-indigo-600"}`}>
                                {item.label}
                              </span>
                              <span className="text-indigo-650 font-bold">{item.percentage}%</span>
                            </div>
                            <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden p-0.5 shadow-inner">
                              <div
                                className="h-full bg-gradient-to-r from-[#5B51D8] to-[#818CF8] rounded-full transition-all duration-500 shadow-sm"
                                style={{ width: `${item.percentage}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Service Hotspots */}
              <Card className="bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl">
                <CardHeader className="pb-2">
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground">Service Hotspots</CardTitle>
                    <CardDescription className="text-xs text-muted-foreground mt-0.5">
                      Key operational hotspots with highest negative guest experience sentiment
                    </CardDescription>
                  </div>
                </CardHeader>
                <CardContent className="pt-4 pb-6">
                  {loadingOverview && (
                    <div className="h-[200px] flex items-center justify-center text-xs text-slate-400">
                      Loading service hotspots...
                    </div>
                  )}
                  {!loadingOverview && negativeAspectDrivers.length === 0 && (
                    <div className="h-[200px] flex items-center justify-center text-xs text-slate-400">
                      No sentiment data recorded.
                    </div>
                  )}
                  {!loadingOverview && negativeAspectDrivers.length > 0 && (
                    <div className="space-y-4 pt-2">
                      {negativeAspectDrivers.map((item) => {
                        const isActive = selectedAspect === item.key && selectedSentiment === "Negative";
                        return (
                          <div
                            key={item.key}
                            onClick={() => {
                              if (isActive) {
                                setSelectedAspect(null);
                                setSelectedSentiment(null);
                              } else {
                                setSelectedAspect(item.key);
                                setSelectedSentiment("Negative");
                              }
                              setPage(0);
                            }}
                            className={`group cursor-pointer p-2 rounded-xl transition-all duration-150 ${isActive ? "bg-rose-50 border border-rose-100 shadow-xs" : "border border-transparent hover:bg-slate-50"
                              }`}
                          >
                            <div className="flex items-center justify-between text-xs font-semibold mb-2">
                              <span className={`text-slate-700 transition ${isActive ? "text-rose-700 font-bold" : "group-hover:text-rose-600"}`}>
                                {item.label}
                              </span>
                              <span className="text-rose-650 font-bold">{item.percentage}%</span>
                            </div>
                            <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden p-0.5 shadow-inner">
                              <div
                                className="h-full bg-gradient-to-r from-[#FF8A65] to-[#FFAB91] rounded-full transition-all duration-500 shadow-sm"
                                style={{ width: `${item.percentage}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

          </>
        )}

        {activeTab === "staff" && (
          <>
            {/* Row 3: Sales Staff Performance Comparison */}
            <Card className="bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl">
              <CardHeader className="pb-3 border-b border-slate-100">
                <div>
                  <CardTitle className="text-sm font-bold text-foreground">Sales Staff Performance Comparison</CardTitle>
                  <CardDescription className="text-xs text-muted-foreground mt-0.5">
                    Analyze and compare guest satisfaction index and customer sentiment metrics across sales staff
                  </CardDescription>
                </div>
              </CardHeader>
              <CardContent className="pt-4">
                {staffPerformanceData.length === 0 ? (
                  <p className="py-8 text-center text-xs text-slate-400 italic">No sales staff feedback data available.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-50/50 border-b border-slate-150 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                          <th className="pl-6 pr-2 py-3 w-10">Select</th>
                          <th className="px-6 py-3">Sales Staff</th>
                          <th className="px-6 py-3 text-center">Reviews</th>
                          <th className="px-6 py-3 text-center">CSAT Index</th>
                          <th className="px-6 py-3 text-center">Conversion</th>
                          <th className="px-6 py-3 text-center">Revenue Won</th>
                          <th className="px-6 py-3 text-center">SLA Punctuality</th>
                          <th className="px-6 py-3 text-center">Overdue Backlog</th>
                          <th className="px-6 py-3 text-center">AI Highlights</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 text-slate-700">
                        {staffPerformanceData.map((item) => {
                          const ratio = item.satisfactionRatio;
                          const ratioBg = ratio >= 85 ? "bg-emerald-50 text-emerald-700 border-emerald-100" : ratio >= 70 ? "bg-slate-100 text-slate-700 border-slate-200" : "bg-rose-50 text-rose-700 border-rose-100";
                          const dotBg = ratio >= 85 ? "bg-emerald-500" : ratio >= 70 ? "bg-slate-400" : "bg-rose-500";
                          const isChecked = selectedStaffForCompare.includes(item.staffId);

                          return (
                            <tr key={item.staffId} className="hover:bg-slate-50/50 transition duration-150">
                              <td className="pl-6 pr-2 py-3">
                                <input
                                  type="checkbox"
                                  checked={isChecked}
                                  onChange={(e) => {
                                    if (e.target.checked) {
                                      if (selectedStaffForCompare.length >= 3) {
                                        toast.info("You can compare a maximum of 3 sales staff members.");
                                        return;
                                      }
                                      setSelectedStaffForCompare([...selectedStaffForCompare, item.staffId]);
                                    } else {
                                      setSelectedStaffForCompare(selectedStaffForCompare.filter(x => x !== item.staffId));
                                    }
                                  }}
                                  className="rounded border-slate-350 text-indigo-650 focus:ring-indigo-500 size-3.5 cursor-pointer"
                                />
                              </td>
                              <td className="px-6 py-3">
                                <div className="flex items-center gap-2">
                                  <div className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-[10px] font-bold text-slate-660 border border-slate-200 shrink-0">
                                    {getInitials(item.staffName)}
                                  </div>
                                  <div>
                                    <span className="font-semibold text-slate-900">{item.staffName}</span>
                                    {item.email && <div className="text-[9px] text-slate-400 font-mono mt-0.5">{item.email}</div>}
                                  </div>
                                </div>
                              </td>
                              <td className="px-6 py-3 text-center font-medium text-slate-800">
                                {item.totalFeedbacks}
                              </td>
                              <td className="px-6 py-3 text-center">
                                <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] border ${ratioBg}`}>
                                  <span className={`size-1.5 rounded-full ${dotBg}`} />
                                  {ratio}%
                                </span>
                              </td>
                              <td className="px-6 py-3 text-center font-semibold text-slate-800">
                                {item.conversionRate ? `${item.conversionRate}%` : "0%"}
                              </td>
                              <td className="px-6 py-3 text-center font-semibold text-indigo-650">
                                {item.totalRevenueWon ? new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(item.totalRevenueWon) : "0 VND"}
                              </td>
                              <td className="px-6 py-3 text-center font-semibold text-slate-800">
                                {item.taskPunctualityRate ? `${item.taskPunctualityRate}%` : "0%"}
                              </td>
                              <td className={`px-6 py-3 text-center font-bold ${item.overdueTasksCount > 0 ? "text-rose-650" : "text-emerald-650"}`}>
                                {item.overdueTasksCount || 0}
                              </td>
                              <td className="px-6 py-3 text-center">
                                <div className="flex items-center justify-center gap-1.5 flex-wrap">
                                  {item.topStrongAspect && item.topStrongAspect !== 'N/A' && (
                                    <span className="bg-emerald-50 text-emerald-700 px-1.5 py-0.5 rounded border border-emerald-100 text-[9px] font-bold">
                                      {item.topStrongAspect}
                                    </span>
                                  )}
                                  {item.topWeakAspect && item.topWeakAspect !== 'N/A' && (
                                    <span className="bg-rose-50 text-rose-700 px-1.5 py-0.5 rounded border border-rose-100 text-[9px] font-bold">
                                      {item.topWeakAspect}
                                    </span>
                                  )}
                                  {(!item.topStrongAspect || item.topStrongAspect === 'N/A') && (
                                    <span className="text-slate-400 italic text-[10px]">No tags</span>
                                  )}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>
          </>
        )}

        {activeTab === "overview" && (
          <>

            {/* Row 4: Deep Dive Table */}
            <div className="bg-white border-0 shadow-[0_10px_30px_rgba(0,0,0,0.04)] rounded-2xl overflow-hidden">
              <div className="p-5 border-b border-slate-100 bg-white flex flex-row items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">Deep-Dive Customer Reviews</h3>
                  <p className="text-xs text-slate-500 mt-1 flex items-center gap-1.5 flex-wrap">
                    <span>Inspect customer comments and staff details. Severe negative items are flagged.</span>
                    {(selectedAspect || selectedSentiment) && (
                      <span className="inline-flex items-center gap-1 bg-indigo-50/50 text-indigo-700 px-2.5 py-0.5 rounded-lg text-[10px] font-semibold border border-indigo-100 animate-in fade-in duration-150">
                        Filtered: <span className="capitalize">{selectedAspect}</span> &bull; {selectedSentiment}
                        <button
                          onClick={() => {
                            setSelectedAspect(null);
                            setSelectedSentiment(null);
                            setPage(0);
                          }}
                          className="ml-1.5 text-indigo-400 hover:text-indigo-650 font-bold cursor-pointer"
                        >
                          &times;
                        </button>
                      </span>
                    )}
                  </p>
                </div>
              </div>
              <div>
                {loadingDeepDive ? (
                  <div className="py-20 text-center text-xs text-slate-400 flex flex-col items-center justify-center gap-2 bg-white">
                    <RefreshCw className="size-6 animate-spin text-slate-350" />
                    <span>Loading matching customer reviews...</span>
                  </div>
                ) : deepDiveData.length === 0 ? (
                  <div className="py-20 text-center text-xs text-slate-400 italic bg-white">
                    No customer reviews found matching the selected filters.
                  </div>
                ) : (
                  <div className="overflow-x-auto bg-white">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-50/70 border-b border-slate-200 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                          <th className="px-6 py-3.5">Customer / Booking</th>
                          <th className="px-6 py-3.5">Assigned Staff</th>
                          <th className="px-6 py-3.5 text-center">Overall Sentiment</th>
                          <th className="px-6 py-3.5 max-w-md">Comment Snippet</th>
                          <th className="px-6 py-3.5">Date</th>
                          <th className="px-6 py-3.5 text-center">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 text-slate-700">
                        {deepDiveData.map((f) => {
                          const isSevere = hasSevereNegative(f);
                          const overallSent = getOverallSentiment(f);
                          return (
                            <tr
                              key={f.feedbackId}
                              onClick={() => {
                                setSelectedFeedback(f);
                                setIsDrawerOpen(true);
                              }}
                              className="hover:bg-indigo-50/30 cursor-pointer transition-colors duration-150"
                            >
                              <td className="px-6 py-4">
                                <div className="font-semibold text-slate-900">{f.customerName}</div>
                                <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                                  {f.bookingCode}
                                </div>
                              </td>
                              <td className="px-6 py-4">
                                <div className="flex items-center gap-2 mt-1">
                                  <div className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-[10px] font-bold text-slate-650 border border-slate-200">
                                    {getInitials(f.salesStaffName)}
                                  </div>
                                  <span className="font-medium text-slate-900">{f.salesStaffName || "Unassigned"}</span>
                                </div>
                              </td>
                              <td className="px-6 py-4 text-center">
                                {overallSent === "POSITIVE" && (
                                  <span className="bg-emerald-50 text-emerald-700 border border-emerald-100/80 rounded-md px-2 py-0.5 text-[10px] font-semibold inline-flex items-center gap-1.5 select-none">
                                    <span className="size-1.5 rounded-full bg-emerald-500" />
                                    Positive
                                  </span>
                                )}
                                {overallSent === "NEGATIVE" && (
                                  <span className="bg-rose-50 text-rose-700 border border-rose-100/80 rounded-md px-2 py-0.5 text-[10px] font-semibold inline-flex items-center gap-1.5 select-none">
                                    <span className="size-1.5 rounded-full bg-rose-500" />
                                    Negative
                                  </span>
                                )}
                                {overallSent === "NEUTRAL" && (
                                  <span className="bg-slate-50 text-slate-600 border border-slate-200/50 rounded-md px-2 py-0.5 text-[10px] font-semibold inline-flex items-center gap-1.5 select-none">
                                    <span className="size-1.5 rounded-full bg-slate-400" />
                                    Neutral
                                  </span>
                                )}
                              </td>
                              <td className="px-6 py-4 max-w-md">
                                <div className="flex items-center gap-2">
                                  {isSevere && (
                                    <span className="bg-rose-50 text-rose-700 border border-rose-100 px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider flex items-center gap-1 shrink-0 select-none" title="Severe complaint detected (>80% negative confidence)">
                                      <span className="size-1 rounded-full bg-rose-500 animate-ping" />
                                      Severe Alert
                                    </span>
                                  )}
                                  <div className="text-slate-800 font-normal italic truncate" title={f.comment}>
                                    {f.comment ? `"${f.comment}"` : <span className="text-slate-400 italic">No comment</span>}
                                  </div>
                                </div>
                              </td>
                              <td className="px-6 py-4 text-slate-500">
                                {f.submittedAt ? new Date(f.submittedAt).toLocaleDateString() : "—"}
                              </td>
                              <td className="px-6 py-4 text-center">
                                <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${f.reviewStatus === "PENDING"
                                  ? "bg-amber-50 text-amber-700 border-amber-200/60"
                                  : f.reviewStatus === "REVIEWED"
                                    ? "bg-emerald-50 text-emerald-700 border-emerald-200/60"
                                    : "bg-slate-100 text-slate-500 border-slate-200"
                                  }`}>
                                  {f.reviewStatus}
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
 
                    {/* Table Pagination */}
                    <div className="flex items-center justify-between border-t border-slate-100 px-6 py-3 bg-slate-50/50 select-none">
                      <p className="text-[11px] text-slate-400 font-medium">
                        Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalDeepDiveElements || 0)} of {totalDeepDiveElements || 0} reviews
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
                          Page {page + 1} of {Math.ceil((totalDeepDiveElements || 0) / pageSize) || 1}
                        </span>
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={(page + 1) * pageSize >= (totalDeepDiveElements || 0)}
                          onClick={() => setPage(page + 1)}
                          className="p-1 size-7 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-center disabled:opacity-40"
                        >
                          <ChevronRight className="size-4 text-slate-600" />
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {/* Aspect Detail Drawer (Sliding from Right on Row Click) */}
        {isDrawerOpen && selectedFeedback && (
          <div className="fixed inset-0 z-50 overflow-hidden" aria-labelledby="slide-over-title" role="dialog" aria-modal="true">
            <div className="absolute inset-0 overflow-hidden">
              {/* Backdrop */}
              <div
                className="absolute inset-0 bg-slate-900/30 backdrop-blur-xs transition-opacity cursor-pointer"
                onClick={() => setIsDrawerOpen(false)}
              />

              <div className="pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10">
                <div className="pointer-events-auto w-screen max-w-md transform transition-all duration-300">
                  <div className="flex h-full flex-col overflow-y-scroll bg-white shadow-2xl border-l border-slate-200">
                    {/* Header */}
                    <div className="p-6 border-b border-slate-100 flex items-center justify-between">
                      <div>
                        <h2 className="text-sm font-bold text-slate-900">Feedback Details</h2>
                        <p className="text-[10px] text-slate-400 font-mono mt-0.5">ID: {selectedFeedback.feedbackId}</p>
                      </div>
                      <button
                        onClick={() => setIsDrawerOpen(false)}
                        className="text-slate-400 hover:text-slate-600 font-bold text-lg cursor-pointer"
                      >
                        &times;
                      </button>
                    </div>

                    {/* Body Content */}
                    <div className="p-6 space-y-6 flex-1">
                      {/* Customer & Booking Details */}
                      <div>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Customer & Booking Info</h3>
                        <div className="bg-slate-50 rounded-lg p-3 space-y-1.5 text-xs text-slate-700">
                          <div className="flex justify-between">
                            <span className="text-slate-450">Guest Name:</span>
                            <span className="font-semibold text-slate-900">{selectedFeedback.customerName}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-slate-450">Booking Code:</span>
                            <span className="font-mono font-semibold text-slate-900">{selectedFeedback.bookingCode}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-slate-450">Date Submitted:</span>
                            <span>{selectedFeedback.submittedAt ? new Date(selectedFeedback.submittedAt).toLocaleString() : "—"}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-slate-455">Review Status:</span>
                            <span className="font-bold text-brand-600">{selectedFeedback.reviewStatus}</span>
                          </div>
                        </div>
                      </div>

                      {/* Assigned Sales Staff details */}
                      <div>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Assigned Sales Staff</h3>
                        <div className="flex items-center gap-3 bg-slate-50 rounded-lg p-3">
                          <div className="w-10 h-10 rounded-full bg-slate-200 flex items-center justify-center text-xs font-bold text-slate-650">
                            {getInitials(selectedFeedback.salesStaffName)}
                          </div>
                          <div>
                            <p className="text-xs font-bold text-slate-900">{selectedFeedback.salesStaffName || "Unassigned Staff"}</p>
                            <p className="text-[10px] text-slate-400 mt-0.5">Sales / Reservation Agent</p>
                          </div>
                        </div>
                      </div>

                      {/* Raw Customer Comment */}
                      <div>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Raw Comment</h3>
                        <div className="bg-slate-50 rounded-lg p-3.5 italic text-xs text-slate-800 leading-relaxed">
                          {selectedFeedback.comment ? `"${selectedFeedback.comment}"` : "No comment content provided."}
                        </div>
                      </div>

                      {/* AI Aspect Breakdown Matrix */}
                      <div>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">AI Aspect Breakdown Matrix</h3>
                        <div className="space-y-2">
                          {[
                            { label: "Attitude (Staff)", sentiment: selectedFeedback.absaAttitudeSentiment, conf: selectedFeedback.absaAttitudeConfidence },
                            { label: "Speed & Process", sentiment: selectedFeedback.absaSpeedSentiment, conf: selectedFeedback.absaSpeedConfidence },
                            { label: "Accuracy & Info", sentiment: selectedFeedback.absaAccuracySentiment, conf: selectedFeedback.absaAccuracyConfidence },
                            { label: "Facility (Hotel)", sentiment: selectedFeedback.absaFacilitySentiment, conf: selectedFeedback.absaFacilityConfidence },
                            { label: "Price & Value", sentiment: selectedFeedback.absaPriceSentiment, conf: selectedFeedback.absaPriceConfidence }
                          ].map((item, idx) => {
                            const confVal = item.conf ? `${Math.round(Number(item.conf))}%` : "—";
                            return (
                              <div key={idx} className="flex items-center justify-between border-b border-slate-100 pb-2 text-xs">
                                <span className="font-medium text-slate-700">{item.label}</span>
                                <div className="flex items-center gap-2">
                                  <span className={`px-2 py-0.5 rounded text-[11px] font-medium border ${getAspectBadgeClass(item.sentiment)}`}>
                                    {item.sentiment || "Neutral"}
                                  </span>
                                  <span className="text-[10px] text-slate-400 font-mono" title="Confidence Score">
                                    {confVal}
                                  </span>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    </div>

                    {/* Closed-Loop Action Toolbar */}
                    <div className="p-6 border-t border-slate-100 bg-slate-50 space-y-3">
                      <div className="grid grid-cols-2 gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handleUpdateStatus("REVIEWED")}
                          disabled={selectedFeedback.reviewStatus === "REVIEWED"}
                          leftIcon={<Star className="size-3.5" />}
                          className="w-full text-xs"
                        >
                          Reviewed
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handleUpdateStatus("DISMISSED")}
                          disabled={selectedFeedback.reviewStatus === "DISMISSED"}
                          leftIcon={<AlertTriangle className="size-3.5" />}
                          className="w-full text-xs text-rose-600 hover:text-rose-700"
                        >
                          Dismiss
                        </Button>
                      </div>

                      <Button
                        variant="primary"
                        size="sm"
                        onClick={handleCreateFollowUpTask}
                        leftIcon={<Sparkles className="size-3.5" />}
                        className="w-full text-xs"
                      >
                        Create Follow-up Task
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Task Creation Modal */}
        {isTaskModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto p-4 select-none">
            <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity cursor-pointer" onClick={() => setIsTaskModalOpen(false)} />

            <div className="relative bg-white rounded-xl border border-slate-200 shadow-2xl w-full max-w-lg overflow-hidden transform transition-all">
              <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-900">Create Follow-up Task</h3>
                <button onClick={() => setIsTaskModalOpen(false)} className="text-slate-400 hover:text-slate-600 font-bold text-lg cursor-pointer">&times;</button>
              </div>

              <form onSubmit={submitTaskForm} className="p-6 space-y-4 text-left">
                {/* Assignee Selection (Locked) */}
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Assignee</label>
                  <div className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs bg-slate-50 text-slate-800 font-semibold flex items-center gap-2">
                    <div className="w-5 h-5 rounded-full bg-slate-200 flex items-center justify-center text-[10px] font-bold text-slate-650">
                      {getInitials(assignedUserName)}
                    </div>
                    <span>{assignedUserName}</span>
                  </div>
                  <input type="hidden" value={assignedUserId} required />
                </div>

                {/* Priority */}
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Priority</label>
                  <select
                    value={taskPriority}
                    onChange={(e) => setTaskPriority(e.target.value as any)}
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs bg-white text-slate-700 focus:outline-none focus:border-brand-500 cursor-pointer"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                </div>

                {/* Start and Due Date Time */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Start Date & Time</label>
                    <input
                      type="datetime-local"
                      value={taskStartAt}
                      onChange={(e) => setTaskStartAt(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500 cursor-pointer"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Due Date & Time</label>
                    <input
                      type="datetime-local"
                      value={taskEndAt}
                      onChange={(e) => setTaskEndAt(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500 cursor-pointer"
                      required
                    />
                  </div>
                </div>

                {/* Customer Contact details */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Contact Customer</label>
                    <input
                      type="text"
                      value={contactName}
                      onChange={(e) => setContactName(e.target.value)}
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Contact Phone</label>
                    <input
                      type="text"
                      value={contactPhone}
                      onChange={(e) => setContactPhone(e.target.value)}
                      placeholder="Enter phone number"
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500"
                    />
                  </div>
                </div>

                {/* Title */}
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Task Title</label>
                  <input
                    type="text"
                    value={taskTitle}
                    onChange={(e) => setTaskTitle(e.target.value)}
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500"
                    required
                  />
                </div>

                {/* Description */}
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">Description & Reason</label>
                  <textarea
                    value={taskDescription}
                    onChange={(e) => setTaskDescription(e.target.value)}
                    rows={5}
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-brand-500 font-mono leading-relaxed"
                    required
                  />
                </div>

                {/* Buttons */}
                <div className="flex items-center justify-end gap-2 border-t border-slate-100 pt-4 mt-6">
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => setIsTaskModalOpen(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    disabled={submittingTask}
                  >
                    {submittingTask ? "Creating..." : "Confirm & Create Task"}
                  </Button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Floating Benchmarking Action Bar */}
        {selectedStaffForCompare.length > 0 && (
          <div className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-slate-900/95 text-white px-6 py-3.5 rounded-full shadow-2xl flex items-center gap-6 z-50 border border-white/10 animate-in slide-in-from-bottom duration-300 backdrop-blur-md">
            <span className="text-xs font-semibold">
              Selected {selectedStaffForCompare.length} staff member(s) to benchmark
            </span>
            <div className="flex items-center gap-3">
              <Button
                variant="primary"
                size="sm"
                disabled={selectedStaffForCompare.length < 2 || selectedStaffForCompare.length > 3}
                onClick={() => setIsCompareModalOpen(true)}
                className="bg-indigo-600 hover:bg-indigo-700 text-white border-0 text-xs px-4 py-1.5 h-8 font-semibold rounded-lg"
              >
                Compare Selected (2-3 required)
              </Button>
              <button
                onClick={() => setSelectedStaffForCompare([])}
                className="text-slate-400 hover:text-white text-xs font-bold px-2 cursor-pointer transition-colors"
              >
                Clear
              </button>
            </div>
          </div>
        )}

        {/* Benchmarking Side-by-Side Drawer */}
        {isCompareModalOpen && comparedStaffObjects.length >= 2 && comparedStaffObjects.length <= 3 && (
          <div className="fixed inset-0 z-50 overflow-hidden" aria-labelledby="benchmark-title" role="dialog" aria-modal="true">
            <div className="absolute inset-0 overflow-hidden">
              {/* Backdrop */}
              <div
                className="absolute inset-0 bg-slate-900/30 backdrop-blur-xs transition-opacity cursor-pointer"
                onClick={() => setIsCompareModalOpen(false)}
              />

              <div className="pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10">
                <div className="pointer-events-auto w-screen max-w-2xl transform transition-all duration-300">
                  <div className="flex h-full flex-col overflow-y-scroll bg-white shadow-2xl border-l border-slate-200">
                    {/* Header */}
                    <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
                      <div>
                        <h2 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                          <Sparkles className="size-4 text-indigo-500 animate-pulse" />
                          Sales Staff Aspect Benchmarking
                        </h2>
                        <p className="text-[11px] text-slate-500 mt-1">Comparing selected representatives across AI-sentiments and sales outcomes</p>
                      </div>
                      <button
                        onClick={() => setIsCompareModalOpen(false)}
                        className="text-slate-400 hover:text-slate-600 font-bold text-lg cursor-pointer"
                      >
                        &times;
                      </button>
                    </div>

                    {/* Body Content */}
                    <div className="p-6 space-y-8 flex-1">
                      {/* 1. Profile Comparison Headers */}
                      <div className={`grid gap-4 border-b border-slate-100 pb-6 ${comparedStaffObjects.length === 3 ? "grid-cols-3" : "grid-cols-2"
                        }`}>
                        {comparedStaffObjects.map((staff, idx) => (
                          <div key={staff.staffId} className={`p-4 rounded-xl border flex flex-col items-center text-center ${idx === 0 ? "bg-indigo-50/20 border-indigo-100" : idx === 1 ? "bg-sky-50/20 border-sky-100" : "bg-emerald-50/20 border-emerald-100"
                            }`}>
                            <div className={`w-12 h-12 rounded-full flex items-center justify-center text-sm font-bold text-white mb-2 shadow-sm ${idx === 0 ? "bg-indigo-500" : idx === 1 ? "bg-sky-500" : "bg-emerald-500"
                              }`}>
                              {getInitials(staff.staffName)}
                            </div>
                            <div className="font-bold text-slate-900 text-xs">{staff.staffName}</div>
                            <div className="text-[10px] text-slate-400 mt-0.5">{staff.email || "Sales Agent"}</div>
                          </div>
                        ))}
                      </div>

                      {/* 2. Core Business Outcomes & SLA Comparison */}
                      <div className="space-y-4">
                        <h3 className="text-xs font-bold text-slate-450 uppercase tracking-wider">Business & SLA Correlation</h3>
                        <div className={`grid gap-4 ${comparedStaffObjects.length === 3 ? "grid-cols-3" : "grid-cols-2"
                          }`}>
                          {comparedStaffObjects.map((staff) => (
                            <div key={staff.staffId} className="space-y-3 bg-slate-50/50 rounded-xl p-4 border border-slate-100 text-xs">
                              <div className="flex justify-between border-b border-slate-100 pb-2">
                                <span className="text-slate-455">CSAT:</span>
                                <span className="font-bold text-slate-900">{staff.satisfactionRatio}%</span>
                              </div>
                              <div className="flex justify-between border-b border-slate-150 pb-2">
                                <span className="text-slate-455">Conversion:</span>
                                <span className="font-bold text-slate-900">{staff.conversionRate}%</span>
                              </div>
                              <div className="flex justify-between border-b border-slate-150 pb-2 flex-wrap gap-1">
                                <span className="text-slate-455">Revenue:</span>
                                <span className="font-bold text-indigo-650 text-right">
                                  {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(staff.totalRevenueWon || 0)}
                                </span>
                              </div>
                              <div className="flex justify-between border-b border-slate-150 pb-2">
                                <span className="text-slate-455">SLA Punct.:</span>
                                <span className="font-bold text-slate-900">{staff.taskPunctualityRate}%</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="text-slate-455">Overdue:</span>
                                <span className={`font-bold ${staff.overdueTasksCount > 0 ? "text-rose-600 animate-pulse" : "text-emerald-600"}`}>
                                  {staff.overdueTasksCount}
                                </span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* 3. Radar Chart Aspect Distribution */}
                      <div className="space-y-4">
                        <h3 className="text-xs font-bold text-slate-450 uppercase tracking-wider">Aspect CSAT Comparison (%)</h3>
                        <div className="h-[250px] w-full bg-slate-50/30 rounded-xl border border-slate-100 p-4 flex items-center justify-center">
                          <ResponsiveContainer width="100%" height="100%">
                            <RadarChart cx="50%" cy="50%" outerRadius="80%" data={radarData}>
                              <PolarGrid stroke="#e2e8f0" />
                              <PolarAngleAxis dataKey="aspect" tick={{ fill: '#475569', fontSize: 10, fontWeight: 650 }} />
                              <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fontSize: 9 }} />
                              {comparedStaffObjects.map((s, idx) => {
                                const radarColors = ["#5B51D8", "#38BDF8", "#10B981"];
                                return (
                                  <Radar
                                    key={s.staffId}
                                    name={s.staffName}
                                    dataKey={s.staffName}
                                    stroke={radarColors[idx]}
                                    fill={radarColors[idx]}
                                    fillOpacity={0.15}
                                  />
                                );
                              })}
                              <Legend wrapperStyle={{ fontSize: 9, paddingTop: 10 }} />
                              <Tooltip contentStyle={{ fontSize: 10, borderRadius: 6 }} />
                            </RadarChart>
                          </ResponsiveContainer>
                        </div>
                      </div>

                      {/* 4. AI-Driven Coaching Suggestions */}
                      {coachingRecommendations.length > 0 && (
                        <div className="space-y-4">
                          <h3 className="text-xs font-bold text-slate-455 uppercase tracking-wider flex items-center gap-1">
                            <Sparkles className="size-3.5 text-indigo-500" />
                            AI-Driven Coaching Recommendations
                          </h3>
                          <div className="space-y-3">
                            {coachingRecommendations.map((rec, i) => (
                              <div key={i} className="flex gap-3 bg-indigo-50/30 border border-indigo-100/50 rounded-xl p-4 text-xs leading-relaxed animate-in fade-in duration-150">
                                <Sparkles className="size-4 text-indigo-500 shrink-0 mt-0.5 animate-pulse" />
                                <div className="space-y-1">
                                  <p className="font-bold text-slate-800">{rec.title}</p>
                                  <p className="text-slate-650 font-normal">{rec.suggestion}</p>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Footer Action Toolbar */}
                    <div className="p-6 border-t border-slate-100 bg-slate-50">
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => {
                          setShowReport(true);
                          setIsCompareModalOpen(false);
                        }}
                        leftIcon={<FileText className="size-3.5" />}
                        className="w-full text-xs py-2 bg-indigo-600 hover:bg-indigo-700 text-white border-0 font-semibold rounded-lg"
                      >
                        Generate Formal Comparison Report
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
      {/* Printable Report View (Active only during browser printing) */}
      {showReport && (
        <div className="hidden print:block print-card">
          <style>{`
            @media print {
              @page {
                size: A4 portrait !important;
                margin: 8mm 10mm 8mm 10mm !important;
              }
              /* Hide all content by default */
              body * {
                visibility: hidden !important;
              }
              /* Keep layout and structural wrappers visible but transparent and non-blocking */
              html, body, #__next, #root, [class*="layout"], [class*="wrapper"], main, div {
                visibility: visible !important;
                background: transparent !important;
                border: none !important;
                box-shadow: none !important;
                padding: 0 !important;
                margin: 0 !important;
                height: auto !important;
                overflow: visible !important;
              }
              /* Explicitly strip sidebar space and topbar graphics */
              aside, nav, header, button, .no-print, .print\:hidden, [class*="sidebar"], [class*="topbar"] {
                display: none !important;
                height: 0 !important;
                width: 0 !important;
                overflow: hidden !important;
              }
              /* Render the print-card report content visible */
              .print-card, .print-card * {
                visibility: visible !important;
                height: auto !important;
                overflow: visible !important;
              }
              .print-card {
                position: absolute !important;
                left: 0 !important;
                top: 0 !important;
                font-family: 'Calibri', 'Segoe UI', Arial, sans-serif !important;
                color: #1e293b !important;
                width: 100% !important;
                max-width: 100% !important;
                background: white !important;
                padding: 0 !important;
                margin: 0 !important;
                font-size: 8.5pt !important;
                line-height: 1.25 !important;
              }
              .print-card h1 {
                font-size: 15pt !important;
                margin-bottom: 1px !important;
              }
              .print-card h2 {
                font-size: 11pt !important;
                margin-bottom: 1px !important;
              }
              .print-card h3 {
                font-size: 9.5pt !important;
                margin-top: 8px !important;
                margin-bottom: 4px !important;
              }
              .print-card p {
                font-size: 8.5pt !important;
                line-height: 1.25 !important;
                margin-bottom: 4px !important;
              }
              .print-card table {
                width: 100% !important;
                margin-top: 4px !important;
                margin-bottom: 6px !important;
              }
              .print-card th, .print-card td {
                padding: 4px 6px !important;
                font-size: 8.5pt !important;
                line-height: 1.2 !important;
              }
            }
          `}</style>
          {renderReportContent()}
        </div>
      )}

      {/* Screen Report Preview Modal */}
      {showReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 select-none print:hidden">
          <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            {/* Header */}
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div>
                <h3 className="text-sm font-bold text-slate-800">Formal Benchmarking Report Preview</h3>
                <p className="text-[10px] text-slate-455 mt-0.5">Previewing printable A4 template layout</p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => window.print()}
                  leftIcon={<Download className="size-3.5" />}
                  className="bg-slate-800 hover:bg-slate-900 text-white text-xs px-4 border-0 font-semibold"
                >
                  Print / Save PDF
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => setShowReport(false)}
                  className="text-xs"
                >
                  Close Preview
                </Button>
              </div>
            </div>
            {/* Body */}
            <div className="flex-1 overflow-y-auto p-8 bg-slate-100/50 flex justify-center">
              <div className="w-[210mm] min-h-[297mm] h-fit bg-white shadow-md border border-slate-200 p-[15mm] text-slate-800 text-xs" style={{ fontFamily: "Calibri, 'Segoe UI', Arial, sans-serif" }}>
                {renderReportContent()}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
