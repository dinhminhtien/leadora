"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";

const HIGHLIGHT_DURATION_MS = 4000;

/**
 * Reads a row id from the `highlight` query param (set when navigating in
 * from a notification), scrolls that row into view smoothly once, and highlights
 * the row consistently across screens.
 */
export function useHighlightRow(primaryParam = "highlight", secondaryParam?: string) {
  const searchParams = useSearchParams();
  const targetId =
    searchParams.get(primaryParam) ||
    (secondaryParam ? searchParams.get(secondaryParam) : null);

  const [highlightedId, setHighlightedId] = useState<string | null>(targetId);
  const [prevTargetId, setPrevTargetId] = useState(targetId);
  const scrolledRef = useRef(false);

  if (targetId !== prevTargetId) {
    setPrevTargetId(targetId);
    setHighlightedId(targetId);
  }

  useEffect(() => {
    scrolledRef.current = false;
  }, [targetId]);

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