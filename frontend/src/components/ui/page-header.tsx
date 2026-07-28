"use client";

/**
 * Page header + breadcrumb — Website UI Blueprint §4.3 and §9.1.1.
 *
 * Anatomy: breadcrumb · title (h1) · subtitle · optional entity-ID chip and
 * status pill · right-aligned action cluster.
 *
 * **Consolidation note.** Two unused `PageHeader` components existed before this
 * (`components/layout/` and `shared/components/`), neither imported anywhere and
 * neither matching the blueprint — one hard-coded `text-zinc-950`, which is
 * invisible in dark mode. Both are removed in favour of this single component so
 * there is one page header to change when the spec moves.
 *
 * Breadcrumb rules from §4.3: `text-caption`, `chevron_right` separator, last
 * crumb is `--foreground` weight 500, earlier crumbs are muted links, and the
 * middle truncates with `…` beyond 4 levels.
 */

import * as React from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";

export type Crumb = {
  label: string;
  href?: string;
};

const MAX_VISIBLE_CRUMBS = 4;

/**
 * Collapses the middle of a long trail: `A / … / C / D`. The first and last two
 * crumbs are what orient a user; the ones between are noise on a narrow header.
 */
function collapseCrumbs(crumbs: Crumb[]): (Crumb | "ellipsis")[] {
  if (crumbs.length <= MAX_VISIBLE_CRUMBS) return crumbs;
  return [crumbs[0], "ellipsis", ...crumbs.slice(-2)];
}

export function Breadcrumb({
  crumbs,
  className,
}: {
  crumbs: Crumb[];
  className?: string;
}) {
  if (!crumbs.length) return null;
  const items = collapseCrumbs(crumbs);

  return (
    <nav
      aria-label="Breadcrumb"
      className={cn(
        "flex min-w-0 items-center gap-1 text-[12px] leading-4 text-muted-foreground",
        className,
      )}
    >
      <ol className="flex min-w-0 items-center gap-1">
        {items.map((item, i) => {
          const isLast = i === items.length - 1;
          return (
            <li key={i} className="flex min-w-0 items-center gap-1">
              {i > 0 && (
                <ChevronRight aria-hidden className="size-3.5 shrink-0 opacity-60" />
              )}
              {item === "ellipsis" ? (
                <span aria-hidden>…</span>
              ) : isLast ? (
                <span
                  aria-current="page"
                  className="truncate font-medium text-foreground"
                >
                  {item.label}
                </span>
              ) : item.href ? (
                <Link
                  href={item.href}
                  className="truncate rounded-xs transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
                >
                  {item.label}
                </Link>
              ) : (
                <span className="truncate">{item.label}</span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/**
 * The mono ID chip §4.3 asks for on detail pages (`L-4821`). Monospace because
 * IDs are scanned character-by-character, not read as words.
 */
export function EntityIdChip({
  id,
  className,
}: {
  id: string;
  className?: string;
}) {
  return (
    <code
      title={id}
      className={cn(
        "numeric inline-flex h-6 max-w-[16ch] items-center truncate rounded-md border border-border bg-muted px-1.5 font-mono text-[11.5px] font-medium text-muted-foreground",
        className,
      )}
    >
      {id}
    </code>
  );
}

type PageHeaderProps = {
  crumbs?: Crumb[];
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Rendered beside the title — status pill, ID chip, corporate badge, etc. */
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
  /** Tightens vertical rhythm for embedded/secondary headers. */
  dense?: boolean;
};

export function PageHeader({
  crumbs,
  title,
  subtitle,
  meta,
  actions,
  className,
  dense = false,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between",
        dense ? "mb-4" : "mb-6",
        className,
      )}
    >
      <div className="min-w-0 flex-1">
        {crumbs && crumbs.length > 0 && <Breadcrumb crumbs={crumbs} className="mb-1.5" />}
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
          <h1
            className={cn(
              "min-w-0 truncate font-bold tracking-[-0.015em] text-foreground",
              dense ? "text-[24px] leading-8" : "text-[30px] leading-[38px]",
            )}
          >
            {title}
          </h1>
          {meta}
        </div>
        {subtitle && (
          <p className="mt-1.5 max-w-3xl text-[13px] leading-[18px] text-muted-foreground">
            {subtitle}
          </p>
        )}
      </div>
      {actions && (
        <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
      )}
    </header>
  );
}
