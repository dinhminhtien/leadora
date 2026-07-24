import { cn } from "@/lib/utils";

/**
 * Design-system skeleton placeholder. Prefer this over a bare spinner for
 * list/detail loading states (per the mobile-ui doctrine applied to web).
 */
function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-muted", className)}
      {...props}
    />
  );
}

export { Skeleton };
