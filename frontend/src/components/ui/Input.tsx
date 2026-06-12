import React from "react";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode;
  error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, icon, error, type = "text", ...props }, ref) => (
    <div className="flex flex-col gap-1">
      <div className="relative flex items-center">
        {icon && <span className="absolute left-3 text-muted-foreground">{icon}</span>}
        <input
          ref={ref}
          type={type}
          className={`w-full rounded-lg border border-border bg-input px-4 py-2 pl-${icon ? "10" : "4"} text-foreground placeholder-muted-foreground focus-visible:outline-none focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-50 ${
            error ? "border-danger focus-visible:border-danger focus-visible:ring-danger/20" : ""
          } ${className || ""}`}
          {...props}
        />
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  )
);

Input.displayName = "Input";
