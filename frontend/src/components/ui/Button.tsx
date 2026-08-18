"use client";

/**
 * Button — Website UI Blueprint §3.1 and §13 (interaction states).
 *
 * **What changed in the redesign.** The previous implementation hard-coded ~30
 * hex literals (`bg-[#185FA5]`, `dark:bg-[#378ADD]`, …). §2.1 forbids that:
 * *"Never hard-code hex values in components."* Every colour below now comes
 * from a semantic token, so retheming happens in `globals.css` alone and dark
 * mode stops needing a per-variant override list.
 *
 * **Backward compatibility is deliberate.** `danger`, `success`, `warning` and
 * `outline` are not in the blueprint's six-variant list, but 40 files use them.
 * Removing them would be a behavioural change to screens this redesign is not
 * touching yet, so they are kept as *tonal* variants and re-expressed in tokens.
 * The blueprint's `destructive` and `link` are added alongside.
 *
 * States per §13: hover fill −10%, active fill −18%, focus `--focus-ring`,
 * disabled opacity .45 + `not-allowed`, loading swaps the leading icon for a
 * spinner while the label stays put and clicks are blocked.
 */

import React from "react";

import { cn } from "@/lib/utils";

export type ButtonVariant =
  // Blueprint §3.1
  | "primary"
  | "secondary"
  | "outline"
  | "ghost"
  | "destructive"
  | "link"
  // Retained tonal variants (pre-existing call sites)
  | "danger"
  | "success"
  | "warning";

export type ButtonSize = "xs" | "sm" | "md" | "lg" | "icon" | "icon-sm";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  /** Stretches to the container width — common in dialog footers on mobile. */
  fullWidth?: boolean;
}

const variantStyles: Record<ButtonVariant, string> = {
  // Solid brand — Create, Submit, Save.
  primary: cn(
    "bg-brand-500 text-brand-foreground border border-brand-600",
    "hover:bg-brand-600 active:bg-brand-700",
  ),

  // Neutral surface — Edit, View, Cancel-adjacent.
  secondary: cn(
    "bg-surface text-foreground border border-border",
    "hover:bg-surface-2 active:bg-surface-3",
  ),

  // Outline on the page background.
  outline: cn(
    "bg-transparent text-foreground border border-border",
    "hover:bg-surface-2 active:bg-surface-3",
  ),

  // Lowest emphasis — icon buttons, toolbar affordances.
  ghost: cn(
    "bg-transparent text-muted-foreground border border-transparent",
    "hover:bg-surface-2 hover:text-foreground active:bg-surface-3",
  ),

  // Solid destructive — the confirm button in a §3.16 destructive dialog.
  destructive: cn(
    "bg-danger text-white border border-danger",
    "hover:brightness-110 active:brightness-95",
    "dark:text-danger-bg",
  ),

  // Inline text action.
  link: cn(
    "bg-transparent border border-transparent text-brand-600 underline-offset-4",
    "hover:underline active:text-brand-700 dark:text-brand-500",
  ),

  // Tonal destructive — inline row actions where a solid red would shout.
  danger: cn(
    "bg-danger/10 text-danger border border-danger/25",
    "hover:bg-danger/15 active:bg-danger/20",
  ),

  // Tonal positive — Approve, Confirm, Convert.
  success: cn(
    "bg-success/10 text-success border border-success/25",
    "hover:bg-success/15 active:bg-success/20",
  ),

  // Tonal caution — Request changes, Pending.
  warning: cn(
    "bg-warning/12 text-warning border border-warning/30",
    "hover:bg-warning/18 active:bg-warning/24",
  ),
};

/** Heights follow §3.1: xs 24 · sm 28 · md 36 · lg 40 · icon 36. */
const sizeStyles: Record<ButtonSize, string> = {
  xs: "h-6 gap-1 rounded-md px-2 text-[11px]",
  sm: "h-7 gap-1.5 rounded-md px-2.5 text-[12px]",
  md: "h-9 gap-1.5 rounded-md px-3.5 text-[13.5px]",
  lg: "h-10 gap-2 rounded-md px-4 text-[14px]",
  icon: "size-9 rounded-md p-0",
  "icon-sm": "size-7 rounded-md p-0",
};

function Spinner({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      className={cn("animate-spin", className)}
    >
      <circle
        className="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
      />
    </svg>
  );
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      isLoading = false,
      leftIcon,
      rightIcon,
      fullWidth = false,
      disabled,
      children,
      type = "button",
      ...props
    },
    ref,
  ) => {
    const iconOnly = size === "icon" || size === "icon-sm";

    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled || isLoading}
        // A loading button is busy, not broken — announce it rather than just
        // dimming it, so a screen-reader user knows the click registered.
        aria-busy={isLoading || undefined}
        className={cn(
          "inline-flex shrink-0 items-center justify-center whitespace-nowrap font-semibold",
          "transition-[background-color,border-color,color,box-shadow,filter] duration-[120ms] ease-[cubic-bezier(0.2,0,0,1)]",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
          "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45",
          "cursor-pointer select-none",
          variantStyles[variant],
          sizeStyles[size],
          fullWidth && "w-full",
          className,
        )}
        {...props}
      >
        {isLoading ? (
          <Spinner className={cn(iconOnly ? "size-4" : "size-3.5", !iconOnly && "shrink-0")} />
        ) : (
          leftIcon && (
            <span className="grid shrink-0 place-items-center [&_svg]:size-4">
              {leftIcon}
            </span>
          )
        )}
        {!iconOnly && children != null && <span className="truncate">{children}</span>}
        {iconOnly && !isLoading && children}
        {!iconOnly && !isLoading && rightIcon && (
          <span className="grid shrink-0 place-items-center [&_svg]:size-3.5">
            {rightIcon}
          </span>
        )}
      </button>
    );
  },
);

Button.displayName = "Button";
