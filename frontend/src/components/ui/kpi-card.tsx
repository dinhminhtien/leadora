"use client";

/**
 * KPI card + greeting bar — Website UI Blueprint §2.15.
 *
 * > "KPI card: label (caption), value (h1 mono), delta chip (▲ green / ▼ red /
 * >  — muted), 7-day sparkline (24px tall)."
 *
 * **Why this is a component.** The dashboard previously repeated the same
 * ~20-line card markup four times, each with its own hand-picked accent colour
 * (`bg-emerald-500/10`, `bg-primary/10`, …). That is four places to update when
 * the spec moves, and four chances for the delta chip's green to disagree with
 * the app's success token.
 *
 * The delta chip encodes direction with an **arrow plus colour**, never colour
 * alone — the same accessibility rule the priority flag follows (§2.8).
 */

import * as React from "react";
import Link from "next/link";
import { ArrowDownRight, ArrowRight, ArrowUpRight, Minus } from "lucide-react";

import { cn } from "@/lib/utils";

/* ------------------------------------------------------------------ *
 * Sparkline
 * ------------------------------------------------------------------ */

/**
 * A 24px trend line. Hand-drawn SVG rather than a charting library: at this size
 * a library's axes, tooltips and responsive container cost far more than the
 * polyline they'd render, and the shape carries no readable values anyway — it
 * is a texture that says "trending up", with the real number beside it.
 */
export function Sparkline({
  points,
  tone = "brand",
  className,
}: {
  points: number[];
  tone?: "brand" | "success" | "danger" | "muted";
  className?: string;
}) {
  if (points.length < 2) return null;

  const width = 100;
  const height = 24;
  const min = Math.min(...points);
  const max = Math.max(...points);
  // A flat series would divide by zero; draw it down the middle instead.
  const span = max - min || 1;

  const path = points
    .map((p, i) => {
      const x = (i / (points.length - 1)) * width;
      const y = height - ((p - min) / span) * (height - 4) - 2;
      return `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");

  const stroke =
    tone === "success" ? "var(--success)"
    : tone === "danger" ? "var(--danger)"
    : tone === "muted" ? "var(--muted-foreground)"
    : "var(--brand-500)";

  return (
    <svg
      aria-hidden
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      className={cn("h-6 w-full", className)}
    >
      <path
        d={`${path} L${width},${height} L0,${height} Z`}
        fill={stroke}
        opacity={0.1}
      />
      <path
        d={path}
        fill="none"
        stroke={stroke}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

/* ------------------------------------------------------------------ *
 * Delta chip
 * ------------------------------------------------------------------ */

export function DeltaChip({
  value,
  suffix = "%",
  /** Set when a fall is good news (e.g. overdue tasks down). */
  invert = false,
  label,
  className,
}: {
  value?: number | null;
  suffix?: string;
  invert?: boolean;
  label?: string;
  className?: string;
}) {
  if (value == null || Number.isNaN(value)) return null;

  const flat = Math.abs(value) < 0.05;
  const rising = value > 0;
  const good = invert ? !rising : rising;

  const Icon = flat ? Minus : rising ? ArrowUpRight : ArrowDownRight;

  return (
    <span
      title={label}
      className={cn(
        "inline-flex shrink-0 items-center gap-0.5 rounded-md px-1.5 py-0.5 text-[11px] font-semibold",
        flat
          ? "bg-muted text-muted-foreground"
          : good
            ? "bg-success/10 text-success"
            : "bg-danger/10 text-danger",
        className,
      )}
    >
      <Icon aria-hidden className="size-3" />
      <span className="numeric">
        {flat ? "0" : Math.abs(value).toFixed(1)}
        {suffix}
      </span>
    </span>
  );
}

/* ------------------------------------------------------------------ *
 * KPI card
 * ------------------------------------------------------------------ */

type KpiCardProps = {
  label: string;
  value: React.ReactNode;
  /** Percentage change; omit to hide the chip. */
  delta?: number | null;
  deltaLabel?: string;
  /** A fall is good news for this metric (overdue, breaches, cancellations). */
  invertDelta?: boolean;
  hint?: React.ReactNode;
  icon?: React.ComponentType<{ className?: string }>;
  tone?: "brand" | "success" | "warning" | "danger" | "teal";
  sparkline?: number[];
  /** Makes the whole card a link into the underlying list. */
  href?: string;
  className?: string;
};

const toneClasses = {
  brand: "bg-brand-500/10 text-brand-600 dark:text-brand-500",
  success: "bg-success/10 text-success",
  warning: "bg-warning/12 text-warning",
  danger: "bg-danger/10 text-danger",
  teal: "bg-teal/12 text-teal",
} as const;

const sparkTone = {
  brand: "brand",
  success: "success",
  warning: "brand",
  danger: "danger",
  teal: "success",
} as const;

export function KpiCard({
  label,
  value,
  delta,
  deltaLabel,
  invertDelta = false,
  hint,
  icon: Icon,
  tone = "brand",
  sparkline,
  href,
  className,
}: KpiCardProps) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          {label}
        </p>
        {Icon ? (
          <span
            aria-hidden
            className={cn("grid size-8 shrink-0 place-items-center rounded-md", toneClasses[tone])}
          >
            <Icon className="size-4" />
          </span>
        ) : (
          <DeltaChip value={delta} invert={invertDelta} label={deltaLabel} />
        )}
      </div>

      <p className="numeric mt-2 truncate text-[30px] font-bold leading-[38px] tracking-[-0.015em] text-foreground">
        {value}
      </p>

      <div className="mt-1 flex min-h-5 items-center gap-2">
        {Icon && <DeltaChip value={delta} invert={invertDelta} label={deltaLabel} />}
        {hint && (
          <span className="min-w-0 truncate text-[12px] text-muted-foreground">{hint}</span>
        )}
        {href && (
          <ArrowRight
            aria-hidden
            className="ml-auto size-3.5 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100"
          />
        )}
      </div>

      {sparkline && sparkline.length > 1 && (
        <div className="mt-2 -mb-1">
          <Sparkline points={sparkline} tone={sparkTone[tone]} />
        </div>
      )}
    </>
  );

  const base = cn(
    "card-surface group block p-5 transition-shadow duration-[120ms]",
    href && "hover:border-brand-300 hover:shadow-elev-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
    className,
  );

  return href ? (
    <Link href={href} className={base}>
      {body}
    </Link>
  ) : (
    <div className={base}>{body}</div>
  );
}

/* ------------------------------------------------------------------ *
 * Greeting bar — §2.15 "All dashboards start with a greeting bar"
 * ------------------------------------------------------------------ */

/** Never changes after mount, so `subscribe` is a no-op unsubscriber. */
const emptySubscribe = () => () => {};

/** `false` while server-rendering and on the hydration pass, `true` after. */
function useIsMounted(): boolean {
  return React.useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false,
  );
}

export function GreetingBar({
  name,
  roleLabel,
  subtitle,
  actions,
  className,
}: {
  name?: string | null;
  roleLabel?: string;
  subtitle?: string;
  actions?: React.ReactNode;
  className?: string;
}) {
  // The greeting and date are client-only: the server's clock is rarely in the
  // user's hour, let alone their greeting window, so rendering them on the
  // server guarantees a hydration mismatch. `useSyncExternalStore` is the
  // sanctioned way to say "no value on the server, this value on the client" —
  // it needs no effect and therefore no cascading render.
  const mounted = useIsMounted();
  const now = mounted ? new Date() : null;
  const greeting = now
    ? now.getHours() < 12
      ? "Good morning"
      : now.getHours() < 18
        ? "Good afternoon"
        : "Good evening"
    : null;
  const today = now
    ? now.toLocaleDateString(undefined, {
        weekday: "long",
        day: "numeric",
        month: "long",
      })
    : null;

  return (
    <div
      className={cn(
        "mb-6 flex flex-col gap-4 rounded-lg border border-border bg-surface p-5 sm:flex-row sm:items-center sm:justify-between",
        className,
      )}
    >
      <div className="min-w-0">
        <p className="text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          {roleLabel ?? "Workspace"}
          {today && <span className="ml-2 font-normal normal-case tracking-normal">{today}</span>}
        </p>
        <h1 className="mt-1 truncate text-[24px] font-bold leading-8 tracking-[-0.01em] text-foreground">
          {greeting ? `${greeting}, ${name ?? "there"}` : `Welcome back, ${name ?? "there"}`}
        </h1>
        {subtitle && (
          <p className="mt-1 text-[13px] text-muted-foreground">{subtitle}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
