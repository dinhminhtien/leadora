"use client";

/**
 * Shared timeline — Website UI Blueprint §9.8.
 *
 * Every history surface in the product (customer history, interaction feed,
 * activity log, lead activity tab) was drawing its own rail, medallion and card,
 * each with slightly different spacing. This is the one implementation, driven
 * by the canonical event vocabulary in `shared/design/timeline-events`.
 *
 * Anatomy per §9.8 — icon · actor · object · action · timestamp · description ·
 * old → new value · status/priority badges. Anything the caller does not supply
 * is simply omitted; nothing collapses the row height, so a dense feed of
 * one-line events stays aligned with a sparse feed of detailed ones.
 */

import * as React from "react";
import {
  AlarmClock, ArrowRight, ArrowRightLeft, Banknote, BedDouble, Bell, Bot,
  BriefcaseBusiness, CalendarCheck, CalendarDays, CheckCircle2, Cpu, FileText,
  Gauge, GitBranch, Handshake, History, Hotel, Mail, MessageSquare, Paperclip,
  Phone, Plus, ReceiptText, ShieldCheck, ShieldX, Star, TrendingUp, UserCog,
  Users, Workflow, XCircle, type LucideIcon,
} from "lucide-react";

import { cn } from "@/lib/utils";
import {
  timelineEventKind,
  timelineEventSpec,
  type TimelineEventKind,
} from "@/shared/design/timeline-events";
import type { StatusTone } from "@/shared/design/status-tokens";

/**
 * Name → component. Kept beside the renderer rather than in the data module so
 * the registry stays importable from non-client code.
 */
const ICONS: Record<string, LucideIcon> = {
  AlarmClock, ArrowRightLeft, Banknote, BedDouble, Bell, Bot, BriefcaseBusiness,
  CalendarCheck, CalendarDays, CheckCircle2, Cpu, FileText, Gauge, GitBranch,
  Handshake, History, Hotel, Mail, MessageSquare, Paperclip, Phone, Plus,
  ReceiptText, ShieldCheck, ShieldX, Star, TrendingUp, UserCog, Users, Workflow,
  XCircle,
};

export function timelineEventIcon(kind: TimelineEventKind): LucideIcon {
  return ICONS[timelineEventSpec(kind).icon] ?? Cpu;
}

/** Medallion colours per tone. Soft fill + saturated glyph reads at 24px. */
const TONE_MEDALLION: Record<StatusTone, string> = {
  primary: "bg-brand-500/12 text-brand-600 dark:text-brand-500",
  success: "bg-success/12 text-success",
  warning: "bg-warning/15 text-warning",
  danger: "bg-danger/12 text-danger",
  info: "bg-info/12 text-info",
  teal: "bg-teal/12 text-teal",
  muted: "bg-muted text-muted-foreground",
};

const TONE_CHIP: Record<StatusTone, string> = {
  primary: "bg-brand-500/10 text-brand-600 dark:text-brand-500",
  success: "bg-success/12 text-success",
  warning: "bg-warning/15 text-warning",
  danger: "bg-danger/12 text-danger",
  info: "bg-info/12 text-info",
  teal: "bg-teal/12 text-teal",
  muted: "bg-muted text-muted-foreground",
};

export type TimelineItemSpec = {
  id: string;
  /** Event vocabulary entry. Pass `rawType` instead to have it inferred. */
  kind?: TimelineEventKind;
  /** Backend type string, normalised through `timelineEventKind()`. */
  rawType?: string | null;
  /** Headline — what happened, e.g. the record title. */
  title: React.ReactNode;
  /** Who did it. */
  actor?: React.ReactNode;
  /** Verb phrase shown before the title, e.g. "approved". */
  action?: React.ReactNode;
  timestamp?: React.ReactNode;
  description?: React.ReactNode;
  /** Field-level change. Both required for the `old → new` row to render. */
  change?: { field?: string; from?: React.ReactNode; to?: React.ReactNode };
  /** Extra chips — status pill, priority flag, amount. */
  badges?: React.ReactNode;
  /** Replaces the event label chip when set. */
  label?: string;
  onClick?: () => void;
};

function EventChip({ tone, children }: { tone: StatusTone; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-pill px-1.5 py-0.5 text-[10px] font-bold",
        TONE_CHIP[tone],
      )}
    >
      {children}
    </span>
  );
}

export function TimelineItem({ item, isLast }: { item: TimelineItemSpec; isLast: boolean }) {
  const kind = item.kind ?? timelineEventKind(item.rawType);
  const spec = timelineEventSpec(kind);
  const Icon = timelineEventIcon(kind);

  return (
    <li className="relative flex gap-3">
      {/* Rail — drawn per item and stopped on the last one, so it never runs
          past the final event into empty space. */}
      {!isLast && (
        <span
          aria-hidden
          className="absolute left-4 top-9 bottom-0 -ml-px w-px bg-border"
        />
      )}

      <span
        aria-hidden
        className={cn(
          "relative z-10 mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ring-4 ring-surface",
          TONE_MEDALLION[spec.tone],
        )}
      >
        <Icon className="size-4" />
      </span>

      <div
        className={cn(
          "min-w-0 flex-1 rounded-lg border border-border bg-surface px-3 py-2.5",
          // pb on the wrapper, not a margin, so the rail meets the next
          // medallion with no gap regardless of card height.
          "mb-3",
          item.onClick && "cursor-pointer transition-colors hover:bg-surface-2",
        )}
        onClick={item.onClick}
      >
        <div className="mb-1 flex flex-wrap items-center gap-1.5">
          <EventChip tone={spec.tone}>{item.label ?? spec.label}</EventChip>
          {item.badges}
          {item.timestamp && (
            <span className="ml-auto shrink-0 text-[11px] text-muted-foreground">
              {item.timestamp}
            </span>
          )}
        </div>

        <p className="truncate text-[13px] font-semibold text-foreground">
          {item.action && (
            <span className="font-normal text-muted-foreground">{item.action} </span>
          )}
          {item.title}
        </p>

        {item.description && (
          <p className="mt-0.5 line-clamp-2 text-[12px] text-muted-foreground">
            {item.description}
          </p>
        )}

        {item.change && (item.change.from != null || item.change.to != null) && (
          <p className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11.5px]">
            {item.change.field && (
              <span className="text-muted-foreground">{item.change.field}:</span>
            )}
            <span className="rounded bg-muted px-1.5 py-0.5 text-muted-foreground line-through">
              {item.change.from ?? "—"}
            </span>
            <ArrowRight aria-hidden className="size-3 text-muted-foreground" />
            <span className="rounded bg-success/12 px-1.5 py-0.5 font-medium text-success">
              {item.change.to ?? "—"}
            </span>
          </p>
        )}

        {item.actor && (
          <p className="mt-1.5 text-[11px] text-muted-foreground">{item.actor}</p>
        )}
      </div>
    </li>
  );
}

/**
 * Groups items under headings (usually month) and renders the rail.
 *
 * `groups` rather than a flat list because every history in this product is read
 * newest-first in monthly chunks, and a heading between chunks is what stops a
 * long feed reading as one undifferentiated column.
 */
export function Timeline({
  groups,
  emptyMessage = "Nothing recorded yet.",
  className,
}: {
  groups: { label: string; items: TimelineItemSpec[] }[];
  emptyMessage?: string;
  className?: string;
}) {
  const total = groups.reduce((n, g) => n + g.items.length, 0);

  if (total === 0) {
    return (
      <p className="py-8 text-center text-[13px] text-muted-foreground">
        {emptyMessage}
      </p>
    );
  }

  return (
    <div className={cn("space-y-5", className)}>
      {groups.map((group) => (
        <section key={group.label}>
          <h4 className="mb-2.5 px-1 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
            {group.label}
          </h4>
          <ul className="relative">
            {group.items.map((item, i) => (
              <TimelineItem
                key={item.id}
                item={item}
                isLast={i === group.items.length - 1}
              />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** Groups ISO-dated items into `Month YYYY` buckets, newest first. */
export function groupByMonth<T>(
  items: T[],
  getTime: (item: T) => number,
  toSpec: (item: T) => TimelineItemSpec,
): { label: string; items: TimelineItemSpec[] }[] {
  const sorted = [...items].sort((a, b) => getTime(b) - getTime(a));
  const groups: { label: string; items: TimelineItemSpec[] }[] = [];

  for (const item of sorted) {
    const label = new Date(getTime(item)).toLocaleDateString("en-GB", {
      month: "long",
      year: "numeric",
    });
    const last = groups[groups.length - 1];
    if (last?.label === label) last.items.push(toSpec(item));
    else groups.push({ label, items: [toSpec(item)] });
  }

  return groups;
}
