"use client";

/**
 * Data-table standard — Website UI Blueprint §2.6, §3.4 and §9.1.
 *
 * > "Fixed anatomy for every list screen: toolbar · column header row · rows ·
 * >  footer."
 *
 * The blueprint's point is that a user should learn one table and then know
 * every list in the product. Before this, each screen hand-rolled its own
 * `<table>` with its own row height, its own hover tint and its own footer, so
 * "the leads table" and "the payments table" behaved differently for no reason
 * a user could see.
 *
 * Implemented here:
 * - **Density** — comfortable 56 · compact 44 · ultra 36 (§2.6), persisted per
 *   view by the caller.
 * - **Sticky header** on vertical scroll; sticky first/last columns on
 *   horizontal scroll.
 * - **Right-aligned numerics** with `tabular-nums`, mono for money and IDs.
 * - **Selection** with an indeterminate header checkbox and a contextual
 *   bulk-action bar that replaces the toolbar while N > 0 (§3.4).
 * - **Keyboard** — `j/k` move the row cursor, `Enter` opens, `x` toggles
 *   selection (§6.2).
 *
 * It is deliberately presentational: sorting, paging and filtering are the
 * caller's state, because those map to server query params that differ per
 * module.
 */

import * as React from "react";
import { ArrowDown, ArrowUp, ChevronsUpDown, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/checkbox";
import { TableSkeleton } from "@/components/ui/skeletons";
import { EmptyState, ErrorState } from "@/components/ui/states";

export type TableDensity = "comfortable" | "compact" | "ultra";

export const DENSITY_ROW_HEIGHT: Record<TableDensity, string> = {
  comfortable: "h-14",
  compact: "h-11",
  ultra: "h-9",
};

const DENSITY_CELL_PADDING: Record<TableDensity, string> = {
  comfortable: "px-4",
  compact: "px-3",
  ultra: "px-2.5",
};

export type SortDirection = "asc" | "desc";

export type ColumnDef<T> = {
  id: string;
  header: React.ReactNode;
  /** Cell renderer. Keep it pure — it runs for every row on every render. */
  cell: (row: T) => React.ReactNode;
  /** Enables the sort affordance; the caller performs the actual sort. */
  sortable?: boolean;
  /** Right-align + tabular figures. Use for money, counts and dates. */
  numeric?: boolean;
  /** Fixed width, e.g. `w-10` or `w-[220px]`. */
  width?: string;
  /** Hidden below this breakpoint, e.g. `md` → `hidden md:table-cell`. */
  minWidth?: "sm" | "md" | "lg" | "xl";
  /** Pin to the left (after the checkbox) or right edge on horizontal scroll. */
  sticky?: "left" | "right";
  className?: string;
};

const MIN_WIDTH_CLASS = {
  sm: "hidden sm:table-cell",
  md: "hidden md:table-cell",
  lg: "hidden lg:table-cell",
  xl: "hidden xl:table-cell",
} as const;

type DataTableProps<T> = {
  rows: T[];
  columns: ColumnDef<T>[];
  /** Stable identity — used for selection, keys and the row cursor. */
  rowId: (row: T) => string;

  isLoading?: boolean;
  error?: unknown;
  onRetry?: () => void;

  density?: TableDensity;

  /** Row click → open detail. Also bound to `Enter` on the focused row. */
  onRowClick?: (row: T) => void;
  /** Highlighted row (deep-link `?highlight=`), flashed by the caller. */
  highlightId?: string | null;

  /** Omit to disable selection entirely. */
  selectedIds?: Set<string>;
  onSelectionChange?: (next: Set<string>) => void;

  /** Rendered inside the bulk bar when rows are selected. */
  bulkActions?: React.ReactNode;

  sortBy?: string;
  sortDir?: SortDirection;
  onSortChange?: (columnId: string, direction: SortDirection) => void;

  /** Empty-state copy. `filtered` switches to the "clear filters" variant. */
  emptyTitle?: string;
  emptyMessage?: string;
  emptyAction?: { label: string; onClick: () => void };
  isFiltered?: boolean;
  onClearFilters?: () => void;

  /** Rendered under the table — pagination, counts. */
  footer?: React.ReactNode;
  className?: string;
  /** Accessible name for the table. */
  label: string;
};

export function DataTable<T>({
  rows,
  columns,
  rowId,
  isLoading = false,
  error,
  onRetry,
  density = "comfortable",
  onRowClick,
  highlightId,
  selectedIds,
  onSelectionChange,
  bulkActions,
  sortBy,
  sortDir = "desc",
  onSortChange,
  emptyTitle = "Nothing here yet",
  emptyMessage,
  emptyAction,
  isFiltered = false,
  onClearFilters,
  footer,
  className,
  label,
}: DataTableProps<T>) {
  const selectable = !!selectedIds && !!onSelectionChange;
  const [cursor, setCursor] = React.useState(0);
  const bodyRef = React.useRef<HTMLTableSectionElement>(null);

  const selectedCount = selectedIds?.size ?? 0;
  const allOnPageSelected =
    rows.length > 0 && rows.every((r) => selectedIds?.has(rowId(r)));
  const someOnPageSelected =
    rows.some((r) => selectedIds?.has(rowId(r))) && !allOnPageSelected;

  const toggleAll = () => {
    if (!onSelectionChange) return;
    const next = new Set(selectedIds);
    if (allOnPageSelected) {
      for (const r of rows) next.delete(rowId(r));
    } else {
      for (const r of rows) next.add(rowId(r));
    }
    onSelectionChange(next);
  };

  const toggleRow = (id: string) => {
    if (!onSelectionChange) return;
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onSelectionChange(next);
  };

  /** §6.2 — `j/k` move, `Enter` opens, `x` selects. Scoped to the table body. */
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (rows.length === 0) return;
    const key = e.key.toLowerCase();

    if (key === "j" || e.key === "ArrowDown") {
      e.preventDefault();
      setCursor(Math.min(safeCursor + 1, rows.length - 1));
    } else if (key === "k" || e.key === "ArrowUp") {
      e.preventDefault();
      setCursor(Math.max(safeCursor - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      onRowClick?.(rows[safeCursor]);
    } else if (key === "x" && selectable) {
      e.preventDefault();
      toggleRow(rowId(rows[safeCursor]));
    }
  };

  // Keep the cursor inside the data after a filter or page change. Clamped
  // during render rather than in an effect: an effect would let one frame paint
  // with an out-of-range cursor and cost a second render to correct it.
  const safeCursor = cursor >= rows.length ? 0 : cursor;

  if (error) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }

  if (isLoading) {
    return (
      <TableSkeleton
        rows={8}
        columns={Math.min(columns.length + (selectable ? 1 : 0), 8)}
        rowHeight={DENSITY_ROW_HEIGHT[density]}
        className={className}
      />
    );
  }

  const rowHeight = DENSITY_ROW_HEIGHT[density];
  const cellPad = DENSITY_CELL_PADDING[density];

  return (
    <div className={cn("flex flex-col", className)}>
      {/* §3.4 — the bulk bar replaces the toolbar contextually. */}
      {selectable && selectedCount > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-t-lg border border-b-0 border-brand-500/30 bg-brand-500/10 px-3 py-2">
          <span className="numeric text-[12.5px] font-semibold text-brand-600 dark:text-brand-500">
            {selectedCount} selected
          </span>
          <Button
            size="xs"
            variant="ghost"
            onClick={() => onSelectionChange?.(new Set())}
            leftIcon={<X className="size-3.5" />}
          >
            Clear
          </Button>
          <div className="ml-auto flex flex-wrap items-center gap-1.5">
            {bulkActions}
          </div>
        </div>
      )}

      <div
        className={cn(
          "overflow-x-auto border border-border bg-surface",
          selectable && selectedCount > 0 ? "rounded-b-lg" : "rounded-lg",
        )}
      >
        <table className="w-full border-collapse text-left">
          <caption className="sr-only">{label}</caption>

          <thead className="sticky top-0 z-10 bg-muted">
            <tr className="border-b border-border">
              {selectable && (
                <th
                  scope="col"
                  className={cn("sticky left-0 z-20 w-10 bg-muted", cellPad)}
                >
                  <Checkbox
                    aria-label={
                      allOnPageSelected ? "Clear selection" : "Select all rows"
                    }
                    checked={
                      allOnPageSelected
                        ? true
                        : someOnPageSelected
                          ? "indeterminate"
                          : false
                    }
                    onCheckedChange={toggleAll}
                  />
                </th>
              )}

              {columns.map((col) => {
                const active = sortBy === col.id;
                return (
                  <th
                    key={col.id}
                    scope="col"
                    aria-sort={
                      active
                        ? sortDir === "asc"
                          ? "ascending"
                          : "descending"
                        : undefined
                    }
                    className={cn(
                      "whitespace-nowrap py-2.5 text-[11px] font-semibold uppercase tracking-[0.06em] text-muted-foreground",
                      cellPad,
                      col.width,
                      col.numeric && "text-right",
                      col.minWidth && MIN_WIDTH_CLASS[col.minWidth],
                      col.sticky === "left" && "sticky left-10 z-20 bg-muted",
                      col.sticky === "right" && "sticky right-0 z-20 bg-muted",
                      col.className,
                    )}
                  >
                    {col.sortable && onSortChange ? (
                      <button
                        type="button"
                        onClick={() =>
                          onSortChange(
                            col.id,
                            active && sortDir === "desc" ? "asc" : "desc",
                          )
                        }
                        className={cn(
                          "inline-flex items-center gap-1 rounded transition-colors hover:text-foreground",
                          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
                          active && "text-foreground",
                          col.numeric && "flex-row-reverse",
                        )}
                      >
                        {col.header}
                        {active ? (
                          sortDir === "asc" ? (
                            <ArrowUp className="size-3" />
                          ) : (
                            <ArrowDown className="size-3" />
                          )
                        ) : (
                          <ChevronsUpDown className="size-3 opacity-40" />
                        )}
                      </button>
                    ) : (
                      col.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody
            ref={bodyRef}
            tabIndex={rows.length > 0 ? 0 : -1}
            onKeyDown={onKeyDown}
            className="focus-visible:outline-none"
          >
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (selectable ? 1 : 0)}>
                  <EmptyState
                    variant={isFiltered ? "filter" : "initial"}
                    title={
                      isFiltered
                        ? "No results match these filters"
                        : emptyTitle
                    }
                    message={
                      isFiltered
                        ? "Try widening or clearing them."
                        : emptyMessage
                    }
                    actionLabel={
                      isFiltered ? undefined : emptyAction?.label
                    }
                    onAction={isFiltered ? undefined : emptyAction?.onClick}
                    secondaryLabel={isFiltered ? "Clear filters" : undefined}
                    onSecondary={isFiltered ? onClearFilters : undefined}
                  />
                </td>
              </tr>
            ) : (
              rows.map((row, i) => {
                const id = rowId(row);
                const isSelected = selectedIds?.has(id) ?? false;
                const isHighlighted = highlightId === id;
                const isCursor = i === safeCursor;

                return (
                  <tr
                    key={id}
                    onClick={() => onRowClick?.(row)}
                    aria-selected={isSelected || undefined}
                    className={cn(
                      "border-b border-border transition-colors last:border-b-0",
                      rowHeight,
                      onRowClick && "cursor-pointer",
                      "hover:bg-surface-2",
                      isSelected && "bg-brand-500/[0.07]",
                      isHighlighted && "bg-warning/10",
                      isCursor && "ring-1 ring-inset ring-brand-500/40",
                    )}
                  >
                    {selectable && (
                      <td
                        className={cn("sticky left-0 z-10 w-10 bg-surface", cellPad)}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <Checkbox
                          aria-label={`Select row ${i + 1}`}
                          checked={isSelected}
                          onCheckedChange={() => toggleRow(id)}
                        />
                      </td>
                    )}

                    {columns.map((col) => (
                      <td
                        key={col.id}
                        className={cn(
                          "text-[13px] text-foreground",
                          cellPad,
                          col.width,
                          col.numeric && "numeric text-right",
                          col.minWidth && MIN_WIDTH_CLASS[col.minWidth],
                          col.sticky === "left" && "sticky left-10 z-10 bg-surface",
                          col.sticky === "right" && "sticky right-0 z-10 bg-surface",
                          col.className,
                        )}
                      >
                        {col.cell(row)}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {footer && (
        <div className="flex flex-wrap items-center justify-between gap-3 border border-t-0 border-border bg-surface px-3 py-2.5 text-[12.5px] text-muted-foreground rounded-b-lg">
          {footer}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * Pagination footer — §9.1.6
 * ------------------------------------------------------------------ */

export function TablePagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [10, 20, 50],
}: {
  /** Zero-based, matching the API. */
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}) {
  const from = totalElements === 0 ? 0 : page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, totalElements);

  return (
    <>
      <span className="numeric">
        {from}–{to} of {totalElements.toLocaleString()}
      </span>

      <div className="flex items-center gap-3">
        {onPageSizeChange && (
          <label className="flex items-center gap-1.5">
            <span className="sr-only">Rows per page</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="h-7 rounded-md border border-input bg-surface px-1.5 text-[12px] text-foreground focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
            >
              {pageSizeOptions.map((n) => (
                <option key={n} value={n}>
                  {n} / page
                </option>
              ))}
            </select>
          </label>
        )}

        <div className="flex items-center gap-1">
          <Button
            size="xs"
            variant="secondary"
            disabled={page <= 0}
            onClick={() => onPageChange(page - 1)}
          >
            Previous
          </Button>
          <span className="numeric px-1.5">
            {totalPages === 0 ? 0 : page + 1} / {totalPages}
          </span>
          <Button
            size="xs"
            variant="secondary"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </>
  );
}
