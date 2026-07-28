"use client";

/**
 * Shared record detail drawer — Website UI Blueprint §2.11 / §9.3.
 *
 * **One detail surface for every module.** Tasks, bookings, reminders,
 * customers, reservations, handovers and payments all need the same thing: peek
 * at a row without losing the list's filters, page and scroll. Writing that
 * seven times would give seven slightly different headers, seven definitions of
 * "empty", and seven places to fix a spacing bug.
 *
 * Modules supply *data*, not layout:
 *
 * ```tsx
 * <RecordDetailDrawer
 *   open={!!row}
 *   onOpenChange={(o) => !o && setRow(null)}
 *   title={row.customerName}
 *   subtitle={{ icon: Hotel, text: row.bookingCode }}
 *   status={{ domain: "booking", value: row.status }}
 *   actions={[{ label: "Confirm", onClick: … }]}
 *   sections={[{ title: "Stay", rows: [{ label: "Check-in", value: … }] }]}
 * />
 * ```
 *
 * Rows whose `value` is null/undefined/empty render an em dash, so a module
 * never has to write its own "—" fallback and a missing field can never be
 * mistaken for a real one.
 */

import * as React from "react";
import Link from "next/link";
import { ArrowUpRight, type LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button, type ButtonVariant } from "@/components/ui/Button";
import {
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { StatusPill } from "@/components/ui/status-pill";
import { initialsOf } from "@/shared/utils/avatar";
import type { StatusDomain } from "@/shared/design/status-tokens";

/* ------------------------------------------------------------------ *
 * Types
 * ------------------------------------------------------------------ */

export type DetailRowSpec = {
  label: string;
  /** `null`/`undefined`/`""` renders as an em dash. */
  value?: React.ReactNode;
  icon?: LucideIcon;
  /** Spans the full width instead of the label/value split — for notes. */
  block?: boolean;
};

export type DetailSectionSpec = {
  title: string;
  rows?: DetailRowSpec[];
  /** Arbitrary content instead of rows — timelines, tables, line items. */
  content?: React.ReactNode;
};

export type DetailActionSpec = {
  label: string;
  icon?: LucideIcon;
  onClick: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  /** Shown as a tooltip — use it to say *why* a disabled action is disabled. */
  title?: string;
};

export type RecordDrawerProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;

  title: React.ReactNode;
  subtitle?: { icon?: LucideIcon; text: React.ReactNode };

  /** Initials avatar. Pass `icon` instead for non-person records. */
  avatarName?: string;
  avatarIcon?: LucideIcon;

  /** Canonical status pill (§2.7). */
  status?: { domain: StatusDomain; value?: string | null };
  /** Extra header chips — SLA badge, priority, overdue flag. */
  badges?: React.ReactNode;

  actions?: DetailActionSpec[];
  /** Explains a constraint above the sections, e.g. why an action is missing. */
  notice?: { tone: "info" | "warning" | "danger"; text: React.ReactNode };

  sections: DetailSectionSpec[];

  /** Mono id shown bottom-left. */
  recordId?: string;
  /** Full-page route, rendered bottom-right. */
  fullPageHref?: string;
  fullPageLabel?: string;

  size?: "sm" | "md" | "lg";
  /** Rendered above the sections — tabs, steppers, progress bars. */
  children?: React.ReactNode;
};

/* ------------------------------------------------------------------ *
 * Primitives
 * ------------------------------------------------------------------ */

function Empty() {
  return <span className="text-muted-foreground">—</span>;
}

/** Treats `""` as missing too — a blank string is not a value a user wants shown. */
function isEmptyValue(value: React.ReactNode): boolean {
  return value == null || value === "" || value === false;
}

export function DetailSection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-2">
      <h3 className="text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
        {title}
      </h3>
      <div className="overflow-hidden rounded-lg border border-border">{children}</div>
    </section>
  );
}

export function DetailRow({ label, value, icon: Icon, block }: DetailRowSpec) {
  if (block) {
    return (
      <div className="border-b border-border px-3 py-2.5 last:border-b-0">
        <p className="mb-1 flex items-center gap-1.5 text-[12px] text-muted-foreground">
          {Icon && <Icon className="size-3.5" />}
          {label}
        </p>
        <div className="whitespace-pre-wrap text-[12.5px] leading-relaxed text-foreground">
          {isEmptyValue(value) ? <Empty /> : value}
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start justify-between gap-4 border-b border-border px-3 py-2.5 last:border-b-0">
      <span className="flex shrink-0 items-center gap-1.5 text-[12px] text-muted-foreground">
        {Icon && <Icon className="size-3.5" />}
        {label}
      </span>
      <span className="min-w-0 text-right text-[12.5px] text-foreground">
        {isEmptyValue(value) ? <Empty /> : value}
      </span>
    </div>
  );
}

const noticeClasses = {
  info: "border-brand-500/30 bg-brand-500/10 text-brand-600 dark:text-brand-500",
  warning: "border-warning/30 bg-warning/10 text-warning",
  danger: "border-danger/30 bg-danger-bg/40 text-danger",
} as const;

/* ------------------------------------------------------------------ *
 * Drawer
 * ------------------------------------------------------------------ */

export function RecordDetailDrawer({
  open,
  onOpenChange,
  title,
  subtitle,
  avatarName,
  avatarIcon: AvatarIcon,
  status,
  badges,
  actions = [],
  notice,
  sections,
  recordId,
  fullPageHref,
  fullPageLabel = "Open in full page",
  size = "lg",
  children,
}: RecordDrawerProps) {
  const SubtitleIcon = subtitle?.icon;

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent size={size} className="gap-0">
        <DrawerHeader className="gap-3">
          <div className="flex items-start gap-3">
            {(avatarName || AvatarIcon) && (
              <span
                aria-hidden
                className="grid size-11 shrink-0 place-items-center rounded-full bg-brand-500/12 text-[14px] font-bold text-brand-600 dark:text-brand-500"
              >
                {AvatarIcon ? <AvatarIcon className="size-5" /> : initialsOf(avatarName)}
              </span>
            )}
            <div className="min-w-0 flex-1">
              <DrawerTitle className="truncate text-[18px] leading-6">{title}</DrawerTitle>
              {subtitle && (
                <DrawerDescription className="flex items-center gap-1.5">
                  {SubtitleIcon && <SubtitleIcon className="size-3.5 shrink-0" />}
                  <span className="truncate">{subtitle.text}</span>
                </DrawerDescription>
              )}
            </div>
          </div>

          {(status || badges) && (
            <div className="flex flex-wrap items-center gap-1.5">
              {status && (
                <StatusPill size="sm" domain={status.domain} value={status.value} />
              )}
              {badges}
            </div>
          )}
        </DrawerHeader>

        <DrawerBody className="space-y-5">
          {children}

          {actions.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {actions.map((a) => {
                const Icon = a.icon;
                return (
                  <Button
                    key={a.label}
                    size="sm"
                    variant={a.variant ?? "secondary"}
                    onClick={a.onClick}
                    disabled={a.disabled}
                    title={a.title}
                    leftIcon={Icon ? <Icon className="size-3.5" /> : undefined}
                  >
                    {a.label}
                  </Button>
                );
              })}
            </div>
          )}

          {notice && (
            <p
              className={cn(
                "rounded-md border px-3 py-2 text-[12px]",
                noticeClasses[notice.tone],
              )}
            >
              {notice.text}
            </p>
          )}

          {sections.map((section) => (
            <DetailSection key={section.title} title={section.title}>
              {section.content ??
                section.rows?.map((row) => <DetailRow key={row.label} {...row} />)}
            </DetailSection>
          ))}
        </DrawerBody>

        <DrawerFooter className="justify-between">
          <span className="numeric text-[11px] text-muted-foreground">
            {recordId ? recordId.slice(0, 8).toUpperCase() : ""}
          </span>
          {fullPageHref && (
            // A styled Link, not a Button wrapping one: an anchor inside a
            // <button> is invalid HTML and breaks open-in-new-tab.
            <Link
              href={fullPageHref}
              className={cn(
                "inline-flex h-7 shrink-0 items-center gap-1.5 rounded-md border border-border bg-surface px-2.5",
                "text-[12px] font-semibold text-foreground transition-colors hover:bg-surface-2",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              )}
            >
              {fullPageLabel}
              <ArrowUpRight className="size-3.5" />
            </Link>
          )}
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  );
}

/* ------------------------------------------------------------------ *
 * Shared formatters — so every drawer renders dates and money identically
 * ------------------------------------------------------------------ */

export function formatDetailDate(iso?: string | null): React.ReactNode {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function formatDetailDateTime(iso?: string | null): React.ReactNode {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** VND, the currency every money field in this product is denominated in. */
export function formatVnd(value?: number | null): React.ReactNode {
  if (value == null || Number.isNaN(value)) return null;
  return <span className="numeric">{value.toLocaleString("vi-VN")} ₫</span>;
}
