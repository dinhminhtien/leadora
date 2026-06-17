import React from "react";

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: string;
  label?: string;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, label, children, ...props }, ref) => (
    <div className="flex flex-col gap-1.5">
      {label && <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">{label}</label>}
      <select
        ref={ref}
        className={`w-full rounded-xl border border-border bg-input py-2 px-3 text-sm text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] disabled:cursor-not-allowed disabled:opacity-50 dark:shadow-none ${
          error ? "border-danger focus:border-danger focus:ring-danger/10" : ""
        } ${className || ""}`}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-xs text-danger font-medium pl-1 mt-0.5">{error}</p>}
    </div>
  )
);

Select.displayName = "Select";
