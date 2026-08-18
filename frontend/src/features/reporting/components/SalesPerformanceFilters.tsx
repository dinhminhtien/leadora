"use client";

import React from "react";
import { RotateCcw } from "lucide-react";
import { SOURCE_OPTIONS } from "@/features/lead/components/LeadEditDrawer";
import { useSalesReps } from "@/features/reporting/hooks/use_reporting";

type Props = {
  assignedUserId: string;
  setAssignedUserId: (v: string) => void;
  source: string;
  setSource: (v: string) => void;
  interestedService: string;
  setInterestedService: (v: string) => void;
  corporate: string;
  setCorporate: (v: string) => void;
  active: boolean;
  reset: () => void;
};

/**
 * The rep and lead-segment filters UC-23.1 asks for on top of the date range.
 *
 * <p>Source is a fixed list because that is how a lead captures it; interested service is a free
 * text box matched as a substring, because that is how a rep types it.
 */
export function SalesPerformanceFilters(props: Props) {
  const { data: reps = [] } = useSalesReps();

  const control =
    "w-full rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:border-teal-400 focus:outline-none";
  const label = "mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400";

  return (
    <div className="rounded-xl border border-slate-100 bg-white p-3 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label className={label}>Sales rep</label>
          <select
            value={props.assignedUserId}
            onChange={(e) => props.setAssignedUserId(e.target.value)}
            className={control}
          >
            <option value="">Whole team</option>
            {reps.map((rep) => (
              <option key={rep.userId} value={rep.userId}>
                {rep.fullName}
              </option>
            ))}
          </select>
        </div>

        <div className="flex-1">
          <label className={label}>Lead source</label>
          <select value={props.source} onChange={(e) => props.setSource(e.target.value)} className={control}>
            <option value="">Any source</option>
            {SOURCE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>

        <div className="flex-1">
          <label className={label}>Service</label>
          <input
            type="text"
            value={props.interestedService}
            placeholder="e.g. Banquet"
            onChange={(e) => props.setInterestedService(e.target.value)}
            className={control}
          />
        </div>

        <div className="flex-1">
          <label className={label}>Segment</label>
          <select value={props.corporate} onChange={(e) => props.setCorporate(e.target.value)} className={control}>
            <option value="">Corporate &amp; individual</option>
            <option value="true">Corporate only</option>
            <option value="false">Individual only</option>
          </select>
        </div>

        <button
          type="button"
          onClick={props.reset}
          disabled={!props.active}
          className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <RotateCcw className="size-3.5" /> Clear
        </button>
      </div>

      {props.active && (
        <p className="mt-2 text-[11px] leading-snug text-slate-400">
          Segment filters describe the lead a record came from. A booking has no source of its own,
          so it is matched through the customer that lead became.
        </p>
      )}
    </div>
  );
}
