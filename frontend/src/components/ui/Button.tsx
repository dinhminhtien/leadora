import React from "react";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "danger"
  | "success"
  | "warning"
  | "ghost"
  | "outline";

export type ButtonSize = "sm" | "md" | "lg";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

// Leadora Design System — Button token spec (PDF §4)
const variantStyles: Record<ButtonVariant, string> = {
  // Create, Submit, Save — main CTA
  primary:
    "bg-[#185FA5] text-[#E6F1FB] border border-[#0C447C] hover:bg-[#0C447C] dark:bg-[#378ADD] dark:border-[#185FA5] dark:hover:bg-[#185FA5]",

  // Edit, View — secondary action (white bg, gray border)
  secondary:
    "bg-white text-[#222222] border border-[#888780] hover:bg-[#F1EFE8] dark:bg-zinc-800 dark:text-zinc-100 dark:border-zinc-600 dark:hover:bg-zinc-700",

  // Delete, Remove — destructive action (light red bg per DS)
  danger:
    "bg-[#FCEBEB] text-[#A32D2D] border border-[#F7C1C1] hover:bg-[#F9D7D7] dark:bg-[#501313] dark:text-[#F09595] dark:border-[#791F1F] dark:hover:bg-[#791F1F]",

  // Approve, Confirm, Convert — positive outcome (light green bg per DS)
  success:
    "bg-[#EAF3DE] text-[#3B6D11] border border-[#C0DD97] hover:bg-[#DFF0D0] dark:bg-[#173404] dark:text-[#97C459] dark:border-[#3B6D11] dark:hover:bg-[#27500A]",

  // Request Changes, Pending — caution action (light amber bg per DS)
  warning:
    "bg-[#FAEEDA] text-[#854F0B] border border-[#FAC775] hover:bg-[#F5E0C0] dark:bg-[#412402] dark:text-[#EF9F27] dark:border-[#BA7517] dark:hover:bg-[#854F0B]",

  // Cancel — low-emphasis (transparent bg per DS)
  ghost:
    "bg-transparent text-[#666666] border border-[#CCCCCC] hover:bg-[#F1EFE8] hover:text-[#222222] dark:text-zinc-400 dark:border-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-100",

  // Generic outline — kept for backward compatibility
  outline:
    "border border-border bg-background text-foreground hover:bg-muted shadow-sm",
};

// Leadora Design System — Button size spec (PDF §4.1)
const sizeStyles: Record<ButtonSize, string> = {
  sm: "px-3 py-1.5 rounded-lg text-xs font-semibold",    // 5px 12px, 11px/22 DXA
  md: "px-4 py-2 rounded-lg text-sm font-semibold",      // 8px 18px, 13px/26 DXA
  lg: "px-5 py-2.5 rounded-lg text-sm font-semibold",    // 10px 22px, 14px/28 DXA
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      isLoading = false,
      leftIcon,
      rightIcon,
      disabled,
      children,
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={`inline-flex items-center justify-center gap-1.5 font-medium whitespace-nowrap transition-all duration-150 ease-out hover:-translate-y-px active:translate-y-0 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 cursor-pointer ${variantStyles[variant]} ${sizeStyles[size]} ${className || ""}`}
        {...props}
      >
        {leftIcon && !isLoading && (
          <span className="shrink-0 size-4 flex items-center justify-center">{leftIcon}</span>
        )}
        {isLoading && (
          <svg className="animate-spin -ml-0.5 mr-1 h-3.5 w-3.5 text-current" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
        )}
        <span>{children}</span>
        {rightIcon && !isLoading && (
          <span className="shrink-0 size-4 flex items-center justify-center">{rightIcon}</span>
        )}
      </button>
    );
  }
);

Button.displayName = "Button";
