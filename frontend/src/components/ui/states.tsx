"use client";

/**
 * Canonical UI states — Website UI Blueprint §3.11 – §3.15 and the §11 catalogue.
 *
 * Every list, board, calendar and detail surface in the app renders its empty,
 * error and permission states from here. Before the redesign each screen
 * improvised: some showed a bare "No results found" row, some a spinner that
 * never resolved, some a raw axios string. §11 fixes one component and one copy
 * pattern per state, which is what this file encodes.
 */

import * as React from "react";
import {
  AlertTriangle,
  Inbox,
  Lock,
  RefreshCw,
  SearchX,
  ServerCrash,
  WifiOff,
  CheckCircle2,
} from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import { resolveApiError } from "@/shared/design/error-messages";

/* ------------------------------------------------------------------ *
 * §3.11 Empty state
 * ------------------------------------------------------------------ */

type EmptyStateProps = {
  /** `initial` = nothing ever created. `filter` = filters excluded everything. */
  variant?: "initial" | "filter";
  icon?: React.ComponentType<{ className?: string }>;
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
  secondaryLabel?: string;
  onSecondary?: () => void;
  className?: string;
  /** Compact form for rails and small cards. */
  dense?: boolean;
};

export function EmptyState({
  variant = "initial",
  icon,
  title,
  message,
  actionLabel,
  onAction,
  secondaryLabel,
  onSecondary,
  className,
  dense = false,
}: EmptyStateProps) {
  const Icon = icon ?? (variant === "filter" ? SearchX : Inbox);
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center",
        dense ? "gap-2 px-4 py-8" : "gap-3 px-6 py-14",
        className,
      )}
    >
      <span
        aria-hidden
        className={cn(
          "grid place-items-center rounded-full bg-muted text-muted-foreground",
          dense ? "size-10" : "size-16",
        )}
      >
        <Icon className={dense ? "size-5" : "size-7"} />
      </span>
      <div className="space-y-1">
        <p
          className={cn(
            "font-semibold text-foreground",
            dense ? "text-[13px]" : "text-[16px] leading-6",
          )}
        >
          {title}
        </p>
        {message && (
          <p className="mx-auto max-w-sm text-[13px] leading-[18px] text-muted-foreground">
            {message}
          </p>
        )}
      </div>
      {(actionLabel || secondaryLabel) && (
        <div className="mt-1 flex flex-wrap items-center justify-center gap-2">
          {actionLabel && onAction && (
            <Button size="sm" variant="primary" onClick={onAction}>
              {actionLabel}
            </Button>
          )}
          {secondaryLabel && onSecondary && (
            <Button size="sm" variant="ghost" onClick={onSecondary}>
              {secondaryLabel}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * §3.13 Error state
 * ------------------------------------------------------------------ */

type ErrorStateProps = {
  error: unknown;
  onRetry?: () => void;
  /** `section` sits inside a card/widget; `page` fills the route body. */
  variant?: "section" | "page";
  className?: string;
};

/**
 * Returns the rendered glyph rather than a component reference. Handing back a
 * component and then calling it as `<Icon />` reads to React's lint rules as
 * "component created during render"; returning an element sidesteps that
 * without an escape hatch.
 */
function errorGlyph(status?: number, code?: string): React.ReactNode {
  const cls = "size-6";
  if (!status && !code) return <WifiOff className={cls} />;
  if (status === 403 || code === "ACCESS_DENIED") return <Lock className={cls} />;
  if (status === 404 || code === "RESOURCE_NOT_FOUND") return <SearchX className={cls} />;
  if (status && status >= 500) return <ServerCrash className={cls} />;
  return <AlertTriangle className={cls} />;
}

export function ErrorState({
  error,
  onRetry,
  variant = "section",
  className,
}: ErrorStateProps) {
  const resolved = resolveApiError(error);
  const glyph = errorGlyph(resolved.status, resolved.errorCode);
  const [copied, setCopied] = React.useState(false);

  // The reference id is what makes a 500 actionable — it ties the user's report
  // to the stack trace the server logged (spec §2.2).
  const reference =
    resolved.status && resolved.status >= 500 ? resolved.serverMessage : undefined;

  const copyReference = async () => {
    if (!resolved.errorCode && !reference) return;
    try {
      await navigator.clipboard.writeText(
        [resolved.errorCode, reference].filter(Boolean).join(" · "),
      );
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard unavailable — the text is on screen anyway */
    }
  };

  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center gap-3 rounded-lg border border-danger/40 bg-danger-bg/40 text-center",
        variant === "page" ? "px-6 py-16" : "px-5 py-10",
        className,
      )}
    >
      <span
        aria-hidden
        className="grid size-12 place-items-center rounded-full bg-danger/10 text-danger"
      >
        {glyph}
      </span>
      <div className="space-y-1">
        <p className="text-[15px] font-semibold text-foreground">
          {resolved.title}
        </p>
        <p className="mx-auto max-w-md text-[13px] leading-[18px] text-muted-foreground">
          {resolved.message}
        </p>
        {resolved.hint && (
          <p className="mx-auto max-w-md text-[12px] text-muted-foreground/80">
            {resolved.hint}
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center justify-center gap-2">
        {/* Retry only where retrying could plausibly work (§3.13). */}
        {onRetry && resolved.retriable && (
          <Button
            size="sm"
            variant="secondary"
            onClick={onRetry}
            leftIcon={<RefreshCw className="size-3.5" />}
          >
            Try again
          </Button>
        )}
        {(resolved.errorCode || reference) && (
          <Button size="sm" variant="ghost" onClick={copyReference}>
            {copied ? "Copied" : "Copy reference"}
          </Button>
        )}
      </div>
      {resolved.errorCode && (
        <code className="numeric rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
          {resolved.errorCode}
        </code>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * §3.14 Permission-denied state
 * ------------------------------------------------------------------ */

export function PermissionDenied({
  permission,
  title = "You do not have access to this area",
  message,
  className,
  onBack,
}: {
  /** The permission code to name, e.g. `LEAD_VIEW`. */
  permission?: string;
  title?: string;
  message?: string;
  className?: string;
  onBack?: () => void;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-lg border border-border bg-surface px-6 py-16 text-center",
        className,
      )}
    >
      <span
        aria-hidden
        className="grid size-14 place-items-center rounded-full bg-muted text-muted-foreground"
      >
        <Lock className="size-6" />
      </span>
      <div className="space-y-1">
        <p className="text-[16px] font-semibold text-foreground">{title}</p>
        <p className="mx-auto max-w-sm text-[13px] leading-[18px] text-muted-foreground">
          {message ??
            (permission ? (
              <>
                Ask an administrator to grant{" "}
                <code className="rounded bg-muted px-1 py-0.5 text-[12px] font-semibold text-foreground">
                  {permission}
                </code>
                .
              </>
            ) : (
              "Ask an administrator to grant you access to this area."
            ))}
        </p>
      </div>
      {onBack && (
        <Button size="sm" variant="secondary" onClick={onBack}>
          Go back
        </Button>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ *
 * §3.15 Success state — for multi-step / high-consequence flows only
 * ------------------------------------------------------------------ */

export function SuccessState({
  title,
  recap,
  primaryLabel,
  onPrimary,
  secondaryLabel,
  onSecondary,
  className,
}: {
  title: string;
  recap?: string;
  primaryLabel?: string;
  onPrimary?: () => void;
  secondaryLabel?: string;
  onSecondary?: () => void;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 px-6 py-12 text-center",
        className,
      )}
    >
      <span
        aria-hidden
        className="grid size-14 place-items-center rounded-full bg-success/10 text-success"
      >
        <CheckCircle2 className="size-7" />
      </span>
      <div className="space-y-1">
        <p className="text-[18px] font-semibold text-foreground">{title}</p>
        {recap && (
          <p className="mx-auto max-w-sm text-[13px] leading-[18px] text-muted-foreground">
            {recap}
          </p>
        )}
      </div>
      <div className="mt-1 flex flex-wrap items-center justify-center gap-2">
        {primaryLabel && onPrimary && (
          <Button size="sm" variant="primary" onClick={onPrimary}>
            {primaryLabel}
          </Button>
        )}
        {secondaryLabel && onSecondary && (
          <Button size="sm" variant="secondary" onClick={onSecondary}>
            {secondaryLabel}
          </Button>
        )}
      </div>
    </div>
  );
}
