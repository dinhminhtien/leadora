import React from "react";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode;
  rightElement?: React.ReactNode;
  error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, icon, rightElement, error, type = "text", ...props }, ref) => (
    <div className="flex flex-col gap-1">
      <div className="relative flex items-center">
        {icon && <span className="absolute left-3.5 text-muted-foreground size-4 flex items-center justify-center pointer-events-none">{icon}</span>}
        <input
          ref={ref}
          type={type}
          className={`w-full rounded-xl border border-border bg-input py-2 px-3.5 text-sm text-foreground placeholder:text-muted-foreground/60 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-50 dark:shadow-none ${
            icon ? "pl-10" : "pl-4"
          } ${
            rightElement ? "pr-10" : "pr-4"
          } ${
            error ? "border-danger focus:border-danger focus:ring-danger/10" : ""
          } ${className || ""}`}
          {...props}
        />
        {rightElement && <div className="absolute right-3 flex items-center justify-center">{rightElement}</div>}
      </div>
      {error && <p className="text-xs text-danger font-medium pl-1 mt-0.5">{error}</p>}
    </div>
  )
);

Input.displayName = "Input";
