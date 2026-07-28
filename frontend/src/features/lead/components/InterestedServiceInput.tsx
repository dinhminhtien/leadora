"use client";

import React, { useId } from "react";
import { useQuery } from "@tanstack/react-query";
import { productService } from "@/services/product_service";

/**
 * Interested Service, with the hotel's actual services offered as suggestions.
 *
 * <p><b>Why a datalist and not a select.</b> {@code leads.interested_service} is free text
 * (VARCHAR(100)) by design — an enquiry can be for something not in the catalogue yet, and refusing
 * to record it would push the sales rep to type it into Notes where nothing can filter on it. A
 * {@code <datalist>} keeps typing open while making the catalogue the path of least resistance.
 *
 * <p><b>Why it was needed.</b> Left as a plain text box, the column filled up with `room`, `Room`,
 * `Rooms`, `rooms` and one `aaaaa` — four spellings of one service, so every count grouped by it
 * was wrong and no filter could find them all. Suggestions do not forbid the fifth spelling, but
 * they make the first four stop happening.
 *
 * <p>Falls back silently to a plain input if the catalogue cannot be loaded: a suggestion list is
 * an aid, and losing it must not block recording an enquiry.
 */

/** Used when the catalogue is empty or unreachable, so the field is never left with no guidance. */
const FALLBACK_SUGGESTIONS = [
  "Rooms", "Wedding banquet", "Conference", "Event space", "Catering", "Airport transfer",
];

export function useServiceSuggestions(): string[] {
  const { data } = useQuery({
    queryKey: ["product-services", "suggestions"],
    queryFn: () => productService.getList(),
    // The catalogue changes rarely; this list is opened on every lead form.
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const names = (data?.data ?? [])
    .filter(s => s.status === "ACTIVE")
    .map(s => s.name.trim())
    .filter(Boolean);

  // De-duplicate case-insensitively — the point of the control is to stop near-identical entries.
  const seen = new Set<string>();
  const unique = names.filter(n => {
    const key = n.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return unique.length > 0 ? unique : FALLBACK_SUGGESTIONS;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  className?: string;
  id?: string;
}

export function InterestedServiceInput({ value, onChange, className, id }: Props) {
  const suggestions = useServiceSuggestions();
  // useId, not a constant: two of these can be on screen at once (list drawer + detail drawer),
  // and a duplicated list id would point both inputs at whichever datalist rendered first.
  const listId = useId();

  return (
    <>
      <input
        id={id}
        list={listId}
        value={value}
        maxLength={100}
        placeholder="e.g. Rooms, Wedding banquet, Conference…"
        onChange={e => onChange(e.target.value)}
        className={className}
        autoComplete="off"
      />
      <datalist id={listId}>
        {suggestions.map(s => <option key={s} value={s} />)}
      </datalist>
    </>
  );
}
