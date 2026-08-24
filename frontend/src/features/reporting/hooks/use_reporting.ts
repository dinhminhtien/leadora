"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { quotationService } from "@/services/quotation_service";
import { userService } from "@/services/user_service";
import {
  reportingService,
  type ReportLogPayload,
  type ReportRangeParams,
  type RepScorecardReviewParams,
  type SalesPerformanceParams,
} from "@/services/reporting_service";

/**
 * The date-range filter shared by all five UC-23 tabs.
 *
 * Owns the inversion check in one place: an inverted range is refused client-side (`enabled`) so
 * the report keeps its previous numbers and shows a validation message, rather than round-tripping
 * to a 422 or — worse — rendering an empty state that reads as "no activity in this period".
 */
export function useReportRange() {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const invalid = Boolean(dateFrom && dateTo && dateFrom > dateTo);
  const params: ReportRangeParams = useMemo(
    () => ({ dateFrom: dateFrom || undefined, dateTo: dateTo || undefined }),
    [dateFrom, dateTo],
  );

  return { dateFrom, dateTo, setDateFrom, setDateTo, invalid, params, enabled: !invalid };
}
/** Holds a value back until it has stopped changing — used for the free-text report filters. */
function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return settled;
}

// Fetch all quotations for the discount report tab
export function useQuotationsForReport() {
  return useQuery({
    queryKey: ["quotations", "report"],
    queryFn: () => quotationService.getList({ size: 200 }),
    select: (res) => res.data?.content ?? [],
    staleTime: 60_000,
  });
}

// Fetch pre-computed dashboard KPIs from backend (used by DashboardScreen)
export function useDashboardSummary() {
  return useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: () => reportingService.getDashboardSummary(),
    select: (res) => res.data,
    staleTime: 10_000,
    refetchInterval: 8_000,
    refetchIntervalInBackground: false,
  });
}

// Persist audit log to backend when report is generated
export function useSaveReportLog() {
  return useMutation({
    mutationFn: (payload: ReportLogPayload) => reportingService.saveReportLog(payload),
  });
}

// UC-23.1 — Sales Performance Statistics Report
export function useSalesPerformanceReport(params: SalesPerformanceParams, enabled = true) {
  return useQuery({
    queryKey: ["sales-performance-report", params],
    queryFn: () => reportingService.getSalesPerformance(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
  });
}

/**
 * The rep and segment filters UC-23.1 adds on top of the shared date range.
 *
 * <p>Kept out of {@link useReportRange} on purpose: the other four tabs have no notion of a lead
 * segment, and widening the shared hook would have put dead controls on all of them.
 */
export function useSalesPerformanceFilters() {
  const [assignedUserId, setAssignedUserId] = useState("");
  const [source, setSource] = useState("");
  const [interestedService, setInterestedService] = useState("");
  const [corporate, setCorporate] = useState("");

  // The service box is free text, and the query key is built from it. Typing "Banquet Hall"
  // unthrottled fires twelve reports at a seven-table UNION on a remote database and leaves twelve
  // Redis entries behind it. The dropdowns need no delay — a selection is one deliberate event.
  const debouncedService = useDebouncedValue(interestedService, 400);

  const active = Boolean(assignedUserId || source || interestedService || corporate);

  const reset = () => {
    setAssignedUserId("");
    setSource("");
    setInterestedService("");
    setCorporate("");
  };

  // Blank selects must drop out of the query string entirely, not travel as "" — an empty string
  // would reach SQL as a segment nobody matches and silently empty the whole report.
  const params = useMemo(
    () => ({
      assignedUserId: assignedUserId || undefined,
      source: source || undefined,
      interestedService: debouncedService || undefined,
      corporate: corporate === "" ? undefined : corporate === "true",
    }),
    [assignedUserId, source, debouncedService, corporate],
  );

  return {
    assignedUserId, setAssignedUserId,
    source, setSource,
    interestedService, setInterestedService,
    corporate, setCorporate,
    active, reset, params,
  };
}

/** Sales reps for the UC-23.1 owner filter. Open to any authenticated role, unlike the admin list. */
export function useSalesReps() {
  return useQuery({
    queryKey: ["sales-reps-summary"],
    queryFn: () => userService.getSummariesByRole("SALES"),
    select: (res) => res.data ?? [],
    staleTime: 5 * 60_000,
  });
}

/**
 * UC-23.6 — Rep Performance Scorecard.
 *
 * <p>Heavier than the other reports (five server-side round trips), so it is not refetched on a
 * timer and the period is left to the user rather than polled.
 */
export function useRepScorecardReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["rep-scorecard", params],
    queryFn: () => reportingService.getRepScorecard(params),
    select: (res) => res.data,
    staleTime: 60_000,
    enabled,
  });
}

/**
 * UC-23.7 — AI review of a scorecard.
 *
 * <p>A mutation rather than a query even though it reads nothing: it costs an external quota per
 * call, so it must fire when a person asks for it and never on a remount, a refocus or a retry.
 */
export function useRepScorecardAiReview() {
  return useMutation({
    mutationFn: (params: RepScorecardReviewParams) =>
      reportingService.requestRepScorecardReview(params),
    retry: false,
  });
}

// UC-23.2 — Follow-up Task Performance Report
export function useTaskPerformanceReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["task-performance-report", params],
    queryFn: () => reportingService.getTaskPerformance(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
  });
}

// UC-23.4 — Sales Pipeline Progression Report
export function usePipelineProgressionReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["pipeline-progression-report", params],
    queryFn: () => reportingService.getPipelineProgression(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
  });
}

// UC-23.5 — Quotation Outcome Report
export function useQuotationOutcomeReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["quotation-outcome-report", params],
    queryFn: () => reportingService.getQuotationOutcome(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
  });
}

// UC-23.3 — SLA Compliance Report
export function useSlaComplianceReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["sla-compliance-report", params],
    queryFn: () => reportingService.getSlaCompliance(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
  });
}
