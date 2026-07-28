"use client";

/**
 * Confirmation dialog — Website UI Blueprint §3.16 and §12.
 *
 * > "Zero surprise on write actions. Every destructive or state-changing action
 * >  confirms with an in-design modal (**never `window.confirm`**)." (§1.5)
 *
 * The codebase currently calls native `window.confirm` / `confirm` in the
 * booking and payment screens. Those dialogs cannot be themed, cannot be
 * translated, cannot carry a required reason field, and look like a browser
 * error. This component replaces them without changing what the actions do.
 *
 * Copy rules from §3.16 and §12:
 * - Title is a **question** starting with a verb — "Delete lead L-4821?"
 * - Body explains the consequence, not the mechanic.
 * - Destructive primary sits right, cancel left of it.
 * - Where the backend requires a reason (Reject / Cancel / Set-Lost / Set-Paid),
 *   a **required** textarea with a character counter appears between body and
 *   footer. That requirement is a server rule; this only surfaces it early.
 */

import * as React from "react";
import { AlertTriangle, Info, ShieldAlert } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export type ConfirmSeverity = "info" | "warning" | "danger";

const severityMeta: Record<
  ConfirmSeverity,
  { Icon: React.ComponentType<{ className?: string }>; ring: string; text: string }
> = {
  info: { Icon: Info, ring: "bg-brand-500/10", text: "text-brand-600 dark:text-brand-500" },
  warning: { Icon: AlertTriangle, ring: "bg-warning/12", text: "text-warning" },
  danger: { Icon: ShieldAlert, ring: "bg-danger/10", text: "text-danger" },
};

export type ConfirmDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Phrase as a question, verb first. */
  title: string;
  /** Explain the consequence of proceeding. */
  description?: React.ReactNode;
  severity?: ConfirmSeverity;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Receives the reason when `requireReason` is set. */
  onConfirm: (reason: string) => void | Promise<void>;
  isLoading?: boolean;
  /** Server-side rule mirror — blocks confirm until a reason is typed. */
  requireReason?: boolean;
  reasonLabel?: string;
  reasonPlaceholder?: string;
  reasonMaxLength?: number;
  /** Extra content between the description and the reason field. */
  children?: React.ReactNode;
  /** Surfaced when the confirm call fails, so the dialog stays open with context. */
  error?: string | null;
};

export function ConfirmDialog({
  open,
  onOpenChange,
  ...rest
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/*
        The body lives in its own component so Radix unmounts it on close and its
        state resets naturally. Resetting in an effect instead would fire a
        cascading render every time the dialog opens.
      */}
      <ConfirmDialogBody onOpenChange={onOpenChange} {...rest} />
    </Dialog>
  );
}

function ConfirmDialogBody({
  onOpenChange,
  title,
  description,
  severity = "warning",
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  onConfirm,
  isLoading = false,
  requireReason = false,
  reasonLabel = "Reason",
  reasonPlaceholder = "Explain why…",
  reasonMaxLength = 500,
  children,
  error,
}: Omit<ConfirmDialogProps, "open">) {
  const [reason, setReason] = React.useState("");
  const [touched, setTouched] = React.useState(false);
  const { Icon, ring, text } = severityMeta[severity];

  const reasonMissing = requireReason && reason.trim().length === 0;
  const showReasonError = touched && reasonMissing;

  const handleConfirm = async () => {
    if (reasonMissing) {
      setTouched(true);
      return;
    }
    await onConfirm(reason.trim());
  };

  return (
    <>
      <DialogContent
        size="sm"
        // While a mutation is in flight the decision is already made — don't let
        // a stray Esc leave the user unsure whether it went through.
        dismissible={!isLoading}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
            e.preventDefault();
            void handleConfirm();
          }
        }}
      >
        <DialogHeader>
          <div className="flex items-start gap-3">
            <span
              aria-hidden
              className={cn("mt-0.5 grid size-9 shrink-0 place-items-center rounded-full", ring)}
            >
              <Icon className={cn("size-5", text)} />
            </span>
            <div className="min-w-0 space-y-1">
              <DialogTitle className="text-[17px] leading-6">{title}</DialogTitle>
              {description && <DialogDescription>{description}</DialogDescription>}
            </div>
          </div>
        </DialogHeader>

        {(children || requireReason || error) && (
          <DialogBody className="space-y-4 py-4">
            {children}

            {requireReason && (
              <div className="space-y-1.5">
                <label
                  htmlFor="confirm-reason"
                  className="flex items-center justify-between text-[12px] font-medium text-foreground"
                >
                  <span>
                    {reasonLabel} <span className="text-danger">*</span>
                  </span>
                  <span className="numeric text-[11px] font-normal text-muted-foreground">
                    {reason.length}/{reasonMaxLength}
                  </span>
                </label>
                <textarea
                  id="confirm-reason"
                  rows={3}
                  value={reason}
                  maxLength={reasonMaxLength}
                  onChange={(e) => setReason(e.target.value)}
                  onBlur={() => setTouched(true)}
                  placeholder={reasonPlaceholder}
                  aria-required="true"
                  aria-invalid={showReasonError || undefined}
                  aria-describedby={showReasonError ? "confirm-reason-error" : undefined}
                  className={cn(
                    "w-full resize-y rounded-md border bg-surface px-3 py-2 text-[13px] text-foreground",
                    "placeholder:text-muted-foreground/70",
                    "focus:outline-none focus:ring-2",
                    showReasonError
                      ? "border-danger focus:border-danger focus:ring-danger/30"
                      : "border-input focus:border-brand-500 focus:ring-brand-500/30",
                  )}
                />
                {showReasonError && (
                  <p
                    id="confirm-reason-error"
                    className="flex items-center gap-1 text-[12px] text-danger"
                  >
                    <AlertTriangle className="size-3" />
                    A reason is required.
                  </p>
                )}
              </div>
            )}

            {error && (
              <p
                role="alert"
                className="rounded-md border border-danger/40 bg-danger-bg/50 px-3 py-2 text-[12.5px] text-danger"
              >
                {error}
              </p>
            )}
          </DialogBody>
        )}

        <DialogFooter>
          <Button
            variant="ghost"
            size="md"
            onClick={() => onOpenChange(false)}
            disabled={isLoading}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={severity === "danger" ? "destructive" : "primary"}
            size="md"
            onClick={handleConfirm}
            isLoading={isLoading}
            disabled={reasonMissing && touched}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </>
  );
}

/**
 * Imperative helper for the many `if (!confirm(...)) return;` call sites.
 *
 * Usage:
 * ```tsx
 * const confirm = useConfirm();
 * // …
 * const ok = await confirm({ title: "Approve this booking?", severity: "info" });
 * if (!ok) return;
 * ```
 * Returns the typed reason alongside the decision when `requireReason` is set.
 */
type ConfirmRequest = Omit<
  ConfirmDialogProps,
  "open" | "onOpenChange" | "onConfirm" | "isLoading" | "error"
>;

export function useConfirm() {
  const [request, setRequest] = React.useState<ConfirmRequest | null>(null);
  const resolverRef = React.useRef<((v: { ok: boolean; reason: string }) => void) | null>(
    null,
  );

  const confirm = React.useCallback((req: ConfirmRequest) => {
    setRequest(req);
    return new Promise<{ ok: boolean; reason: string }>((resolve) => {
      resolverRef.current = resolve;
    });
  }, []);

  const settle = React.useCallback((ok: boolean, reason = "") => {
    resolverRef.current?.({ ok, reason });
    resolverRef.current = null;
    setRequest(null);
  }, []);

  const element = request ? (
    <ConfirmDialog
      {...request}
      open
      onOpenChange={(next) => {
        if (!next) settle(false);
      }}
      onConfirm={(reason) => settle(true, reason)}
    />
  ) : null;

  return { confirm, confirmElement: element };
}
