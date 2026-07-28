"use client";

/**
 * Role tool cards — Website UI Blueprint §8 ("role-specific tool cards").
 *
 * The Manager and Admin dashboards each carried their own copy of this grid,
 * with their own hard-coded `color` strings per tile. Two copies is two places
 * for the hover treatment and the tonal accents to drift, so the pattern is
 * declared once here and each dashboard supplies only its data.
 */

import * as React from "react";
import Link from "next/link";
import { ArrowUpRight, type LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

export type ToolTone = "brand" | "teal" | "warning" | "info" | "danger" | "success";

const toneClasses: Record<ToolTone, string> = {
  brand: "border-brand-500/20 bg-brand-500/10 text-brand-600 dark:text-brand-500",
  teal: "border-teal/20 bg-teal/10 text-teal",
  warning: "border-warning/25 bg-warning/12 text-warning",
  info: "border-info/20 bg-info/10 text-info",
  danger: "border-danger/25 bg-danger/10 text-danger",
  success: "border-success/25 bg-success/10 text-success",
};

export type ToolCardItem = {
  href: string;
  label: string;
  description: string;
  icon: LucideIcon;
  tone?: ToolTone;
};

export function ToolCardGrid({
  title,
  icon: TitleIcon,
  items,
  className,
}: {
  title: string;
  icon?: LucideIcon;
  items: ToolCardItem[];
  className?: string;
}) {
  if (items.length === 0) return null;

  return (
    <section className={cn("card-surface p-5", className)}>
      <h2 className="mb-3 flex items-center gap-2 text-[13px] font-semibold text-foreground">
        {TitleIcon && <TitleIcon aria-hidden className="size-4 text-brand-500" />}
        {title}
      </h2>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "group relative flex flex-col gap-2 rounded-lg border border-border bg-surface-2/50 p-3",
                "transition-[box-shadow,border-color] duration-[120ms]",
                "hover:border-brand-300 hover:shadow-elev-2",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "grid size-8 place-items-center rounded-md border",
                  toneClasses[item.tone ?? "brand"],
                )}
              >
                <Icon className="size-4" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-[12.5px] font-semibold text-foreground">
                  {item.label}
                </span>
                <span className="mt-0.5 block text-[11px] leading-tight text-muted-foreground">
                  {item.description}
                </span>
              </span>
              <ArrowUpRight
                aria-hidden
                className="absolute right-3 top-3 size-3.5 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100"
              />
            </Link>
          );
        })}
      </div>
    </section>
  );
}
