"use client";

/**
 * Search-first async picker.
 *
 * The pattern it replaces: render every option the user could possibly choose into a
 * `<select>` (or a "browse all" dialog), then make them scroll. That costs a full-table
 * fetch on mount, scales badly past a few dozen rows, and gives the user no way to say
 * what they are actually looking for.
 *
 * This shows a search box first and asks the server for a page at a time. It is
 * deliberately dumb about *what* it is picking — the caller supplies [fetchPage] and the
 * row renderer — so the same component serves deals, customers, leads or anything else
 * behind a paged, searchable endpoint. Business rules live in that endpoint, never here:
 * whatever [fetchPage] returns is what the user sees, unfiltered.
 *
 * Paging runs through React Query, so re-opening the picker or retyping a term already
 * searched costs nothing, and an in-flight page that is superseded cannot land out of
 * order over the newer one.
 */

import * as React from "react";
import { keepPreviousData, useInfiniteQuery } from "@tanstack/react-query";
import { ChevronDown, Loader2, Search, X } from "lucide-react";

import { cn } from "@/lib/utils";
import { EmptyState, ErrorState } from "@/components/ui/states";

export type SearchPickerPage<T> = {
  items: T[];
  /** Whether a further page exists. Drives the infinite-scroll sentinel. */
  hasMore: boolean;
};

export type SearchPickerProps<T> = {
  value: T | null;
  onChange: (value: T | null) => void;

  /**
   * Cache namespace for this picker's results. The debounced query and page index are
   * appended automatically — pass only the stable prefix, e.g. `["deals", "quotable"]`.
   */
  queryKey: readonly unknown[];

  /**
   * Fetches one page of results. Called with the debounced, trimmed query (`""` before
   * the user types anything) and a zero-based page index.
   *
   * Must apply whatever eligibility rules the destination requires — this component
   * renders exactly what it is handed. Keep the identity stable (`useCallback`).
   */
  fetchPage: (query: string, page: number) => Promise<SearchPickerPage<T>>;

  getKey: (item: T) => string;
  renderItem: (item: T) => React.ReactNode;
  /** Collapsed-state rendering of the current selection. Defaults to [renderItem]. */
  renderSelected?: (item: T) => React.ReactNode;

  placeholder?: string;
  searchPlaceholder?: string;
  /** Shown above the results before the user has typed. */
  hintLabel?: string;
  emptyTitle?: string;
  emptyMessage?: string;
  /** Rendered under the trigger once an unsearched page has proved there is nothing to pick. */
  noOptionsHint?: React.ReactNode;

  /**
   * Enables the "Recent" row. Selections are remembered in `localStorage` under this key,
   * most recent first, capped at five. Omit to disable.
   */
  recentsKey?: string;

  /**
   * Load the first (unsearched) page on mount rather than on first open. Costs one small
   * paged request and buys the ability to warn — before the user clicks — that nothing is
   * selectable at all. Use it where an empty catalogue is a real, actionable state.
   */
  eager?: boolean;

  /** How long a fetched page stays fresh. Defaults to one minute. */
  staleTime?: number;

  disabled?: boolean;
  error?: string;
  id?: string;
  className?: string;
};

const DEBOUNCE_MS = 300;
const MAX_RECENTS = 5;

function readRecents<T>(key: string | undefined): T[] {
  if (!key || typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(`leadora.recent.${key}`);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

function writeRecents<T>(key: string | undefined, items: T[]) {
  if (!key || typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      `leadora.recent.${key}`,
      JSON.stringify(items.slice(0, MAX_RECENTS)),
    );
  } catch {
    /* private mode / quota — recents are a convenience, never a requirement */
  }
}

export function SearchPicker<T>({
  value,
  onChange,
  queryKey,
  fetchPage,
  getKey,
  renderItem,
  renderSelected,
  placeholder = "Search to select…",
  searchPlaceholder = "Type to search…",
  hintLabel,
  emptyTitle = "No matches",
  emptyMessage = "Try a different search term.",
  noOptionsHint,
  recentsKey,
  eager = false,
  staleTime = 60_000,
  disabled,
  error,
  id,
  className,
}: SearchPickerProps<T>) {
  const [open, setOpen] = React.useState(false);
  const [rawQuery, setRawQuery] = React.useState("");
  const [query, setQuery] = React.useState("");
  const [activeIndex, setActiveIndex] = React.useState(0);
  const [recents, setRecents] = React.useState<T[]>([]);

  const rootRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const listRef = React.useRef<HTMLDivElement>(null);
  const sentinelRef = React.useRef<HTMLDivElement>(null);

  const {
    data,
    isPending,
    isFetching,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
    refetch,
    error: fetchError,
  } = useInfiniteQuery({
    queryKey: [...queryKey, query],
    queryFn: ({ pageParam }) => fetchPage(query, pageParam),
    initialPageParam: 0,
    // Pages are requested in order, so the count of pages held *is* the next index.
    getNextPageParam: (last, all) => (last.hasMore ? all.length : undefined),
    enabled: open || eager,
    // Keep the previous term's rows on screen while the new ones load, so typing does
    // not strobe the panel between results and an empty state.
    placeholderData: keepPreviousData,
    staleTime,
  });

  const items = React.useMemo(() => {
    const pages = data?.pages ?? [];
    // De-duplicate across pages: a row that shifted between requests would otherwise
    // appear twice and collide on its React key.
    const seen = new Set<string>();
    const flat: T[] = [];
    for (const page of pages) {
      for (const item of page.items) {
        const key = getKey(item);
        if (seen.has(key)) continue;
        seen.add(key);
        flat.push(item);
      }
    }
    return flat;
  }, [data, getKey]);

  /**
   * Distinguishes "nothing matched this search" from "nothing exists at all", so
   * [noOptionsHint] can explain the second without ever libelling the first. An empty
   * *search* proves nothing about the catalogue; an empty *unsearched* page proves it.
   */
  const catalogueIsEmpty = query === "" && !isPending && !isFetching && items.length === 0;

  // Debounce the raw input into the query that actually hits the network.
  React.useEffect(() => {
    const t = setTimeout(() => setQuery(rawQuery.trim()), DEBOUNCE_MS);
    return () => clearTimeout(t);
  }, [rawQuery]);

  // Reset the keyboard cursor whenever the result set is replaced.
  React.useEffect(() => {
    const t = setTimeout(() => setActiveIndex(0), 0);
    return () => clearTimeout(t);
  }, [query]);

  // Close on outside click.
  React.useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [open]);

  // Infinite scroll: pull the next page when the sentinel enters the results viewport.
  React.useEffect(() => {
    if (!open || !hasNextPage) return;
    const node = sentinelRef.current;
    const root = listRef.current;
    if (!node || !root) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void fetchNextPage();
      },
      { root, rootMargin: "120px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [open, hasNextPage, fetchNextPage, items.length]);

  // Keep the keyboard-highlighted row in view.
  React.useEffect(() => {
    if (!open) return;
    listRef.current
      ?.querySelector<HTMLElement>('[data-active="true"]')
      ?.scrollIntoView({ block: "nearest" });
  }, [activeIndex, open]);

  const showRecents = open && query === "" && items.length === 0 && !isPending && recents.length > 0;
  const options = showRecents ? recents : items;

  const commit = (item: T | null) => {
    onChange(item);
    setOpen(false);
    setRawQuery("");
    setQuery("");
    if (item && recentsKey) {
      const key = getKey(item);
      const next = [item, ...recents.filter((r) => getKey(r) !== key)].slice(0, MAX_RECENTS);
      setRecents(next);
      writeRecents(recentsKey, next);
    }
  };

  const toggleOpen = () => {
    // Read recents here rather than in an effect: `localStorage` is unavailable during
    // SSR, so hydrating from it would mismatch, and reading on open is always current.
    if (!open) setRecents(readRecents<T>(recentsKey));
    setOpen((o) => !o);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      setOpen(false);
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, options.length - 1));
      return;
    }
    if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      const item = options[activeIndex];
      if (item) commit(item);
    }
  };

  const renderCollapsed = renderSelected ?? renderItem;

  return (
    <div className={cn("relative w-full", className)} ref={rootRef}>
      <button
        id={id}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={toggleOpen}
        className={cn(
          "flex min-h-10.5 w-full items-center justify-between gap-2 rounded-xl border bg-input px-3 py-1.5 text-left text-sm text-foreground shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] transition",
          "focus:border-primary focus:ring-2 focus:ring-primary/10 focus:outline-none",
          "disabled:cursor-not-allowed disabled:opacity-50 dark:shadow-none",
          error ? "border-danger" : "border-border",
        )}
      >
        <span className="min-w-0 flex-1">
          {value ? (
            renderCollapsed(value)
          ) : (
            <span className="text-xs text-muted-foreground">{placeholder}</span>
          )}
        </span>
        <span className="flex shrink-0 items-center gap-1">
          {value && !disabled && (
            <span
              role="button"
              tabIndex={0}
              aria-label="Clear selection"
              onClick={(e) => {
                e.stopPropagation();
                commit(null);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  e.stopPropagation();
                  commit(null);
                }
              }}
              className="rounded p-0.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
            >
              <X className="size-3.5" />
            </span>
          )}
          <ChevronDown
            className={cn("size-4 text-muted-foreground transition-transform", open && "rotate-180")}
          />
        </span>
      </button>

      {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      {!error && catalogueIsEmpty && noOptionsHint}

      {open && (
        <div className="absolute top-full left-0 z-50 mt-1.5 w-full overflow-hidden rounded-xl border border-border bg-surface shadow-xl animate-in fade-in slide-in-from-top-1 duration-100">
          <div className="border-b border-border p-2">
            <div className="relative">
              <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
              <input
                ref={inputRef}
                type="text"
                value={rawQuery}
                onChange={(e) => setRawQuery(e.target.value)}
                onKeyDown={onKeyDown}
                placeholder={searchPlaceholder}
                className="w-full rounded-lg border border-border bg-input py-1.5 pr-8 pl-8 text-xs text-foreground transition focus:border-primary focus:ring-1 focus:ring-primary/10 focus:outline-none"
              />
              {rawQuery && (
                <button
                  type="button"
                  aria-label="Clear search"
                  onClick={() => {
                    setRawQuery("");
                    inputRef.current?.focus();
                  }}
                  className="absolute top-1/2 right-2 -translate-y-1/2 rounded p-0.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
                >
                  <X className="size-3.5" />
                </button>
              )}
            </div>
          </div>

          <div ref={listRef} role="listbox" className="max-h-64 overflow-y-auto">
            {showRecents && (
              <p className="px-3 pt-2 pb-1 text-[10px] font-bold tracking-wider text-muted-foreground uppercase">
                Recent
              </p>
            )}
            {!showRecents && hintLabel && query === "" && items.length > 0 && (
              <p className="px-3 pt-2 pb-1 text-[10px] font-bold tracking-wider text-muted-foreground uppercase">
                {hintLabel}
              </p>
            )}

            {isPending ? (
              <div className="flex items-center justify-center gap-2 py-8 text-xs text-muted-foreground">
                <Loader2 className="size-4 animate-spin text-primary" />
                Searching…
              </div>
            ) : fetchError ? (
              // Say what actually went wrong. A generic "search failed" hides the
              // difference between an expired session, a missing endpoint and a
              // server fault — all of which need a different response from the user.
              <ErrorState
                error={fetchError}
                onRetry={() => void refetch()}
                className="border-0 bg-transparent px-3 py-6"
              />
            ) : options.length === 0 ? (
              <EmptyState
                dense
                variant={query ? "filter" : "initial"}
                title={emptyTitle}
                message={query ? emptyMessage : undefined}
              />
            ) : (
              <>
                {options.map((item, index) => {
                  const key = getKey(item);
                  const selected = value != null && getKey(value) === key;
                  return (
                    <button
                      key={key}
                      type="button"
                      role="option"
                      aria-selected={selected}
                      data-active={index === activeIndex}
                      onMouseEnter={() => setActiveIndex(index)}
                      onClick={() => commit(item)}
                      className={cn(
                        "w-full border-l-2 border-transparent px-3 py-2 text-left transition",
                        index === activeIndex && "bg-muted/60",
                        selected && "border-primary bg-primary/5",
                      )}
                    >
                      {renderItem(item)}
                    </button>
                  );
                })}
                {hasNextPage && (
                  <div ref={sentinelRef} className="flex items-center justify-center py-3">
                    {isFetchingNextPage ? (
                      <Loader2 className="size-4 animate-spin text-primary" />
                    ) : (
                      <button
                        type="button"
                        onClick={() => void fetchNextPage()}
                        className="text-[11px] font-semibold text-primary hover:underline"
                      >
                        Load more
                      </button>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
