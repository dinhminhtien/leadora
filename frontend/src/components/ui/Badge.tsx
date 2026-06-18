import React from "react";

export type BadgeVariant =
  | "default"
  | "success"
  | "warning"
  | "danger"
  | "info"
  | "primary";

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  size?: "sm" | "md";
}

// Leadora Design System — Badge token spec (PDF §5)
const variantStyles: Record<BadgeVariant, string> = {
  default:  "bg-[#F1EFE8] text-[#444441] dark:bg-zinc-800 dark:text-zinc-300",
  primary:  "bg-[#E6F1FB] text-[#0C447C] dark:bg-[#0C2840] dark:text-[#85B7EB]",
  success:  "bg-[#EAF3DE] text-[#27500A] dark:bg-[#173404] dark:text-[#97C459]",
  warning:  "bg-[#FAEEDA] text-[#633806] dark:bg-[#412402] dark:text-[#EF9F27]",
  danger:   "bg-[#FCEBEB] text-[#791F1F] dark:bg-[#501313] dark:text-[#F09595]",
  info:     "bg-[#EEEDFE] text-[#3C3489] dark:bg-[#1E1A50] dark:text-[#7F77DD]",
};

export const Badge = React.forwardRef<HTMLSpanElement, BadgeProps>(
  (
    {
      className,
      variant = "default",
      size = "sm",
      children,
      ...props
    },
    ref
  ) => {
    const sizeClass = size === "sm" ? "px-2 py-0.5 text-xs" : "px-3 py-1 text-sm";

    return (
      <span
        ref={ref}
        className={`inline-flex items-center gap-1 rounded-full font-medium ${variantStyles[variant]} ${sizeClass} ${className || ""}`}
        {...props}
      >
        {children}
      </span>
    );
  }
);

Badge.displayName = "Badge";
