"use client";

/**
 * Skeletons — Website UI Blueprint §3.12 and §11 (`Loading-list`).
 *
 * > "Skeletons mirror final layout: table skeleton has header + 8 rows of
 * >  animated bars; card skeleton has title + 3 lines + chip row."
 *
 * The point of mirroring the final layout is that nothing moves when the data
 * lands. A spinner in the middle of a table body collapses to zero height and
 * then pushes the whole page down — which is the layout jank §3.12 exists to
 * prevent. Every skeleton here takes the same paddings and row heights as the
 * component it stands in for.
 */

import * as React from "react";

import { cn } from "@/lib/utils";

/** A single shimmering bar. `w` is any Tailwind width class. */
export function SkeletonBar({
  className,
  w = "w-full",
  h = "h-3.5",
}: {
  className?: string;
  w?: string;
  h?: string;
}) {
  return (
    <span
      aria-hidden
      className={cn("shimmer block rounded-md", w, h, className)}
    />
  );
}

/** Circle placeholder for avatars. */
export function SkeletonCircle({ className = "size-8" }: { className?: string }) {
  return <span aria-hidden className={cn("shimmer block rounded-full", className)} />;
}

/**
 * Table skeleton — header row + N body rows at the real row height so the
 * header, toolbar and pagination footer never shift.
 */
export function TableSkeleton({
  rows = 8,
  columns = 6,
  rowHeight = "h-14",
  className,
}: {
  rows?: number;
  columns?: number;
  /** Match the active density: comfortable h-14 · compact h-11 · ultra h-9. */
  rowHeight?: string;
  className?: string;
}) {
  return (
    <div
      role="status"
      aria-label="Loading rows"
      className={cn("w-full overflow-hidden rounded-lg border border-border", className)}
    >
      <div className="flex items-center gap-4 border-b border-border bg-muted px-4 py-3">
        {Array.from({ length: columns }).map((_, i) => (
          <SkeletonBar key={i} h="h-3" w={i === 0 ? "w-8" : "w-24"} />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div
          key={r}
          className={cn(
            "flex items-center gap-4 border-b border-border px-4 last:border-b-0",
            rowHeight,
          )}
        >
          {Array.from({ length: columns }).map((_, c) => (
            <SkeletonBar
              key={c}
              w={c === 0 ? "w-8" : c === 1 ? "w-40" : "w-20"}
              // Slight length variance stops the block reading as a static grid.
              className={c === 1 ? "max-w-[40%]" : undefined}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Card skeleton — title + 3 lines + chip row, per §3.12. */
export function CardSkeleton({ className }: { className?: string }) {
  return (
    <div
      role="status"
      aria-label="Loading"
      className={cn("card-surface space-y-3 p-5", className)}
    >
      <SkeletonBar w="w-1/3" h="h-4" />
      <div className="space-y-2">
        <SkeletonBar w="w-full" />
        <SkeletonBar w="w-5/6" />
        <SkeletonBar w="w-2/3" />
      </div>
      <div className="flex gap-2 pt-1">
        <SkeletonBar w="w-16" h="h-5" className="rounded-pill" />
        <SkeletonBar w="w-20" h="h-5" className="rounded-pill" />
      </div>
    </div>
  );
}

/** KPI tile skeleton — matches the §2.15 card (label, value, delta, sparkline). */
export function KpiSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn("card-surface space-y-3 p-5", className)}>
      <div className="flex items-start justify-between">
        <SkeletonBar w="w-24" h="h-3" />
        <SkeletonBar w="w-12" h="h-5" className="rounded-md" />
      </div>
      <SkeletonBar w="w-28" h="h-8" />
      <SkeletonBar w="w-full" h="h-6" />
    </div>
  );
}

/** Detail-page skeleton — header block + two-column body. */
export function DetailSkeleton({ className }: { className?: string }) {
  return (
    <div role="status" aria-label="Loading record" className={cn("space-y-6", className)}>
      <div className="card-surface flex items-start gap-4 p-5">
        <SkeletonCircle className="size-12" />
        <div className="flex-1 space-y-2">
          <SkeletonBar w="w-1/3" h="h-5" />
          <SkeletonBar w="w-1/4" h="h-3" />
        </div>
        <SkeletonBar w="w-24" h="h-6" className="rounded-pill" />
      </div>
      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-8">
          <CardSkeleton />
          <CardSkeleton />
        </div>
        <div className="space-y-6 lg:col-span-4">
          <CardSkeleton />
        </div>
      </div>
    </div>
  );
}

/** List skeleton — N stacked card rows, for boards, agendas and rails. */
export function ListSkeleton({
  count = 5,
  className,
}: {
  count?: number;
  className?: string;
}) {
  return (
    <div role="status" aria-label="Loading list" className={cn("space-y-2", className)}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="card-surface flex items-center gap-3 p-3">
          <SkeletonCircle className="size-9" />
          <div className="flex-1 space-y-2">
            <SkeletonBar w="w-1/2" />
            <SkeletonBar w="w-1/3" h="h-3" />
          </div>
          <SkeletonBar w="w-16" h="h-5" className="rounded-pill" />
        </div>
      ))}
    </div>
  );
}

/** Chart skeleton — keeps the plot area's height so axes don't jump. */
export function ChartSkeleton({
  height = "h-64",
  className,
}: {
  height?: string;
  className?: string;
}) {
  return (
    <div className={cn("card-surface space-y-3 p-5", className)}>
      <SkeletonBar w="w-40" h="h-4" />
      <div className={cn("flex items-end gap-2", height)}>
        {[45, 70, 35, 85, 55, 95, 65, 40, 78, 52].map((h, i) => (
          <span
            key={i}
            aria-hidden
            className="shimmer flex-1 rounded-t-md"
            style={{ height: `${h}%` }}
          />
        ))}
      </div>
    </div>
  );
}
