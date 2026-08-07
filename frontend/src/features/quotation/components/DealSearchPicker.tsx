"use client";

/**
 * Deal picker for the quotation form (UC-14.1).
 *
 * Replaces the old "every deal in one `<select>`" control. Behaviour that matters:
 * the option set is **not** decided here. `GET /deals/quotable` applies the eligibility
 * rule — the deal is still active, so WON and LOST never come back — plus the caller's
 * owner scoping, and this component renders whatever it is handed. Nothing is filtered
 * client-side, so a closed deal can never be offered and an active one can never be
 * hidden by a stale mirror of the rule.
 */

import { AlertCircle, Briefcase } from "lucide-react";
import Link from "next/link";

import { SearchPicker, type SearchPickerPage } from "@/components/ui/search-picker";
import { ROUTE_PATHS } from "@/app/routes/route_paths";
import { pageMeta } from "@/services/api_client";
import { dealService, type Deal } from "@/services/deal_service";

/** Page size for the picker. Small enough to feel instant, big enough to fill the panel. */
const PAGE_SIZE = 20;

/** Cache namespace; the picker appends the search term and page index. */
const QUERY_KEY = ["deals", "quotable"] as const;

/**
 * The request's `AbortSignal` is deliberately **not** forwarded to axios.
 *
 * React Query aborts a query's signal as soon as it is superseded — which happens on
 * every debounced keystroke. Axios reports that as a `CanceledError`, and React Query
 * does not recognise its own cancellation coming back in that shape, so it lands the
 * query in the error state: typing a character made the panel show "search failed" for a
 * request that was merely replaced. These pages are small and cached, so letting a
 * superseded one finish is cheaper than the bug.
 */
async function fetchQuotableDeals(
  search: string,
  page: number,
): Promise<SearchPickerPage<Deal>> {
  const response = await dealService.getQuotable({
    search: search || undefined,
    page,
    size: PAGE_SIZE,
  });
  return {
    items: response.data?.content ?? [],
    hasMore: !pageMeta(response.data).last,
  };
}

export function DealSearchPicker({
  value,
  onChange,
  error,
  id,
}: {
  value: Deal | null;
  onChange: (deal: Deal | null) => void;
  error?: string;
  id?: string;
}) {
  return (
    <SearchPicker<Deal>
      id={id}
      value={value}
      onChange={onChange}
      queryKey={QUERY_KEY}
      fetchPage={fetchQuotableDeals}
      getKey={(deal) => deal.id}
      // One request up front so an empty pipeline is announced before the rep clicks in.
      eager
      recentsKey="quotable-deals"
      placeholder="Search deals by name, customer or company…"
      searchPlaceholder="Search deals by name, customer or company…"
      hintLabel="Closing soonest"
      error={error}
      emptyTitle="No matching deal"
      emptyMessage="Only active deals can be quoted — won and lost deals are closed."
      noOptionsHint={
        <p className="dark:text-warning mt-1.5 flex items-start gap-1.5 text-xs text-amber-700">
          <AlertCircle className="mt-0.5 size-3.5 shrink-0" />
          <span>
            No deal is ready to quote. A deal must still be active — won and lost deals
            are closed.{" "}
            <Link
              href={ROUTE_PATHS.deals}
              className="font-semibold underline underline-offset-2"
            >
              Open Deals
            </Link>{" "}
            to create one.
          </span>
        </p>
      }
      renderSelected={(deal) => (
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-xs leading-tight font-semibold text-foreground">
            {deal.title}
          </span>
          <span className="truncate text-[10px] leading-none text-muted-foreground">
            {[deal.contactName, deal.stage].filter(Boolean).join(" · ")}
          </span>
        </span>
      )}
      renderItem={(deal) => (
        <span className="flex items-start gap-2">
          <Briefcase className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
          <span className="flex min-w-0 flex-col">
            <span className="truncate text-xs font-semibold text-foreground">
              {deal.title}
            </span>
            {/* Title alone is ambiguous when a customer has several deals — the
                contact and stage are what tell them apart. */}
            <span className="truncate text-[10px] text-muted-foreground">
              {[deal.contactName, deal.stage, deal.expectedClose ? `closes ${deal.expectedClose}` : null]
                .filter(Boolean)
                .join(" · ")}
            </span>
          </span>
        </span>
      )}
    />
  );
}
