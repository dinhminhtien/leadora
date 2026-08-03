"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { quotationService } from "@/services/quotation_service";
import {
  reportingService,
  type ReportLogPayload,
  type ReportRangeParams,
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
// Fetch all quotations for the discount report tab
export function useQuotationsForReport() {
  return useQuery({
    queryKey: ["quotations-for-report"],
    queryFn: () => quotationService.getList(),
    select: (res) => res.data ?? [],
    staleTime: 60_000,
  });
}

// Fetch pre-computed dashboard KPIs from backend (used by DashboardScreen)
export function useDashboardSummary() {
  return useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: () => reportingService.getDashboardSummary(),
    select: (res) => res.data,
    staleTime: 30_000,
    refetchInterval: 8_000, // Background poll every 8s as fallback for active dashboards
  });
}

// Persist audit log to backend when report is generated
export function useSaveReportLog() {
  return useMutation({
    mutationFn: (payload: ReportLogPayload) => reportingService.saveReportLog(payload),
  });
}

// UC-23.1 — Sales Performance Statistics Report
export function useSalesPerformanceReport(params: ReportRangeParams, enabled = true) {
  return useQuery({
    queryKey: ["sales-performance-report", params],
    queryFn: () => reportingService.getSalesPerformance(params),
    select: (res) => res.data,
    staleTime: 30_000,
    enabled,
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
