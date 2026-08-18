"use client";

/**
 * Keyboard shortcut reference — Website UI Blueprint §6.
 *
 * Opened with `?` or from Help. The blueprint documents four shortcut tiers
 * (global · list · task-module · focus/landmarks); this overlay is the single
 * place a user can discover them, which is what makes a keyboard-first product
 * learnable rather than merely fast for whoever wrote it.
 */

import * as React from "react";

import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Kbd } from "@/components/layout/command-palette";

type Shortcut = { keys: string[]; label: string };
type Section = { title: string; items: Shortcut[] };

/** Mirrors §6.1 – §6.3. */
const SECTIONS: Section[] = [
  {
    title: "Global",
    items: [
      { keys: ["⌘", "K"], label: "Open command palette" },
      { keys: ["?"], label: "Show this reference" },
      { keys: ["/"], label: "Focus the toolbar search" },
      { keys: ["Esc"], label: "Close the top-most overlay" },
    ],
  },
  {
    title: "Go to",
    items: [
      { keys: ["g", "h"], label: "Home dashboard" },
      { keys: ["g", "l"], label: "Leads" },
      { keys: ["g", "p"], label: "Pipeline board" },
      { keys: ["g", "d"], label: "Deals" },
      { keys: ["g", "t"], label: "Follow-up tasks" },
      { keys: ["g", "c"], label: "Calendar" },
      { keys: ["g", "r"], label: "Reminders" },
    ],
  },
  {
    title: "Lists",
    items: [
      { keys: ["j"], label: "Move cursor down" },
      { keys: ["k"], label: "Move cursor up" },
      { keys: ["x"], label: "Toggle row selection" },
      { keys: ["Enter"], label: "Open the focused row" },
      { keys: ["e"], label: "Edit the focused row" },
      { keys: ["a"], label: "Assign" },
      { keys: ["s"], label: "Change status" },
    ],
  },
  {
    title: "Tasks & calendar",
    items: [
      { keys: ["c"], label: "Create task" },
      { keys: ["t"], label: "Jump to today" },
      { keys: ["w"], label: "Week view" },
      { keys: ["m"], label: "Month view" },
      { keys: ["1"], label: "Set priority — Low" },
      { keys: ["2"], label: "Set priority — Medium" },
      { keys: ["3"], label: "Set priority — High" },
      { keys: ["⌘", "."], label: "Complete and open next" },
    ],
  },
];

export function ShortcutsOverlay({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>Keyboard shortcuts</DialogTitle>
          <DialogDescription>
            Leadora is keyboard-first. Shortcuts are ignored while you are typing
            in a field.
          </DialogDescription>
        </DialogHeader>
        <DialogBody>
          <div className="grid gap-x-8 gap-y-6 sm:grid-cols-2">
            {SECTIONS.map((section) => (
              <section key={section.title}>
                <h3 className="mb-2 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
                  {section.title}
                </h3>
                <ul className="space-y-1.5">
                  {section.items.map((s) => (
                    <li
                      key={s.label}
                      className="flex items-center justify-between gap-4"
                    >
                      <span className="text-[13px] text-foreground">{s.label}</span>
                      <span className="flex shrink-0 items-center gap-1">
                        {s.keys.map((k, i) => (
                          <React.Fragment key={i}>
                            {i > 0 && (
                              <span className="text-[10px] text-muted-foreground">
                                then
                              </span>
                            )}
                            <Kbd className="h-5 min-w-5 px-1.5 text-[11px]">{k}</Kbd>
                          </React.Fragment>
                        ))}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </DialogBody>
      </DialogContent>
    </Dialog>
  );
}
