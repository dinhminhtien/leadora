"use client";

import { useSlaMonitoring } from "@/features/sla/hooks/use_sla";

interface SlaStatusBadgeProps {
  entityId: string;
  entityType: string;
}

function formatHours(hours: number): string {
  const abs = Math.abs(Math.round(hours));
  if (abs >= 24) {
    const d = Math.floor(abs / 24);
    const h = abs % 24;
    return h > 0 ? `${d}d ${h}h` : `${d}d`;
  }
  return `${abs}h`;
}

export function SlaStatusBadge({ entityId, entityType }: SlaStatusBadgeProps) {
  const { data: records = [], isLoading } = useSlaMonitoring(entityType);

  if (isLoading) {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-semibold bg-slate-100 text-slate-400">
        —
      </span>
    );
  }

  const tracking = records.find((r) => r.entityId === entityId);

  if (!tracking) {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-semibold bg-slate-100 text-slate-400">
        No SLA
      </span>
    );
  }

  // Resolved records: show historical performance at time of resolution
  if (tracking.slaStatus === "RESOLVED") {
    if (tracking.displayStatus === "BREACHED") {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-red-50 text-red-500 border border-red-200">
          Resolved · Late
        </span>
      );
    }
    if (tracking.displayStatus === "WARNING") {
      return (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-amber-50 text-amber-600 border border-amber-200">
          Resolved · Warning
        </span>
      );
    }
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-50 text-emerald-600 border border-emerald-200">
        Resolved · On Time
      </span>
    );
  }

  // Active / breached records
  if (tracking.displayStatus === "BREACHED") {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-red-100 text-red-700 border border-red-200">
        Breached · {formatHours(tracking.hoursRemaining)} ago
      </span>
    );
  }

  if (tracking.displayStatus === "WARNING") {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-amber-100 text-amber-700 border border-amber-200">
        Warning · {formatHours(tracking.hoursRemaining)} left
      </span>
    );
  }

  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-100 text-emerald-700 border border-emerald-200">
      On Track · {formatHours(tracking.hoursRemaining)} left
    </span>
  );
}
