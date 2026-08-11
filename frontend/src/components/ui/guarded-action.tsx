"use client";

/**
 * Actions that explain themselves when unavailable.
 *
 * A disabled button with no explanation is the single most common complaint
 * about rule-heavy CRMs: the user sees "Convert" greyed out and has no way to
 * learn that the lead must be Qualified first. The blueprint's §13 interaction
 * matrix and the BR set both require the *reason* to be reachable, so this
 * component makes the reason a required argument — you cannot disable an action
 * through it without saying why.
 *
 * Why not simply hide the button? Hiding teaches nothing and makes the product
 * feel inconsistent between roles ("my colleague has a Convert button"). §14's
 * role matrix draws the line: **hide** what a role may never do, **disable with
 * a reason** what this record's current state forbids. `PermissionDenied` in
 * `states.tsx` covers the whole-screen case.
 *
 * Usage:
 *
 * ```tsx
 * <GuardedButton
 *   reason={lead.status !== "QUALIFIED" ? "Lead must be Qualified before it can be converted." : null}
 *   onClick={convert}
 * >
 *   Convert
 * </GuardedButton>
 * ```
 *
 * `reason={null}` (or omitted) means enabled. Any string disables and explains.
 */

import * as React from "react";
import { Info, Lock } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button, type ButtonProps } from "@/components/ui/Button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

/**
 * Wraps any trigger so a blocked action still announces its reason.
 *
 * The tooltip sits on a focusable `span` rather than on the disabled control:
 * a `disabled` element fires no pointer or focus events in any browser, so a
 * tooltip attached directly to it is unreachable by mouse *and* by keyboard.
 * The span also carries the reason as accessible text, so screen readers get it
 * without hovering (§11 / blueprint §6.4).
 */
export function GuardedAction({
  reason,
  children,
  className,
}: {
  /** `null` = allowed. A string = blocked, and this is the explanation. */
  reason?: string | null;
  children: React.ReactNode;
  className?: string;
}) {
  if (!reason) return <>{children}</>;

  return (
    <TooltipProvider delayDuration={150}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            tabIndex={0}
            role="note"
            aria-label={`Unavailable: ${reason}`}
            className={cn(
              "inline-flex cursor-not-allowed rounded-md",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              className,
            )}
          >
            {/* `pointer-events-none` lets the hover land on the span above. */}
            <span className="pointer-events-none contents">{children}</span>
          </span>
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-xs">
          <span className="flex items-start gap-1.5">
            <Lock aria-hidden className="mt-px size-3 shrink-0" />
            <span>{reason}</span>
          </span>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export type GuardedButtonProps = Omit<ButtonProps, "disabled"> & {
  /** `null`/omitted = enabled. A string disables the button and explains why. */
  reason?: string | null;
  /** Disable without a reason — loading/in-flight only, never a rule. */
  busy?: boolean;
};

/**
 * `Button` + `GuardedAction`. The `disabled` prop is deliberately removed from
 * the type so a rule cannot be expressed as a bare `disabled` — it has to go
 * through `reason`, which forces the explanation to exist.
 */
export function GuardedButton({
  reason,
  busy = false,
  children,
  ...buttonProps
}: GuardedButtonProps) {
  return (
    <GuardedAction reason={reason}>
      <Button {...buttonProps} disabled={!!reason || busy}>
        {children}
      </Button>
    </GuardedAction>
  );
}

/**
 * Inline explanation for cases where a tooltip is too easy to miss — a locked
 * record's edit form, or a footer where several rules are blocking at once.
 * §12 asks that a blocked *state* be visible without interaction.
 */
export function BlockedHint({
  reason,
  className,
  tone = "muted",
}: {
  reason: React.ReactNode;
  className?: string;
  tone?: "muted" | "warning";
}) {
  return (
    <p
      role="note"
      className={cn(
        "flex items-start gap-1.5 rounded-md border px-2.5 py-1.5 text-[12px] leading-[17px]",
        tone === "warning"
          ? "border-warning/30 bg-warning/10 text-warning"
          : "border-border bg-muted text-muted-foreground",
        className,
      )}
    >
      <Info aria-hidden className="mt-px size-3.5 shrink-0" />
      <span>{reason}</span>
    </p>
  );
}
