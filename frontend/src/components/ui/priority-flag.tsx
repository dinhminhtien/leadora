"use client";

/**
 * Priority indicator — Website UI Blueprint §2.8 / §10.14.7.
 *
 * Three rules from the blueprint, all of them load-bearing:
 *
 * 1. **Priority uses shape + icon, never colour alone.** A filled flag (HIGH),
 *    a half-filled flag (MEDIUM) and an outlined flag (LOW) are distinguishable
 *    without colour vision, which is what makes the control WCAG-safe.
 * 2. **Priority is never merged with the status pill.** They are orthogonal
 *    channels: status says *where the task is*, priority says *how much it
 *    matters*. Sharing a colour ramp would make `HIGH` look like `CANCELLED`.
 * 3. **It sits left of the task title**, separated by 8px.
 *
 * The system has exactly `LOW / MEDIUM / HIGH` (`TaskPriority`). `URGENT`
 * appears in the blueprint only as a documented future extension, so it is
 * accepted defensively here but never produced by this UI.
 */

import * as React from "react";

import { cn } from "@/lib/utils";

export type TaskPriorityValue = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

type PriorityMeta = {
  label: string;
  className: string;
  /** How much of the flag is filled — the non-colour channel. */
  fill: "none" | "half" | "full" | "double";
  /** Sort weight: higher sorts first (§10.14.7 Urgent > High > Medium > Low). */
  weight: number;
};

const PRIORITY: Record<TaskPriorityValue, PriorityMeta> = {
  URGENT: { label: "Urgent", className: "text-danger", fill: "double", weight: 4 },
  HIGH: { label: "High", className: "text-danger", fill: "full", weight: 3 },
  MEDIUM: { label: "Medium", className: "text-warning", fill: "half", weight: 2 },
  LOW: { label: "Low", className: "text-muted-foreground", fill: "none", weight: 1 },
};

function normalize(value?: string | null): TaskPriorityValue {
  const upper = (value ?? "MEDIUM").toUpperCase();
  return (upper in PRIORITY ? upper : "MEDIUM") as TaskPriorityValue;
}

/** Sort weight for a priority — used by list/board sorters. */
export function priorityWeight(value?: string | null): number {
  return PRIORITY[normalize(value)].weight;
}

export function priorityLabel(value?: string | null): string {
  return PRIORITY[normalize(value)].label;
}

/** The flag glyph. `fill` is the accessible channel; colour merely reinforces it. */
function FlagGlyph({ fill }: { fill: PriorityMeta["fill"] }) {
  if (fill === "double") {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" className="size-3.5" aria-hidden>
        <path d="M5 13l7-7 7 7" />
        <path d="M5 20l7-7 7 7" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" className="size-3.5" aria-hidden>
      {/* Pole is always drawn so the silhouette stays flag-shaped at every level. */}
      <path d="M5 21V4" fill="none" />
      {fill === "full" && <path d="M5 4h13l-3 4 3 4H5z" fill="currentColor" stroke="none" />}
      {fill === "half" && (
        <>
          <path d="M5 4h13l-3 4 3 4H5z" fill="none" />
          <path d="M5 4h6.5v8H5z" fill="currentColor" stroke="none" />
        </>
      )}
      {fill === "none" && <path d="M5 4h13l-3 4 3 4H5z" fill="none" />}
    </svg>
  );
}

/**
 * Icon-only form for dense rows — the label is carried by `title`/`aria-label`
 * so screen readers and tooltips still announce it.
 */
export function PriorityFlag({
  value,
  className,
}: {
  value?: string | null;
  className?: string;
}) {
  const key = normalize(value);
  const meta = PRIORITY[key];
  return (
    <span
      role="img"
      aria-label={`${meta.label} priority`}
      title={`${meta.label} priority`}
      className={cn("inline-flex shrink-0 items-center", meta.className, className)}
    >
      <FlagGlyph fill={meta.fill} />
    </span>
  );
}

/** Flag + text, for the table's dedicated priority column and detail headers. */
export function PriorityChip({
  value,
  className,
  size = "md",
}: {
  value?: string | null;
  className?: string;
  size?: "sm" | "md";
}) {
  const key = normalize(value);
  const meta = PRIORITY[key];
  return (
    <span
      title={`${meta.label} priority`}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md border border-border bg-surface font-semibold",
        size === "sm" ? "h-5 px-1.5 text-[11px]" : "h-6 px-2 text-[12px]",
        meta.className,
        className,
      )}
    >
      <FlagGlyph fill={meta.fill} />
      {meta.label}
    </span>
  );
}

/**
 * The 3px vertical stripe used on calendar events and board cards (§10.15.3).
 * Colour-only by necessity — it always accompanies a flag or label elsewhere in
 * the same card, so it is never the sole carrier of the information.
 */
export function PriorityStripe({
  value,
  className,
}: {
  value?: string | null;
  className?: string;
}) {
  const key = normalize(value);
  const bg =
    key === "LOW"
      ? "bg-muted-foreground"
      : key === "MEDIUM"
        ? "bg-warning"
        : "bg-danger";
  return (
    <span
      aria-hidden
      className={cn("w-[3px] shrink-0 self-stretch rounded-full", bg, className)}
    />
  );
}
