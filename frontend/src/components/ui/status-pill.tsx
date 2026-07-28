"use client";

/**
 * Status pill — Website UI Blueprint §2.7.
 *
 * Anatomy: coloured 6px dot + label. Pill radius, `text-caption` (12/16),
 * weight 600, `h-5` compact / `h-6` default.
 *
 * Two ways to use it, and the second is strongly preferred:
 *
 * ```tsx
 * <StatusPill tone="success">Paid</StatusPill>          // explicit tone
 * <StatusPill domain="payment" value={p.status} />      // canonical binding
 * ```
 *
 * The `domain` form looks the tone and label up in `status-tokens.ts`, which is
 * what keeps `QUALIFIED` the same green on every screen that renders it.
 */

import * as React from "react";

import { cn } from "@/lib/utils";
import {
  humanizeStatus,
  statusTone,
  type StatusDomain,
  type StatusTone,
} from "@/shared/design/status-tokens";

/**
 * Tone → classes. Tints are expressed with `color-mix`-backed opacity utilities
 * over the semantic token, so a single token change in `globals.css` restyles
 * every pill in both themes without touching this file.
 */
const toneClasses: Record<StatusTone, string> = {
  primary:
    "bg-brand-500/10 text-brand-600 ring-brand-500/20 dark:text-brand-500",
  success: "bg-success/10 text-success ring-success/25",
  warning: "bg-warning/12 text-warning ring-warning/30",
  danger: "bg-danger/10 text-danger ring-danger/25",
  info: "bg-info/10 text-info ring-info/25",
  teal: "bg-teal/12 text-teal ring-teal/25",
  muted: "bg-muted text-muted-foreground ring-border",
};

type StatusPillProps = {
  /** Explicit tone. Ignored when `domain` + `value` are supplied. */
  tone?: StatusTone;
  /** Canonical domain binding (§2.7). Preferred over `tone`. */
  domain?: StatusDomain;
  /** Raw wire value, e.g. `PENDING_APPROVAL`. */
  value?: string | null;
  /** Overrides the auto-humanized label. */
  children?: React.ReactNode;
  /** Hide the leading dot (used inside dense table cells). */
  dot?: boolean;
  size?: "sm" | "md";
  /** `CONVERTED` / `WON` render filled for extra emphasis (§2.7). */
  filled?: boolean;
  className?: string;
  title?: string;
};

const filledClasses: Record<StatusTone, string> = {
  primary: "bg-brand-500 text-brand-foreground ring-brand-500",
  success: "bg-success text-white ring-success dark:text-success-bg",
  warning: "bg-warning text-white ring-warning dark:text-warning-bg",
  danger: "bg-danger text-white ring-danger dark:text-danger-bg",
  info: "bg-info text-white ring-info dark:text-info-bg",
  teal: "bg-teal text-teal-foreground ring-teal",
  muted: "bg-muted-foreground text-background ring-muted-foreground",
};

export function StatusPill({
  tone,
  domain,
  value,
  children,
  dot = true,
  size = "md",
  filled = false,
  className,
  title,
}: StatusPillProps) {
  const resolvedTone: StatusTone =
    domain && value ? statusTone(domain, value) : (tone ?? "muted");

  const label =
    children ?? (value ? humanizeStatus(value, domain) : humanizeStatus(null));

  return (
    <span
      title={title ?? (typeof label === "string" ? label : undefined)}
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 rounded-pill font-semibold ring-1 ring-inset",
        "whitespace-nowrap tracking-[0.005em]",
        size === "sm" ? "h-5 px-2 text-[11px]" : "h-6 px-2.5 text-[12px]",
        filled ? filledClasses[resolvedTone] : toneClasses[resolvedTone],
        className,
      )}
    >
      {dot && (
        <span
          aria-hidden
          className={cn(
            "size-1.5 shrink-0 rounded-full",
            filled ? "bg-current opacity-80" : "bg-current",
          )}
        />
      )}
      <span className="truncate">{label}</span>
    </span>
  );
}

/**
 * The computed OVERDUE flag (§10.14.6).
 *
 * Deliberately a *separate* component from `StatusPill`: OVERDUE is not a task
 * status and must never replace the OPEN pill — it is appended beside it. Making
 * it a distinct component means no future edit can accidentally add it to the
 * task status map.
 */
export function OverdueBadge({
  className,
  size = "md",
}: {
  className?: string;
  size?: "sm" | "md";
}) {
  return (
    <span
      title="Past its due date"
      className={cn(
        "inline-flex items-center gap-1 rounded-pill bg-danger/10 font-semibold text-danger ring-1 ring-inset ring-danger/25",
        size === "sm" ? "h-5 px-1.5 text-[10.5px]" : "h-6 px-2 text-[11.5px]",
        className,
      )}
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.5}
        strokeLinecap="round"
        className="size-3"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </svg>
      Overdue
    </span>
  );
}
