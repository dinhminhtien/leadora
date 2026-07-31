import React, { useState } from "react";

import {
  PHONE_MAX_DIGITS,
  PHONE_MESSAGE,
  PHONE_PATTERN,
  normalizePhone,
} from "@/shared/utils/phone";

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
        // Normalise *before* stripping, so a pasted `+84 912 345 678` becomes
        // `0912345678` and not `84912345678`. Stripping first eats the `+` that
        // identifies the country code, and with 11 digits now legal the wrong
        // reading would be accepted silently instead of refused — which is what
        // the old ten-digit cap accidentally protected against.
        rawVal = normalizePhone(rawVal)
          .replace(/\D/g, "")
          .slice(0, PHONE_MAX_DIGITS);
        e.target.value = rawVal;

        // Counts up while the user is still short, so the field says how far it
        // has to go rather than just refusing.
        if (rawVal.length === 0) {
          setLocalError(null);
        } else if (rawVal.length < 10) {
          setLocalError(`${PHONE_MESSAGE} (${rawVal.length}/10)`);
        } else {
          setLocalError(PHONE_PATTERN.test(rawVal) ? null : PHONE_MESSAGE);
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
        setLocalError(
          val.length > 0 && !PHONE_PATTERN.test(val) ? PHONE_MESSAGE : null
        );
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
            maxLength={phoneOnly ? PHONE_MAX_DIGITS : props.maxLength}
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
