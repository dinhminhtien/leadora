"use client";

/**
 * Global keyboard model — Website UI Blueprint §6.1.
 *
 * | Keys         | Action                    |
 * |--------------|---------------------------|
 * | ⌘K / Ctrl+K  | Command palette           |
 * | `g` then `l` | Leads (and the rest of the `g` chords from the nav IA) |
 * | `?`          | Shortcut overlay          |
 * | `/`          | Focus the toolbar search  |
 * | `Esc`        | Close top-most overlay    |
 *
 * **Two rules that keep this from fighting the user.** First, every handler
 * bails when focus is inside a text field or a `contenteditable` — otherwise
 * typing "gl" into a note would navigate away mid-sentence. Second, the `g`
 * chord expires after 1.2s so a stray `g` doesn't silently arm a jump that
 * fires much later when the user presses an unrelated key.
 */

import * as React from "react";
import { useRouter } from "next/navigation";

import { GO_TO_SHORTCUTS } from "@/app/routes/navigation";
import { canAccessPath, type AppRole } from "@/shared/auth/access";

const CHORD_TIMEOUT_MS = 1200;

/** True when keystrokes belong to whatever the user is typing into. */
function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return (
    tag === "INPUT" ||
    tag === "TEXTAREA" ||
    tag === "SELECT" ||
    target.isContentEditable
  );
}

type Options = {
  role: AppRole;
  permissions: string[];
  onOpenPalette: () => void;
  onOpenShortcuts: () => void;
  /** Focuses the current screen's toolbar search, if it has one. */
  onFocusSearch?: () => void;
  enabled?: boolean;
};

export function useGlobalShortcuts({
  role,
  permissions,
  onOpenPalette,
  onOpenShortcuts,
  onFocusSearch,
  enabled = true,
}: Options) {
  const router = useRouter();
  const chordRef = React.useRef<{ key: string; at: number } | null>(null);

  // Keep the latest callbacks without re-binding the listener on every render.
  // Written in an effect rather than during render: a ref mutation in the render
  // body is not safe under concurrent rendering, where a render can be discarded.
  const handlers = React.useRef({ onOpenPalette, onOpenShortcuts, onFocusSearch });
  React.useEffect(() => {
    handlers.current = { onOpenPalette, onOpenShortcuts, onFocusSearch };
  }, [onOpenPalette, onOpenShortcuts, onFocusSearch]);

  React.useEffect(() => {
    if (!enabled) return;

    const onKeyDown = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;

      // ⌘K works even from inside an input — it is the universal escape hatch.
      if (mod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        handlers.current.onOpenPalette();
        return;
      }

      if (isTypingTarget(e.target)) return;
      if (mod || e.altKey) return;

      // `?` — shortcut overlay (Shift+/ on most layouts).
      if (e.key === "?") {
        e.preventDefault();
        handlers.current.onOpenShortcuts();
        return;
      }

      // `/` — focus the screen's search box.
      if (e.key === "/") {
        if (handlers.current.onFocusSearch) {
          e.preventDefault();
          handlers.current.onFocusSearch();
        }
        return;
      }

      const key = e.key.toLowerCase();
      const pending = chordRef.current;

      // Second key of a `g …` chord.
      if (pending?.key === "g" && Date.now() - pending.at < CHORD_TIMEOUT_MS) {
        chordRef.current = null;
        const href = GO_TO_SHORTCUTS[key];
        // Silently ignore a jump the user isn't allowed to make, rather than
        // navigating them into a redirect bounce.
        if (href && canAccessPath(role, href, permissions)) {
          e.preventDefault();
          router.push(href);
        }
        return;
      }

      // Arm the chord.
      if (key === "g") {
        chordRef.current = { key: "g", at: Date.now() };
        return;
      }

      chordRef.current = null;
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [enabled, role, permissions, router]);
}
