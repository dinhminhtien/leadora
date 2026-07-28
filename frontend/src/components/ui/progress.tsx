"use client";

/**
 * Progress — Website UI Blueprint §10.11 (payment progress bar: paid / total)
 * and §10.6 (deal stage progress).
 *
 * `tone` exists because a payment bar that is fully paid should read as success,
 * while one that is overdue should read as danger — the same component, told
 * what the number *means* rather than re-implemented per module.
 */

import * as React from "react";
import * as ProgressPrimitive from "@radix-ui/react-progress";

import { cn } from "@/lib/utils";

type ProgressTone = "brand" | "success" | "warning" | "danger";

const toneClasses: Record<ProgressTone, string> = {
  brand: "bg-brand-500",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
};

type ProgressProps = React.ComponentPropsWithoutRef<
  typeof ProgressPrimitive.Root
> & {
  tone?: ProgressTone;
};

const Progress = React.forwardRef<
  React.ComponentRef<typeof ProgressPrimitive.Root>,
  ProgressProps
>(({ className, value, tone = "brand", ...props }, ref) => {
  const pct = Math.min(100, Math.max(0, value ?? 0));
  return (
    <ProgressPrimitive.Root
      ref={ref}
      className={cn(
        "relative h-2 w-full overflow-hidden rounded-pill bg-surface-3",
        className,
      )}
      value={pct}
      {...props}
    >
      <ProgressPrimitive.Indicator
        className={cn(
          "h-full w-full flex-1 rounded-pill transition-transform duration-300 ease-[cubic-bezier(0.2,0,0,1)]",
          toneClasses[tone],
        )}
        style={{ transform: `translateX(-${100 - pct}%)` }}
      />
    </ProgressPrimitive.Root>
  );
});
Progress.displayName = "Progress";

export { Progress };
