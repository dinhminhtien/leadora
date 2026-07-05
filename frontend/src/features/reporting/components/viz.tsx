"use client";

/**
 * Shared visualization primitives for the reporting performance tabs.
 *
 * Colors are NOT eyeballed — they come from the data-viz reference palette and were checked with
 * the skill's validator (scripts/validate_palette.js):
 *   - Categorical/status {blue #2a78d6, good #0ca30c, critical #d03b3b}: all checks PASS,
 *     worst adjacent CVD ΔE 12.4 → legal WITH secondary encoding (we always ship a legend +
 *     inline value labels + 2px surface gaps, and status colors carry an icon + label).
 *   - Priority ordinal blue {#184f95, #2a78d6, #86b6ef}: PASS as an ordinal ramp (one hue,
 *     monotone lightness, light end clears the surface).
 *
 * Conventions applied from marks-and-anatomy.md: 2px surface gap between stacked segments,
 * rounded track, tabular-nums only in table-like value columns (stat values stay proportional),
 * text uses ink tokens (never the series color), inline labels only when they fit.
 */
import React from "react";

export const VIZ = {
  // categorical / status marks (validated set)
  open: "#2a78d6", // blue — neutral / in-progress
  good: "#0ca30c", // green — completed / won
  critical: "#d03b3b", // red — overdue / lost
  muted: "#94928c", // grey — cancelled / inactive
  // priority ordinal ramp (blue, high → low)
  priHigh: "#184f95",
  priMed: "#2a78d6",
  priLow: "#86b6ef",
  // meter tracks — a lighter step of each fill's own ramp (state reads across the whole bar)
  trackBlue: "#cde2fb",
  trackGreen: "#c9ecc9",
  trackRed: "#f6d5d5",
  // ink tokens
  ink: "#0b0b0b",
  inkMuted: "#52514e",
  surface: "#ffffff",
} as const;

/** Pick white or ink for a label sitting inside a colored fill, by the fill's luminance. */
function textOnFill(hex: string): string {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return lum > 0.62 ? VIZ.ink : "#ffffff";
}

/** Compact number formatter for stat-tile values (1,284 / 12.9K / 4.2M). */
export function compact(n: number): string {
  if (Math.abs(n) >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (Math.abs(n) >= 10_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString("en-US");
}

/** Compact VND for constrained stat tiles (keeps large revenue from overflowing the tile). */
export function vndCompact(n?: number): string {
  const v = n ?? 0;
  if (Math.abs(v) >= 1_000_000_000) return `${(v / 1_000_000_000).toFixed(1)}B ₫`;
  if (Math.abs(v) >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M ₫`;
  if (Math.abs(v) >= 10_000) return `${Math.round(v / 1_000)}K ₫`;
  return `${v.toLocaleString("en-US")} ₫`;
}

/* ── Date-range filter (shared by every report tab) ────────────────────────── */

export function ReportDateRange({
  dateFrom,
  dateTo,
  setDateFrom,
  setDateTo,
}: {
  dateFrom: string;
  dateTo: string;
  setDateFrom: (v: string) => void;
  setDateTo: (v: string) => void;
}) {
  const inputCls =
    "w-full rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700 focus:border-teal-400 focus:outline-none";
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-slate-100 bg-white p-3 shadow-sm sm:flex-row sm:items-end">
      <div className="flex-1">
        <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Date From</label>
        <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className={inputCls} />
      </div>
      <div className="flex-1">
        <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Date To</label>
        <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className={inputCls} />
      </div>
      <p className="text-[11px] text-slate-400 sm:pb-2">Leave empty = all time</p>
    </div>
  );
}

/* ── Horizontal labeled bars (per-mark distribution — stages, statuses, types) ─ */

export type HBarItem = { label: string; value: number; color: string; sub?: string };

export function HBarList({ items }: { items: HBarItem[] }) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <div className="space-y-2">
      {items.map((it) => (
        <div key={it.label} className="flex items-center gap-2 text-[11px]">
          <span className="w-28 shrink-0 truncate text-slate-600" title={it.label}>{it.label}</span>
          <div className="h-3.5 flex-1 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full transition-[width] duration-300"
              style={{ width: `${(it.value / max) * 100}%`, background: it.color, minWidth: it.value > 0 ? 6 : 0 }}
            />
          </div>
          <span className="w-24 shrink-0 text-right tabular-nums font-semibold text-slate-700">
            {it.value}
            {it.sub ? <span className="ml-1 font-normal text-slate-400">{it.sub}</span> : null}
          </span>
        </div>
      ))}
    </div>
  );
}

/* ── Empty state (UC exception E5.1 — "No report data found") ──────────────── */

export function EmptyReport({
  message = "No report data found for the selected period.",
}: {
  message?: string;
}) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-10 text-center shadow-sm">
      <p className="text-sm text-slate-400">{message}</p>
    </div>
  );
}

/* ── Stat tile ─────────────────────────────────────────────────────────────── */

export function StatTile({
  label,
  value,
  sub,
  icon,
  accent = VIZ.ink,
}: {
  label: string;
  value: string;
  sub?: string;
  icon?: React.ReactNode;
  /** Accent for the value — reserve status hues for real good/bad numbers, else default ink. */
  accent?: string;
}) {
  return (
    <div className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
      <div className="mb-1 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400">
        {icon}
        {label}
      </div>
      {/* Standalone value → proportional figures (tabular-nums only belongs in aligned columns). */}
      <p className="text-lg font-extrabold" style={{ color: accent }}>
        {value}
      </p>
      {sub && <p className="mt-0.5 text-[11px] text-slate-400">{sub}</p>}
    </div>
  );
}

/* ── Meter (single headline proportion) ────────────────────────────────────── */

export function Meter({
  value,
  fill,
  track,
  height = 8,
}: {
  /** 0–100. */
  value: number;
  fill: string;
  /** Lighter step of the fill's own ramp. */
  track: string;
  height?: number;
}) {
  const pct = Math.max(0, Math.min(100, value));
  return (
    <div
      className="w-full overflow-hidden rounded-full"
      style={{ height, background: track }}
    >
      <div
        className="h-full rounded-full transition-[width] duration-500"
        style={{ width: `${pct}%`, background: fill }}
      />
    </div>
  );
}

/* ── Stacked segment bar (proportion by category) ──────────────────────────── */

export type Segment = { label: string; value: number; color: string };

export function SegmentBar({
  segments,
  height = 20,
}: {
  segments: Segment[];
  height?: number;
}) {
  const shown = segments.filter((s) => s.value > 0);
  const total = shown.reduce((a, s) => a + s.value, 0) || 1;
  return (
    <div className="space-y-2">
      {/* 2px surface gap between fills — the white gap does the separating, not a stroke. */}
      <div
        className="flex w-full gap-0.5 overflow-hidden rounded-full"
        style={{ height, background: VIZ.surface }}
      >
        {shown.map((s) => {
          const widthPct = (s.value / total) * 100;
          // Only place the value inside the fill when it comfortably fits (~>9% width).
          const showInline = widthPct > 9;
          // flex-grow (not width %) so the 2px gaps are subtracted from the track by flexbox
          // instead of overflowing it and clipping the last segment.
          return (
            <div
              key={s.label}
              className="flex h-full min-w-0 items-center justify-center text-[10px] font-bold tabular-nums"
              style={{
                flex: `${s.value} 1 0`,
                background: s.color,
                color: textOnFill(s.color),
              }}
              title={`${s.label}: ${s.value}`}
            >
              {showInline ? s.value : ""}
            </div>
          );
        })}
      </div>
      {/* Legend is always present for ≥2 series — identity never rides color alone. */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
        {segments.map((s) => (
          <span key={s.label} className="flex items-center gap-1.5">
            <span className="size-2 rounded-full" style={{ background: s.color }} />
            {s.label}: <span className="font-semibold tabular-nums text-slate-700">{s.value}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
