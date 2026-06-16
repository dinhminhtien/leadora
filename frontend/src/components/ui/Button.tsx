import React from "react";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "danger"
  | "success"
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

const variantStyles: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-white border border-primary/10 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.15),0_1px_2px_0_rgba(0,0,0,0.05)] hover:bg-primary/95 dark:border-primary/20",
  secondary:
    "bg-muted text-foreground border border-border shadow-sm hover:bg-zinc-200 dark:hover:bg-zinc-800/80",
  danger:
    "bg-danger text-white border border-danger/10 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.15),0_1px_2px_0_rgba(0,0,0,0.05)] hover:bg-danger/95",
  success:
    "bg-success text-white border border-success/10 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.15),0_1px_2px_0_rgba(0,0,0,0.05)] hover:bg-success/95",
  ghost:
    "text-muted-foreground hover:bg-muted hover:text-foreground",
  outline:
    "border border-border bg-background text-foreground hover:bg-muted shadow-sm",
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: "px-3 py-1.5 rounded-lg text-xs font-semibold",
  md: "px-4 py-2 rounded-xl text-sm font-semibold",
  lg: "px-5 py-2.5 rounded-xl text-base font-semibold",
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
        className={`inline-flex items-center justify-center gap-1.5 font-medium whitespace-nowrap transition-all duration-150 ease-out hover:-translate-y-[1px] active:translate-y-0 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 cursor-pointer ${variantStyles[variant]} ${sizeStyles[size]} ${className || ""}`}
        {...props}
      >
        {leftIcon && !isLoading && <span className="shrink-0 size-4 flex items-center justify-center">{leftIcon}</span>}
        {isLoading && (
          <svg className="animate-spin -ml-0.5 mr-1 h-3.5 w-3.5 text-current" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
        )}
        <span>{children}</span>
        {rightIcon && !isLoading && <span className="shrink-0 size-4 flex items-center justify-center">{rightIcon}</span>}
      </button>
    );
  }
);

Button.displayName = "Button";
