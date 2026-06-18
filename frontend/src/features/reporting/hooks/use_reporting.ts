"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { quotationService } from "@/services/quotation_service";
import { reportingService, type ReportLogPayload } from "@/services/reporting_service";

// Fetch all quotations for the discount report tab
export function useQuotationsForReport() {
  return useQuery({
    queryKey: ["quotations-for-report"],
    queryFn: () => quotationService.getList(),
    select: (res) => res.data ?? [],
    staleTime: 60_000,
  });
}

// Persist audit log to backend when report is generated
export function useSaveReportLog() {
  return useMutation({
    mutationFn: (payload: ReportLogPayload) => reportingService.saveReportLog(payload),
  });
}
