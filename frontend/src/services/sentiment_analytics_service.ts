import { apiClient, type ApiResponse } from "@/services/api_client";
import type { CustomerFeedback } from "@/services/customer_feedback_service";

export interface AnalyzedFeedback extends CustomerFeedback {
  absaAttitudeSentiment?: string;
  absaAttitudeConfidence?: number;
  absaSpeedSentiment?: string;
  absaSpeedConfidence?: number;
  absaAccuracySentiment?: string;
  absaAccuracyConfidence?: number;
  absaFacilitySentiment?: string;
  absaFacilityConfidence?: number;
  absaPriceSentiment?: string;
  absaPriceConfidence?: number;
  absaStatus?: string;
}

export interface AspectSentimentSummary {
  positive: number;
  neutral: number;
  negative: number;
  positivePercentage: number;
  neutralPercentage: number;
  negativePercentage: number;
  total: number;
}

export interface SentimentOverviewResponse {
  attitude: AspectSentimentSummary;
  speed: AspectSentimentSummary;
  accuracy: AspectSentimentSummary;
  facility: AspectSentimentSummary;
  price: AspectSentimentSummary;
}

export interface AspectTrendSummary {
  positive: number;
  neutral: number;
  negative: number;
}

export interface TrendPoint {
  period: string;
  overall: AspectTrendSummary;
  attitude: AspectTrendSummary;
  speed: AspectTrendSummary;
  accuracy: AspectTrendSummary;
  facility: AspectTrendSummary;
  price: AspectTrendSummary;
}

export interface SentimentTrendResponse {
  points: TrendPoint[];
}

export interface StaffSentimentPerformanceResponse {
  staffId: string;
  staffName: string;
  email?: string;
  avatarUrl?: string;

  // Feedback metrics
  totalFeedbacks: number;
  positiveFeedbacks: number;
  neutralFeedbacks: number;
  negativeFeedbacks: number;
  satisfactionRatio: number; // Overall CSAT (%)

  // 5-Aspect Satisfaction Matrix (% Positive)
  attitudePositiveRatio: number;
  speedPositiveRatio: number;
  accuracyPositiveRatio: number;
  facilityPositiveRatio: number;
  pricePositiveRatio: number;

  // Sales Performance Correlation
  totalDeals: number;
  wonDeals: number;
  lostDeals: number;
  conversionRate: number; // won / (won + lost) %
  totalRevenueWon: number;

  // SLA & Task Correlation
  completedTasks: number;
  onTimeTasks: number;
  taskPunctualityRate: number; // onTime / completed %
  overdueTasksCount: number;

  // AI Highlight Tags
  topStrongAspect: string;
  topWeakAspect: string;
}

const ENDPOINT = "/analytics/sentiment";

export const sentimentAnalyticsService = {
  async getOverview(params?: { startDate?: string; endDate?: string }) {
    const response = await apiClient.get<ApiResponse<SentimentOverviewResponse>>(
      `${ENDPOINT}/overview`,
      { params },
    );
    return response.data;
  },

  async getTrends(params?: { startDate?: string; endDate?: string; groupBy?: string }) {
    const response = await apiClient.get<ApiResponse<SentimentTrendResponse>>(
      `${ENDPOINT}/trends`,
      { params },
    );
    return response.data;
  },

  async getStaffPerformance(params?: { startDate?: string; endDate?: string }) {
    const response = await apiClient.get<ApiResponse<StaffSentimentPerformanceResponse[]>>(
      `${ENDPOINT}/staff-performance`,
      { params },
    );
    return response.data;
  },

  async getDeepDive(params: {
    aspect?: string;
    sentiment?: string;
    salesStaffName?: string;
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
  }) {
    const response = await apiClient.get<ApiResponse<{ content: AnalyzedFeedback[]; totalElements: number; totalPages: number }>>(
      `${ENDPOINT}/deep-dive`,
      { params },
    );
    return response.data;
  },
};

