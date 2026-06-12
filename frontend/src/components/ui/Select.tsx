import React from "react";

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  error?: string;
  label?: string;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, label, children, ...props }, ref) => (
    <div className="flex flex-col gap-1">
      {label && <label className="text-xs font-semibold text-slate-500">{label}</label>}
      <select
        ref={ref}
        className={`w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-800 text-xs focus:outline-none focus:border-blue-500 focus:bg-white disabled:cursor-not-allowed disabled:opacity-50 transition ${
          error ? "border-red-500 focus:border-red-500 focus:ring-red-200" : ""
        } ${className || ""}`}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
);

Select.displayName = "Select";
