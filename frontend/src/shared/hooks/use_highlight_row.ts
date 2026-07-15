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
  const [prevTargetId, setPrevTargetId] = useState(targetId);
  const scrolledRef = useRef(false);

  // Re-sync whenever the URL's highlight target changes — e.g. clicking a second
  // notification for a different record while already on this page. The route
  // doesn't remount in that case, so the useState initializer above only fires
  // once. Adjusting state during render (React's documented pattern for "reset
  // state when a prop changes") picks up the new target immediately, without
  // the extra render an effect-based reset would cause.
  if (targetId !== prevTargetId) {
    setPrevTargetId(targetId);
    setHighlightedId(targetId);
  }

  // Refs must only be touched outside render — reset the one-time-scroll guard
  // here (in an effect) whenever the target changes, instead of during render.
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