import React, { useState } from "react";

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode;
  rightElement?: React.ReactNode;
  error?: string;
  phoneOnly?: boolean;
  numericOnly?: boolean;
  decimalOnly?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      icon,
      rightElement,
      error: propError,
      type = "text",
      phoneOnly,
      numericOnly,
      decimalOnly,
      onChange,
      onKeyDown,
      onBlur,
      value,
      defaultValue,
      ...props
    },
    ref
  ) => {
    const [localError, setLocalError] = useState<string | null>(null);

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (phoneOnly || numericOnly || decimalOnly) {
        const allowedKeys = [
          "Backspace",
          "Delete",
          "Tab",
          "Enter",
          "ArrowLeft",
          "ArrowRight",
          "Home",
          "End",
        ];
        if (allowedKeys.includes(e.key) || e.ctrlKey || e.metaKey) {
          if (onKeyDown) onKeyDown(e);
          return;
        }
        if (decimalOnly && e.key === "." && !((e.target as HTMLInputElement).value || "").includes(".")) {
          if (onKeyDown) onKeyDown(e);
          return;
        }
        if (!/^\d$/.test(e.key)) {
          e.preventDefault();
          return;
        }
      }
      if (onKeyDown) onKeyDown(e);
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      let rawVal = e.target.value;

      if (phoneOnly) {
        // Automatically filter non-digits and limit to 10 digits
        rawVal = rawVal.replace(/\D/g, "").slice(0, 10);
        e.target.value = rawVal;

        // Dynamic format validation
        if (rawVal.length > 0) {
          if (rawVal.length < 10) {
            setLocalError(`Phone number must be 10 digits (${rawVal.length}/10)`);
          } else if (!/^(0[35789])\d{8}$/.test(rawVal)) {
            setLocalError("Must start with valid prefix (03, 05, 07, 08, 09)");
          } else {
            setLocalError(null);
          }
        } else {
          setLocalError(null);
        }
      } else if (numericOnly) {
        rawVal = rawVal.replace(/\D/g, "");
        e.target.value = rawVal;
      } else if (decimalOnly) {
        rawVal = rawVal.replace(/[^0-9.]/g, "");
        const parts = rawVal.split(".");
        if (parts.length > 2) {
          rawVal = parts[0] + "." + parts.slice(1).join("");
        }
        e.target.value = rawVal;
      }

      if (onChange) {
        onChange(e);
      }
    };

    const handleBlur = (e: React.FocusEvent<HTMLInputElement>) => {
      if (phoneOnly) {
        const val = e.target.value;
        if (val.length > 0 && !/^(0[35789])\d{8}$/.test(val)) {
          setLocalError("Invalid Vietnamese phone number (10 digits starting with 03, 05, 07, 08, 09)");
        } else {
          setLocalError(null);
        }
      }
      if (onBlur) onBlur(e);
    };

    const effectiveError = propError || localError;

    return (
      <div className="flex flex-col gap-1">
        <div className="relative flex items-center">
          {icon && (
            <span className="absolute left-3.5 text-muted-foreground size-4 flex items-center justify-center pointer-events-none">
              {icon}
            </span>
          )}
          <input
            ref={ref}
            type={phoneOnly ? "tel" : type}
            value={value}
            defaultValue={defaultValue}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            onBlur={handleBlur}
            maxLength={phoneOnly ? 10 : props.maxLength}
            className={`w-full rounded-xl border border-border bg-input py-2 px-3.5 text-sm text-foreground placeholder:text-muted-foreground/60 shadow-[inset_0_1.5px_3px_rgba(0,0,0,0.025)] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed disabled:opacity-50 dark:shadow-none ${
              icon ? "pl-10" : "pl-4"
            } ${rightElement ? "pr-10" : "pr-4"} ${
              effectiveError
                ? "border-danger focus:border-danger focus:ring-danger/10"
                : ""
            } ${className || ""}`}
            {...props}
          />
          {rightElement && (
            <div className="absolute right-3 flex items-center justify-center">
              {rightElement}
            </div>
          )}
        </div>
        {effectiveError && (
          <p className="text-xs text-danger font-medium pl-1 mt-0.5">
            {effectiveError}
          </p>
        )}
      </div>
    );
  }
);

Input.displayName = "Input";
