"use client";

/**
 * List toolbar — Website UI Blueprint §2.6 (toolbar row) and §9.1.2.
 *
 * > "Toolbar: search · saved filters · filter chips · sort · density · columns ·
 * >  bulk-action bar (contextual)."
 *
 * The search input carries `data-toolbar-search`, which is what the global `/`
 * shortcut focuses (§6.1). Registering it here means every list gets the
 * shortcut for free rather than each screen wiring its own ref.
 *
 * Applied filters render as removable chips beneath the row, so a user can
 * always see *why* a list is short — the most common support question about a
 * filtered table.
 */

import * as React from "react";
import { Search, SlidersHorizontal, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import type { TableDensity } from "@/components/ui/data-table";

/* ------------------------------------------------------------------ *
 * Search field
 * ------------------------------------------------------------------ */

export function ToolbarSearch({
  value,
  onChange,
  placeholder = "Search…",
  className,
  /** Submit-on-Enter instead of live filtering, for server-side search. */
  onSubmit,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  onSubmit?: (value: string) => void;
}) {
  return (
    <div className={cn("relative min-w-0 flex-1 sm:max-w-xs", className)}>
      <Search
        aria-hidden
        className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
      />
      <input
        // Focused by the global `/` shortcut — see use_global_shortcuts.
        data-toolbar-search
        type="search"
        role="searchbox"
        aria-label={placeholder}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && onSubmit) onSubmit(value);
        }}
        className={cn(
          "h-9 w-full rounded-md border border-input bg-surface pl-8 pr-8 text-[13px] text-foreground",
          "placeholder:text-muted-foreground/70",
          "focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30",
        )}
      />
      {value && (
        <button
          type="button"
          aria-label="Clear search"
          onClick={() => onChange("")}
          className="absolute right-2 top-1/2 grid size-5 -translate-y-1/2 place-items-center rounded text-muted-foreground transition-colors hover:bg-surface-2 hover:text-foreground"
        >
          <X className="size-3.5" />
        </button>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Filter chips
 * ------------------------------------------------------------------ */

export type FilterChipSpec = {
  id: string;
  label: string;
  onRemove: () => void;
};

export function ActiveFilterChips({
  chips,
  onClearAll,
  className,
}: {
  chips: FilterChipSpec[];
  onClearAll?: () => void;
  className?: string;
}) {
  if (chips.length === 0) return null;

  return (
    <div className={cn("flex flex-wrap items-center gap-1.5", className)}>
      {chips.map((chip) => (
        <span
          key={chip.id}
          className="inline-flex items-center gap-1 rounded-pill border border-brand-500/25 bg-brand-500/10 py-0.5 pl-2.5 pr-1 text-[11.5px] font-medium text-brand-600 dark:text-brand-500"
        >
          {chip.label}
          <button
            type="button"
            aria-label={`Remove filter ${chip.label}`}
            onClick={chip.onRemove}
            className="grid size-4 place-items-center rounded-full transition-colors hover:bg-brand-500/20"
          >
            <X className="size-2.5" />
          </button>
        </span>
      ))}
      {chips.length > 1 && onClearAll && (
        <button
          type="button"
          onClick={onClearAll}
          className="rounded px-1.5 text-[11.5px] font-medium text-muted-foreground underline-offset-2 transition-colors hover:text-foreground hover:underline"
        >
          Clear all
        </button>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Segmented control — view switchers, scope toggles
 * ------------------------------------------------------------------ */

export function SegmentedControl<T extends string>({
  value,
  onChange,
  options,
  label,
  size = "md",
  className,
}: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string; icon?: React.ComponentType<{ className?: string }> }[];
  label: string;
  size?: "sm" | "md";
  className?: string;
}) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className={cn(
        "inline-flex shrink-0 items-center gap-0.5 rounded-md border border-border bg-muted p-0.5",
        className,
      )}
    >
      {options.map((opt) => {
        const Icon = opt.icon;
        const active = value === opt.value;
        return (
          <button
            key={opt.value}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(opt.value)}
            title={opt.label}
            className={cn(
              "inline-flex items-center gap-1.5 rounded font-medium transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              size === "sm" ? "h-6 px-2 text-[11.5px]" : "h-7 px-2.5 text-[12.5px]",
              active
                ? "bg-surface text-foreground shadow-elev-1"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {Icon && <Icon className="size-3.5" />}
            <span className={cn(Icon && "hidden sm:inline")}>{opt.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Density menu — §2.6 "Density is a mode, not a default"
 * ------------------------------------------------------------------ */

const DENSITY_LABELS: Record<TableDensity, string> = {
  comfortable: "Comfortable",
  compact: "Compact",
  ultra: "Ultra compact",
};

export function DensityMenu({
  value,
  onChange,
}: {
  value: TableDensity;
  onChange: (density: TableDensity) => void;
}) {
  const [open, setOpen] = React.useState(false);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          size="sm"
          variant="secondary"
          leftIcon={<SlidersHorizontal className="size-3.5" />}
          title="Row density"
        >
          <span className="hidden lg:inline">{DENSITY_LABELS[value]}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-44">
        <p className="px-2 pb-1 pt-1.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          Row density
        </p>
        {(Object.keys(DENSITY_LABELS) as TableDensity[]).map((d) => (
          <button
            key={d}
            onClick={() => {
              onChange(d);
              setOpen(false);
            }}
            className={cn(
              "flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-[13px] transition-colors",
              d === value
                ? "bg-brand-500/10 font-medium text-brand-600 dark:text-brand-500"
                : "text-foreground hover:bg-surface-2",
            )}
          >
            {DENSITY_LABELS[d]}
          </button>
        ))}
      </PopoverContent>
    </Popover>
  );
}

/* ------------------------------------------------------------------ *
 * Toolbar shell
 * ------------------------------------------------------------------ */

export function ListToolbar({
  children,
  chips,
  onClearFilters,
  className,
}: {
  children: React.ReactNode;
  chips?: FilterChipSpec[];
  onClearFilters?: () => void;
  className?: string;
}) {
  return (
    <div className={cn("mb-3 space-y-2", className)}>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
      {chips && chips.length > 0 && (
        <ActiveFilterChips chips={chips} onClearAll={onClearFilters} />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Stat tiles — the clickable summary strip above a list (§10.4, §10.5)
 * ------------------------------------------------------------------ */

export function StatTiles({
  tiles,
  className,
}: {
  tiles: {
    label: string;
    value: React.ReactNode;
    tone?: "brand" | "success" | "warning" | "danger" | "muted";
    onClick?: () => void;
    active?: boolean;
  }[];
  className?: string;
}) {
  const toneText = {
    brand: "text-brand-600 dark:text-brand-500",
    success: "text-success",
    warning: "text-warning",
    danger: "text-danger",
    muted: "text-foreground",
  } as const;

  // Tailwind compiles classes statically, so a template literal built from
  // `tiles.length` would never generate. Map the count to a real class instead.
  const columns =
    {
      2: "lg:grid-cols-2",
      3: "lg:grid-cols-3",
      4: "lg:grid-cols-4",
      5: "lg:grid-cols-5",
      6: "lg:grid-cols-6",
    }[Math.min(Math.max(tiles.length, 2), 6)] ?? "lg:grid-cols-4";

  return (
    <div className={cn("mb-4 grid grid-cols-2 gap-3", columns, className)}>
      {tiles.map((tile) => {
        const Wrapper = tile.onClick ? "button" : "div";
        return (
          <Wrapper
            key={tile.label}
            {...(tile.onClick ? { type: "button" as const, onClick: tile.onClick } : {})}
            className={cn(
              "card-surface px-4 py-3 text-left transition-[box-shadow,border-color] duration-[120ms]",
              tile.onClick &&
                "hover:border-brand-300 hover:shadow-elev-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              tile.active && "border-brand-500 ring-1 ring-brand-500/30",
            )}
          >
            <p className="text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              {tile.label}
            </p>
            <p
              className={cn(
                "numeric mt-1 text-[22px] font-bold leading-7",
                toneText[tile.tone ?? "muted"],
              )}
            >
              {tile.value}
            </p>
          </Wrapper>
        );
      })}
    </div>
  );
}
