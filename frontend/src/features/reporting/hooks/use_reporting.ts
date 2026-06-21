"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { quotationService } from "@/services/quotation_service";
import { reportingService, type ReportLogPayload } from "@/services/reporting_service";
import { dealService } from "@/services/deal_service";

// Fetch all quotations for the discount report tab
export function useQuotationsForReport() {
  return useQuery({
    queryKey: ["quotations-for-report"],
    queryFn: () => quotationService.getList(),
    select: (res) => res.data ?? [],
    staleTime: 60_000,
  });
}

// Fetch all deals for pipeline metrics (used by ReportingScreen analytics)
export function useDealsForReport() {
  return useQuery({
    queryKey: ["deals-for-report"],
    queryFn: () => dealService.getList(),
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
