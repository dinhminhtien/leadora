"use client";

/**
 * The `…` overflow menu that ends every table row — Website UI Blueprint §2.6
 * and §3.4.
 *
 * The blueprint puts row actions behind one trigger for a reason: a row with
 * four inline buttons spends most of its width on controls the user rarely
 * clicks, pushes the real data into truncation, and makes every row look like an
 * alert. One `…` keeps the row scannable and gives every list the same shape.
 *
 * Rules are first-class here. An action carries an optional `reason`; when set,
 * the item is disabled *and* renders the explanation underneath, so a blocked
 * action still teaches instead of silently greying out (Phase 7 / BR set). This
 * is the menu equivalent of `GuardedButton`.
 */

import * as React from "react";
import { Lock, MoreHorizontal } from "lucide-react";

import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export type RowAction = {
  key: string;
  label: string;
  icon?: React.ComponentType<{ className?: string }>;
  onSelect: () => void;
  /** Destructive styling — delete, reject, close-lost. */
  tone?: "default" | "danger" | "success";
  /** When set, the item is disabled and this explains why. */
  reason?: string | null;
  /** Renders a divider above this item. */
  separatorBefore?: boolean;
};

export function RowActions({
  actions,
  label = "Row actions",
  align = "end",
}: {
  actions: RowAction[];
  label?: string;
  align?: "start" | "end";
}) {
  if (actions.length === 0) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={label}
          // The row itself is usually clickable — without this, opening the menu
          // would also open the record behind it.
          onClick={(e) => e.stopPropagation()}
          className={cn(
            "inline-grid size-7 place-items-center rounded-md text-muted-foreground transition-colors",
            "hover:bg-surface-2 hover:text-foreground",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
          )}
        >
          <MoreHorizontal className="size-4" />
        </button>
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align={align}
        className="w-56"
        onClick={(e) => e.stopPropagation()}
      >
        <DropdownMenuLabel className="text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          {label}
        </DropdownMenuLabel>

        {actions.map((action) => {
          const Icon = action.icon;
          const blocked = !!action.reason;

          return (
            <React.Fragment key={action.key}>
              {action.separatorBefore && <DropdownMenuSeparator />}
              <DropdownMenuItem
                disabled={blocked}
                onSelect={(e) => {
                  if (blocked) {
                    // Keep the menu open so the reason stays readable.
                    e.preventDefault();
                    return;
                  }
                  action.onSelect();
                }}
                className={cn(
                  "flex-col items-start gap-0.5",
                  !blocked && action.tone === "danger" && "text-danger focus:text-danger",
                  !blocked && action.tone === "success" && "text-success focus:text-success",
                )}
              >
                <span className="flex w-full items-center gap-2">
                  {blocked ? (
                    <Lock aria-hidden className="size-3.5 shrink-0" />
                  ) : (
                    Icon && <Icon className="size-3.5 shrink-0" />
                  )}
                  {action.label}
                </span>
                {blocked && (
                  <span className="pl-[1.375rem] text-[11px] leading-[15px] text-muted-foreground">
                    {action.reason}
                  </span>
                )}
              </DropdownMenuItem>
            </React.Fragment>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Round initials chip used in owner/assignee cells — the reference list design
 * pairs every person's name with one of these.
 */
export function OwnerCell({ name }: { name?: string | null }) {
  if (!name) {
    return <span className="text-xs italic text-muted-foreground">Unassigned</span>;
  }

  const initials = name
    .trim()
    .split(/\s+/)
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <span className="flex items-center gap-2">
      <span
        aria-hidden
        className="grid size-6 shrink-0 place-items-center rounded-full bg-brand-500/12 text-[10px] font-bold text-brand-600 dark:text-brand-500"
      >
        {initials}
      </span>
      <span className="truncate text-xs text-foreground">{name}</span>
    </span>
  );
}
