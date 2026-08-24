"use client";

import { useQuery } from "@tanstack/react-query";
import {
  sentimentAnalyticsService,
} from "@/services/sentiment_analytics_service";

const QUERY_KEY = "sentiment-analytics";

export function useSentimentOverview(params?: { startDate?: string; endDate?: string }) {
  return useQuery({
    queryKey: [QUERY_KEY, "overview", params],
    queryFn: () => sentimentAnalyticsService.getOverview(params),
    staleTime: 60_000,
  });
}

export function useSentimentTrends(params?: { startDate?: string; endDate?: string; groupBy?: string }) {
  return useQuery({
    queryKey: [QUERY_KEY, "trends", params],
    queryFn: () => sentimentAnalyticsService.getTrends(params),
    staleTime: 60_000,
  });
}

export function useStaffSentimentPerformance(params?: { startDate?: string; endDate?: string }) {
  return useQuery({
    queryKey: [QUERY_KEY, "staff-performance", params],
    queryFn: () => sentimentAnalyticsService.getStaffPerformance(params),
    staleTime: 60_000,
  });
}

export function useFeedbackAggregate(params?: { startDate?: string; endDate?: string }) {
  return useQuery({
    queryKey: [QUERY_KEY, "aggregate", params],
    queryFn: () =>
      sentimentAnalyticsService.getDeepDive({
        startDate: params?.startDate,
        endDate: params?.endDate,
        page: 0,
        size: 500,
      }),
    select: (res) => res.data?.content ?? [],
    staleTime: 5 * 60_000,
  });
}

export function useFeedbackDeepDive(params: {
  aspect?: string;
  sentiment?: string;
  salesStaffName?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: [QUERY_KEY, "deep-dive", params],
    queryFn: () => sentimentAnalyticsService.getDeepDive(params),
    staleTime: 30_000,
  });
}
