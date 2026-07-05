"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";

const HIGHLIGHT_DURATION_MS = 4000;

/**
 * Reads a row id from the `highlight` query param (set when navigating in
 * from a notification), scrolls that row into view once, and clears the
 * highlight after a few seconds so it doesn't linger indefinitely.
 */
export function useHighlightRow(paramName = "highlight") {
  const searchParams = useSearchParams();
  const targetId = searchParams.get(paramName);
  const [highlightedId, setHighlightedId] = useState<string | null>(targetId);
  const scrolledRef = useRef(false);

  useEffect(() => {
    if (!highlightedId) return;
    const timer = setTimeout(() => setHighlightedId(null), HIGHLIGHT_DURATION_MS);
    return () => clearTimeout(timer);
  }, [highlightedId]);

  const setRowRef = (id: string) => (el: HTMLElement | null) => {
    if (el && id === targetId && !scrolledRef.current) {
      scrolledRef.current = true;
      el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  };

  return { highlightedId, setRowRef };
}