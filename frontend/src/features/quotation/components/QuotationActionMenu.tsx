"use client";

/**
 * Row-level overflow menu for secondary quotation actions (Revise, Close,
 * Remind, …).
 *
 * **Why this is a Popover and not an absolutely-positioned div.** The previous
 * version rendered the menu as `absolute right-0 top-full` inside the row. That
 * fails in two ways, both of which show up on the last rows of the table:
 *
 *  1. **Clipping.** The table sits in an `overflow-x-auto` wrapper, which
 *     establishes a clipping context. Anything positioned outside the wrapper's
 *     box is cut off — or worse, silently extends its scrollable area.
 *  2. **No flip.** `top-full` always opens *downward*. For a row near the bottom
 *     the menu ran past the table and off-screen, so the lower items could not
 *     be clicked at all.
 *
 * Radix's Popover fixes both by construction: it portals the content to the
 * document body (escaping every clipping ancestor) and its collision detection
 * flips the menu above the trigger when there is not enough room below. It also
 * brings focus management, `Esc`-to-close and outside-click for free, which lets
 * the hand-rolled full-screen backdrop `div` go away.
 *
 * The external API is unchanged — the list still owns `openActionMenuId` — so
 * this is a drop-in replacement.
 */

import * as React from "react";
import { MoreVertical, type LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

export type QuotationMenuAction = {
  key: string;
  label: string;
  Icon: LucideIcon;
  onClick: () => void;
  tone?: "primary" | "danger" | "default";
};

const toneClasses = {
  danger: "text-danger hover:bg-danger/10",
  primary: "text-brand-600 hover:bg-brand-500/10 dark:text-brand-500",
  default: "text-foreground hover:bg-surface-2",
} as const;

export function QuotationActionMenu({
  actions,
  isOpen,
  onToggle,
  onClose,
}: {
  actions: QuotationMenuAction[];
  isOpen: boolean;
  onToggle: () => void;
  onClose: () => void;
}) {
  if (actions.length === 0) return null;

  return (
    <Popover
      open={isOpen}
      onOpenChange={(next) => {
        // Radix drives both directions; map them onto the caller's existing
        // toggle/close pair rather than changing the list's state shape.
        if (next) onToggle();
        else onClose();
      }}
    >
      <PopoverTrigger asChild>
        <button
          type="button"
          title="More actions"
          aria-label="More actions"
          // Some list variants make the whole row clickable — keep a menu click
          // from also triggering the row.
          onClick={(e) => e.stopPropagation()}
          className={cn(
            "flex size-7 items-center justify-center rounded-md border transition-colors",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
            isOpen
              ? "border-brand-500/40 bg-brand-500/10 text-brand-600 dark:text-brand-500"
              : "border-border text-muted-foreground hover:bg-surface-2 hover:text-foreground",
          )}
        >
          <MoreVertical className="size-3.5" />
        </button>
      </PopoverTrigger>

      <PopoverContent
        align="end"
        side="bottom"
        sideOffset={4}
        // Flip above the trigger when the row is near the bottom of the
        // viewport, and keep an 8px gutter so the menu never touches the edge.
        avoidCollisions
        collisionPadding={8}
        className="w-44 p-1"
        onClick={(e) => e.stopPropagation()}
      >
        {actions.map((a) => (
          <button
            key={a.key}
            type="button"
            onClick={() => {
              onClose();
              a.onClick();
            }}
            className={cn(
              "flex w-full items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-[12.5px] font-medium transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-500",
              toneClasses[a.tone ?? "default"],
            )}
          >
            <a.Icon className="size-3.5 shrink-0" />
            {a.label}
          </button>
        ))}
      </PopoverContent>
    </Popover>
  );
}
