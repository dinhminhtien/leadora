"use client";

/**
 * The toolbar controls every management list is required to carry — Website UI
 * Blueprint §2.6 and §9.1.2: search · filters · **column picker** · **export** ·
 * **refresh** · density · bulk actions.
 *
 * `list-toolbar.tsx` already shipped search, filter chips, the segmented control
 * and the density menu. The three missing pieces live here, plus the state hook
 * that ties them together, so a screen adopts the whole standard in one import
 * rather than re-deriving column visibility and CSV escaping per module.
 *
 * Everything is presentational or local state — no screen's data fetching,
 * permissions or business rules pass through here.
 */

import * as React from "react";
import { Columns3, Download, RefreshCw, RotateCcw } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import type { ColumnDef, TableDensity } from "@/components/ui/data-table";

/* ------------------------------------------------------------------ *
 * Refresh
 * ------------------------------------------------------------------ */

/**
 * Manual refetch. Spins while in flight so a click on an already-fresh list
 * still gives feedback — without it users click repeatedly, assuming it missed.
 */
export function RefreshButton({
  onRefresh,
  isRefreshing = false,
  className,
}: {
  onRefresh: () => void;
  isRefreshing?: boolean;
  className?: string;
}) {
  return (
    <Button
      size="sm"
      variant="secondary"
      onClick={onRefresh}
      disabled={isRefreshing}
      title="Refresh"
      aria-label="Refresh list"
      className={className}
      leftIcon={
        <RefreshCw className={cn("size-3.5", isRefreshing && "animate-spin")} />
      }
    >
      <span className="hidden lg:inline">Refresh</span>
    </Button>
  );
}

/* ------------------------------------------------------------------ *
 * Column picker
 * ------------------------------------------------------------------ */

/**
 * Per-column visibility. Columns marked `required` cannot be hidden — hiding the
 * name column leaves a table of anonymous rows, which is never what the user
 * meant.
 */
export function ColumnPicker<T>({
  columns,
  hiddenIds,
  onChange,
  requiredIds = [],
}: {
  columns: ColumnDef<T>[];
  hiddenIds: Set<string>;
  onChange: (next: Set<string>) => void;
  requiredIds?: string[];
}) {
  const [open, setOpen] = React.useState(false);
  const visibleCount = columns.length - hiddenIds.size;

  const toggle = (id: string) => {
    const next = new Set(hiddenIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onChange(next);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          size="sm"
          variant="secondary"
          leftIcon={<Columns3 className="size-3.5" />}
          title="Choose columns"
        >
          <span className="hidden lg:inline">
            Columns
            {hiddenIds.size > 0 && (
              <span className="numeric ml-1 text-muted-foreground">
                ({visibleCount}/{columns.length})
              </span>
            )}
          </span>
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-56">
        <div className="flex items-center justify-between px-2 pb-1 pt-1.5">
          <p className="text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
            Columns
          </p>
          {hiddenIds.size > 0 && (
            <button
              type="button"
              onClick={() => onChange(new Set())}
              className="inline-flex items-center gap-1 rounded text-[11px] font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              <RotateCcw className="size-3" />
              Reset
            </button>
          )}
        </div>

        <div className="max-h-72 overflow-y-auto">
          {columns.map((col) => {
            const required = requiredIds.includes(col.id);
            const visible = !hiddenIds.has(col.id);
            return (
              <label
                key={col.id}
                className={cn(
                  "flex items-center gap-2 rounded-md px-2 py-1.5 text-[13px] transition-colors",
                  required
                    ? "cursor-not-allowed text-muted-foreground"
                    : "cursor-pointer text-foreground hover:bg-surface-2",
                )}
              >
                <Checkbox
                  checked={visible}
                  disabled={required}
                  onCheckedChange={() => !required && toggle(col.id)}
                  aria-label={`Show column ${typeof col.header === "string" ? col.header : col.id}`}
                />
                <span className="truncate">
                  {typeof col.header === "string" ? col.header : col.id}
                </span>
                {required && (
                  <span className="ml-auto text-[10px] uppercase tracking-wide">
                    fixed
                  </span>
                )}
              </label>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}

/* ------------------------------------------------------------------ *
 * Export
 * ------------------------------------------------------------------ */

/**
 * RFC-4180 quoting. A guest called `O'Brien, Jr.` or a note containing a newline
 * would otherwise shift every following column — silent data corruption in a
 * file the user then sends to finance.
 */
function csvCell(value: unknown): string {
  if (value == null) return "";
  const s = String(value);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function toCsv(headers: string[], rows: unknown[][]): string {
  // Excel reads a bare UTF-8 CSV as the local ANSI codepage and mangles đ/ệ/₫.
  // The BOM is what makes Vietnamese names survive a round trip.
  const body = [headers, ...rows]
    .map((r) => r.map(csvCell).join(","))
    .join("\r\n");
  return `﻿${body}`;
}

export function downloadCsv(filename: string, csv: string) {
  const url = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8;" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(".csv") ? filename : `${filename}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export type ExportRow = Record<string, unknown>;

/**
 * Exports what the user is looking at. `rows` is the current page's data after
 * filtering, and the column set follows the picker — an export that silently
 * included hidden columns or filtered-out rows would not match the screen.
 *
 * `onExportAll` is optional and only meaningful where the caller can fetch
 * beyond the current page.
 */
export function ExportMenu({
  filename,
  headers,
  rows,
  onExportAll,
  disabled,
}: {
  filename: string;
  headers: string[];
  rows: (string | number | null | undefined)[][];
  onExportAll?: () => void | Promise<void>;
  disabled?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const [busy, setBusy] = React.useState(false);

  const exportPage = () => {
    downloadCsv(filename, toCsv(headers, rows));
    setOpen(false);
  };

  const exportAll = async () => {
    if (!onExportAll) return;
    setBusy(true);
    try {
      await onExportAll();
    } finally {
      setBusy(false);
      setOpen(false);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          size="sm"
          variant="secondary"
          disabled={disabled || rows.length === 0}
          title={rows.length === 0 ? "Nothing to export" : "Export"}
          leftIcon={<Download className="size-3.5" />}
        >
          <span className="hidden lg:inline">Export</span>
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-52">
        <p className="px-2 pb-1 pt-1.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
          Export as CSV
        </p>
        <button
          type="button"
          onClick={exportPage}
          className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-[13px] text-foreground transition-colors hover:bg-surface-2"
        >
          This page
          <span className="numeric text-[11px] text-muted-foreground">
            {rows.length}
          </span>
        </button>
        {onExportAll && (
          <button
            type="button"
            onClick={exportAll}
            disabled={busy}
            className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-[13px] text-foreground transition-colors hover:bg-surface-2 disabled:opacity-60"
          >
            All matching rows
            {busy && <span className="text-[11px] text-muted-foreground">…</span>}
          </button>
        )}
      </PopoverContent>
    </Popover>
  );
}

/* ------------------------------------------------------------------ *
 * State hook
 * ------------------------------------------------------------------ */

const STORAGE_PREFIX = "leadora.table.";

function readStored<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(STORAGE_PREFIX + key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    // A corrupt or blocked localStorage must never take a list screen down.
    return fallback;
  }
}

function writeStored(key: string, value: unknown) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(value));
  } catch {
    /* private mode / quota — the control still works for this session */
  }
}

export type TableControls<T> = {
  density: TableDensity;
  setDensity: (d: TableDensity) => void;
  hiddenColumnIds: Set<string>;
  setHiddenColumnIds: (next: Set<string>) => void;
  /** `columns` minus whatever the picker has hidden — pass to `DataTable`. */
  visibleColumns: ColumnDef<T>[];
  selectedIds: Set<string>;
  setSelectedIds: (next: Set<string>) => void;
  clearSelection: () => void;
  sortBy?: string;
  sortDir: "asc" | "desc";
  onSortChange: (columnId: string, direction: "asc" | "desc") => void;
};

/**
 * Owns the presentation state every standard list shares: density, column
 * visibility, sort and selection.
 *
 * Density and column choices persist per `viewKey` (§9.10 "saved views") because
 * they are a workspace preference — a user who set the leads table to compact
 * expects it compact tomorrow. Sort and selection deliberately do **not**
 * persist: a stale sort hides new rows, and a stale selection invites bulk
 * actions on records the user has forgotten they picked.
 */
export function useTableControls<T>(
  viewKey: string,
  columns: ColumnDef<T>[],
  options?: { defaultDensity?: TableDensity; defaultSortBy?: string },
): TableControls<T> {
  const [density, setDensityState] = React.useState<TableDensity>(
    options?.defaultDensity ?? "comfortable",
  );
  const [hiddenColumnIds, setHiddenState] = React.useState<Set<string>>(
    () => new Set(),
  );
  const [selectedIds, setSelectedIds] = React.useState<Set<string>>(
    () => new Set(),
  );
  const [sortBy, setSortBy] = React.useState<string | undefined>(
    options?.defaultSortBy,
  );
  const [sortDir, setSortDir] = React.useState<"asc" | "desc">("desc");

  // Hydrate after mount, not during render: reading localStorage while
  // rendering makes the server and first client pass disagree and React
  // discards the markup with a hydration error.
  React.useEffect(() => {
    // Precedence: this list's own setting → the account-wide default set in
    // Profile → the caller's default. Without the middle step the Profile
    // preference would be a control that changes nothing.
    const accountDefault = readStored<TableDensity>(
      "__default.density",
      options?.defaultDensity ?? "comfortable",
    );
    setDensityState(readStored<TableDensity>(`${viewKey}.density`, accountDefault));
    setHiddenState(new Set(readStored<string[]>(`${viewKey}.hidden`, [])));
    // Re-reading on every option change would clobber the user's choice with
    // the default, so this intentionally keys on the view alone.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewKey]);

  const setDensity = React.useCallback(
    (d: TableDensity) => {
      setDensityState(d);
      writeStored(`${viewKey}.density`, d);
    },
    [viewKey],
  );

  const setHiddenColumnIds = React.useCallback(
    (next: Set<string>) => {
      setHiddenState(next);
      writeStored(`${viewKey}.hidden`, [...next]);
    },
    [viewKey],
  );

  const visibleColumns = React.useMemo(
    () => columns.filter((c) => !hiddenColumnIds.has(c.id)),
    [columns, hiddenColumnIds],
  );

  const onSortChange = React.useCallback(
    (columnId: string, direction: "asc" | "desc") => {
      setSortBy(columnId);
      setSortDir(direction);
    },
    [],
  );

  const clearSelection = React.useCallback(() => setSelectedIds(new Set()), []);

  return {
    density,
    setDensity,
    hiddenColumnIds,
    setHiddenColumnIds,
    visibleColumns,
    selectedIds,
    setSelectedIds,
    clearSelection,
    sortBy,
    sortDir,
    onSortChange,
  };
}
